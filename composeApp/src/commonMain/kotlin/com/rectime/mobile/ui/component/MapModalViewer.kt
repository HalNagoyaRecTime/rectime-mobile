package com.rectime.mobile.ui.component

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.rectime.mobile.core.config.apiBaseUrl
import com.rectime.mobile.ui.theme.AppTheme
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.map_1f
import rectime_mobile.composeapp.generated.resources.map_2f
import rectime_mobile.composeapp.generated.resources.map_class_area

private const val PreviewAspectRatio = 16f / 9f

private val ModalHorizontalPadding = 16.dp
private val MapCardShape = RoundedCornerShape(12.dp)

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

@Composable
fun MapModal(onDismiss: () -> Unit) {
    val sections = remember { venueMapSections() }
    val listScrollState = rememberScrollState()
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
                scrollState = listScrollState,
                onDismiss = onDismiss,
                onOpenViewer = { section, index ->
                    viewerSelection = ViewerSelection(section = section, initialIndex = index)
                },
            )
        } else {
            ImageViewerContent(
                images = selection.section.images.map { ImageViewerItem(it.title, it.bundledResource, it.remoteUrl) },
                initialIndex = selection.initialIndex,
                onClose = { viewerSelection = null },
            )
        }
    }
}

@Composable
private fun VenueMapList(
    sections: List<VenueMapSection>,
    scrollState: ScrollState,
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
                    .verticalScroll(scrollState)
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
