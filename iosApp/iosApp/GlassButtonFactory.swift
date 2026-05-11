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
    // UIKit view (= Compose ノードサイズ) の内側に余白を確保し glass を円形に見せる
    // clipsToBounds = false でアニメーションは UIKit view 外にはみ出せる
    private let bleedRatio: CGFloat = 0.1
    private let minimumButtonSide: CGFloat = 24

    init(sfSymbol: String) {
        self.sfSymbol = sfSymbol
        super.init(nibName: nil, bundle: nil)
    }

    @MainActor required dynamic init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.isOpaque = false
        view.backgroundColor = .clear
        // アニメーションが UIKit view 外にはみ出せるようにする（z-order スライスはフレームで決まるので影響なし）
        view.clipsToBounds = false

        let hosting = UIHostingController(
            rootView: GlassButtonView(sfSymbol: sfSymbol, viewModel: viewModel)
        )
        hosting.view.isOpaque = false
        hosting.view.backgroundColor = .clear
        hosting.view.clipsToBounds = false
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
        let bleed = min(viewSize * bleedRatio, (viewSize - minimumButtonSide) / 2)
        let side = viewSize - bleed * 2
        viewModel.buttonSide = max(minimumButtonSide, side)
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
                .buttonStyle(.glass(.clear))
                .tint(.primary)
                .frame(width: viewModel.buttonSide, height: viewModel.buttonSide)
                .contentShape(Circle())
            }
        }
        // UIHostingController のデフォルト背景を透明にする
        .background(.clear)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
