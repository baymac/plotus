package com.plotus

import java.util.prefs.Preferences

actual fun readPreference(key: String): String? = try { Preferences.userRoot().node("com/plotus/preview").get(key, null) } catch (_: Exception) { null }
actual fun writePreference(key: String, value: String) { try { Preferences.userRoot().node("com/plotus/preview").put(key, value) } catch (_: Exception) { } }
