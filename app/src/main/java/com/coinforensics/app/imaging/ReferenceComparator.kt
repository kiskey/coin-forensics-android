package com.coinforensics.app.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.coinforensics.app.model.ComparisonResult
import com.coinforensics.app.model.NormalizedRegion
import com.coinforensics.app.model.RegionComparison
import com.coinforensics.app.model.RegionShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ReferenceComparator {
    private const val SIZE = 224
    private const val DETAIL_SIZE = 512

    fun compare(targetInput: Bitmap, referenceInput: Bitmap): ComparisonResult {
        val target = centerCropSquare(targetInput, SIZE)
        val reference = centerCropSquare(referenceInput, SIZE)
        val targetGray = gray(target)
        val targetEdge = gradient(targetGray, SIZE, SIZE)
        val mask = circularMask(SIZE)

        var bestScore = Double.NEGATIVE_INFINITY
        var bestLum = 0.0
        var bestEdge = 0.0
        var bestRotation = 0f
        var bestScale = 1f
        var bestDx = 0
        var bestDy = 0
        var bestBitmap = reference

        val rotations = floatArrayOf(-5f, -3f, -1.5f, 0f, 1.5f, 3f, 5f)
        val scales = floatArrayOf(0.975f, 1.0f, 1.025f)
        val offsets = intArrayOf(-6, -3, 0, 3, 6)

        for (rotation in rotations) {
            for (scale in scales) {
                for (dy in offsets) {
                    for (dx in offsets) {
                        val transformed = transform(reference, rotation, scale, dx, dy)
                        val refGray = gray(transformed)
                        val refEdge = gradient(refGray, SIZE, SIZE)
                        val lumCorr = correlation(targetGray, refGray, mask)
                        val edgeCorr = correlation(targetEdge, refEdge, mask)
                        val normalizedLum = ((lumCorr + 1.0) / 2.0).coerceIn(0.0, 1.0)
                        val normalizedEdge = ((edgeCorr + 1.0) / 2.0).coerceIn(0.0, 1.0)
                        val combined = normalizedLum * 0.34 + normalizedEdge * 0.66
                        if (combined > bestScore) {
                            bestScore = combined
                            bestLum = normalizedLum
                            bestEdge = normalizedEdge
                            bestRotation = rotation
                            bestScale = scale
                            bestDx = dx
                            bestDy = dy
                            bestBitmap = transformed
                        }
                    }
                }
            }
        }

        val score = (bestScore * 100.0).coerceIn(0.0, 100.0)
        return ComparisonResult(
            consistencyScore = score,
            luminanceCorrelation = bestLum * 100.0,
            edgeCorrelation = bestEdge * 100.0,
            bestRotationDegrees = bestRotation,
            bestScale = bestScale,
            bestOffsetX = bestDx,
            bestOffsetY = bestDy,
            differenceBitmap = differenceMap(target, bestBitmap, mask),
            interpretation = ScoreMath.interpretation(score)
        )
    }


    fun compareRegions(
        targetInput: Bitmap,
        referenceInput: Bitmap,
        overall: ComparisonResult,
        regions: List<NormalizedRegion>
    ): List<RegionComparison> {
        if (regions.isEmpty()) return emptyList()
        // Alignment is solved cheaply at 224 px, but region evidence is measured at a higher
        // working scale so high-resolution user photographs retain materially more die detail.
        val size = DETAIL_SIZE
        val target = centerCropSquareDynamic(targetInput, size)
        val reference = centerCropSquareDynamic(referenceInput, size)
        val offsetScale = size.toFloat() / SIZE.toFloat()
        val alignedReference = transformDynamic(
            reference,
            overall.bestRotationDegrees,
            overall.bestScale,
            (overall.bestOffsetX * offsetScale).roundToInt(),
            (overall.bestOffsetY * offsetScale).roundToInt(),
            size
        )
        val targetGray = grayDynamic(target, size)
        val referenceGray = grayDynamic(alignedReference, size)
        val targetEdge = gradient(targetGray, size, size)
        val referenceEdge = gradient(referenceGray, size, size)

        return regions.map { region ->
            val mask = regionMask(region, size)
            val lum = ((correlation(targetGray, referenceGray, mask) + 1.0) / 2.0)
                .coerceIn(0.0, 1.0) * 100.0
            val edge = ((correlation(targetEdge, referenceEdge, mask) + 1.0) / 2.0)
                .coerceIn(0.0, 1.0) * 100.0
            val combined = (lum * 0.25 + edge * 0.75).coerceIn(0.0, 100.0)
            RegionComparison(
                regionId = region.id,
                label = region.label,
                side = region.side,
                consistencyScore = combined,
                luminanceScore = lum,
                edgeScore = edge,
                interpretation = ScoreMath.interpretation(combined)
            )
        }
    }

    private fun regionMask(region: NormalizedRegion, size: Int = SIZE): BooleanArray {
        val out = BooleanArray(size * size)
        val cx = (size - 1) / 2.0
        val cy = (size - 1) / 2.0
        val radius = size * 0.455
        val coinR2 = radius * radius
        val left = (region.left.coerceIn(0.0, 1.0) * size).toInt()
        val top = (region.top.coerceIn(0.0, 1.0) * size).toInt()
        val right = (region.right.coerceIn(0.0, 1.0) * size).toInt()
        val bottom = (region.bottom.coerceIn(0.0, 1.0) * size).toInt()

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                val dist2 = dx * dx + dy * dy
                val insideCoin = dist2 <= coinR2
                val include = when (region.shape) {
                    RegionShape.FULL_COIN -> insideCoin
                    RegionShape.RECT -> insideCoin && x >= left && x < right && y >= top && y < bottom
                    RegionShape.ANNULUS -> {
                        val normalizedR = sqrt(dist2) / radius
                        insideCoin && normalizedR >= region.innerRadius && normalizedR <= region.outerRadius
                    }
                }
                out[y * size + x] = include
            }
        }
        return out
    }

    private fun centerCropSquareDynamic(src: Bitmap, size: Int): Bitmap = centerCropSquare(src, size)

    private fun transformDynamic(src: Bitmap, rotation: Float, scale: Float, dx: Int, dy: Int, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val matrix = Matrix().apply {
            postTranslate(-size / 2f, -size / 2f)
            postScale(scale, scale)
            postRotate(rotation)
            postTranslate(size / 2f + dx, size / 2f + dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun grayDynamic(bitmap: Bitmap, size: Int): DoubleArray {
        val px = IntArray(size * size)
        bitmap.getPixels(px, 0, size, 0, 0, size, size)
        return DoubleArray(px.size) { i ->
            val c = px[i]
            0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)
        }
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val side = min(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val crop = Bitmap.createBitmap(src, left, top, side, side)
        return Bitmap.createScaledBitmap(crop, size, size, true)
    }

    private fun transform(src: Bitmap, rotation: Float, scale: Float, dx: Int, dy: Int): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val matrix = Matrix().apply {
            postTranslate(-SIZE / 2f, -SIZE / 2f)
            postScale(scale, scale)
            postRotate(rotation)
            postTranslate(SIZE / 2f + dx, SIZE / 2f + dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun gray(bitmap: Bitmap): DoubleArray {
        val px = IntArray(SIZE * SIZE)
        bitmap.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return DoubleArray(px.size) { i ->
            val c = px[i]
            0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)
        }
    }

    private fun gradient(gray: DoubleArray, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] +
                    gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] +
                    gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                out[i] = sqrt(gx * gx + gy * gy)
            }
        }
        return out
    }

    private fun circularMask(size: Int): BooleanArray {
        val out = BooleanArray(size * size)
        val cx = (size - 1) / 2.0
        val cy = (size - 1) / 2.0
        val radius = size * 0.455
        val r2 = radius * radius
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                out[y * size + x] = dx * dx + dy * dy <= r2
            }
        }
        return out
    }

    private fun correlation(a: DoubleArray, b: DoubleArray, mask: BooleanArray): Double {
        var count = 0
        var sumA = 0.0
        var sumB = 0.0
        for (i in a.indices) {
            if (!mask[i]) continue
            sumA += a[i]
            sumB += b[i]
            count++
        }
        if (count < 10) return 0.0
        val meanA = sumA / count
        val meanB = sumB / count
        var numerator = 0.0
        var denomA = 0.0
        var denomB = 0.0
        for (i in a.indices) {
            if (!mask[i]) continue
            val da = a[i] - meanA
            val db = b[i] - meanB
            numerator += da * db
            denomA += da * da
            denomB += db * db
        }
        val denom = sqrt(denomA * denomB)
        return if (denom < 1e-9) 0.0 else (numerator / denom).coerceIn(-1.0, 1.0)
    }

    private fun differenceMap(target: Bitmap, reference: Bitmap, mask: BooleanArray): Bitmap {
        val t = gray(target)
        val r = gray(reference)
        val tStats = stats(t, mask)
        val rStats = stats(r, mask)
        val targetPx = IntArray(SIZE * SIZE)
        target.getPixels(targetPx, 0, SIZE, 0, 0, SIZE, SIZE)
        val out = IntArray(targetPx.size)

        for (i in out.indices) {
            val base = (0.2126 * Color.red(targetPx[i]) + 0.7152 * Color.green(targetPx[i]) + 0.0722 * Color.blue(targetPx[i])).roundToInt()
            if (!mask[i]) {
                val d = (base * 0.35).roundToInt()
                out[i] = Color.rgb(d, d, d)
                continue
            }
            val zt = (t[i] - tStats.first) / tStats.second
            val zr = (r[i] - rStats.first) / rStats.second
            val delta = abs(zt - zr).coerceIn(0.0, 3.0) / 3.0
            val red = max(base * 0.35, 255.0 * delta).roundToInt().coerceIn(0, 255)
            val green = (base * (0.50 - 0.25 * delta)).roundToInt().coerceIn(0, 255)
            val blue = (base * (0.50 - 0.32 * delta)).roundToInt().coerceIn(0, 255)
            out[i] = Color.rgb(red, green, blue)
        }
        return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun stats(values: DoubleArray, mask: BooleanArray): Pair<Double, Double> {
        var n = 0
        var sum = 0.0
        for (i in values.indices) if (mask[i]) { sum += values[i]; n++ }
        val mean = if (n == 0) 0.0 else sum / n
        var sq = 0.0
        for (i in values.indices) if (mask[i]) sq += (values[i] - mean) * (values[i] - mean)
        val sd = sqrt(sq / max(1, n)).coerceAtLeast(1.0)
        return mean to sd
    }
}
