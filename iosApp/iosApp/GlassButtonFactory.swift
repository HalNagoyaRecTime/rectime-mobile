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
    // bleed must match glassBleed in AppIconButton.kt
    private let bleed: CGFloat = 40

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
        hosting.view.translatesAutoresizingMaskIntoConstraints = false

        addChild(hosting)
        view.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            hosting.view.topAnchor.constraint(equalTo: view.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hosting.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        hosting.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let viewSize = min(view.bounds.width, view.bounds.height)
        let side = viewSize - bleed * 2
        // Fallback: if bleed exceeds the view (Box constraint not bypassed), fill the view
        viewModel.buttonSide = side > 0 ? side : viewSize
    }
}

@available(iOS 26, *)
@Observable
class GlassButtonViewModel {
    var onClick: (() -> Void)? = nil
    var buttonSide: CGFloat = 0
}

@available(iOS 26, *)
struct GlassButtonView: View {
    let sfSymbol: String
    let viewModel: GlassButtonViewModel

    var body: some View {
        ZStack {
            if viewModel.buttonSide > 0 {
                Button { viewModel.onClick?() } label: {
                    Image(systemName: sfSymbol)
                        .font(.system(size: 16, weight: .medium))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .tint(.primary)
                .frame(width: viewModel.buttonSide, height: viewModel.buttonSide)
                .glassEffect(.regular.interactive(), in: Circle())
                .contentShape(Circle())
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
    }
}
