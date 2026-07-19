package com.rectime.mobile.feature.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

data class DetailScreen(val eventId: Int) : Screen {
    override val key: String = "detail_$eventId"

    @Composable
    override fun Content(navigationController: NavigationController) {

        val viewModel = viewModel(key = key) { DetailViewModel(eventId) }
        val uiState by viewModel.uiState.collectAsState()

        PushScreenScaffold(
            title = "競技詳細",
            onBack = { navigationController.requestPop() },
            bottomContent = {
                Button(
                    onClick = {
                        //画面遷移先を後程追加
                    },
                    modifier = Modifier.fillMaxWidth()
                ){
                    Text("呼び出し情報")
                }
            },
        ) {
            item {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    uiState.error != null -> {
                        Text(
                            text = uiState.error ?: "不明なエラー",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }

                    uiState.eventDetail != null -> {
                        uiState.eventDetail?.let { event ->
                            Text(
                                text = event.eventName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )

                            Text(
                                text = event.ruleText ?: "説明はありません",
                                color = AppTheme.colors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
