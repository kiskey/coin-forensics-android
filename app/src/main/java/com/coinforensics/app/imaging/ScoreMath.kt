package com.coinforensics.app.imaging

import kotlin.math.max
import kotlin.math.min

object ScoreMath {
    fun clamp01(v: Double): Double = min(1.0, max(0.0, v))
    fun percent(v: Double): Double = clamp01(v) * 100.0

    fun weighted(vararg pairs: Pair<Double, Double>): Double {
        val denominator = pairs.sumOf { it.second }
        if (denominator <= 0.0) return 0.0
        return pairs.sumOf { it.first * it.second } / denominator
    }

    fun interpretation(score: Double): String = when {
        score >= 92.0 -> "Strong visual consistency with the supplied reference. This is not proof of authenticity."
        score >= 84.0 -> "Moderate-to-strong visual consistency with the supplied reference; inspect remaining differences."
        score >= 72.0 -> "Mixed visual consistency. Differences may reflect lighting, wear, die variation, or a non-matching specimen."
        score >= 58.0 -> "Weak visual consistency. Better-aligned photos or a more appropriate genuine reference are recommended."
        else -> "Significant visual differences from the supplied reference. Do not treat this as a counterfeit verdict without independent evidence."
    }
}
