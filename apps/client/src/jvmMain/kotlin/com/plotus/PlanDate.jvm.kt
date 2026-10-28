package com.plotus

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun planTimestamp(iso:String):String=runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault("Date unavailable")
