package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlin.test.*

class ConfirmedChangesTest {
    private val plot=PlotSetup(lengths=List(4){10.0}).rebuild()
    private val project=Project("test-id","Home","Jaipur",revision=2,plotSetup=plot)
    @Test fun deleteSuccessAndLostRepliesDoNotLeaveAnUndeletableProject()=runBlocking<Unit> {
        var reads=0
        assertNull(confirmProjectDeletion({},{reads++;project}));assertEquals(0,reads)
        assertNull(confirmProjectDeletion({throw AuthFailure(404,"Missing")},{error("No lookup needed")}))
        assertNull(confirmProjectDeletion({throw IllegalStateException("Reply lost")},{reads++;throw AuthFailure(404,"Missing")}))
        assertEquals(1,reads)
    }
    @Test fun aFailedDeleteNeverRemovesAProjectThatStillExists()=runBlocking<Unit> {
        val failure=IllegalStateException("Disconnected")
        assertSame(failure,assertFailsWith<IllegalStateException> {confirmProjectDeletion({throw failure},{project})})
        assertSame(failure,assertFailsWith<IllegalStateException> {confirmProjectDeletion({throw failure},{throw IllegalStateException("Still offline")})})
        assertFailsWith<AuthFailure> {confirmProjectDeletion({throw AuthFailure(401,"Signed out")},{error("No lookup for auth errors")})}
    }
    @Test fun aChangedProjectRequiresANewExplicitDeleteAttempt()=runBlocking<Unit> {
        val newer=project.copy(revision=3);var deletes=0
        assertEquals(newer,confirmProjectDeletion({deletes++;throw AuthFailure(409,"Changed")},{newer}))
        assertEquals(1,deletes)
        assertNull(confirmProjectDeletion({deletes++},{newer}));assertEquals(2,deletes)
    }
    @Test fun saveRecoveryAcceptsOnlyTheExactNewerServerPlot()=runBlocking<Unit> {
        val requested=plot.copy(northDegrees=45.0)
        val saved=project.copy(revision=3,plotSetup=requested)
        val failure=IllegalStateException("Reply lost")
        assertEquals(saved,confirmPlotSave(project,requested,{throw failure},{saved}))
        assertEquals(saved,confirmPlotSave(project,requested,{throw AuthFailure(409,"Already saved")},{saved}))
        assertSame(failure,assertFailsWith<IllegalStateException> {confirmPlotSave(project,requested,{throw failure},{project})})
        assertSame(failure,assertFailsWith<IllegalStateException> {confirmPlotSave(project,requested,{throw failure},{saved.copy(plotSetup=plot)})})
    }
    @Test fun cancellationNeverReportsAnUnconfirmedWriteAsSuccessful()=runBlocking<Unit> {
        assertFailsWith<CancellationException> {confirmProjectDeletion({throw CancellationException()},{error("Cancelled")})}
        assertFailsWith<CancellationException> {confirmPlotSave(project,plot,{throw CancellationException()},{error("Cancelled")})}
    }
}
