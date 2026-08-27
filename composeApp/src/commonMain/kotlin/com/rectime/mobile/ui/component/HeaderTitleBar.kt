package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.modifier.outerShadow

private val HeaderUnderlineHeight = 3.dp  // BottomNavIndicatorの値を流用
private val HeaderBottomRadius = 20.dp
private val BackIconOffsetX = 15.dp
private val HeaderContentBottomPadding = 7.dp

@Composable
internal fun headerTitleBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        AppTheme.layout.headerDetailAction +
        HeaderContentBottomPadding

@Composable
internal fun HeaderTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onLeadingClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(bottomStart = HeaderBottomRadius, bottomEnd = HeaderBottomRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = AppTheme.colors.detailsScreenHeaderBackground, shape = shape)
            .outerShadow(
                shape = shape,
                color = AppTheme.colors.dropShadowDark,
                blurRadius = 8.dp,
                offsetX = 0.dp,
                offsetY = 4.dp,
            )
            .clip(shape),
    ){
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = HeaderContentBottomPadding)
                .height(AppTheme.layout.headerDetailAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = AppTheme.colors.textDetailsScreenHeader,
                fontWeight = FontWeight.Medium,
                fontSize = 21.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.layout.headerAction),
            )
            if (leading != null || onLeadingClick != null) {
                AppIconButton(
                    onClick = onLeadingClick,
                    color = Color.Transparent,
                    content = leading,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = BackIconOffsetX),
                )
            }
            if (trailing != null || onTrailingClick != null) {
                AppIconButton(
                    onClick = onTrailingClick,
                    content = trailing,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HeaderUnderlineHeight)
                .background(AppTheme.colors.themeColorFirst),
        )
    }
}
