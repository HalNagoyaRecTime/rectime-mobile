import UIKit
import SwiftUI
import ComposeApp

@objc class GlassButtonFactory: NSObject, GlassViewControllerFactory {

    func makeViewController() -> UIViewController {
        if #available(iOS 26, *) {
            let viewModel = GlassViewModel()
            return GlassHostingController(viewModel: viewModel)
        } else {
            let vc = UIViewController()
            vc.view.backgroundColor = .clear
            return vc
        }
    }

    func updateViewController(vc: UIViewController, isPressed: Bool) {
        if #available(iOS 26, *) {
            (vc as? GlassHostingController)?.viewModel.isPressed = isPressed
        }
    }
}

@available(iOS 26, *)
@Observable
class GlassViewModel {
    var isPressed: Bool = false
}

@available(iOS 26, *)
class GlassHostingController: UIHostingController<GlassCircleView> {
    let viewModel: GlassViewModel

    init(viewModel: GlassViewModel) {
        self.viewModel = viewModel
        super.init(rootView: GlassCircleView(viewModel: viewModel))
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
    }

    @MainActor required dynamic init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

@available(iOS 26, *)
struct GlassCircleView: View {
    let viewModel: GlassViewModel

    var body: some View {
        Circle()
            .glassEffect(.regular.interactive())
            .brightness(viewModel.isPressed ? 0.12 : 0)
            .animation(.spring(response: 0.2, dampingFraction: 0.8), value: viewModel.isPressed)
    }
}
