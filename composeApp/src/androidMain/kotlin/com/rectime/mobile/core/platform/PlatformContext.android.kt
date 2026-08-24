package com.rectime.mobile.core.platform

import android.content.Context

private var platformContext: Context? = null

fun initializePlatformContext(context: Context) {
    platformContext = context.applicationContext
}

internal fun getPlatformContext(): Context? = platformContext
