package com.rectime.mobile.feature.accountdeletion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            .semantics {
                if (state.isOpening) {
                    stateDescription = "削除手続きページを開いています"
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = state::showDialog,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(
                text = "アカウント削除手続き",
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.isDialogVisible) {
            AlertDialog(
                onDismissRequest = state::dismissDialog,
                title = {
                    Text("アカウント削除手続き")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics {
                            if (state.isOpening) {
                                stateDescription = "削除手続きページを開いています"
                            }
                        },
                    ) {
                        Text("RecTimeアカウントの削除はWebで手続きします。")
                        Text("Microsoft 365アカウント自体は削除されません。Webページで内容を確認し、削除手続きの最終確認を行ってください。")
                        state.errorMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !state.isOpening,
                        onClick = {
                            coroutineScope.launch {
                                state.open()
                            }
                        },
                    ) {
                        Text("削除手続きページを開く")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !state.isOpening,
                        onClick = state::dismissDialog,
                    ) {
                        Text("キャンセル")
                    }
                },
            )
        }
    }
}
