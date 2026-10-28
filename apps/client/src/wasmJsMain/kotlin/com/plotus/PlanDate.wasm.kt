@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.plotus

@JsFun("(iso) => { const date = new Date(iso); return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleDateString(undefined, {day:'numeric',month:'short',year:'numeric'}) + ' · ' + date.toLocaleTimeString(undefined, {hour:'numeric',minute:'2-digit'}); }")
private external fun localPlanTimestamp(iso:String):String

internal actual fun planTimestamp(iso:String):String=localPlanTimestamp(iso)
