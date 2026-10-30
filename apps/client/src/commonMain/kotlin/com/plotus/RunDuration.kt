package com.plotus

internal fun runDurationLabel(milliseconds:Long?):String {
    if(milliseconds==null||milliseconds<0)return "Unavailable"
    val seconds=milliseconds/1000
    if(seconds==0L)return "<1 sec"
    val hours=seconds/3600
    val minutes=seconds%3600/60
    val remaining=seconds%60
    return listOfNotNull(
        hours.takeIf {it>0}?.let {"$it hr"},
        minutes.takeIf {it>0}?.let {"$it min"},
        remaining.takeIf {it>0}?.let {"$it sec"},
    ).joinToString(" ")
}
