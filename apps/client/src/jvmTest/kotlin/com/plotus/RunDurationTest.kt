package com.plotus

import kotlin.test.*

class RunDurationTest {
    @Test fun durationKeepsUnitsAndDoesNotRoundShortRunsUp() {
        assertEquals("<1 sec",runDurationLabel(999))
        assertEquals("1 sec",runDurationLabel(1000))
        assertEquals("1 min",runDurationLabel(60000))
        assertEquals("2 min 5 sec",runDurationLabel(125999))
        assertEquals("1 hr 1 min 1 sec",runDurationLabel(3661000))
        assertEquals("Unavailable",runDurationLabel(null))
        assertEquals("Unavailable",runDurationLabel(-1))
    }
}
