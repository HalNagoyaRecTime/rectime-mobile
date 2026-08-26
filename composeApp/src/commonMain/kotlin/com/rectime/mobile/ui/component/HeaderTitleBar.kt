package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color

@Composable
internal fun HeaderTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onLeadingClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = AppTheme.colors.detailsScreenHeaderBackground),
    ){
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppTheme.layout.headerAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = AppTheme.colors.textDetailsScreenHeader,
                fontWeight = FontWeight.Medium,
                fontSize = 25.sp,
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
                    modifier = Modifier.align(Alignment.CenterStart),
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
    }
}
