package com.rectime.mobile.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.recmap

// 横長のマップをできるだけ大きく見せるため、画面幅いっぱいに近いカードにする
private const val MapModalWidthRatio = 0.96f
private val CloseButtonSize = 32.dp
private val CloseIconSize = 18.dp
private val MapCornerRadius = 12.dp
private val HintFontSize = 14.sp
private const val MinScale = 1f
private const val MaxScale = 5f

@Composable
fun MapModal(onDismiss: () -> Unit) {
    AppModal(onDismiss = onDismiss, widthRatio = MapModalWidthRatio) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ピンチ操作で拡大・縮小できます",
                color = AppTheme.colors.textMapModal,
                fontSize = HintFontSize,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(CloseButtonSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SolidGroup.Xmark,
                    contentDescription = "閉じる",
                    tint = AppTheme.colors.textMapModal,
                    modifier = Modifier.size(CloseIconSize),
                )
            }
        }

        ZoomableMap()
    }
}

@Composable
private fun ZoomableMap() {
    var scale by remember { mutableFloatStateOf(MinScale) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MapCornerRadius))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MinScale, MaxScale)
                    // 拡大した分だけはみ出した範囲内に平行移動を収める
                    val maxOffsetX = size.width * (scale - 1f) / 2f
                    val maxOffsetY = size.height * (scale - 1f) / 2f
                    offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                    offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                }
            },
    ) {
        Image(
            painter = painterResource(Res.drawable.recmap),
            contentDescription = "会場マップ",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
