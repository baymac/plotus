package com.plotus

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.time.Instant

internal actual fun planTimestamp(iso:String):String=runCatching {
    val date=NSDate.dateWithTimeIntervalSince1970(Instant.parse(iso).toEpochMilliseconds()/1000.0)
    NSDateFormatter().apply {dateFormat="d MMM yyyy · h:mm a"}.stringFromDate(date)
}.getOrDefault("Date unavailable")
