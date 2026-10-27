package com.plotus

internal actual fun readAppLocation(): AppLocation? = null
internal actual fun observeAppLocation(onChange: () -> Unit): () -> Unit = {}
internal actual fun writeAppLocation(path: String, replace: Boolean) {}
