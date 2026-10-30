package com.plotus

import kotlin.test.*

class RunDurationTest {
    @Test fun elapsedTimeSurvivesReopeningAndFreezesWhenCancelled() {
        val plan=com.plotus.shared.GeneratedFloorPlan("job","running",createdAt="2026-10-30T10:00:00Z")
        val now=kotlin.time.Instant.parse("2026-10-30T10:02:05Z").toEpochMilliseconds()
        assertEquals(125000L,generationElapsedMillis(plan,now))
        assertEquals(125000L,generationElapsedMillis(plan.copy(status="cancelling"),now))
        assertEquals(0L,generationElapsedMillis(plan,now-300000))
        assertEquals(0L,generationElapsedMillis(plan.copy(createdAt=""),now))
        assertEquals(91000L,generationElapsedMillis(plan.copy(status="cancelled",durationMillis=91000),now))
    }
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
