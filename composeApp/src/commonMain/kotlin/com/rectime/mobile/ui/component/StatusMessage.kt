package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

// 空・エラー状態の一覧画面(通知、ランキング等)で共通して使う、
// メッセージ+任意の再試行ボタンの表示。
@Composable
fun StatusMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = AppTheme.colors.textSecondary,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            PressSurface(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.Button },
                color = if (actionEnabled) AppTheme.colors.themeColorFirst else AppTheme.colors.borderSubtle,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    actionLabel,
                    color = if (actionEnabled) AppTheme.colors.textThemeColorFirst else AppTheme.colors.textMuted,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
