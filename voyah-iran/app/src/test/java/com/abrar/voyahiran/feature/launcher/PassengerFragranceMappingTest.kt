package com.abrar.voyahiran.feature.launcher

import org.junit.Assert.*
import org.junit.Test

class PassengerFragranceMappingTest {
    @Test fun matchesOemChannelsNotArtworkSuffixes() {
        assertEquals(1,PassengerFragranceMapping.channel(2)) // bamboo
        assertEquals(2,PassengerFragranceMapping.channel(3)) // peony
        assertEquals(3,PassengerFragranceMapping.channel(1)) // watery mist
        assertEquals(2,PassengerFragranceMapping.artwork(1))
        assertEquals(3,PassengerFragranceMapping.artwork(2))
        assertEquals(1,PassengerFragranceMapping.artwork(3))
        assertNull(PassengerFragranceMapping.artwork(0))
        assertNull(PassengerFragranceMapping.artwork(255))
    }
}
