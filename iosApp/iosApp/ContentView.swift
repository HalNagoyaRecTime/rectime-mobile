import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    init() {
        GlassViewControllerRegistry.shared.factory = GlassButtonFactory()
        if #available(iOS 26, *) {
            GlassViewControllerRegistry.shared.isLiquidGlassAvailable = true
        }
    }

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



