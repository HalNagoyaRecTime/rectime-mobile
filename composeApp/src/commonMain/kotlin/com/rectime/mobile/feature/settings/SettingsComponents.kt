package com.rectime.mobile.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import com.rectime.mobile.core.config.appVersion
import com.rectime.mobile.core.platform.platformAppIconPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_notification_outline

// 52dpのログアウトボタンと同じ丸みをセクションにも使う。
internal val SettingsCornerRadius = 26.dp

internal enum class SettingsIcon(val path: String) {
    Notification(""),
    Contact("M3,4 L21,4 L21,20 L3,20 Z M3,5 L12,12 L21,5"),
    Terms("M6,2 L15,2 L20,7 L20,22 L6,22 Z M15,2 L15,7 L20,7 M9,12 L17,12 M9,16 L17,16"),
    Privacy("M12,2 L21,6 L20,14 C19,18 16,21 12,23 C8,21 5,18 4,14 L3,6 Z M9,11 L9,16 L15,16 L15,11 Z M10,11 L10,9 C10,6 14,6 14,9 L14,11"),
    Delete("M4,6 L20,6 M9,6 L9,3 L15,3 L15,6 M6,6 L7,22 L17,22 L18,6 M10,10 L10,18 M14,10 L14,18"),
    Version("M12,2 C6.5,2 2,6.5 2,12 C2,17.5 6.5,22 12,22 C17.5,22 22,17.5 22,12 C22,6.5 17.5,2 12,2 Z M12,10 L12,17 M12,6 L12,7"),
    Chevron("M9,5 L16,12 L9,19"),
    ;

    val image: ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
    ).build()
}

@Composable
internal fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = AppTheme.colors.userInformationHeader,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(SettingsCornerRadius))
                .background(AppTheme.colors.commonBackground),
            content = content,
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    icon: SettingsIcon,
    detail: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val color = when {
        !enabled -> AppTheme.colors.textMuted
        else -> AppTheme.colors.userInformationBody
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon == SettingsIcon.Notification) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_notification_outline),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(icon.image, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Text(title, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
            if (subtitle != null) {
                Text(subtitle, color = AppTheme.colors.textMuted, fontSize = 14.sp)
            }
        }
        if (detail != null) {
            Text(detail, color = AppTheme.colors.textMuted, fontSize = 13.sp)
        }
        if (onClick != null) {
            Icon(
                SettingsIcon.Chevron.image,
                contentDescription = null,
                tint = AppTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SettingsSeparator() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp, end = 16.dp),
        color = AppTheme.colors.commonSeparatorLine,
        thickness = 0.5.dp,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppInformationSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.commonBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = platformAppIconPainter(),
                contentDescription = "RE:CREATION アプリアイコン",
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "RE:CREATION",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.userInformationBody,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Version $appVersion",
                fontSize = 14.sp,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(AppTheme.colors.settingBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = AppTheme.colors.commonSeparatorLine)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Produced by HAL Nagoya",
                fontSize = 14.sp,
                color = AppTheme.colors.userInformationBody,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Developed by RE:CREATION App Team",
                fontSize = 13.sp,
                color = AppTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
