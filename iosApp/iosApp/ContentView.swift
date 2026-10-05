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
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            // 認証情報の復元・キャッシュ表示は演出の裏で通常どおり開始する。
            ComposeView()
                .allowsHitTesting(!showSplash)
                .accessibilityHidden(showSplash)
            if showSplash {
                RecreationSplashView() { showSplash = false }
                    .zIndex(1)
            }
        }
            .ignoresSafeArea()
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("RecreationLiveSchedule"))) { notification in
                LiveActivityCoordinator.shared.receive(notification.userInfo?["payload"] as? String)
            }
            .onChange(of: scenePhase, initial: true) { _, phase in
                LiveActivityCoordinator.shared.setActive(phase == .active)
            }
            .onOpenURL { url in
                AuthDeepLinkHandler.shared.handle(url: url.absoluteString)
            }
    }
}
