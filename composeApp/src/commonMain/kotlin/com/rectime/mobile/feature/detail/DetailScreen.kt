package com.rectime.mobile.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenHeader
import com.rectime.mobile.ui.theme.AppTheme

data class DetailScreen(val id: String) : Screen {
    override val key: String = "detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val screenHorizontalPadding = AppTheme.layout.screenHorizontalPadding
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTheme.layout.headerAction

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, horizontal = screenHorizontalPadding),
            ) {
                Text(
                    text = "詳細情報の表示エリアです。",
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            PushScreenHeader(
                title = "詳細",
                onBack = { navigationController.requestPop() },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = screenHorizontalPadding),
            )
        }
    }
}
