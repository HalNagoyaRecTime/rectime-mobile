package com.rectime.mobile.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.core.network.createAppHttpClient
import com.rectime.mobile.feature.auth.DevRoleOverride
import com.rectime.mobile.feature.auth.Role
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch

private sealed class TestResult {
    data object Idle : TestResult()
    data object Loading : TestResult()
    data class Success(val status: Int, val body: String) : TestResult()
    data class Error(val message: String) : TestResult()
}

object DebugScreen : Screen {
    override val key: String = "debug"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val scope = rememberCoroutineScope()
        var path by remember { mutableStateOf("/health") }
        var result by remember { mutableStateOf<TestResult>(TestResult.Idle) }

        PushScreenScaffold(
            title = "デバッグメニュー",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = "ロール（開発用切り替え）",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleChip(
                        label = "未設定（バックエンドの値を使用）",
                        selected = DevRoleOverride.role == null,
                        onClick = { DevRoleOverride.role = null },
                    )
                    Role.entries.forEach { role ->
                        RoleChip(
                            label = role.label,
                            selected = DevRoleOverride.role == role,
                            onClick = { DevRoleOverride.role = role },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "API Base URL",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = apiBaseUrl,
                    color = AppTheme.colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("パス") },
                    placeholder = { Text("/health") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "送信先: $apiBaseUrl$path",
                    color = AppTheme.colors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                result = TestResult.Loading
                                result = runTest(apiBaseUrl + path)
                            }
                        },
                        enabled = result !is TestResult.Loading,
                    ) {
                        Text("接続テスト")
                    }

                    if (result is TestResult.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (val r = result) {
                    is TestResult.Idle -> Unit
                    is TestResult.Loading -> Unit
                    is TestResult.Success -> {
                        val isOk = r.status in 200..299
                        ResultCard(
                            label = "ステータス",
                            value = r.status.toString(),
                            ok = isOk,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ResultCard(
                            label = "レスポンスボディ",
                            value = r.body.ifBlank { "(空)" },
                            ok = isOk,
                        )
                    }
                    is TestResult.Error -> {
                        ResultCard(
                            label = "エラー",
                            value = r.message,
                            ok = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PressSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) AppTheme.colors.surfaceAccent else AppTheme.colors.surfaceMuted,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ResultCard(label: String, value: String, ok: Boolean) {
    val bg = AppTheme.colors.surfaceMuted
    val labelColor = AppTheme.colors.textSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = labelColor, fontSize = 12.sp)
        Text(
            text = value,
            color = AppTheme.colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

private suspend fun runTest(url: String): TestResult {
    return try {
        val client = createAppHttpClient()
        val response = client.get(url)
        val body = response.bodyAsText()
        client.close()
        TestResult.Success(status = response.status.value, body = body)
    } catch (e: Exception) {
        TestResult.Error(message = e.message ?: "不明なエラー")
    }
}
