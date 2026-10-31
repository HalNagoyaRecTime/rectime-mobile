package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable

/** 会場・集合場所の画像を表示する。画像が未登録のときはビューア内に「画像がありません」を表示する。 */
@Composable
fun PlaceImageModal(placeName: String, imageUrl: String?, onDismiss: () -> Unit) {
    ImageViewerDialog(
        imageUrl = imageUrl,
        contentDescription = placeName,
        title = placeName,
        onDismiss = onDismiss,
    )
}
