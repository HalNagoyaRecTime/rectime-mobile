package com.rectime.mobile.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.BackBtn
import com.rectime.mobile.ui.theme.AppTheme

/**
 * DetailScreen: Placeholder for any detail-level information.
 */
data class DetailScreen(val id: String) : Screen {
    override val key: String = "detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackBtn(onClick = { navigationController.requestPop() })
            }

            Text(
                text = "Detail: $id", 
                color = AppTheme.colors.textPrimary, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "詳細情報の表示エリアです。", 
                color = AppTheme.colors.textSecondary
            )
        }
    }
}
