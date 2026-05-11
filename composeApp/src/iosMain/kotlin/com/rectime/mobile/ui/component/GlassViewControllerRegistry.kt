package com.rectime.mobile.ui.component

import platform.UIKit.UIViewController

// Kotlin interface → Obj-C protocol として ComposeApp フレームワークに公開される
// Swift の GlassButtonFactory がこの protocol に準拠する
interface GlassViewControllerFactory {
    fun makeViewController(): UIViewController
}

object GlassViewControllerRegistry {
    var factory: GlassViewControllerFactory? = null
}
