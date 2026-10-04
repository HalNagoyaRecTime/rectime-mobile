package com.rectime.mobile.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark

/** 既存の画像URLを、枠のない全画面ビューアで開く。 */
@Composable
fun ImageViewerDialog(imageUrl: String, contentDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ModalScrimController(dimAmount = 0f)
        val painter = rememberAsyncImagePainter(imageUrl)
        val state by painter.state.collectAsState()
        ImageViewerContent(painter, contentDescription, onDismiss) {
            when (state) {
                is AsyncImagePainter.State.Error -> Text(
                    text = "再読み込み",
                    color = Color.White,
                    modifier = Modifier.clickable { painter.restart() },
                )
                is AsyncImagePainter.State.Empty, is AsyncImagePainter.State.Loading ->
                    Text("読み込み中", color = Color.White)
                else -> Unit
            }
        }
    }
}

/** 拡大・移動は画像に適用し、閉じる操作は画面上に固定する。 */
@Composable
internal fun ImageViewerContent(
    painter: Painter,
    contentDescription: String,
    onDismiss: () -> Unit,
    status: @Composable () -> Unit = {},
) {
    val transform = remember(painter) { ImageViewerTransform() }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(viewport, painter.intrinsicSize) {
        transform.updateGeometry(viewport, painter.intrinsicSize)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clipToBounds()
                .testTag("image-viewer")
                .semantics { stateDescription = "拡大率 ${transform.scale}" }
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(transform) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { transform.doubleTap(it) },
                    )
                }
                .pointerInput(transform) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        transform.transform(zoom, pan, centroid)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offset.x
                    translationY = transform.offset.y
                },
            )
            status()
        }
        if (showControls) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SolidGroup.Xmark, contentDescription = "閉じる", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}
