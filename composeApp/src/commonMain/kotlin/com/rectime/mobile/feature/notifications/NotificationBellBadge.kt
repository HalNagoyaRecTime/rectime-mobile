package com.rectime.mobile.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Bell

@Composable
fun NotificationBellBadge(
    modifier: Modifier = Modifier,
) {
    val repository = remember { NotificationsRepository() }
    val unreadCount by NotificationUnreadStore.unreadCount.collectAsStateWithLifecycle()

    LaunchedEffect(repository) {
        runCatching {
            repository.getUnreadCount()
        }.onSuccess { count ->
            NotificationUnreadStore.setCount(count)
        }
    }

    Box(modifier = modifier) {
        Icon(
            imageVector = SolidGroup.Bell,
            contentDescription = "通知",
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp),
        )
        if (unreadCount > 0) {
            Text(
                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                color = AppTheme.colors.textOnAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-7).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppTheme.colors.surfaceAccentStrong)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
