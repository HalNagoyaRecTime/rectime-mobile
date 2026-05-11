import UIKit
import SwiftUI
import ComposeApp

@objc class GlassButtonFactory: NSObject, GlassViewControllerFactory {

    func makeButtonViewController(sfSymbol: String) -> UIViewController {
        if #available(iOS 26, *) {
            return GlassButtonViewController(sfSymbol: sfSymbol)
        } else {
            return UIViewController()
        }
    }

    func updateButtonOnClick(vc: UIViewController, onClick: (() -> Void)?) {
        if #available(iOS 26, *) {
            (vc as? GlassButtonViewController)?.viewModel.onClick = onClick
        }
    }
}

// MARK: - iOS 26

@available(iOS 26, *)
class GlassButtonViewController: UIViewController {
    let viewModel = GlassButtonViewModel()
    private let sfSymbol: String

    init(sfSymbol: String) {
        self.sfSymbol = sfSymbol
        super.init(nibName: nil, bundle: nil)
    }

    @MainActor required dynamic init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        let hosting = UIHostingController(
            rootView: GlassButtonView(sfSymbol: sfSymbol, viewModel: viewModel)
        )
        hosting.view.backgroundColor = .clear

        addChild(hosting)
        hosting.view.frame = view.bounds
        hosting.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(hosting.view)
        hosting.didMove(toParent: self)
    }
}

@available(iOS 26, *)
@Observable
class GlassButtonViewModel {
    var onClick: (() -> Void)? = nil
}

@available(iOS 26, *)
struct GlassButtonView: View {
    let sfSymbol: String
    let viewModel: GlassButtonViewModel

    var body: some View {
        Button { viewModel.onClick?() } label: {
            Image(systemName: sfSymbol)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(LiquidGlassButtonStyle())
    }
}

@available(iOS 26, *)
struct LiquidGlassButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background {
                Circle()
                    .glassEffect(.regular.interactive())
            }
            .scaleEffect(configuration.isPressed ? 1.1 : 1.0)
            .animation(
                .spring(response: 0.25, dampingFraction: 0.85),
                value: configuration.isPressed
            )
    }
}
