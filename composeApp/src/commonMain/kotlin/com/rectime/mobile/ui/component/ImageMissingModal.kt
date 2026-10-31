package com.rectime.mobile.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

internal const val IMAGE_MISSING_MESSAGE = "画像がありません"

private const val ModalWidthRatio = 0.8f
private val ModalContentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp)
private val IconSize = 28.dp
private val IconBorderWidth = 2.dp
private val IconFontSize = 16.sp
private val MessageFontSize = 20.sp
private val ContentSpacing = 12.dp

/**
 * 表示する画像が無いときの案内。白背景・角丸のモーダルに「!」アイコンとメッセージを出す。
 * モーダルの外側のタップや戻る操作で閉じる。
 */
@Composable
internal fun ImageMissingModal(onDismiss: () -> Unit) {
    val color = AppTheme.colors.textPrimary
    AppModal(
        onDismiss = onDismiss,
        widthRatio = ModalWidthRatio,
        contentPadding = ModalContentPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ContentSpacing),
        ) {
            // アイコンは装飾なので読み上げ対象から外し、メッセージだけを読ませる。
            Box(
                modifier = Modifier
                    .size(IconSize)
                    .border(IconBorderWidth, color, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = color, fontSize = IconFontSize, fontWeight = FontWeight.Bold)
            }
            Text(IMAGE_MISSING_MESSAGE, color = color, fontSize = MessageFontSize, fontWeight = FontWeight.Bold)
        }
    }
}
