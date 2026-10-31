package com.rectime.mobile.ui.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** 会場・集合場所の画像を取得できないときにスナックバーで表示する文言。 */
const val PLACE_IMAGE_UNAVAILABLE_MESSAGE = "画像を取得できませんでした。"

/** 場所の画像URLが未登録（null・空文字）なら null を返す。 */
fun placeImageUrlOrNull(imageUrl: String?): String? = imageUrl?.takeIf { it.isNotBlank() }

/** 会場・集合場所の画像を表示する。 */
@Composable
fun PlaceImageModal(placeName: String, imageUrl: String, onDismiss: () -> Unit) {
    ImageViewerDialog(
        imageUrl = imageUrl,
        contentDescription = placeName,
        title = placeName,
        onDismiss = onDismiss,
    )
}

/**
 * 場所の画像を取得できないことを、[snackbarHostState] のスナックバーで知らせる関数を返す。
 * 連続で押されても通知が積み上がらないよう、表示中のスナックバーは閉じてから表示する。
 */
@Composable
fun rememberPlaceImageUnavailableNotifier(snackbarHostState: SnackbarHostState): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        val notify: () -> Unit = {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(PLACE_IMAGE_UNAVAILABLE_MESSAGE)
            }
        }
        notify
    }
}
