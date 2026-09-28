package com.rectime.mobile.feature.accountdeletion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.component.SettingsModal
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun AccountDeletionSection(
    modifier: Modifier = Modifier,
    content: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
) {
    val launcher = remember { AccountDeletionLauncher() }
    AccountDeletionSection(modifier = modifier, launcher = launcher, content = content)
}

@Composable
internal fun AccountDeletionSection(
    modifier: Modifier,
    launcher: AccountDeletionLauncher,
    content: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
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
                    stateDescription = "アカウント削除ページを開いています"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (content != null) {
            content(!state.isOpening, state::showDialog)
        } else {
            TextButton(
                onClick = state::showDialog,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(
                    text = "アカウント削除",
                    fontSize = 13.sp,
                    color = AppTheme.colors.themeColorFirst,
                )
            }
        }

        if (state.isDialogVisible) {
            SettingsModal(
                title = "アカウント削除",
                onDismiss = state::dismissDialog,
                confirmText = if (state.isOpening) "ページを開いています…" else "アカウント削除ページを開く",
                onConfirm = { coroutineScope.launch { state.open() } },
                enabled = !state.isOpening,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().semantics {
                        if (state.isOpening) stateDescription = "アカウント削除ページを開いています"
                    },
                ) {
                    Text(
                        text = "アカウントの削除はWebで行います。",
                        fontSize = 13.sp,
                        color = AppTheme.colors.textSettingModalBody,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "遷移先で認証を行ってください。",
                        fontSize = 13.sp,
                        color = AppTheme.colors.textSettingModalBody,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
    }
}
