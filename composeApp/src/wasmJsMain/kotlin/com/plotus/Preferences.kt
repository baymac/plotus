package com.plotus

import kotlinx.browser.window

actual fun readPreference(key: String): String? = try { window.localStorage.getItem(key) } catch (_: Exception) { null }
actual fun writePreference(key: String, value: String) { try { window.localStorage.setItem(key, value) } catch (_: Exception) { } }
