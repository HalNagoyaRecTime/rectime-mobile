package com.rectime.mobile.ui.component

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.rectime.mobile.core.images.exportableImage
import androidx.compose.material3.CircularProgressIndicator
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.rectime.mobile.core.images.ImageSaver
import androidx.compose.ui.platform.testTag
import com.rectime.mobile.core.images.ImageSaveResult
import com.rectime.mobile.core.images.imageFileName
import com.rectime.mobile.core.images.rememberPlatformImageSaver
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Download
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronLeft
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val ViewerMinScale = 1f
private const val ViewerSwipeThresholdDp = 56
private const val ViewerDismissThresholdDp = 120
private const val ViewerTouchSlopDp = 8
private val ViewerControlSize = 48.dp

/** Resource and/or remote image. A resource acts as the offline fallback when both are supplied. */
data class ImageViewerItem(
    val title: String,
    val resource: DrawableResource? = null,
    val remoteUrl: String? = null,
) {
    init { require(resource != null || !remoteUrl.isNullOrBlank()) }
}

private enum class ViewerGestureMode { Undecided, HorizontalSwipe, VerticalSwipe, PanZoom }

/** Reusable modal for any image group. Gestures stay in a separate modal window. */
@Composable
fun ImageViewerDialog(images: List<ImageViewerItem>, initialIndex: Int = 0, onClose: () -> Unit) {
    if (images.isEmpty()) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ModalScrimController(dimAmount = 0f)
        ImageViewerContent(images, initialIndex, onClose)
    }
}

