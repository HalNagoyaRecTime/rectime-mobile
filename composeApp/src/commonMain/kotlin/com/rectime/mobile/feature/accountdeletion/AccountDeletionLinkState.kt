package com.rectime.mobile.feature.accountdeletion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val ACCOUNT_DELETION_OPEN_ERROR =
    "削除手続きページを開けませんでした。もう一度お試しください。"

internal class AccountDeletionLinkState(
    private val openPage: suspend () -> Boolean,
) {
    var isDialogVisible: Boolean by mutableStateOf(false)
        private set

    var isOpening: Boolean by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    fun showDialog() {
        if (isOpening) return
        errorMessage = null
        isDialogVisible = true
    }

    fun dismissDialog() {
        if (isOpening) return
        errorMessage = null
        isDialogVisible = false
    }

    suspend fun open() {
        if (isOpening) return
        isOpening = true
        errorMessage = null
        try {
            if (openPage()) {
                isDialogVisible = false
            } else {
                errorMessage = ACCOUNT_DELETION_OPEN_ERROR
            }
        } finally {
            isOpening = false
        }
    }
}
