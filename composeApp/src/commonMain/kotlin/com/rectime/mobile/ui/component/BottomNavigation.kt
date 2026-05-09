package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.CalendarDays
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.House

private data class NavigationItemConfig(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onSelectRoot: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavigationItemConfig(HomeScreen, "ホーム", SolidGroup.House),
        NavigationItemConfig(CalendarScreen, "日程", SolidGroup.CalendarDays),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.navigationSurface),
    ) {
        // Border top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppTheme.colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .heightIn(min = AppTheme.layout.bottomTabMinHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomNavigationItem(
                    label = item.label,
                    icon = item.icon,
                    selected = currentScreen.key == item.screen.key,
                    onClick = { onSelectRoot(item.screen) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) AppTheme.colors.navigationActive else AppTheme.colors.navigationInactive

    PressSurface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 1.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
