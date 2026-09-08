package com.rectime.mobile.feature.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun LegalDocumentLinks(
    modifier: Modifier = Modifier,
) {
    val launcher = remember { LegalDocumentLauncher() }
    LegalDocumentLinks(modifier = modifier, launcher = launcher)
}

@Composable
internal fun LegalDocumentLinks(
    modifier: Modifier,
    launcher: LegalDocumentLauncher,
) {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(launcher) {
        LegalDocumentLinkState(openDocument = launcher::open)
    }

    fun open(document: LegalDocument) {
        coroutineScope.launch {
            state.open(document)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (state.isOpening) stateDescription = "ブラウザを開いています"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = !state.isOpening,
                onClick = { open(LegalDocument.Terms) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(20.dp), // ← 最小タッチサイズ(40dp)を上書きして余白を消す
            ) {
                Text(
                    text = "利用規約",
                    fontSize = 13.sp,
                    color = AppTheme.colors.themeColorFirst,
                )
            }
            TextButton(
                enabled = !state.isOpening,
                onClick = { open(LegalDocument.PrivacyPolicy) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(20.dp),
            ) {
                Text(
                    text = "プライバシーポリシー",
                    fontSize = 13.sp,
                    color = AppTheme.colors.themeColorFirst,
                )
            }
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