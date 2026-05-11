import UIKit
import SwiftUI
import ComposeApp

// GlassViewControllerFactory は Kotlin interface → Obj-C protocol として公開される
@objc class GlassButtonFactory: NSObject, GlassViewControllerFactory {

    func makeViewController() -> UIViewController {
        if #available(iOS 26, *) {
            let vc = UIHostingController(rootView: GlassCircleView())
            vc.view.backgroundColor = .clear
            return vc
        } else {
            let vc = UIViewController()
            vc.view.backgroundColor = .clear
            return vc
        }
    }
}

@available(iOS 26, *)
private struct GlassCircleView: View {
    var body: some View {
        Circle()
            .fill(.clear)
            .glassEffect(.regular.interactive())
    }
}
