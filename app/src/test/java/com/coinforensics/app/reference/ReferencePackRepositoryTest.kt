package com.coinforensics.app.reference

import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.DiagnosticScope
import com.coinforensics.app.model.ReferenceSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferencePackRepositoryTest {
    @Test
    fun british1911PackSeparatesExactAndTypeLevelEvidence() {
        val pack = ReferencePackRepository.builtIns().single { it.id == "gb-trade-dollar-1911b-prid21" }
        assertEquals(26.96, pack.physical.expectedWeightGrams ?: 0.0, 0.001)
        assertEquals(39.0, pack.physical.expectedDiameterMm ?: 0.0, 0.001)
        assertTrue(pack.sources.any { it.kind == ReferenceSourceKind.CERTIFIED_GENUINE && it.scope == DiagnosticScope.EXACT_DATE_VARIETY })
        assertTrue(pack.sources.any { it.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC && it.scope == DiagnosticScope.SAME_TYPE_OTHER_DATE })
        assertTrue(pack.sources.any { it.id == "greatcollections-1747409" })
        assertTrue(pack.sources.any { it.id == "numista-forum-1911-transfer" })
        assertTrue(pack.markers.any { it.id == "overdate-variety" })
        assertTrue(pack.regions.any { it.id == "obverse-date" })
        assertTrue(pack.regions.any { it.id == "reverse-jawi" })
    }

    @Test
    fun weightCheckFlagsLargeDeviationWithoutCallingItAuthenticityProbability() {
        val pack = ReferencePackRepository.builtIns().single()
        val assessment = DieReferenceAnalyzer.assess(
            pack = pack,
            metadata = CoinMetadata(country = "Great Britain", denomination = "Trade Dollar", year = "1911", weightGrams = "26.10"),
            images = emptyMap()
        )
        val weight = assessment.checks.single { it.markerId == "weight-2696" }
        assertEquals("INCONSISTENT", weight.status.name)
        assertTrue(weight.detail.contains("26.96"))
        assertTrue(weight.detail.contains("26.10"))
        assertTrue(assessment.interpretation.contains("evidence", ignoreCase = true))
        assertTrue(!assessment.interpretation.contains("authenticity probability", ignoreCase = true))
    }
    @Test
    fun physicalChecksAcceptDocumentedNominalMeasurements() {
        val pack = ReferencePackRepository.builtIns().single()
        val assessment = DieReferenceAnalyzer.assess(
            pack = pack,
            metadata = CoinMetadata(
                country = "Great Britain",
                denomination = "Trade Dollar",
                year = "1911",
                weightGrams = "26.96",
                diameterMm = "39.00"
            ),
            images = emptyMap()
        )
        assertEquals("CONSISTENT", assessment.checks.single { it.markerId == "weight-2696" }.status.name)
        assertEquals("CONSISTENT", assessment.checks.single { it.markerId == "diameter-390" }.status.name)
    }

}
