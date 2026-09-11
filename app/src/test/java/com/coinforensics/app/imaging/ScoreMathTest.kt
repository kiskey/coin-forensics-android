package com.coinforensics.app.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreMathTest {
    @Test fun clampBounds() {
        assertEquals(0.0, ScoreMath.clamp01(-2.0), 0.0)
        assertEquals(1.0, ScoreMath.clamp01(3.0), 0.0)
    }

    @Test fun weightedMean() {
        assertEquals(0.75, ScoreMath.weighted(1.0 to 1.0, 0.5 to 1.0), 1e-9)
    }

    @Test fun interpretationIsCautious() {
        assertTrue(ScoreMath.interpretation(98.0).contains("not proof", ignoreCase = true))
    }
}
