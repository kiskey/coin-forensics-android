package com.coinforensics.app.reference

import com.coinforensics.app.model.AutomatedTest
import com.coinforensics.app.model.CoinIdentityCandidate
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.DiscoveryProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericReferencePackFactoryTest {
    @Test
    fun unknownIdentityDoesNotInventPhysicalSpecifications() {
        val pack = GenericReferencePackFactory.create(
            metadata = CoinMetadata(country = "Egypt", denomination = "20 Qirsh", year = "1917"),
            identity = null,
            discovered = emptyList()
        )

        assertTrue(pack.regions.size >= 14)
        assertFalse(pack.markers.any { it.automatedTest == AutomatedTest.WEIGHT_TOLERANCE })
        assertFalse(pack.markers.any { it.automatedTest == AutomatedTest.DIAMETER_TOLERANCE })
    }

    @Test
    fun selectedCatalogueIdentityEnablesPhysicalCorroboration() {
        val identity = CoinIdentityCandidate(
            id = "123",
            provider = DiscoveryProvider.NUMISTA,
            title = "Example coin",
            sourceUrl = "https://example.invalid/coin/123",
            weightGrams = 28.0,
            diameterMm = 40.0
        )
        val pack = GenericReferencePackFactory.create(
            metadata = CoinMetadata(country = "Egypt", denomination = "20 Qirsh", year = "1917"),
            identity = identity,
            discovered = emptyList()
        )

        assertTrue(pack.markers.any { it.automatedTest == AutomatedTest.WEIGHT_TOLERANCE })
        assertTrue(pack.markers.any { it.automatedTest == AutomatedTest.DIAMETER_TOLERANCE })
    }
}
