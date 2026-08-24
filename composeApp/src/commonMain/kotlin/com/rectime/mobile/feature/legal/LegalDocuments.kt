package com.rectime.mobile.feature.legal

import com.rectime.mobile.core.config.productionWebOrigin
import com.rectime.mobile.core.config.resolvePublicWebUrl
import com.rectime.mobile.core.platform.openExternalUrl
import kotlinx.coroutines.CancellationException

enum class LegalDocument(internal val path: String) {
    Terms("/legal/terms.html"),
    PrivacyPolicy("/legal/privacy.html"),
}

class LegalDocumentLauncher(
    private val origin: String = productionWebOrigin,
    private val openUrl: suspend (String) -> Boolean = { openExternalUrl(it) },
) {
    suspend fun open(document: LegalDocument): Boolean {
        val url = resolvePublicWebUrl(
            path = document.path,
            origin = origin,
        ) ?: return false

        return try {
            openUrl(url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Throwable messages may include the complete URL. Do not log them.
            false
        }
    }
}
