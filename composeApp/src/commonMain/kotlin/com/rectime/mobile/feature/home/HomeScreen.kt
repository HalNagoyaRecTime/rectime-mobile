package com.rectime.mobile.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen

object HomeScreen : Screen {
    override val key: String = "home"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = HomeViewModel()
        val uiState = viewModel.uiState.value

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("ランキング")

            uiState.rankingItems.forEachIndexed { index, item ->
                Text("${index + 1}位 ${item.className} : ${item.point}pt")
            }
        }
    }
}
