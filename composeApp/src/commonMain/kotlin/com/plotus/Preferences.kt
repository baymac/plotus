package com.plotus

expect fun readPreference(key: String): String?
expect fun writePreference(key: String, value: String)
