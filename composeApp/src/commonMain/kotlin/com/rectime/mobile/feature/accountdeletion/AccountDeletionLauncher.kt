package com.rectime.mobile.feature.accountdeletion

import com.rectime.mobile.core.config.accountDeletionPath
import com.rectime.mobile.core.config.publicWebOrigin
import com.rectime.mobile.core.config.resolvePublicWebUrl
import com.rectime.mobile.core.platform.openInAppBrowser
import kotlinx.coroutines.CancellationException

class AccountDeletionLauncher(
    private val origin: String = publicWebOrigin,
    // The deletion page asks people to sign in, so keep it inside the app (App Review Guideline 4).
    private val openUrl: suspend (String) -> Boolean = { openInAppBrowser(it) },
) {
    suspend fun open(): Boolean {
        val url = resolvePublicWebUrl(
            path = accountDeletionPath,
            origin = origin,
        ) ?: return false

        return try {
            openUrl(url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Throwable messages may contain the complete URL. Do not log them.
            false
        }
    }
}
