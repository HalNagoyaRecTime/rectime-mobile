package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.rectime.mobile.core.images.ImageSaveResult
import com.rectime.mobile.core.images.ImageSaver
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.map_1f
import rectime_mobile.composeapp.generated.resources.map_2f
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ImageViewerUiTest {
    private val images = listOf(
        ImageViewerItem("1F", Res.drawable.map_1f),
        ImageViewerItem("2F", Res.drawable.map_2f),
    )

    @Test
    fun controlsSwitchPagesAndSaveDisplayedImage() = runComposeUiTest {
        var savedName: String? = null
        val saver = object : ImageSaver {
            override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult {
                assertTrue(image.width > 0 && image.height > 0)
                savedName = fileName
                return ImageSaveResult.Saved
            }
        }
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, {}, saver) } }
        onNodeWithText("1 / 2").assertExists()
        onNodeWithContentDescription("次の画像").performClick()
        onNodeWithText("2 / 2").assertExists()
        onNodeWithContentDescription("画像を保存").performClick()
        waitForIdle()
        assertEquals("2F.png", savedName)
        onNodeWithText("画像を保存しました").assertExists()
        val bitmap = onRoot().captureToImage()
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use {
                File(System.getProperty("java.io.tmpdir"), "rectime-image-viewer.png").writeBytes(it.bytes)
            }
        }
    }

    @Test
    fun doubleTapDoesNotDismissButSingleTapDoes() = runComposeUiTest {
        var closes = 0
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, { closes++ }) } }
        onNodeWithTag("image-viewer-image").performTouchInput { doubleClick(center) }
        waitForIdle()
        assertEquals(0, closes)
        onNodeWithTag("image-viewer-image").performTouchInput { click(center) }
        waitUntil(timeoutMillis = 2_000) { closes == 1 }
    }

    @Test
    fun horizontalSwipeChangesPageWithoutClosing() = runComposeUiTest {
        var closes = 0
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, { closes++ }) } }
        onNodeWithTag("image-viewer-image").performTouchInput {
            down(center)
            moveTo(Offset(center.x - 180f, center.y), delayMillis = 100)
            up()
        }
        waitForIdle()
        onNodeWithText("2 / 2").assertExists()
        assertEquals(0, closes)
    }
    @Test
    fun pinchAndZoomedPanCannotChangePageOrDismiss() = runComposeUiTest {
        var closes = 0
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, { closes++ }) } }
        onNodeWithTag("image-viewer-image").performTouchInput {
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            moveTo(0, center - Offset(120f, 0f), delayMillis = 100)
            moveTo(1, center + Offset(120f, 0f), delayMillis = 100)
            up(0)
            up(1)
        }
        onNodeWithTag("image-viewer-image").performTouchInput {
            down(center)
            moveTo(center + Offset(-180f, 180f), delayMillis = 100)
            up()
        }
        waitForIdle()
        onNodeWithText("1 / 2").assertExists()
        assertEquals(0, closes)
        val bitmap = onNodeWithTag("image-viewer-image").captureToImage()
        val color = bitmap.toPixelMap()[bitmap.width / 2, bitmap.height / 2]
        assertTrue(color.red + color.green + color.blue > 0.2f, "Image must remain visible after pinch/pan release")
    }

    @Test
    fun downwardSwipeDismissesUnzoomedImage() = runComposeUiTest {
        var closes = 0
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, { closes++ }) } }
        onNodeWithTag("image-viewer-image").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 200f), delayMillis = 100)
            up()
        }
        waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun previewStillOpensAfterScrollingTheMapList() = runComposeUiTest {
        setContent { AppTheme(ThemeStateHolder()) { MapModal {} } }
        onNodeWithText("施設案内マップ 1F").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("1 / 2").assertExists()
        onNodeWithTag("image-viewer-image").performTouchInput { click(center) }
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithText("会場マップ").fetchSemanticsNode() }.isSuccess
        }
        onNodeWithText("施設案内マップ 1F").assertIsDisplayed()
    }

    @Test
    fun failedRemoteUsesAndSavesTheVisibleFallback() = runComposeUiTest {
        var savedName: String? = null
        val saver = object : ImageSaver {
            override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult {
                savedName = fileName
                return ImageSaveResult.Saved
            }
        }
        val failedImage = listOf(ImageViewerItem("Fallback", Res.drawable.map_1f, "unsupported://image"))
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(failedImage, 0, {}, saver) } }
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithText("代替画像を表示中 · タップで再読み込み").fetchSemanticsNode() }.isSuccess
        }
        onNodeWithContentDescription("画像を保存").performClick()
        waitForIdle()
        assertEquals("Fallback.png", savedName)
    }

    @Test
    fun permissionDenialReleasesTheSaveButtonForRetry() = runComposeUiTest {
        var calls = 0
        val saver = object : ImageSaver {
            override suspend fun save(image: ImageBitmap, fileName: String): ImageSaveResult {
                calls++
                return if (calls == 1) ImageSaveResult.PermissionDenied else ImageSaveResult.Saved
            }
        }
        setContent { AppTheme(ThemeStateHolder()) { ImageViewerContent(images, 0, {}, saver) } }
        onNodeWithContentDescription("画像を保存").performClick()
        waitForIdle()
        onNodeWithText("写真への保存を許可してください").assertExists()
        onNodeWithContentDescription("画像を保存").performClick()
        waitForIdle()
        assertEquals(2, calls)
        onNodeWithText("画像を保存しました").assertExists()
    }

    @Test
    fun narrowPhoneKeepsTitleAndControlsVisible() = runComposeUiTest {
        val image = listOf(ImageViewerItem("施設案内マップ ".repeat(20), Res.drawable.map_2f))
        setContent {
            AppTheme(ThemeStateHolder()) {
                Box(Modifier.size(320.dp, 640.dp).testTag("viewer-phone")) {
                    ImageViewerContent(image, 0, {})
                }
            }
        }
        onNodeWithContentDescription("閉じる").assertIsDisplayed()
        onNodeWithContentDescription("画像を保存").assertIsDisplayed()
        val bitmap = onNodeWithTag("viewer-phone").captureToImage()
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use {
                File(System.getProperty("java.io.tmpdir"), "rectime-image-viewer-phone.png").writeBytes(it.bytes)
            }
        }
    }

}
