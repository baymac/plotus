package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlin.test.*

class MeasurementPreferencesTest {
    @Test fun choicesPersistAcrossDevicesAndLocalCachesStayAccountScoped()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val stored=mutableMapOf<String,AccountPreferences>()
        val cache=mutableMapOf<String,String>()
        fun device(owner:String)=MeasurementPreferences(scope,owner,{method,value->
            if(method=="PUT")stored[owner]=value!!
            stored[owner] ?: AccountPreferences()
        },cache::get,{key,value->cache[key]=value})
        try {
            val first=device("a");first.refresh();first.select(PlotUnits.METRES)
            assertEquals(PlotUnits.METRES,stored["a"]?.units)
            val second=device("a");second.refresh();assertEquals(PlotUnits.METRES,second.units)
            second.select(PlotUnits.FEET_INCHES);first.refresh();assertEquals(PlotUnits.FEET_INCHES,first.units)
            first.select(PlotUnits.METRES)
            val other=device("b");other.refresh();assertEquals(PlotUnits.FEET_INCHES,other.units)
        } finally {scope.cancel()}
    }

    @Test fun failedWritesSurviveReloadAndRetryWithoutLosingTheChoice()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val cache=mutableMapOf<String,String>();var offline=true;var stored=AccountPreferences()
        val request:suspend (String,AccountPreferences?)->AccountPreferences={method,value->
            if(offline)error("Offline")
            if(method=="PUT")stored=value!!
            stored
        }
        fun device()=MeasurementPreferences(scope,"owner",request,cache::get,{key,value->cache[key]=value})
        try {
            val first=device();first.refresh();first.select(PlotUnits.METRES)
            assertNotNull(first.error)
            val reloaded=device();assertEquals(PlotUnits.METRES,reloaded.units)
            offline=false;reloaded.refresh()
            assertEquals(PlotUnits.METRES,stored.units);assertNull(reloaded.error)
            assertEquals("",cache["measurement-units:owner:pending"])
        } finally {scope.cancel()}
    }

    @Test fun aLateRefreshDoesNotOverwriteANewerSelection()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val reply=CompletableDeferred<AccountPreferences>();var stored=AccountPreferences();var reads=0
        val cache=mutableMapOf<String,String>()
        val device=MeasurementPreferences(scope,"owner",{method,value->
            if(method=="PUT")stored=value!!
            if(method=="GET"&&reads++==0)reply.await() else stored
        },cache::get,{key,value->cache[key]=value})
        try {
            val refresh=scope.launch {device.refresh()}
            device.select(PlotUnits.METRES)
            reply.complete(AccountPreferences());refresh.join()
            assertEquals(PlotUnits.METRES,device.units)
            assertEquals(PlotUnits.METRES,stored.units)
        } finally {scope.cancel()}
    }
}
