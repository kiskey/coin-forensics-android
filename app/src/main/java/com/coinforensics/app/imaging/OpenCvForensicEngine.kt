package com.coinforensics.app.imaging

import android.graphics.Bitmap
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticStatus
import com.coinforensics.app.model.NormalizedRegion
import com.coinforensics.app.model.RegionShape
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native OpenCV forensic comparator.
 *
 * The existing ReferenceComparator remains an independent deterministic signal. This engine adds:
 *  - coin-boundary normalization using Hough circle detection with a safe center-crop fallback,
 *  - CLAHE photometric normalization,
 *  - ORB keypoints + Hamming/Lowe ratio matching,
 *  - RANSAC homography registration,
 *  - Canny edge overlap,
 *  - Laplacian microtexture correlation,
 *  - masked luminance correlation,
 *  - source-pack region scoring and a visual difference heatmap.
 *
 * It never synthesizes UV/IR data and never turns a similarity score into an authenticity
 * probability. The caller must compare genuine and counterfeit evidence separately.
 */
object OpenCvForensicEngine {
    private const val ANALYSIS_SIZE = 1024
    private const val ORB_FEATURES = 2600
    private const val LOWE_RATIO = 0.76
    private const val MIN_HOMOGRAPHY_MATCHES = 8
    private const val COIN_RADIUS_FRACTION = 0.485

    @Volatile
    private var loadState: Boolean? = null

    fun isAvailable(): Boolean {
        loadState?.let { return it }
        return synchronized(this) {
            loadState ?: run {
                val loaded = try {
                    OpenCVLoader.initLocal()
                } catch (_: Throwable) {
                    false
                }
                loadState = loaded
                loaded
            }
        }
    }

    fun compare(
        targetBitmap: Bitmap,
        referenceBitmap: Bitmap,
        regions: List<NormalizedRegion> = emptyList()
    ): OpenCvPairAnalysis {
        if (!isAvailable()) {
            return OpenCvPairAnalysis.unavailable("OpenCV native runtime could not be initialized.")
        }
        return try {
            compareInternal(targetBitmap, referenceBitmap, regions)
        } catch (error: Throwable) {
            OpenCvPairAnalysis.unavailable(
                "OpenCV comparison failed safely: ${error.javaClass.simpleName}${error.message?.let { ": $it" } ?: ""}"
            )
        }
    }

    fun consensus(results: List<OpenCvPairAnalysis>): OpenCvConsensus? {
        val usable = results.filter { it.available }
        if (usable.isEmpty()) return null
        val regionIds = usable.flatMap { it.regionMetrics }.map { it.regionId }.distinct()
        return OpenCvConsensus(
            controlCount = usable.size,
            overallScore = median(usable.map { it.overallScore }),
            geometryScore = median(usable.map { it.geometryScore }),
            edgeScore = median(usable.map { it.edgeScore }),
            textureScore = median(usable.map { it.textureScore }),
            luminanceScore = median(usable.map { it.luminanceScore }),
            registrationReliability = median(usable.map { it.registrationReliability }),
            goodMatches = median(usable.map { it.goodMatches.toDouble() }).roundToInt(),
            inliers = median(usable.map { it.inliers.toDouble() }).roundToInt(),
            regionScores = regionIds.associateWith { id ->
                median(usable.mapNotNull { result -> result.regionMetrics.firstOrNull { it.regionId == id }?.overallScore })
            }
        )
    }

