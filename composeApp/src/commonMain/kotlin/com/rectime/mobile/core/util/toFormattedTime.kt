package com.rectime.mobile.core.util

fun String.toFormattedTime(): String {
    if (this.length < 4) {
        return "--:--"
    }
    return "${this.substring(0, 2)}:${this.substring(2, 4)}"
}
