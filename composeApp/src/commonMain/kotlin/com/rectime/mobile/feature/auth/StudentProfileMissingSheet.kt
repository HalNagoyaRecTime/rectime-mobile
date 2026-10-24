package com.rectime.mobile.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.component.AppBottomSheet
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.TriangleExclamation
import com.rectime.mobile.ui.theme.AppTheme

private const val ERROR_LABEL = "要連絡"
internal const val STUDENT_PROFILE_MISSING_TITLE = "学生情報を受け取れませんでした"
internal const val STUDENT_PROFILE_MISSING_MESSAGE =
    "お手数ですが、レクリエーション当日までに運営へご連絡ください。\n\n" +
        "※当日は対応できません。ご注意ください。"

private const val CLOSE_BUTTON_LABEL = "閉じる"
private val ErrorIconSize = 25.dp
private val ErrorLabelFontSize = 25.sp
private val TitleFontSize = 20.sp
private val MessageFontSize = 14.sp
private val MessageLineHeight = 22.sp
private val CloseButtonContentColor = Color.White

/**
 * 学生登録漏れの案内。見落としを防ぐため、戻る操作やシート外のタップでは閉じず、
 * 下スワイプか閉じるボタンでだけ閉じる。閉じた後もアプリはそのまま使える。
 */
@Composable
internal fun StudentProfileMissingSheet(onDismiss: () -> Unit) {
    val spacing = AppTheme.spacing
    AppBottomSheet(onDismiss = onDismiss, dismissOnBackPressOrOutsideTap = false) { close ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xxl, vertical = spacing.lg),
        ) {
            // ぱっと見で異常だと分かるよう、タイトルの上に警告アイコンと「エラー」を出す。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = SolidGroup.TriangleExclamation,
                    contentDescription = null,
                    tint = AppTheme.colors.textLoginError,
                    modifier = Modifier.size(ErrorIconSize),
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = ERROR_LABEL,
                    fontSize = ErrorLabelFontSize,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textLoginError,
                )
            }
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = STUDENT_PROFILE_MISSING_TITLE,
                fontSize = TitleFontSize,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textDetailsScreenBody,
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                text = STUDENT_PROFILE_MISSING_MESSAGE,
                fontSize = MessageFontSize,
                fontWeight = SemiBold,
                lineHeight = MessageLineHeight,
                color = AppTheme.colors.textDetailsScreenBody,
            )
            Spacer(Modifier.height(spacing.xxl))
            Button(
                onClick = close,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.themeColorFirst,
                    contentColor = CloseButtonContentColor,
                ),
            ) {
                Text(CLOSE_BUTTON_LABEL)
            }
            Spacer(Modifier.height(spacing.sm))
        }
    }
}
