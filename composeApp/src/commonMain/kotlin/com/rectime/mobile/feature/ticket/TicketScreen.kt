package com.rectime.mobile.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PressSurface
import com.rectime.mobile.ui.theme.AppTheme

/**
 * TicketScreen: Modal sheet for displaying the user's ticket/QR.
 */
object TicketScreen : Screen {
    override val key: String = "ticket"

    @Composable
    override fun Content(navigationController: NavigationController) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "マイQR", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = "チケット情報のワイヤー表示", color = AppTheme.colors.textSecondary)
            PressSurface(
                onClick = { navigationController.requestDismissSheet() },
                color = AppTheme.colors.surfaceAccent,
            ) {
                Text(text = "閉じる", color = AppTheme.colors.textPrimary)
            }
        }
    }
}
