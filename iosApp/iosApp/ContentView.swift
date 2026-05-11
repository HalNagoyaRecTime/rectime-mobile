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
        // GlassButtonFactory は GlassViewControllerFactory protocol に準拠
        // Kotlin object の Obj-C シングルトンは .shared でアクセス
        GlassViewControllerRegistry.shared.factory = GlassButtonFactory()
    }

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



