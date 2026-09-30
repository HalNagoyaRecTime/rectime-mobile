package com.rectime.mobile.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronLeft
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.map_1f
import rectime_mobile.composeapp.generated.resources.map_2f
import rectime_mobile.composeapp.generated.resources.map_class_area

private const val ViewerMinScale = 1f
private const val ViewerMaxScale = 4f
private const val ViewerSwipeThresholdDp = 56
private const val ViewerDismissThresholdDp = 120
private const val ViewerTouchSlopDp = 8
private const val PreviewAspectRatio = 16f / 9f

private val ModalHorizontalPadding = 16.dp
private val MapCardShape = RoundedCornerShape(12.dp)
private val ViewerControlSize = 48.dp

/** The two independent image groups shown by the map modal. */
internal enum class VenueMapSectionId {
    ClassMeetingPoint,
    Facility,
}

internal enum class VenueMapImageId {
    ClassMeetingPoint,
    FacilityFirstFloor,
    FacilitySecondFloor,
}

internal data class VenueMapImage(
    val id: VenueMapImageId,
    val title: String,
    val bundledResource: DrawableResource,
    val remoteUrl: String? = null,
)

internal data class VenueMapSection(
    val id: VenueMapSectionId,
    val title: String,
    val images: List<VenueMapImage>,
)

/**
 * The API currently exposes only /map/recmap.png. The floor maps therefore stay
 * bundled until the backend publishes separate, documented URLs for them.
 */
internal fun venueMapSections(baseUrl: String = apiBaseUrl): List<VenueMapSection> = listOf(
    VenueMapSection(
        id = VenueMapSectionId.ClassMeetingPoint,
        title = "各クラス集合場所",
        images = listOf(
            VenueMapImage(
                id = VenueMapImageId.ClassMeetingPoint,
                title = "各クラス集合場所",
                bundledResource = Res.drawable.map_class_area,
                remoteUrl = venueMapImageUrl(baseUrl),
            ),
        ),
    ),
    VenueMapSection(
        id = VenueMapSectionId.Facility,
        title = "施設案内マップ",
        images = listOf(
            VenueMapImage(
                id = VenueMapImageId.FacilityFirstFloor,
                title = "施設案内マップ 1F",
                bundledResource = Res.drawable.map_1f,
            ),
            VenueMapImage(
                id = VenueMapImageId.FacilitySecondFloor,
                title = "施設案内マップ 2F",
                bundledResource = Res.drawable.map_2f,
            ),
        ),
    ),
)

internal fun initialIndexFor(
    images: List<VenueMapImage>,
    imageId: VenueMapImageId,
): Int = images.indexOfFirst { it.id == imageId }.coerceAtLeast(0)

private data class ViewerSelection(
    val section: VenueMapSection,
    val initialIndex: Int,
)

private enum class ViewerGestureMode {
    Undecided,
    HorizontalSwipe,
    VerticalSwipe,
    PanZoom,
}

