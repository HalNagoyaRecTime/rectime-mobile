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
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ComposeView()
                .ignoresSafeArea()

            #if DEBUG
            Button {
                LocalNotificationTester.shared.scheduleTestNotification()
            } label: {
                Label("通知テスト", systemImage: "bell.badge")
                    .font(.footnote.weight(.semibold))
            }
            .buttonStyle(.borderedProminent)
            .padding()
            #endif
        }
        .onOpenURL { url in
            AuthDeepLinkHandler.shared.handle(url: url.absoluteString)
        }
    }
}
