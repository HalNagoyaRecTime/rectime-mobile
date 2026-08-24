package com.rectime.mobile.core.platform

import android.content.Intent
import android.net.Uri

internal actual suspend fun openPlatformExternalUrl(url: String): Boolean {
    val context = getPlatformContext() ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    // Some Android exception messages contain the Intent including its data URI,
    // so failures are deliberately returned without logging the Throwable.
    return runCatching { context.startActivity(intent) }.isSuccess
}