    private fun compareInternal(
        targetBitmap: Bitmap,
        referenceBitmap: Bitmap,
        regions: List<NormalizedRegion>
    ): OpenCvPairAnalysis {
        val targetColor = normalizeCoin(targetBitmap)
        val referenceColor = normalizeCoin(referenceBitmap)
        val targetGray = normalizedGray(targetColor)
        val referenceGray = normalizedGray(referenceColor)
        val coinMask = fullCoinMask(ANALYSIS_SIZE)

        val targetKeypoints = MatOfKeyPoint()
        val referenceKeypoints = MatOfKeyPoint()
        val targetDescriptors = Mat()
        val referenceDescriptors = Mat()
        val orb = ORB.create(ORB_FEATURES)
        orb.detectAndCompute(targetGray, coinMask, targetKeypoints, targetDescriptors)
        orb.detectAndCompute(referenceGray, coinMask, referenceKeypoints, referenceDescriptors)

        val goodMatches = mutableListOf<DMatch>()
        if (!targetDescriptors.empty() && !referenceDescriptors.empty()) {
            val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
            val knn = ArrayList<MatOfDMatch>()
            matcher.knnMatch(referenceDescriptors, targetDescriptors, knn, 2)
            knn.forEach { pair ->
                val matches = pair.toArray()
                if (matches.size >= 2 && matches[0].distance.toDouble() < LOWE_RATIO * matches[1].distance.toDouble()) {
                    goodMatches += matches[0]
                }
                pair.release()
            }
            matcher.clear()
        }

        val inlierMask = Mat()
        var homography = Mat()
        var inliers = 0
        var homographyAvailable = false
        if (goodMatches.size >= MIN_HOMOGRAPHY_MATCHES) {
            val refPoints = referenceKeypoints.toArray()
            val targetPoints = targetKeypoints.toArray()
            val from = MatOfPoint2f()
            val to = MatOfPoint2f()
            from.fromList(goodMatches.map { refPoints[it.queryIdx].pt })
            to.fromList(goodMatches.map { targetPoints[it.trainIdx].pt })
            homography = Calib3d.findHomography(from, to, Calib3d.RANSAC, 3.0, inlierMask)
            homographyAvailable = !homography.empty()
            if (homographyAvailable && !inlierMask.empty()) inliers = Core.countNonZero(inlierMask)
            from.release()
            to.release()
        }

        if (!homographyAvailable) {
            homography.release()
            homography = identityHomography()
        }

        val alignedReferenceColor = Mat()
        Imgproc.warpPerspective(
            referenceColor,
            alignedReferenceColor,
            homography,
            Size(ANALYSIS_SIZE.toDouble(), ANALYSIS_SIZE.toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(0.0, 0.0, 0.0, 0.0)
        )
        val alignedReferenceGray = normalizedGray(alignedReferenceColor)

        val targetEdges = canny(targetGray)
        val referenceEdges = canny(alignedReferenceGray)
        val targetTexture = laplacianMagnitude(targetGray)
        val referenceTexture = laplacianMagnitude(alignedReferenceGray)

        val inlierRatio = if (goodMatches.isEmpty()) 0.0 else inliers.toDouble() / goodMatches.size.toDouble()
        val matchCoverage = (goodMatches.size / 120.0).coerceIn(0.0, 1.0)
        val registrationReliability = if (homographyAvailable) {
            (inlierRatio * 0.72 + matchCoverage * 0.28).coerceIn(0.0, 1.0)
        } else 0.0
        val geometryScore = if (homographyAvailable) {
            ((inlierRatio * 0.72 + matchCoverage * 0.28) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        val luminanceScore = correlationScore(targetGray, alignedReferenceGray, coinMask)
        val edgeScore = edgeDice(targetEdges, referenceEdges, coinMask) * 100.0
        val textureScore = correlationScore(targetTexture, referenceTexture, coinMask)

        val overallScore = if (homographyAvailable) {
            (geometryScore * 0.30 + edgeScore * 0.35 + textureScore * 0.20 + luminanceScore * 0.15)
                .coerceIn(0.0, 100.0)
        } else {
            // Center/circle-normalized evidence remains useful when keypoint registration fails,
            // but cap it because die-level geometric registration was not established.
            min(78.0, edgeScore * 0.48 + textureScore * 0.30 + luminanceScore * 0.22)
        }

        val regionMetrics = regions.map { region ->
            val mask = regionMask(region, ANALYSIS_SIZE)
            val localLum = correlationScore(targetGray, alignedReferenceGray, mask)
            val localEdge = edgeDice(targetEdges, referenceEdges, mask) * 100.0
            val localTexture = correlationScore(targetTexture, referenceTexture, mask)
            val localOverall = if (homographyAvailable) {
                (localEdge * 0.46 + localTexture * 0.32 + localLum * 0.22).coerceIn(0.0, 100.0)
            } else {
                min(78.0, localEdge * 0.50 + localTexture * 0.32 + localLum * 0.18)
            }
            mask.release()
            OpenCvRegionMetric(
                regionId = region.id,
                label = region.label,
                side = region.side,
                overallScore = localOverall,
                edgeScore = localEdge,
                textureScore = localTexture,
                luminanceScore = localLum
            )
        }

        val alignedBitmap = matToBitmap(alignedReferenceColor)
        val heatmap = differenceHeatmap(targetGray, alignedReferenceGray, coinMask)
        val targetKeypointCount = targetKeypoints.rows().coerceAtLeast(targetKeypoints.toArray().size)
        val referenceKeypointCount = referenceKeypoints.rows().coerceAtLeast(referenceKeypoints.toArray().size)

        releaseAll(
            targetColor,
            referenceColor,
            targetGray,
            referenceGray,
            coinMask,
            targetKeypoints,
            referenceKeypoints,
            targetDescriptors,
            referenceDescriptors,
            inlierMask,
            homography,
            alignedReferenceColor,
            alignedReferenceGray,
            targetEdges,
            referenceEdges,
            targetTexture,
            referenceTexture
        )
        orb.clear()

        return OpenCvPairAnalysis(
            available = true,
            overallScore = overallScore,
            geometryScore = geometryScore,
            edgeScore = edgeScore,
            textureScore = textureScore,
            luminanceScore = luminanceScore,
            registrationReliability = registrationReliability,
            targetKeypoints = targetKeypointCount,
            referenceKeypoints = referenceKeypointCount,
            goodMatches = goodMatches.size,
            inliers = inliers,
            inlierRatio = inlierRatio,
            usedHomography = homographyAvailable,
            regionMetrics = regionMetrics,
            alignedReference = alignedBitmap,
            differenceHeatmap = heatmap,
            note = if (homographyAvailable) {
                "ORB/Hamming features registered with RANSAC homography."
            } else {
                "ORB/RANSAC registration was insufficient; circle/center-normalized appearance metrics were used with a score cap."
            }
        )
    }

    private fun normalizeCoin(bitmap: Bitmap): Mat {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val rgba = when (src.channels()) {
            4 -> src
            3 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_RGB2RGBA); src.release() }
            1 -> Mat().also { Imgproc.cvtColor(src, it, Imgproc.COLOR_GRAY2RGBA); src.release() }
            else -> src
        }

        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val maxDim = max(gray.cols(), gray.rows()).coerceAtLeast(1)
        val detectScale = min(1.0, 1200.0 / maxDim.toDouble())
        val detect = Mat()
        val detectWidth = max(1, (gray.cols() * detectScale).roundToInt())
        val detectHeight = max(1, (gray.rows() * detectScale).roundToInt())
        Imgproc.resize(gray, detect, Size(detectWidth.toDouble(), detectHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.GaussianBlur(detect, detect, Size(7.0, 7.0), 1.4)

        val circles = Mat()
        val minDimension = min(detect.cols(), detect.rows())
        if (minDimension >= 120) {
            Imgproc.HoughCircles(
                detect,
                circles,
                Imgproc.HOUGH_GRADIENT,
                1.2,
                minDimension / 3.5,
                120.0,
                38.0,
                (minDimension * 0.25).roundToInt(),
                (minDimension * 0.54).roundToInt()
            )
        }

        val rect = chooseCoinRect(rgba.cols(), rgba.rows(), circles, detectScale)
        val cropped = Mat(rgba, rect).clone()
        val resized = Mat()
        Imgproc.resize(
            cropped,
            resized,
            Size(ANALYSIS_SIZE.toDouble(), ANALYSIS_SIZE.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_AREA
        )

        val mask = fullCoinMask(ANALYSIS_SIZE)
        val normalized = Mat.zeros(ANALYSIS_SIZE, ANALYSIS_SIZE, resized.type())
        resized.copyTo(normalized, mask)

        if (rgba !== src) rgba.release() else src.release()
        gray.release()
        detect.release()
        circles.release()
        cropped.release()
        resized.release()
        mask.release()
        return normalized
    }

    private fun chooseCoinRect(width: Int, height: Int, circles: Mat, detectScale: Double): Rect {
        if (!circles.empty() && circles.cols() > 0 && detectScale > 0.0) {
            var best: DoubleArray? = null
            var bestScore = Double.NEGATIVE_INFINITY
            val centerX = (width * detectScale) / 2.0
            val centerY = (height * detectScale) / 2.0
            for (i in 0 until circles.cols()) {
                val c = circles.get(0, i) ?: continue
                if (c.size < 3) continue
                val distance = hypot(c[0] - centerX, c[1] - centerY)
                val score = c[2] - distance * 0.20
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            best?.let { c ->
                val cx = c[0] / detectScale
                val cy = c[1] / detectScale
                val radius = c[2] / detectScale
                var side = (radius * 2.10).roundToInt()
                side = side.coerceIn(1, min(width, height))
                val x = (cx - side / 2.0).roundToInt().coerceIn(0, max(0, width - side))
                val y = (cy - side / 2.0).roundToInt().coerceIn(0, max(0, height - side))
                return Rect(x, y, side, side)
            }
        }
        val side = min(width, height).coerceAtLeast(1)
        return Rect((width - side) / 2, (height - side) / 2, side, side)
    }

    private fun normalizedGray(color: Mat): Mat {
        val gray = Mat()
        when (color.channels()) {
            4 -> Imgproc.cvtColor(color, gray, Imgproc.COLOR_RGBA2GRAY)
            3 -> Imgproc.cvtColor(color, gray, Imgproc.COLOR_RGB2GRAY)
            else -> color.copyTo(gray)
        }
        val out = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(gray, out)
        clahe.collectGarbage()
        clahe.clear()
        gray.release()
        return out
    }

    private fun canny(gray: Mat): Mat = Mat().also {
        Imgproc.Canny(gray, it, 55.0, 145.0, 3, true)
    }

    private fun laplacianMagnitude(gray: Mat): Mat {
        val lap = Mat()
        val absLap = Mat()
        Imgproc.Laplacian(gray, lap, CvType.CV_16S, 3, 1.0, 0.0, Core.BORDER_DEFAULT)
        Core.convertScaleAbs(lap, absLap)
        lap.release()
        return absLap
    }

    private fun identityHomography(): Mat = Mat.zeros(3, 3, CvType.CV_64F).apply {
        put(0, 0, 1.0)
        put(1, 1, 1.0)
        put(2, 2, 1.0)
    }

    private fun fullCoinMask(size: Int): Mat = Mat.zeros(size, size, CvType.CV_8UC1).apply {
        Imgproc.circle(
            this,
            Point(size / 2.0, size / 2.0),
            (size * COIN_RADIUS_FRACTION).roundToInt(),
            Scalar(255.0),
            -1,
            Imgproc.LINE_AA,
            0
        )
    }

    private fun regionMask(region: NormalizedRegion, size: Int): Mat {
        val mask = Mat.zeros(size, size, CvType.CV_8UC1)
        val center = Point(size / 2.0, size / 2.0)
        val radius = size * COIN_RADIUS_FRACTION
        when (region.shape) {
            RegionShape.FULL_COIN -> Imgproc.circle(mask, center, radius.roundToInt(), Scalar(255.0), -1)
            RegionShape.RECT -> {
                val left = (region.left.coerceIn(0.0, 1.0) * size).roundToInt()
                val top = (region.top.coerceIn(0.0, 1.0) * size).roundToInt()
                val right = (region.right.coerceIn(0.0, 1.0) * size).roundToInt()
                val bottom = (region.bottom.coerceIn(0.0, 1.0) * size).roundToInt()
                Imgproc.rectangle(mask, Point(left.toDouble(), top.toDouble()), Point(right.toDouble(), bottom.toDouble()), Scalar(255.0), -1)
                val coin = fullCoinMask(size)
                val clipped = Mat()
                Core.bitwise_and(mask, coin, clipped)
                mask.release()
                coin.release()
                return clipped
            }
            RegionShape.ANNULUS -> {
                Imgproc.circle(mask, center, (radius * region.outerRadius.coerceIn(0.0, 1.0)).roundToInt(), Scalar(255.0), -1)
                Imgproc.circle(mask, center, (radius * region.innerRadius.coerceIn(0.0, 1.0)).roundToInt(), Scalar(0.0), -1)
            }
        }
        return mask
    }

    private fun correlationScore(a: Mat, b: Mat, mask: Mat): Double {
        if (a.empty() || b.empty() || mask.empty()) return 0.0
        val count = (a.total()).toInt()
        if (count <= 0 || b.total() != a.total() || mask.total() != a.total()) return 0.0
        val aa = ByteArray(count)
        val bb = ByteArray(count)
        val mm = ByteArray(count)
        a.get(0, 0, aa)
        b.get(0, 0, bb)
        mask.get(0, 0, mm)

        var n = 0
        var sumA = 0.0
        var sumB = 0.0
        for (i in 0 until count) {
            if ((mm[i].toInt() and 0xff) == 0) continue
            sumA += aa[i].toInt() and 0xff
            sumB += bb[i].toInt() and 0xff
            n++
        }
        if (n < 64) return 0.0
        val meanA = sumA / n
        val meanB = sumB / n
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        for (i in 0 until count) {
            if ((mm[i].toInt() and 0xff) == 0) continue
            val da = (aa[i].toInt() and 0xff) - meanA
            val db = (bb[i].toInt() and 0xff) - meanB
            covariance += da * db
            varianceA += da * da
            varianceB += db * db
        }
        val denom = kotlin.math.sqrt(varianceA * varianceB)
        val corr = if (denom < 1e-9) 0.0 else (covariance / denom).coerceIn(-1.0, 1.0)
        return (((corr + 1.0) / 2.0) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun edgeDice(a: Mat, b: Mat, mask: Mat): Double {
        val maskedA = Mat()
        val maskedB = Mat()
        val intersection = Mat()
        Core.bitwise_and(a, a, maskedA, mask)
        Core.bitwise_and(b, b, maskedB, mask)
        Core.bitwise_and(maskedA, maskedB, intersection)
        val countA = Core.countNonZero(maskedA).toDouble()
        val countB = Core.countNonZero(maskedB).toDouble()
        val countIntersection = Core.countNonZero(intersection).toDouble()
        maskedA.release()
        maskedB.release()
        intersection.release()
        return if (countA + countB < 1.0) 0.0 else (2.0 * countIntersection / (countA + countB)).coerceIn(0.0, 1.0)
    }

    private fun differenceHeatmap(targetGray: Mat, referenceGray: Mat, mask: Mat): Bitmap {
        val diff = Mat()
        val masked = Mat()
        val normalized = Mat()
        val heat = Mat()
        val rgba = Mat()
        Core.absdiff(targetGray, referenceGray, diff)
        Core.bitwise_and(diff, diff, masked, mask)
        Core.normalize(masked, normalized, 0.0, 255.0, Core.NORM_MINMAX)
        Imgproc.applyColorMap(normalized, heat, Imgproc.COLORMAP_TURBO)
        Imgproc.cvtColor(heat, rgba, Imgproc.COLOR_BGR2RGBA)
        val bitmap = matToBitmap(rgba)
        releaseAll(diff, masked, normalized, heat, rgba)
        return bitmap
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    private fun releaseAll(vararg mats: Mat) = mats.forEach { it.release() }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}

data class OpenCvRegionMetric(
    val regionId: String,
    val label: String,
    val side: CoinSide,
    val overallScore: Double,
    val edgeScore: Double,
    val textureScore: Double,
    val luminanceScore: Double
)

data class OpenCvPairAnalysis(
    val available: Boolean,
    val overallScore: Double,
    val geometryScore: Double,
    val edgeScore: Double,
    val textureScore: Double,
    val luminanceScore: Double,
    val registrationReliability: Double,
    val targetKeypoints: Int,
    val referenceKeypoints: Int,
    val goodMatches: Int,
    val inliers: Int,
    val inlierRatio: Double,
    val usedHomography: Boolean,
    val regionMetrics: List<OpenCvRegionMetric>,
    val alignedReference: Bitmap?,
    val differenceHeatmap: Bitmap?,
    val note: String
) {
    companion object {
        fun unavailable(note: String) = OpenCvPairAnalysis(
            available = false,
            overallScore = 0.0,
            geometryScore = 0.0,
            edgeScore = 0.0,
            textureScore = 0.0,
            luminanceScore = 0.0,
            registrationReliability = 0.0,
            targetKeypoints = 0,
            referenceKeypoints = 0,
            goodMatches = 0,
            inliers = 0,
            inlierRatio = 0.0,
            usedHomography = false,
            regionMetrics = emptyList(),
            alignedReference = null,
            differenceHeatmap = null,
            note = note
        )
    }
}

data class OpenCvConsensus(
    val controlCount: Int,
    val overallScore: Double,
    val geometryScore: Double,
    val edgeScore: Double,
    val textureScore: Double,
    val luminanceScore: Double,
    val registrationReliability: Double,
    val goodMatches: Int,
    val inliers: Int,
    val regionScores: Map<String, Double>
)

/** Pure policy kept separate from native OpenCV calls so JVM unit tests can verify verdict behavior. */
object OpenCvDecisionPolicy {
    fun genuineStatus(score: Double, registrationReliability: Double): DiagnosticStatus = when {
        registrationReliability < 0.18 -> DiagnosticStatus.MANUAL_REVIEW
        score >= 82.0 -> DiagnosticStatus.CONSISTENT
        score >= 65.0 -> DiagnosticStatus.CAUTION
        score < 52.0 && registrationReliability >= 0.45 -> DiagnosticStatus.INCONSISTENT
        else -> DiagnosticStatus.CAUTION
    }

    fun counterfeitCaution(
        counterfeitScore: Double,
        genuineConsensusScore: Double?,
        registrationReliability: Double,
        authoritative: Boolean
    ): Boolean = authoritative &&
        genuineConsensusScore != null &&
        registrationReliability >= 0.30 &&
        counterfeitScore >= 80.0 &&
        counterfeitScore - genuineConsensusScore >= 7.0
}
