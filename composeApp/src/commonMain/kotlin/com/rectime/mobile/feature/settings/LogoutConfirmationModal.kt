package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable

@Composable
fun LogoutConfirmationModal(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsModal(
        title = "ログアウトしますか？",
        onDismiss = onDismiss,
        confirmText = "ログアウトする",
        onConfirm = onConfirm,
    )
}
