package com.coinforensics.app.imaging

import android.graphics.Bitmap
import android.graphics.Color
import com.coinforensics.app.model.EvidenceFinding
import com.coinforensics.app.model.ForensicAnalysis
import com.coinforensics.app.model.ForensicMode
import com.coinforensics.app.model.ImageQualityMetrics
import com.coinforensics.app.model.Severity
import com.coinforensics.app.model.SurfaceMetrics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object ForensicEngine {
    private const val MAX_PROCESS_DIMENSION = 1400
    private const val MAX_ANALYSIS_DIMENSION = 900

    fun process(input: Bitmap, mode: ForensicMode, intensity: Float = 1f): Bitmap {
        val bitmap = scaledForWork(input, MAX_PROCESS_DIMENSION)
        if (mode == ForensicMode.ORIGINAL) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = toGray(pixels)
        val out = IntArray(pixels.size)

        when (mode) {
            ForensicMode.GRAYSCALE -> grayToArgb(gray, out)
            ForensicMode.RED -> channelToArgb(pixels, out, Channel.RED)
            ForensicMode.GREEN -> channelToArgb(pixels, out, Channel.GREEN)
            ForensicMode.BLUE -> channelToArgb(pixels, out, Channel.BLUE)
            ForensicMode.LOCAL_CONTRAST -> localContrast(gray, out, w, h, intensity)
            ForensicMode.HIGH_PASS -> highPass(gray, out, w, h, intensity)
            ForensicMode.SOBEL -> sobel(gray, out, w, h, intensity)
            ForensicMode.LAPLACIAN -> laplacian(gray, out, w, h, intensity)
            ForensicMode.TEXTURE_VARIANCE -> textureVariance(gray, out, w, h, intensity)
            ForensicMode.SPECULAR -> specular(pixels, out, intensity)
            ForensicMode.RELIEF -> pseudoRelief(gray, out, w, h, intensity)
            ForensicMode.FALSE_COLOR -> falseColor(gray, out, intensity)
            ForensicMode.ORIGINAL -> pixels.copyInto(out)
        }

        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    fun analyze(input: Bitmap): ForensicAnalysis {
        val bitmap = scaledForWork(input, MAX_ANALYSIS_DIMENSION)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = toGray(pixels)

        val minDimOriginal = min(input.width, input.height).toDouble()
        val resolutionScore = ScoreMath.percent((minDimOriginal - 500.0) / 1300.0)

        var sum = 0.0
        var sumSq = 0.0
        var highlights = 0
        var shadows = 0
        var glare = 0
        for (i in pixels.indices) {
            val y = gray[i].toDouble()
            sum += y
            sumSq += y * y
            if (y >= 250) highlights++
            if (y <= 5) shadows++

            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            if (mx >= 238 && sat < 0.11) glare++
        }

        val n = gray.size.toDouble().coerceAtLeast(1.0)
        val mean = sum / n
        val variance = max(0.0, sumSq / n - mean * mean)
        val stdDev = sqrt(variance)
        val contrastScore = ScoreMath.percent(stdDev / 62.0)

        val lap = laplacianValues(gray, w, h)
        val lapMean = lap.average()
        val lapVar = lap.fold(0.0) { acc, v -> acc + (v - lapMean).pow(2) } / lap.size.coerceAtLeast(1)
        val sharpnessNorm = 1.0 - exp(-lapVar / 1050.0)
        val sharpnessScore = ScoreMath.percent(sharpnessNorm)

        val highlightPercent = highlights * 100.0 / n
        val shadowPercent = shadows * 100.0 / n
        val glarePercent = glare * 100.0 / n
        val clippingPenalty = ((highlightPercent + shadowPercent) / 12.0).coerceIn(0.0, 0.35)
        val glarePenalty = (glarePercent / 18.0).coerceIn(0.0, 0.30)
        val overall = ScoreMath.weighted(
            resolutionScore to 0.25,
            sharpnessScore to 0.35,
            contrastScore to 0.25,
            100.0 to 0.15
        ) * (1.0 - clippingPenalty) * (1.0 - glarePenalty)

        val quality = ImageQualityMetrics(
            width = input.width,
            height = input.height,
            resolutionScore = resolutionScore,
            sharpnessScore = sharpnessScore,
            contrastScore = contrastScore,
            highlightClipPercent = highlightPercent,
            shadowClipPercent = shadowPercent,
            glarePercent = glarePercent,
            overallScore = overall.coerceIn(0.0, 100.0)
        )

        val sobel = sobelValues(gray, w, h)
        val edgeDensity = sobel.count { it > 72.0 } * 100.0 / sobel.size.coerceAtLeast(1)
        val microtextureEnergy = lap.map { abs(it) }.average() / 255.0
        val localVar = estimateLocalVariance(gray, w, h)
        val rim = estimateRim(gray, sobel, w, h)

        val surface = SurfaceMetrics(
            microtextureEnergy = microtextureEnergy,
            localVariance = localVar,
            edgeDensityPercent = edgeDensity,
            rimCircularityScore = rim?.first,
            rimPatternRegularity = rim?.second
        )

        val findings = buildList {
            if (quality.resolutionScore < 55) add(
                EvidenceFinding(
                    "Resolution limits authentication",
                    "The shortest image dimension is ${min(input.width, input.height)} px. Fine die markers and tooling can be lost below roughly 1200–1800 px across the coin.",
                    Severity.WARNING
                )
            ) else add(EvidenceFinding("Resolution", "Image resolution is usable for local forensic transforms.", Severity.GOOD))

            if (quality.sharpnessScore < 45) add(
                EvidenceFinding(
                    "Soft focus / motion risk",
                    "Fine surface texture is weak in the captured pixels. Re-photograph before relying on tiny pits, flow lines, or serif details.",
                    Severity.CAUTION
                )
            ) else add(EvidenceFinding("Fine-detail capture", "Measured high-frequency detail is adequate for inspection.", Severity.GOOD))

            if (quality.glarePercent > 5.0) add(
                EvidenceFinding(
                    "Specular glare",
                    "${format1(quality.glarePercent)}% of pixels look like bright low-saturation reflections. Glare can hide hairlines and imitate smooth patches.",
                    Severity.CAUTION
                )
            )

            rim?.let { (circularity, regularity) ->
                if (circularity < 76.0) add(
                    EvidenceFinding(
                        "Rim geometry needs review",
                        "Radial edge consistency is ${format1(circularity)}/100. Perspective, an off-center photo, damage, clipping, or a genuine irregular rim can all lower this score.",
                        Severity.CAUTION
                    )
                ) else add(EvidenceFinding("Rim geometry", "Radial edge consistency is ${format1(circularity)}/100.", Severity.INFO))

                add(EvidenceFinding("Rim-pattern regularity", "Angular rim texture regularity is ${format1(regularity)}/100; compare this only against the same type and similarly lit reference.", Severity.INFO))
            }

            if (microtextureEnergy < 0.035 && quality.sharpnessScore >= 50) add(
                EvidenceFinding(
                    "Very low microtexture",
                    "The surface is unusually smooth at this resolution. Heavy wear, polishing, smoothing, lighting, or image processing are possible explanations; this alone is not a counterfeit marker.",
                    Severity.CAUTION
                )
            )

            if (edgeDensity > 28.0) add(
                EvidenceFinding(
                    "Dense high-frequency surface detail",
                    "The image contains a high concentration of sharp micro-edges. Cleaning hairlines, corrosion, rough casting, JPEG noise, or detailed original surfaces can all cause this; inspect the texture map manually.",
                    Severity.INFO
                )
            )

            add(
                EvidenceFinding(
                    "Invisible-light rule",
                    "UV and IR conclusions are only valid when a real UV/IR photograph is supplied. The app never synthesizes invisible wavelengths from an RGB photo.",
                    Severity.INFO
                )
            )
        }

        return ForensicAnalysis(quality, surface, findings)
    }

    private enum class Channel { RED, GREEN, BLUE }

    private fun scaledForWork(input: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(input.width, input.height)
        if (largest <= maxDimension) return input
        val scale = maxDimension.toDouble() / largest
        return Bitmap.createScaledBitmap(
            input,
            max(1, (input.width * scale).roundToInt()),
            max(1, (input.height * scale).roundToInt()),
            true
        )
    }

    private fun toGray(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val c = pixels[i]
        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)).roundToInt().coerceIn(0, 255)
    }

    private fun grayToArgb(gray: IntArray, out: IntArray) {
        for (i in gray.indices) out[i] = Color.rgb(gray[i], gray[i], gray[i])
    }

    private fun channelToArgb(pixels: IntArray, out: IntArray, channel: Channel) {
        for (i in pixels.indices) {
            val c = pixels[i]
            val v = when (channel) {
                Channel.RED -> Color.red(c)
                Channel.GREEN -> Color.green(c)
                Channel.BLUE -> Color.blue(c)
            }
            out[i] = when (channel) {
                Channel.RED -> Color.rgb(v, 0, 0)
                Channel.GREEN -> Color.rgb(0, v, 0)
                Channel.BLUE -> Color.rgb(0, 0, v)
            }
        }
    }

    private fun localContrast(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val radius = max(3, (min(w, h) / 70.0).roundToInt())
        val blur = boxBlur(gray, w, h, radius)
        val gain = 1.4 + intensity.coerceIn(0.5f, 2.5f) * 1.25
        for (i in gray.indices) {
            val v = (128.0 + (gray[i] - blur[i]) * gain).roundToInt().coerceIn(0, 255)
            out[i] = Color.rgb(v, v, v)
        }
    }

    private fun highPass(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val radius = max(2, (min(w, h) / 120.0).roundToInt())
        val blur = boxBlur(gray, w, h, radius)
        val gain = 1.2 + intensity.coerceIn(0.5f, 2.5f) * 1.5
        for (i in gray.indices) {
            val v = (128 + (gray[i] - blur[i]) * gain).roundToInt().coerceIn(0, 255)
            out[i] = Color.rgb(v, v, v)
        }
    }

    private fun sobel(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val values = sobelValues(gray, w, h)
        val gain = 0.75 + intensity.coerceIn(0.5f, 2.5f) * 0.85
        for (i in values.indices) {
            val v = (values[i] * gain).roundToInt().coerceIn(0, 255)
            out[i] = Color.rgb(v, v, v)
        }
    }

    private fun laplacian(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val values = laplacianValues(gray, w, h)
        val gain = 0.8 + intensity.coerceIn(0.5f, 2.5f) * 1.25
        for (i in values.indices) {
            val v = (abs(values[i]) * gain).roundToInt().coerceIn(0, 255)
            out[i] = Color.rgb(v, v, v)
        }
    }

    private fun textureVariance(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val radius = max(2, (min(w, h) / 100.0).roundToInt())
        val mean = boxBlur(gray, w, h, radius)
        val squares = IntArray(gray.size) { i -> (gray[i] * gray[i] / 255.0).roundToInt() }
        val meanSqScaled = boxBlur(squares, w, h, radius)
        val gain = 1.0 + intensity.coerceIn(0.5f, 2.5f) * 1.2
        for (i in gray.indices) {
            val ex2 = meanSqScaled[i] * 255.0
            val ex = mean[i].toDouble()
            val variance = max(0.0, ex2 - ex * ex)
            val normalized = (sqrt(variance) * gain * 2.0).roundToInt().coerceIn(0, 255)
            out[i] = heatColor(normalized)
        }
    }

    private fun specular(pixels: IntArray, out: IntArray, intensity: Float) {
        val threshold = (244 - intensity.coerceIn(0.5f, 2.5f) * 10).roundToInt().coerceIn(215, 245)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            out[i] = if (mx >= threshold && sat < 0.14) {
                Color.rgb(255, 64, 32)
            } else {
                val y = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt()
                val d = (y * 0.42).roundToInt()
                Color.rgb(d, d, d)
            }
        }
    }

    private fun pseudoRelief(gray: IntArray, out: IntArray, w: Int, h: Int, intensity: Float) {
        val gain = 0.65 + intensity.coerceIn(0.5f, 2.5f) * 0.9
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val dx = (gray[i + 1] - gray[i - 1]).toDouble()
                val dy = (gray[i + w] - gray[i - w]).toDouble()
                val shade = 128.0 + (-0.62 * dx - 0.78 * dy) * gain
                val v = shade.roundToInt().coerceIn(0, 255)
                out[i] = Color.rgb(v, v, v)
            }
        }
        copyBorder(out, w, h)
    }

    private fun falseColor(gray: IntArray, out: IntArray, intensity: Float) {
        val gamma = (1.2 / intensity.coerceIn(0.5f, 2.5f)).coerceIn(0.55, 1.8)
        for (i in gray.indices) {
            val normalized = (255.0 * (gray[i] / 255.0).pow(gamma)).roundToInt().coerceIn(0, 255)
            out[i] = heatColor(normalized)
        }
    }

    private fun heatColor(v: Int): Int {
        val x = v.coerceIn(0, 255) / 255.0
        val r = (255 * (1.5 - abs(4 * x - 3)).coerceIn(0.0, 1.0)).roundToInt()
        val g = (255 * (1.5 - abs(4 * x - 2)).coerceIn(0.0, 1.0)).roundToInt()
        val b = (255 * (1.5 - abs(4 * x - 1)).coerceIn(0.0, 1.0)).roundToInt()
        return Color.rgb(r, g, b)
    }

    private fun boxBlur(gray: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val stride = w + 1
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }
        val out = IntArray(gray.size)
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)
                val sum = integral[(y1 + 1) * stride + (x1 + 1)] - integral[y0 * stride + (x1 + 1)] -
                    integral[(y1 + 1) * stride + x0] + integral[y0 * stride + x0]
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                out[y * w + x] = (sum / count).toInt()
            }
        }
        return out
    }

    private fun sobelValues(gray: IntArray, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] +
                    gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] +
                    gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                out[i] = sqrt((gx * gx + gy * gy).toDouble()) / 4.0
            }
        }
        return out
    }

    private fun laplacianValues(gray: IntArray, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                out[i] = (4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]).toDouble()
            }
        }
        return out
    }

    private fun estimateLocalVariance(gray: IntArray, w: Int, h: Int): Double {
        val radius = max(2, min(w, h) / 120)
        val mean = boxBlur(gray, w, h, radius)
        var sum = 0.0
        var samples = 0
        val step = max(1, min(w, h) / 220)
        var y = radius
        while (y < h - radius) {
            var x = radius
            while (x < w - radius) {
                val i = y * w + x
                val d = gray[i] - mean[i]
                sum += d * d
                samples++
                x += step
            }
            y += step
        }
        return if (samples == 0) 0.0 else sqrt(sum / samples) / 255.0
    }

    private fun estimateRim(gray: IntArray, sobel: DoubleArray, w: Int, h: Int): Pair<Double, Double>? {
        if (min(w, h) < 220) return null
        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0
        val minDim = min(w, h).toDouble()
        val r0 = (minDim * 0.34).roundToInt()
        val r1 = (minDim * 0.49).roundToInt()
        val radii = DoubleArray(180)

        for (a in radii.indices) {
            val angle = a * 2.0 * PI / radii.size
            var bestR = r0
            var bestG = -1.0
            for (r in r0..r1) {
                val x = (cx + cos(angle) * r).roundToInt()
                val y = (cy + sin(angle) * r).roundToInt()
                if (x !in 1 until w - 1 || y !in 1 until h - 1) continue
                val g = sobel[y * w + x]
                if (g > bestG) {
                    bestG = g
                    bestR = r
                }
            }
            radii[a] = bestR.toDouble()
        }

        val meanR = radii.average()
        if (meanR <= 0) return null
        val std = sqrt(radii.fold(0.0) { acc, r -> acc + (r - meanR).pow(2) } / radii.size)
        val circularity = (100.0 * (1.0 - (std / meanR) * 8.0)).coerceIn(0.0, 100.0)

        val ringR = meanR * 0.90
        val samples = DoubleArray(360)
        for (a in samples.indices) {
            val angle = a * 2.0 * PI / samples.size
            val x = (cx + cos(angle) * ringR).roundToInt().coerceIn(0, w - 1)
            val y = (cy + sin(angle) * ringR).roundToInt().coerceIn(0, h - 1)
            samples[a] = gray[y * w + x].toDouble()
        }
        val sMean = samples.average()
        val centered = DoubleArray(samples.size) { samples[it] - sMean }
        val denom = centered.sumOf { it * it }.coerceAtLeast(1e-6)
        var maxCorr = 0.0
        for (lag in 3..24) {
            var corr = 0.0
            for (i in centered.indices) corr += centered[i] * centered[(i + lag) % centered.size]
            maxCorr = max(maxCorr, corr / denom)
        }
        val regularity = (maxCorr.coerceIn(0.0, 1.0) * 100.0)
        return circularity to regularity
    }

    private fun copyBorder(out: IntArray, w: Int, h: Int) {
        if (w < 2 || h < 2) return
        for (x in 0 until w) {
            out[x] = Color.rgb(128, 128, 128)
            out[(h - 1) * w + x] = Color.rgb(128, 128, 128)
        }
        for (y in 0 until h) {
            out[y * w] = Color.rgb(128, 128, 128)
            out[y * w + w - 1] = Color.rgb(128, 128, 128)
        }
    }

    private fun format1(v: Double): String = "%.1f".format(v)
}
