package com.plotus

import androidx.compose.runtime.mutableStateMapOf
import com.plotus.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pending navigation survives reloads without allowing a stale device cache to beat server state. */
internal class ProjectSectionMemory(
    private val scope:CoroutineScope,
    private val owner:String,
    private val save:suspend (String,PlanningSection)->Unit,
    private val read:(String)->String?=::readPreference,
    private val cache:(String,String)->Unit=::writePreference,
) {
    private val pending=mutableStateMapOf<String,PlanningSection>()
    private val locks=mutableMapOf<String,Mutex>()
    private fun key(id:String)="ps:$owner:$id"
    fun pendingSection(id:String):PlanningSection? = pending[id] ?: PlanningSection.entries.firstOrNull {it.name==read(key(id))}?.also {pending[id]=it}
    fun section(project:Project)=pendingSection(project.id) ?: project.lastOpenedSection
    fun enrich(project:Project)=project.copy(lastOpenedSection=section(project))
    fun remember(id:String,section:PlanningSection) {
        pending[id]=section;cache(key(id),section.name)
        retry(id)
    }
    fun retry(id:String){if(pendingSection(id)!=null)scope.launch {flush(id)}}
    suspend fun flush(id:String)=locks.getOrPut(id){Mutex()}.withLock {
        try {
            while(pendingSection(id)!=null) {
                val section=pendingSection(id)!!
                save(id,section)
                if(pending[id]==section){pending.remove(id);cache(key(id),"")}
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){/* Keep the pending section; the next refresh retries it. */}
    }
    fun forget(id:String){pending.remove(id);cache(key(id),"")}
}
