package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.modifier.outerShadow

private val TrailingIconOffsetX = (-10).dp

@Composable
fun RootHeader(
    title: String,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = AppTheme.colors.headerBackground)
                .outerShadow(
                    shape = RectangleShape,
                    color = AppTheme.colors.dropShadow,
                    blurRadius = 8.dp,
                    offsetX = 0.dp,
                    offsetY = 4.dp,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(AppTheme.layout.headerAction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = AppTheme.colors.themeColorFirst,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 25.dp, bottom = 8.dp),
                )
                if (trailing != null || onTrailingClick != null) {
                    AppIconButton(
                        onClick = onTrailingClick,
                        content = trailing,
                        color = Color.Transparent,
                        modifier = Modifier
                            .offset(x = TrailingIconOffsetX),
                    )
                }
            }
        }
    }
}
