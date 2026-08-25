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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

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
                .height(AppTheme.layout.headerEdgeFade)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.colors.edgeFadeColor,
                            AppTheme.colors.edgeFadeColor.copy(alpha = 0f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = AppTheme.colors.headerBackground),
        ){
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
                    )
                }
            }
        }
    }
}
