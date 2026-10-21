package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

/** Shared layout for settings dialogs, based on the logout confirmation design. */
@Composable
internal fun SettingsModal(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "キャンセル",
    enabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppModal(
        onDismiss = { if (enabled) onDismiss() },
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSettingModalHeader,
                textAlign = TextAlign.Center,
            )
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            Spacer(Modifier.height(32.dp))
            if (confirmText != null && onConfirm != null) {
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    shape = RoundedCornerShape(AppTheme.radius.full),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.themeColorFirst,
                        contentColor = AppTheme.colors.commonBackground,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(confirmText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = enabled,
                shape = RoundedCornerShape(AppTheme.radius.full),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppTheme.colors.textSettingModalBody,
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(dismissText, fontSize = 15.sp)
            }
        }
    }
}