/** Embed inside an existing full-screen modal; use ImageViewerDialog for standalone calls. */
@Composable
fun ImageViewerContent(
    images: List<ImageViewerItem>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
    saver: ImageSaver = rememberPlatformImageSaver(),
) {
    if (images.isEmpty()) return
    var currentIndex by remember(images, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)))
    }
    val transform = remember(images, currentIndex) { ImageViewerTransform() }
    var swipeDownDistance by remember(images, currentIndex) { mutableFloatStateOf(0f) }
    var swipeHorizontalDistance by remember(images, currentIndex) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val displayedDown by animateFloatAsState(swipeDownDistance, if (dragging) snap() else spring())
    val displayedHorizontal by animateFloatAsState(swipeHorizontalDistance, if (dragging) snap() else spring())
    val scope = rememberCoroutineScope()
    val close by rememberUpdatedState(onClose)
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var tapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { ViewerTouchSlopDp.dp.toPx() }
    val swipeThresholdPx = with(density) { ViewerSwipeThresholdDp.dp.toPx() }
    val dismissThresholdPx = with(density) { ViewerDismissThresholdDp.dp.toPx() }
    val currentImage = images[currentIndex]

    val renderedImage = rememberViewerPainter(currentImage)
    val painter = renderedImage.painter
    LaunchedEffect(currentIndex) {
        tapJob?.cancel()
        lastTapTime = 0L
        saveMessage = null
    }
    fun saveCurrentImage() {
        tapJob?.cancel()
        lastTapTime = 0L
        if (saving || !renderedImage.canSave) return
        saving = true
        val title = currentImage.title
        scope.launch {
            try {
                val bitmap = renderPainterForSaving(painter)
                saveMessage = when (saver.save(bitmap, imageFileName(title))) {
                    ImageSaveResult.Saved -> "画像を保存しました"
                    ImageSaveResult.Cancelled -> null
                    ImageSaveResult.PermissionDenied -> "写真への保存を許可してください"
                    ImageSaveResult.Failed -> "保存できませんでした。もう一度お試しください"
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { saveMessage = "画像を保存できませんでした" }
            finally { saving = false }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = (0.92f * (1f - swipeDownDistance / 600f)).coerceIn(0.35f, 0.92f),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 68.dp, bottom = 64.dp)
                    .testTag("image-viewer-image")
                    .onSizeChanged {
                        transform.updateGeometry(Size(it.width.toFloat(), it.height.toFloat()), painter.intrinsicSize)
                    }
                    .graphicsLayer {
                        translationY = displayedDown
                        translationX = displayedHorizontal
                    }
                    .pointerInput(images, currentIndex, painter) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragging = true
                            val previousTapTime = lastTapTime
                            tapJob?.cancel()
                            var endTime = down.uptimeMillis
                            var tapPosition = down.position
                            var mode = ViewerGestureMode.Undecided
                            var totalPan = Offset.Zero
                            transform.viewport = Size(size.width.toFloat(), size.height.toFloat())
                            transform.image = painter.intrinsicSize

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val hasMultiplePointers = changes.size > 1

                                    if (mode == ViewerGestureMode.PanZoom || hasMultiplePointers ||
                                        zoom != 1f || (transform.scale > ViewerMinScale && pan != Offset.Zero)) {
                                        mode = ViewerGestureMode.PanZoom
                                        swipeDownDistance = 0f
                                        swipeHorizontalDistance = 0f
                                        transform.transform(zoom, pan, event.calculateCentroid(useCurrent = false))
                                    } else {
                                        totalPan += pan
                                        if (
                                            mode == ViewerGestureMode.Undecided &&
                                            totalPan.getDistance() > touchSlopPx
                                        ) {
                                            mode = if (kotlin.math.abs(totalPan.x) >= kotlin.math.abs(totalPan.y)) {
                                                ViewerGestureMode.HorizontalSwipe
                                            } else {
                                                ViewerGestureMode.VerticalSwipe
                                            }
                                        }

                                        if (mode == ViewerGestureMode.HorizontalSwipe) {
                                            swipeHorizontalDistance = totalPan.x * 0.65f
                                        }
                                        if (mode == ViewerGestureMode.VerticalSwipe) {
                                            swipeDownDistance = totalPan.y.coerceAtLeast(0f)
                                        }
                                    }

                                    // Consume every move in the viewer, including the
                                    // undecided phase, so EventDetail/root navigation
                                    // never receives a competing scroll or back gesture.
                                    changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                    endTime = changes.first().uptimeMillis
                                    tapPosition = changes.first().position
                                    if (changes.none { it.pressed }) break
                                }

                                if (mode != ViewerGestureMode.Undecided) lastTapTime = 0L
                                dragging = false
                                swipeHorizontalDistance = 0f
                                when (mode) {
                                    ViewerGestureMode.HorizontalSwipe -> {
                                        if (kotlin.math.abs(totalPan.x) >= swipeThresholdPx) {
                                            val direction = if (totalPan.x < 0f) 1 else -1
                                            currentIndex = (currentIndex + direction).coerceIn(0, images.lastIndex)
                                        }
                                        swipeDownDistance = 0f
                                    }

                                    ViewerGestureMode.VerticalSwipe -> {
                                        if (totalPan.y >= dismissThresholdPx) {
                                            close()
                                        } else {
                                            swipeDownDistance = 0f
                                        }
                                    }

                                    ViewerGestureMode.Undecided -> {
                                        swipeDownDistance = 0f
                                        val doubleTap = previousTapTime > 0 && endTime - previousTapTime <= 300
                                        if (doubleTap) {
                                            lastTapTime = 0L
                                            transform.doubleTap(tapPosition)
                                        } else {
                                            lastTapTime = endTime
                                            tapJob = scope.launch {
                                                delay(300)
                                                close()
                                            }
                                        }
                                    }
                                    ViewerGestureMode.PanZoom -> swipeDownDistance = 0f
                                }
                            } finally {
                                dragging = false
                                swipeDownDistance = 0f
                                swipeHorizontalDistance = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (renderedImage.isLoading && currentImage.resource == null) {
                    CircularProgressIndicator(color = Color.White)
                }
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        slideInHorizontally(tween(220)) { it * direction } togetherWith
                            slideOutHorizontally(tween(220)) { -it * direction }
                    },
                ) { index ->
                    val pagePainter = rememberViewerPainter(images[index]).painter
                    Image(
                        painter = pagePainter,
                        contentDescription = images[index].title,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = transform.scale
                            scaleY = transform.scale
                            translationX = transform.offset.x
                            translationY = transform.offset.y
                        },
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            ViewerControlButton(
                contentDescription = "閉じる",
                icon = SolidGroup.Xmark,
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp),
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.6f),
            )

            ViewerControlButton(
                contentDescription = if (saving) "保存中" else "画像を保存",
                icon = SolidGroup.Download,
                onClick = ::saveCurrentImage,
                enabled = !saving && renderedImage.canSave,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp),
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.6f),
            )

            Text(
                text = currentImage.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 64.dp, end = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )

            if (currentIndex > 0) {
                ViewerControlButton(
                    contentDescription = "前の画像",
                    icon = SolidGroup.ChevronLeft,
                    onClick = { currentIndex -= 1 },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                    tint = Color.White,
                    background = Color.Black.copy(alpha = 0.6f),
                )
            }
            if (currentIndex < images.lastIndex) {
                ViewerControlButton(
                    contentDescription = "次の画像",
                    icon = SolidGroup.ChevronRight,
                    onClick = { currentIndex += 1 },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    tint = Color.White,
                    background = Color.Black.copy(alpha = 0.6f),
                )
            }

            Text(
                text = if (saving) "保存中…" else saveMessage ?: renderedImage.status ?: "${currentIndex + 1} / ${images.size}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable(enabled = renderedImage.retry != null) { renderedImage.retry?.invoke() }
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun ViewerControlButton(
    contentDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = ViewerControlSize,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private data class ViewerPainter(
    val painter: Painter,
    val canSave: Boolean,
    val isLoading: Boolean = false,
    val status: String? = null,
    val retry: (() -> Unit)? = null,
)

@Composable
private fun rememberViewerPainter(image: ImageViewerItem): ViewerPainter {
    val fallback = image.resource?.let { painterResource(it) }
    if (image.remoteUrl == null) return ViewerPainter(requireNotNull(fallback), true)
    val context = LocalPlatformContext.current
    val request = remember(context, image.remoteUrl) {
        ImageRequest.Builder(context).data(image.remoteUrl).exportableImage().build()
    }
    val remote = rememberAsyncImagePainter(request)
    val state by remote.state.collectAsState()
    return when (state) {
        is AsyncImagePainter.State.Success -> ViewerPainter(remote, true)
        is AsyncImagePainter.State.Error -> ViewerPainter(
            fallback ?: remote, fallback != null,
            status = if (fallback != null) "代替画像を表示中 · タップで再読み込み" else "読み込めませんでした · タップで再読み込み",
            retry = remote::restart,
        )
        else -> ViewerPainter(fallback ?: remote, false, isLoading = true)
    }
}

private fun renderPainterForSaving(painter: Painter): ImageBitmap {
    val size = painter.intrinsicSize
    require(size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0)
    val factor = (4096f / maxOf(size.width, size.height)).coerceAtMost(1f)
    val bitmap = ImageBitmap((size.width * factor).toInt().coerceAtLeast(1), (size.height * factor).toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        with(painter) { draw(this@draw.size) }
    }
    return bitmap
}
