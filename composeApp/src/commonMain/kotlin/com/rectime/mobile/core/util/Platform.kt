package com.rectime.mobile.core.util

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform