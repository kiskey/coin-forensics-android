package com.coinforensics.app.imaging

import com.coinforensics.app.model.DiagnosticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCvDecisionPolicyTest {
    @Test
    fun strongRegisteredGenuineMatchIsConsistent() {
        assertEquals(DiagnosticStatus.CONSISTENT, OpenCvDecisionPolicy.genuineStatus(88.0, 0.62))
    }

    @Test
    fun highAppearanceScoreWithoutRegistrationNeedsManualReview() {
        assertEquals(DiagnosticStatus.MANUAL_REVIEW, OpenCvDecisionPolicy.genuineStatus(94.0, 0.10))
    }

    @Test
    fun professionallyDocumentedFakeCanRaiseRelativeCaution() {
        assertTrue(OpenCvDecisionPolicy.counterfeitCaution(89.0, 76.0, 0.58, authoritative = true))
    }

    @Test
    fun communityFakeNeverRaisesAutomatedCounterfeitCaution() {
        assertFalse(OpenCvDecisionPolicy.counterfeitCaution(95.0, 70.0, 0.80, authoritative = false))
    }

    @Test
    fun fakeSimilarityWithoutGenuineBaselineIsNotAutomatedEvidence() {
        assertFalse(OpenCvDecisionPolicy.counterfeitCaution(96.0, null, 0.85, authoritative = true))
    }
}
