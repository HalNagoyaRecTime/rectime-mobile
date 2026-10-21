package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rectime.mobile.ui.theme.AppTheme

private const val DefaultModalWidthRatio = 0.9f
private val ModalCornerRadius = 16.dp
private val ModalPadding = 16.dp
private val ModalContentSpacing = 10.dp
private const val DefaultDimAmount = 0.3f // ← OS標準の暗幕(Androidのみ)をこの濃さに変更する（実際はOSデフォルト値に引っ張られ0.5f前後になる）

@Composable
fun AppModal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    widthRatio: Float = DefaultModalWidthRatio,
    // Dp単体からPaddingValuesに変更。省略時は今まで通り四方16dp（MapModal等の既存呼び出しは無変更で動く）
    contentPadding: PaddingValues = PaddingValues(ModalPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    // 既定のダイアログ幅だと画面幅いっぱいまで広げられないため、幅の制御はwidthRatioに委ねる
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // OS標準の暗幕(Androidのみ実際に効く。iOS/Desktopは何もしない)の濃さを調整する
        ModalScrimController(dimAmount = DefaultDimAmount)

        // ダイアログのウィンドウが画面幅いっぱいになるため、カードの中央寄せは自分で行う
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = modifier
                    .fillMaxWidth(widthRatio)
                    .clip(RoundedCornerShape(ModalCornerRadius))
                    .background(AppTheme.colors.commonBackground)
                    .padding(contentPadding),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ModalContentSpacing),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}