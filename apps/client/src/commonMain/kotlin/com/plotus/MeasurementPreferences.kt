package com.plotus

import androidx.compose.runtime.*
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val LocalMeasurementUnits=compositionLocalOf {PlotUnits.FEET_INCHES}
internal val LocalSetMeasurementUnits=staticCompositionLocalOf<(PlotUnits)->Unit> {{}}

/** One account preference; project measurements remain in metres. Pending writes survive reloads. */
class MeasurementPreferences(
    private val scope:CoroutineScope,
    owner:String,
    private val request:suspend (String,AccountPreferences?)->AccountPreferences,
    private val read:(String)->String?=::readPreference,
    private val cache:(String,String)->Unit=::writePreference,
) {
    private val key="measurement-units:$owner"
    private fun parse(value:String?)=PlotUnits.entries.firstOrNull {it.name==value}
    private var pending=parse(read("$key:pending"))
    var units by mutableStateOf(pending ?: parse(read(key)) ?: PlotUnits.FEET_INCHES);private set
    var loading by mutableStateOf(true);private set
    var saving by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    private val lock=Mutex()
    private var version=0

    fun select(next:PlotUnits) {
        units=next;pending=next;version++;cache(key,next.name);cache("$key:pending",next.name)
        scope.launch {refresh()}
    }
    fun dismissError(){error=null}
    suspend fun refresh()=lock.withLock {
        try {
            while(pending!=null) {
                saving=true
                val selected=pending!!
                request("PUT",AccountPreferences(selected))
                if(pending==selected){pending=null;cache("$key:pending","")}
            }
            saving=false
            val observed=version
            val saved=request("GET",null)
            if(version==observed&&pending==null){units=saved.units;cache(key,units.name)}
            error=null
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){error=if(pending!=null)"Couldn’t sync measurement units. Your choice is kept on this device and will retry automatically." else "Couldn’t load measurement units. Please try again."}
        finally {loading=false;saving=false}
    }
}
