package com.rectime.mobile.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ArrowLeft
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark

@Composable
internal fun AppBarButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "appBarBtnScale",
    )
    Box(
        modifier = modifier
            .size(AppTheme.layout.headerAction)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content?.invoke()
    }
}

@Composable
fun AppBar(
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
            .height(AppTheme.layout.headerAction),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.layout.headerAction),
        )
        if (leading != null || onLeadingClick != null) {
            AppBarButton(
                onClick = onLeadingClick,
                content = leading,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (trailing != null || onTrailingClick != null) {
            AppBarButton(
                onClick = onTrailingClick,
                content = trailing,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
fun PushAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    AppBar(
        title = title,
        modifier = modifier,
        onLeadingClick = onBack,
        leading = {
            Icon(
                imageVector = SolidGroup.ArrowLeft,
                contentDescription = "戻る",
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        },
        onTrailingClick = onTrailingClick,
        trailing = trailing,
    )
}

@Composable
fun SheetAppBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBar(
        title = title,
        modifier = modifier,
        onTrailingClick = onClose,
        trailing = {
            Icon(
                imageVector = SolidGroup.Xmark,
                contentDescription = "閉じる",
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
