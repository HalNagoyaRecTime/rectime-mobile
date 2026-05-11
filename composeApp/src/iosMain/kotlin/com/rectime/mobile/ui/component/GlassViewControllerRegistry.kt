package com.rectime.mobile.ui.component

import platform.UIKit.UIViewController

interface GlassViewControllerFactory {
    fun makeButtonViewController(sfSymbol: String): UIViewController
    fun updateButtonOnClick(vc: UIViewController, onClick: (() -> Unit)?)
}

object GlassViewControllerRegistry {
    var factory: GlassViewControllerFactory? = null
    var isLiquidGlassAvailable: Boolean = false
}
