package com.rectime.mobile.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.EllipsisVertical
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 画像URLと任意のタイトルを渡す、共通の画像ビューア。 */
@Composable
fun ImageViewerDialog(
    imageUrl: String,
    contentDescription: String,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    var closeRequested by remember(imageUrl) { mutableStateOf(false) }
    Dialog(onDismissRequest = { closeRequested = true }, properties = imageViewerDialogProperties()) {
        ModalScrimController(dimAmount = 0f)
        ImageViewerWindowController()
        val painter = rememberAsyncImagePainter(imageUrl)
        val state by painter.state.collectAsState()
        val openActions = rememberImageActions()
        val scope = rememberCoroutineScope()
        var preparing by remember(imageUrl) { mutableStateOf(false) }
        var actionError by remember(imageUrl) { mutableStateOf(false) }
        val image = (state as? AsyncImagePainter.State.Success)?.result?.image
        ImageViewerContent(
            painter = painter,
            contentDescription = contentDescription,
            onDismiss = onDismiss,
            title = title,
            closeRequested = closeRequested,
            actionsEnabled = image != null && !preparing,
            onMore = {
                if (image != null && !preparing) {
                    preparing = true
                    actionError = false
                    scope.launch {
                        try {
                            actionError = !openActions(image, title?.takeIf { it.isNotBlank() })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            actionError = true
                        } finally {
                            preparing = false
                        }
                    }
                }
            },
        ) {
            when {
                actionError -> ViewerStatus("画像メニューを開けませんでした")
                state is AsyncImagePainter.State.Error -> Text(
                    text = "再読み込み",
                    color = Color.White,
                    modifier = Modifier.clickable { painter.restart() },
                )
                state is AsyncImagePainter.State.Empty || state is AsyncImagePainter.State.Loading ->
                    Text("読み込み中", color = Color.White)
            }
        }
    }
}

