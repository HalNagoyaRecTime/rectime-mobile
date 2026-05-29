package com.rectime.mobile.feature.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private var externalUrlContext: Context? = null
private const val TAG = "AuthExternalUrl"

fun setExternalUrlContext(context: Context) {
    externalUrlContext = context
}

actual fun openExternalUrl(url: String): Boolean {
    val context = externalUrlContext ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    return runCatching {
        context.startActivity(intent)
    }.onFailure { error ->
        Log.e(TAG, "Failed to open external url: $url", error)
    }.isSuccess
}
