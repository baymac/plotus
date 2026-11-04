package com.plotus.server

import com.plotus.shared.*
import kotlin.test.*
import kotlinx.serialization.json.Json

class ArchitectAITest {
    private val report=PlanReviewReport(rulesVersion="test",findings=listOf(
        ReviewFinding("known","measured","privacy","high","Review the doorway","A measured sightline needs review.",ReviewOperation.IMPROVE_PRIVACY)))
    @Test fun explanationsCanOnlyRankExistingFindings() {
        val result=checkedExplanation(ArchitectExplanation("Test the doorway arrangement.",
            listOf(AIReviewRecommendation("known","Consider a shorter privacy buffer while retaining access."))),report,"fixture-model")
        assertEquals("known",result.recommendations.single().findingID)
        assertEquals("ready",result.status)
        assertFailsWith<ApiFailure>{checkedExplanation(ArchitectExplanation("Test the doorway.",
            listOf(AIReviewRecommendation("invented","Try moving it."))),report,"fixture-model")}
    }
    @Test fun inventedMeasurementsAndApprovalClaimsAreRejected() {
        for(text in listOf("Recover 50 square feet.","Recover fifty square feet.","This is compliant.","The layout is structurally approved.","This will improve the plan.")) {
            assertFailsWith<ApiFailure>{checkedExplanation(ArchitectExplanation(text,emptyList()),report,"fixture-model")}
        }
    }
    @Test fun aRequiredFailureCannotBeOmittedOrRankedBelowAnOptionalFinding() {
        val evidence=report.copy(findings=report.findings+report.findings.single().copy(id="required",priority="critical"))
        assertFailsWith<ApiFailure>{checkedExplanation(ArchitectExplanation("Test the arrangement.",listOf(AIReviewRecommendation("known","Explore the doorway."))),evidence,"fixture-model")}
        val review=checkedExplanation(ArchitectExplanation("Test the arrangement.",listOf(
            AIReviewRecommendation("known","Explore the doorway."),AIReviewRecommendation("required","Resolve the required access check."))),evidence,"fixture-model")
        assertEquals("required",review.recommendations.first().findingID)
    }
    @Test fun modelCannotOutputGeometryScoresOrExecutableInstructions() {
        assertFails {Json.decodeFromString<ArchitectExplanation>("""{"summary":"Test the arrangement.","recommendations":[],"score":99}""")}
        assertFails {Json.decodeFromString<ArchitectExplanation>("""{"summary":"Test the arrangement.","recommendations":[],"operation":"disable_checks"}""")}
    }
}
