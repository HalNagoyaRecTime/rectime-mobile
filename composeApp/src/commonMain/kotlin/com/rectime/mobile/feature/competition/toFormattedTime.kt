package com.rectime.mobile.feature.competition

fun String.toFormattedTime(): String {
        return "${this.substring(0, 2)}:${this.substring(2, 4)}"
    }