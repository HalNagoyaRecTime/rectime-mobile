package com.rectime.mobile.feature.accountdeletion

import com.rectime.mobile.core.config.accountDeletionPath
import com.rectime.mobile.core.config.productionWebOrigin
import com.rectime.mobile.core.config.resolvePublicWebUrl
import com.rectime.mobile.core.platform.openExternalUrl
import kotlinx.coroutines.CancellationException

class AccountDeletionLauncher(
    private val origin: String = productionWebOrigin,
    private val openUrl: suspend (String) -> Boolean = { openExternalUrl(it) },
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
