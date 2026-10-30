package com.plotus

import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlin.test.*

class ProjectSectionMemoryTest {
    @Test fun confirmedServerSectionWinsOnOtherDevicesAndAccountsRemainSeparate()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val cache=mutableMapOf<String,String>();var saved=PlanningSection.PLOT
        val memory=ProjectSectionMemory(scope,"one",{_,section->saved=section},cache::get,{key,value->cache[key]=value})
        val project=Project("project","Home","City")
        try {
            memory.remember(project.id,PlanningSection.FLOOR_PLAN)
            assertEquals(PlanningSection.FLOOR_PLAN,saved);assertNull(memory.pendingSection(project.id))
            assertEquals(PlanningSection.PLOT,memory.section(project.copy(lastOpenedSection=PlanningSection.PLOT)))
            val other=ProjectSectionMemory(scope,"two",{_,_->},cache::get,{key,value->cache[key]=value})
            assertEquals(PlanningSection.PLOT,other.section(project))
            assertEquals(PlanningSection.FLOOR_PLAN,other.section(project.copy(lastOpenedSection=saved)))
        } finally {scope.cancel()}
    }
    @Test fun pendingSectionsSurviveReloadAndTheLastVisitWinsWhenWritesOverlap()=runBlocking<Unit> {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val cache=mutableMapOf<String,String>();var offline=true;var saved=PlanningSection.PLOT
        val request:suspend (String,PlanningSection)->Unit={_,section->if(offline)error("Offline");saved=section}
        fun memory()=ProjectSectionMemory(scope,"owner",request,cache::get,{key,value->cache[key]=value})
        try {
            memory().remember("project",PlanningSection.FLOOR_PLAN)
            val reloaded=memory()
            assertEquals(PlanningSection.FLOOR_PLAN,reloaded.section(Project("project","Home","City")))
            offline=false;reloaded.flush("project")
            assertEquals(PlanningSection.FLOOR_PLAN,saved);assertNull(reloaded.pendingSection("project"))
            val reply=CompletableDeferred<Unit>();var writes=0
            val overlap=ProjectSectionMemory(scope,"owner",{_,section->if(writes++==0)reply.await();saved=section},cache::get,{key,value->cache[key]=value})
            overlap.remember("project",PlanningSection.FLOOR_PLAN)
            overlap.remember("project",PlanningSection.PLOT)
            reply.complete(Unit);overlap.flush("project")
            assertEquals(PlanningSection.PLOT,saved);assertNull(overlap.pendingSection("project"))
        } finally {scope.cancel()}
    }
}
