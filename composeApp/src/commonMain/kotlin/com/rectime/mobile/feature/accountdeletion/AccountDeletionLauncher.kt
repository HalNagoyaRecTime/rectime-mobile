package com.rectime.mobile.feature.accountdeletion

import com.rectime.mobile.core.config.AppEnvironment
import com.rectime.mobile.core.config.Campus
import com.rectime.mobile.core.config.CurrentCampus
import com.rectime.mobile.core.config.accountDeletionPath
import com.rectime.mobile.core.config.appEnvironment
import com.rectime.mobile.core.config.campusWebOrigin
import com.rectime.mobile.core.config.resolvePublicWebUrl
import com.rectime.mobile.core.platform.openInAppBrowser
import kotlinx.coroutines.CancellationException

class AccountDeletionLauncher(
    private val currentCampus: () -> Campus = { CurrentCampus.campus },
    private val environment: AppEnvironment = appEnvironment,
    // The deletion page asks people to sign in, so keep it inside the app (App Review Guideline 4).
    private val openUrl: suspend (String) -> Boolean = { openInAppBrowser(it) },
) {
    suspend fun open(): Boolean {
        val campus = currentCampus()
        val origin = campusWebOrigin(campus, environment) ?: return false
        val url = resolvePublicWebUrl(
            path = accountDeletionPath,
            origin = origin,
            environment = environment,
            campus = campus,
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
