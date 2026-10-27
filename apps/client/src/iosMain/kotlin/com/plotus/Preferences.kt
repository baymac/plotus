package com.plotus

import platform.Foundation.NSUserDefaults

actual fun readPreference(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
actual fun writePreference(key: String, value: String) { NSUserDefaults.standardUserDefaults.setObject(value, forKey = key) }
