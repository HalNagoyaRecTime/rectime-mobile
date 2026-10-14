@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.rectime.mobile.ui.component

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import platform.Foundation.NSURL
import platform.QuickLook.*
import platform.PDFKit.PDFDocument
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import platform.darwin.NSObject

private class PdfItem(private val url: NSURL) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL = url
}
private class PdfSource(path: String) : NSObject(), QLPreviewControllerDataSourceProtocol {
    val item = PdfItem(NSURL.fileURLWithPath(path))
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1
    override fun previewController(controller: QLPreviewController, previewItemAtIndex: Long): QLPreviewItemProtocol = item
}
@Composable
internal actual fun PdfPreview(path: String, modifier: Modifier, onFailure: () -> Unit) {
    val source = remember(path) { PdfSource(path) }
    val readable by produceState<Boolean?>(null, path) {
        value = withContext(Dispatchers.Default) {
            runCatching { PDFDocument(NSURL.fileURLWithPath(path)).let { !it.isLocked() && it.pageCount > 0uL } }.getOrDefault(false)
        }
    }
    if (readable == false || !QLPreviewController.canPreviewItem(source.item)) {
        LaunchedEffect(path) { onFailure() }
    } else if (readable == true) UIKitViewController(factory = { QLPreviewController().apply { dataSource = source } }, modifier = modifier)
}
@Composable
internal actual fun rememberAttachmentActions(): suspend (String, String, Boolean) -> Boolean = remember {
    { path, _, _ -> withContext(Dispatchers.Main) {
        val window = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .filter { it.activationState == UISceneActivationStateForegroundActive }
            .flatMap { it.windows.filterIsInstance<UIWindow>() }.firstOrNull { it.isKeyWindow() }
        val root = window?.rootViewController
        if (root == null) false else {
            var presenter: UIViewController = root
            while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
            val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
            controller.popoverPresentationController?.apply {
                sourceView = presenter.view
                val size = presenter.view.bounds.useContents { size }
                sourceRect = CGRectMake(size.width / 2, size.height - 1, 1.0, 1.0)
            }
            presenter.presentViewController(controller, true, null)
            true
        }
    } }
}
