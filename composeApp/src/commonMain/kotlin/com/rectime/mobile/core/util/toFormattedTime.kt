package com.rectime.mobile.core.util

fun String.toFormattedTime(padHour: Boolean = true): String {
    if (this.length < 4) {
        return "--:--"
    }
    val hour = substring(0, 2).let { if (padHour) it else it.removePrefix("0") }
    return "$hour:${substring(2, 4)}"
}
