package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import com.rectime.mobile.core.config.apiBaseUrl

internal fun venueMapImageUrl(baseUrl: String = apiBaseUrl): String =
    "${baseUrl.trimEnd('/')}/map/recmap.png"

@Composable
fun MapModal(onDismiss: () -> Unit) {
    ImageViewerDialog(
        imageUrl = venueMapImageUrl(),
        contentDescription = "会場マップ",
        title = "会場マップ",
        onDismiss = onDismiss,
    )
}

@Composable
fun PlaceImageModal(placeName: String, imageUrl: String?, onDismiss: () -> Unit) {
    if (imageUrl == null) {
        MapModal(onDismiss = onDismiss)
    } else {
        ImageViewerDialog(
            imageUrl = imageUrl,
            contentDescription = placeName,
            title = placeName,
            onDismiss = onDismiss,
        )
    }
}
