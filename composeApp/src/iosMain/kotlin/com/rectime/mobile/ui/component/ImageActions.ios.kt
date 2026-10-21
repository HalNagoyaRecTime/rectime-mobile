@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.rectime.mobile.ui.component

import com.rectime.mobile.core.config.appDisplayName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import coil3.Image
import coil3.toBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.Foundation.create
import platform.UIKit.UIViewController
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCoverVertical
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.popoverPresentationController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import org.jetbrains.skia.EncodedImageFormat
import platform.LinkPresentation.LPLinkMetadata

internal actual fun imageViewerDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    usePlatformInsets = false,
    scrimColor = Color.Transparent,
    animateTransition = false,
)

@Composable
internal actual fun ImageViewerWindowController() = Unit

@Composable
internal actual fun rememberImageActions(): suspend (Image, String?) -> Boolean = remember {
    actions@{ image: Image, title: String? ->
        val data = withContext(Dispatchers.Default) {
            val encoded = org.jetbrains.skia.Image.makeFromBitmap(image.toBitmap())
            try {
                val png = encoded.encodeToData(EncodedImageFormat.PNG)
                try { png?.bytes } finally { png?.close() }
            } finally { encoded.close() }
        } ?: return@actions false
        withContext(Dispatchers.Main) {
            val nativeData = data.usePinned { NSData.create(bytes = it.addressOf(0), length = data.size.toULong()) }
            val nativeImage = UIImage.imageWithData(nativeData)
            val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
                .filter { it.activationState == UISceneActivationStateForegroundActive }
                .flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
            val root = window?.rootViewController
            if (nativeImage == null || root == null) false else {
                var presenter: UIViewController = root
                while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
                if (presenter is UIActivityViewController) false else {
                    // 標準の共有シートを下から表示する。iPadのポップオーバーも下端を基準にする。
                    val controller = UIActivityViewController(
                        listOf(ImageShareItem(nativeImage, title)),
                        applicationActivities = null,
                    )
                    controller.modalPresentationStyle = UIModalPresentationPageSheet
                    controller.modalTransitionStyle = UIModalTransitionStyleCoverVertical
                    controller.popoverPresentationController?.apply {
                        sourceView = presenter.view
                        val width = presenter.view.bounds.useContents { size.width }
                        val height = presenter.view.bounds.useContents { size.height }
                        val bottom = presenter.view.safeAreaInsets.useContents { bottom }
                        sourceRect = CGRectMake(width / 2, height - bottom, 1.0, 1.0)
                    }
                    presenter.presentViewController(controller, animated = true, completion = null)
                    true
                }
            }
        }
    }
}

// 表示済み画像とタイトルを渡し、共有シートのプレビューを通信なしで埋める。
private class ImageShareItem(private val image: UIImage, title: String?) : NSObject(), UIActivityItemSourceProtocol {
    private val metadata = LPLinkMetadata().apply {
        this.title = title
        // プレビューの画像・アイコンは指定せず、OSの既定表示を使う。
        // 補足欄の専用APIはない。プレビューだけにアプリ名をファイル名情報として渡す。
        // 実際の共有項目はUIImageのままで、この表示用URLは共有・通信しない。
        originalURL = NSURL.fileURLWithPath(appDisplayName)
    }

    override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any = image

    override fun activityViewController(
        activityViewController: UIActivityViewController,
        itemForActivityType: String?,
    ): Any = image

    // UIKitではこの型が前方宣言されているため、同じObjCオブジェクトを境界の型へ変換する。
    override fun activityViewControllerLinkMetadata(
        activityViewController: UIActivityViewController,
    ): objcnames.classes.LPLinkMetadata = metadata as objcnames.classes.LPLinkMetadata
}
