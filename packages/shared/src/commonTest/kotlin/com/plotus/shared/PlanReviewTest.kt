package com.plotus.shared

import kotlin.test.*

class PlanReviewTest {
    private fun report()=PlanReviewReport(rulesVersion="plotus-architect-review.1",sourceHash="a".repeat(64),designScore=80,designLevel="Good",
        metrics=listOf("space" to 25,"circulation" to 20,"function" to 20,"daylight" to 15,"privacy" to 10,"access" to 10).map {
            ReviewMetric(it.first,it.first,it.second,80.0)
        },gates=listOf(ReviewCheck("configured_layout","Native layout","pass")),canOptimize=true)
    @Test fun requiredChecksAreIndependentOfTheDesignScore() {
        val failed=report().copy(gates=listOf(ReviewCheck("configured_layout","Native layout","fail")))
        assertTrue(failed.isValid());assertFalse(failed.requiredChecksPass)
        assertTrue(report().requiredChecksPass)
        assertFalse(report().copy(gates=emptyList()).requiredChecksPass)
    }
    @Test fun missingEvidenceCannotAcquireAnOverallScore() {
        val partial=report().copy(metrics=report().metrics.map {if(it.id=="privacy")it.copy(score=null) else it})
        assertFalse(partial.isValid())
        assertTrue(partial.copy(designScore=null,designLevel="Not assessed").isValid())
    }
    @Test fun scoreCannotBeOverriddenByAnExplanationOrTransportMismatch() {
        assertFalse(report().copy(designScore=99).isValid())
        assertFalse(report().copy(metrics=report().metrics.map {it.copy(weight=10)}).isValid())
        assertFalse(report().copy(metrics=report().metrics.map {it.copy(score=Double.NaN)}).isValid())
    }
    @Test fun unknownAndRegulatoryChecksDoNotBecomePassed() {
        val report=report().copy(gates=report().gates+ReviewCheck("local_code","Local code","not_assessed",false))
        assertTrue(report.requiredChecksPass)
        assertEquals("not_assessed",report.gates.last().status)
    }
}