/** 拡大中は画像移動、等倍時の一指下スワイプは閉じる操作にする。 */
@Composable
internal fun ImageViewerContent(
    painter: Painter,
    contentDescription: String,
    onDismiss: () -> Unit,
    title: String? = null,
    closeRequested: Boolean = false,
    actionsEnabled: Boolean = true,
    onMore: () -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    var dismissed by remember { mutableStateOf(false) }
    // OS・ボタン・スワイプの終了要求が重なっても呼び出し元へは一度だけ通知する。
    val dismiss = {
        if (!dismissed) {
            dismissed = true
            latestDismiss()
        }
    }
    val transform = remember(painter) { ImageViewerTransform() }
    val scope = rememberCoroutineScope()
    var zoomAnimation by remember(transform) { mutableStateOf<Job?>(null) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var dragDistance by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var swipeClosing by remember { mutableStateOf(false) }
    var buttonClosing by remember { mutableStateOf(false) }
    val latestCloseRequested by rememberUpdatedState(closeRequested)
    val closing by remember {
        derivedStateOf { latestCloseRequested || buttonClosing || swipeClosing }
    }
    val backgroundOpacity = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }
    val flingDistance = with(density) { 24.dp.toPx() }
    val flingVelocity = with(density) { 900.dp.toPx() }
    // 画像は即時表示し、背景だけを塗る。閉じる下スワイプは画面外まで継続する。
    val exitDistance by remember(dismissThreshold) {
        derivedStateOf { maxOf(viewport.height, dragDistance) + dismissThreshold }
    }
    val drag by animateFloatAsState(
        targetValue = if (swipeClosing) exitDistance else dragDistance,
        animationSpec = when {
            dragging -> snap()
            closing -> tween(220, easing = FastOutLinearInEasing)
            else -> spring()
        },
        finishedListener = { if (swipeClosing && it >= exitDistance) dismiss() },
    )
    // 透明度の読み取りは描画段階で行い、背景のフェードで本文を再構成しない。
    fun dragProgress(): Float = if (viewport.height > 0) (drag / viewport.height).coerceIn(0f, 1f) else 0f
    LaunchedEffect(viewport, painter.intrinsicSize) {
        transform.updateGeometry(viewport, painter.intrinsicSize)
    }
    LaunchedEffect(closing) {
        if (closing) zoomAnimation?.cancel()
        backgroundOpacity.animateTo(if (closing) 0f else 1f, tween(180))
        if (closing && !swipeClosing) dismiss()
    }
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Color.Black.copy(alpha = .8f * backgroundOpacity.value * (1 - dragProgress())))
    }) {
        // 画像は画面端まで移動できる。操作ヘッダーだけをセーフエリア内に置く。
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = if (closing && !swipeClosing) backgroundOpacity.value else 1f
        }) {
            Box(
                modifier = Modifier.fillMaxSize().clipToBounds()
                    .testTag("image-viewer")
                    .semantics { stateDescription = "拡大率 ${transform.scale}" }
                    .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(transform) {
                        detectTapGestures(
                            onTap = { if (!closing) showControls = !showControls },
                            onDoubleTap = { position ->
                                if (!closing) {
                                    zoomAnimation?.cancel()
                                    zoomAnimation = scope.launch { transform.animateDoubleTap(position) }
                                }
                            },
                        )
                    }
                    .pointerInput(transform, dismissThreshold, flingDistance, flingVelocity) {
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            // 指の操作を優先し、アニメーションの現在位置からピンチ・移動を始める。
                            zoomAnimation?.cancel()
                            if (closing) {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                return@awaitEachGesture
                            }
                            val velocity = VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
                            var totalPan = Offset.Zero
                            var mode = 0 // 0: タップ判定中、1: 画像操作、2: 下スワイプ
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.firstOrNull { it.id == first.id }?.let {
                                        velocity.addPosition(it.uptimeMillis, it.position)
                                    }
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val fingers = event.changes.count { it.pressed }
                                    totalPan += pan
                                    if (fingers > 1) {
                                        mode = 1
                                        dragDistance = 0f
                                        dragging = false
                                    } else if (mode == 0 && totalPan.getDistance() > viewConfiguration.touchSlop) {
                                        mode = if (transform.scale <= 1f && totalPan.y > abs(totalPan.x)) 2 else 1
                                        dragging = mode == 2
                                    }
                                    when (mode) {
                                        1 -> transform.transform(zoom, pan, event.calculateCentroid())
                                        2 -> dragDistance = (dragDistance + pan.y).coerceAtLeast(0f)
                                    }
                                    if (mode != 0) event.changes.forEach { if (it.pressed) it.consume() }
                                } while (event.changes.any { it.pressed })
                                val farEnough = dragDistance >= minOf(dismissThreshold, viewport.height * .2f)
                                val flungDown = dragDistance >= flingDistance && velocity.calculateVelocity().y >= flingVelocity
                                if (mode == 2 && (farEnough || flungDown)) swipeClosing = true
                            } finally {
                                dragging = false
                                if (!closing) dragDistance = 0f
                            }
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
                        translationY = transform.offset.y + drag
                    },
                )
                Box(Modifier.graphicsLayer { alpha = 1 - dragProgress() }) { status() }
            }
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150)),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .height(64.dp)
                        .padding(horizontal = 12.dp).graphicsLayer { alpha = 1 - dragProgress() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerHeaderButton({ if (showControls) buttonClosing = true }, !closing) {
                        Icon(SolidGroup.Xmark, "閉じる", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        if (!title.isNullOrBlank()) Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ViewerHeaderButton({ if (showControls) onMore() }, actionsEnabled && !closing) {
                        Icon(SolidGroup.EllipsisVertical, "画像メニュー", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerHeaderButton(onClick: () -> Unit, enabled: Boolean = true, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else .4f },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun ViewerStatus(message: String) {
    Text(message, color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .65f)).padding(12.dp))
}
