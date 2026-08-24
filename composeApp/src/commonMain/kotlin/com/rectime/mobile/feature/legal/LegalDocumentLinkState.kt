package com.rectime.mobile.feature.legal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val LEGAL_DOCUMENT_OPEN_ERROR =
    "ブラウザでページを開けませんでした。もう一度お試しください。"

internal class LegalDocumentLinkState(
    private val openDocument: suspend (LegalDocument) -> Boolean,
) {
    var isOpening: Boolean by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    suspend fun open(document: LegalDocument) {
        if (isOpening) return
        isOpening = true
        errorMessage = null
        try {
            if (!openDocument(document)) {
                errorMessage = LEGAL_DOCUMENT_OPEN_ERROR
            }
        } finally {
            isOpening = false
        }
    }
}
