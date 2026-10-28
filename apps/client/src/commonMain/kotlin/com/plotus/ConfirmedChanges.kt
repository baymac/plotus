package com.plotus

import com.plotus.shared.PlotSetup
import com.plotus.shared.Project
import kotlinx.coroutines.CancellationException

private fun uncertain(failure:Exception)=failure !is AuthFailure || failure.status==0 || failure.status>=500

/** A missing owned project is the intended delete result, including after a lost reply. */
internal suspend fun confirmProjectDeletion(remove:suspend ()->Unit,read:suspend ()->Project):Project? {
    try {remove();return null}
    catch(cancelled:CancellationException) {throw cancelled}
    catch(failure:Exception) {
        if(failure is AuthFailure&&failure.status==404)return null
        if(failure is AuthFailure&&failure.status==409) {
            try {return read()}
            catch(missing:AuthFailure) {if(missing.status==404)return null;throw missing}
        }
        if(uncertain(failure)) {
            try {read()}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(missing:Exception) {if(missing is AuthFailure&&missing.status==404)return null}
        }
        throw failure
    }
}

/** Only accept a recovered save when the server contains exactly the requested plot at a newer revision. */
internal suspend fun confirmPlotSave(current:Project,setup:PlotSetup,write:suspend ()->Project,read:suspend ()->Project):Project {
    try {return write()}
    catch(cancelled:CancellationException) {throw cancelled}
    catch(failure:Exception) {
        if(uncertain(failure)||(failure is AuthFailure&&failure.status==409)) {
            val latest=try {read()}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {null}
            if(latest!=null&&latest.revision>current.revision&&latest.plotSetup==setup)return latest
        }
        throw failure
    }
}
