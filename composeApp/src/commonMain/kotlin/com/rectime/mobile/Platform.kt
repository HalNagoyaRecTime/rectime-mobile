package com.rectime.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform