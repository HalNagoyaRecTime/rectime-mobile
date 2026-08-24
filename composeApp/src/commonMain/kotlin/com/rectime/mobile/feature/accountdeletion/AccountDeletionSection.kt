package com.rectime.mobile.feature.accountdeletion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun AccountDeletionSection(
    modifier: Modifier = Modifier,
) {
    val launcher = remember { AccountDeletionLauncher() }
    AccountDeletionSection(modifier = modifier, launcher = launcher)
}

@Composable
internal fun AccountDeletionSection(
    modifier: Modifier,
    launcher: AccountDeletionLauncher,
) {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(launcher) {
        AccountDeletionLinkState(openPage = launcher::open)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (state.isOpening) {
                    stateDescription = "削除手続きページを開いています"
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "アカウント削除手続き",
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = "RecTimeアカウントの削除はWebで手続きします。",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        Text(
            text = "Microsoft 365アカウント自体は削除されません。Webページで内容を確認し、削除手続きの最終確認を行ってください。",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        OutlinedButton(
            enabled = !state.isOpening,
            onClick = {
                coroutineScope.launch {
                    state.open()
                }
            },
        ) {
            Text("削除手続きページを開く")
        }
        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
    }
}