@Composable
fun MapModal(onDismiss: () -> Unit) {
    val sections = remember { venueMapSections() }
    var viewerSelection by remember { mutableStateOf<ViewerSelection?>(null) }

    // This is intentionally one Dialog. Switching from the list to the viewer
    // changes the content of the same modal, so EventDetail never gets a second
    // navigation/modal layer underneath the image viewer.
    Dialog(
        onDismissRequest = {
            if (viewerSelection != null) {
                viewerSelection = null
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The viewer supplies its own dark scrim. Avoid adding a second opaque
        // platform scrim behind it on Android.
        ModalScrimController(dimAmount = 0f)

        val selection = viewerSelection
        if (selection == null) {
            VenueMapList(
                sections = sections,
                onDismiss = onDismiss,
                onOpenViewer = { section, index ->
                    viewerSelection = ViewerSelection(section = section, initialIndex = index)
                },
            )
        } else {
            VenueMapViewer(
                images = selection.section.images,
                initialIndex = selection.initialIndex,
                onClose = { viewerSelection = null },
            )
        }
    }
}

@Composable
private fun VenueMapList(
    sections: List<VenueMapSection>,
    onDismiss: () -> Unit,
    onOpenViewer: (VenueMapSection, Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.commonBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            MapModalHeader(title = "会場マップ", onClose = onDismiss)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ModalHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 19.sp,
                    )
                    section.images.forEachIndexed { index, image ->
                        VenueMapPreview(
                            image = image,
                            onClick = { onOpenViewer(section, index) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MapModalHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppTheme.colors.textPrimary,
            fontSize = 21.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        ViewerControlButton(
            contentDescription = "閉じる",
            icon = SolidGroup.Xmark,
            onClick = onClose,
            tint = AppTheme.colors.textPrimary,
            background = Color.Transparent,
        )
    }
}

@Composable
private fun VenueMapPreview(
    image: VenueMapImage,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MapCardShape)
            .border(1.dp, AppTheme.colors.borderSubtle, MapCardShape)
            .clickable(onClick = onClick),
    ) {
        VenueMapImageContent(
            image = image,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PreviewAspectRatio),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = image.title,
            color = AppTheme.colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun VenueMapViewer(
    images: List<VenueMapImage>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    var currentIndex by remember(images, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)))
    }
    var scale by remember(images, initialIndex) { mutableFloatStateOf(ViewerMinScale) }
    var offset by remember(images, initialIndex) { mutableStateOf(Offset.Zero) }
    var swipeDownDistance by remember(images, initialIndex) { mutableFloatStateOf(0f) }

    val latestScaleState = rememberUpdatedState(scale)
    val latestOffsetState = rememberUpdatedState(offset)
    val density = LocalDensity.current
    val touchSlopPx = with(density) { ViewerTouchSlopDp.dp.toPx() }
    val swipeThresholdPx = with(density) { ViewerSwipeThresholdDp.dp.toPx() }
    val dismissThresholdPx = with(density) { ViewerDismissThresholdDp.dp.toPx() }
    val currentImage = images[currentIndex]

    LaunchedEffect(currentIndex) {
        scale = ViewerMinScale
        offset = Offset.Zero
        swipeDownDistance = 0f
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
                    .graphicsLayer { translationY = swipeDownDistance }
                    .pointerInput(images, currentIndex) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)

                            var mode = ViewerGestureMode.Undecided
                            var totalPan = Offset.Zero
                            var gestureScale = latestScaleState.value
                            var gestureOffset = latestOffsetState.value

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val hasMultiplePointers = changes.size > 1

                                if (hasMultiplePointers || zoom != 1f || gestureScale > ViewerMinScale) {
                                    mode = ViewerGestureMode.PanZoom
                                    gestureScale = (gestureScale * zoom).coerceIn(
                                        ViewerMinScale,
                                        ViewerMaxScale,
                                    )
                                    gestureOffset = clampViewerOffset(
                                        offset = gestureOffset + pan * gestureScale,
                                        scale = gestureScale,
                                        size = size,
                                    )
                                    // Pan/zoom is deliberately the only operation in
                                    // this branch. A zoomed image can never leak a
                                    // horizontal swipe to the pager or its parent.
                                    if (gestureScale != latestScaleState.value || pan != Offset.Zero) {
                                        scale = gestureScale
                                        offset = gestureOffset
                                    }
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
                                if (changes.none { it.pressed }) break
                            }

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
                                        onClose()
                                    } else {
                                        swipeDownDistance = 0f
                                    }
                                }

                                ViewerGestureMode.Undecided,
                                ViewerGestureMode.PanZoom,
                                -> swipeDownDistance = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                VenueMapImageContent(
                    image = currentImage,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
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

            Text(
                text = currentImage.title,
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
                text = "${currentIndex + 1} / ${images.size}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

private fun clampViewerOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxOffsetX = size.width * (scale - 1f) / 2f
    val maxOffsetY = size.height * (scale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

@Composable
private fun VenueMapImageContent(
    image: VenueMapImage,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val fallbackPainter = painterResource(image.bundledResource)
    val remoteUrl = image.remoteUrl
    if (remoteUrl == null) {
        Image(
            painter = fallbackPainter,
            contentDescription = image.title,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = remoteUrl,
            contentDescription = image.title,
            contentScale = contentScale,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            modifier = modifier,
        )
    }
}

@Composable
private fun ViewerControlButton(
    contentDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = ViewerControlSize,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
