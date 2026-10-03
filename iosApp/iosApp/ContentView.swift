import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController {
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var showSplash = true

    var body: some View {
        ZStack {
            // 認証情報の復元・キャッシュ表示は演出の裏で通常どおり開始する。
            ComposeView()
                .allowsHitTesting(!showSplash)
                .accessibilityHidden(showSplash)
            if showSplash {
                RecreationSplashView { showSplash = false }
                    .zIndex(1)
            }
        }
            .ignoresSafeArea()
            .onOpenURL { url in
                AuthDeepLinkHandler.shared.handle(url: url.absoluteString)
            }
    }
}
