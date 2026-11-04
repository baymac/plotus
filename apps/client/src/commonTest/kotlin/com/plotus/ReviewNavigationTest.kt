package com.plotus

import com.plotus.shared.FloorStep
import kotlin.test.*

class ReviewNavigationTest {
    private val project="11111111-1111-4111-8111-111111111111"
    private val generation="22222222-2222-4222-8222-222222222222"
    private val job="33333333-3333-4333-8333-333333333333"
    @Test fun reviewAndOptimizationLinksReopenTheSelectedGeneration() {
        val navigation=AppNavigation(AppLocation("/projects/"+project+"/floor-plan/history/"+generation+"/compare/"+job)){_,_->}
        assertEquals(generation,navigation.generationID)
        assertEquals(PlanReviewRoute(ReviewView.COMPARE,job),navigation.reviewRoute)
        assertEquals(FloorStep.HISTORY,navigation.floorStep)
        val path=navigation.path
        val reopened=AppNavigation(AppLocation(path)){_,_->}
        assertEquals(path,reopened.path)
        navigation.openFloor(project,FloorStep.HISTORY)
        assertNull(navigation.reviewRoute);assertNull(navigation.generationID)
    }
    @Test fun malformedReviewTargetsAreNotSafeLoginReturnPaths() {
        for(suffix in listOf("/compare/not-a-job","/review/extra","/metric/not-a-metric","/finding/../../settings"))
            assertNull(floorLocation("/projects/"+project+"/floor-plan/history/"+generation+suffix))
        assertNull(floorLocation("/projects/"+project+"/floor-plan/requirements/"+generation+"/review"))
    }
    @Test fun scoreEvidenceAndFindingRoutesDoNotChangeTheProjectOrGeneration() {
        val n=AppNavigation(AppLocation("/projects")){_,_->}
        n.openFloor(project,FloorStep.HISTORY,generation=generation,review=PlanReviewRoute(ReviewView.METRIC,"space"))
        assertEquals(project,n.projectID);assertEquals(generation,n.generationID)
        assertEquals("space",floorReview(n.path)?.itemID)
        n.openFloor(project,FloorStep.HISTORY,generation=generation,review=null)
        assertNull(n.reviewRoute)
    }
}
