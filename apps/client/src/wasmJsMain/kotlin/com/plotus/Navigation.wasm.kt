@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.plotus

import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.JsFun

@JsFun("""() => JSON.stringify({path:location.pathname,next:new URL(location.href).searchParams.get('next')})""")
private external fun locationJson(): String

@JsFun("""(path,replace) => {
 if(replace)history.replaceState(null,'',path);else history.pushState(null,'',path);
 const titles={'/projects':'Projects','/settings':'Settings','/settings/general':'General','/settings/account':'Account','/login':'Sign in'};
 document.title=(titles[new URL(location.href).pathname] || (location.pathname.includes('/plot/') ? 'Plot setup' : 'Workspace'))+' · Plotus';
}""")
private external fun updateLocation(path: String, replace: Boolean)

internal actual fun readAppLocation(): AppLocation? = authJson.decodeFromString<AppLocation>(locationJson())
internal actual fun observeAppLocation(onChange: () -> Unit): () -> Unit {
    val listener: (Event) -> Unit = { onChange() }
    window.addEventListener("popstate", listener)
    return { window.removeEventListener("popstate", listener) }
}
internal actual fun writeAppLocation(path: String, replace: Boolean) = updateLocation(path, replace)
