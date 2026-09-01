import UIKit
import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseMessaging

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController({
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }, onPushTokenDeleteRequested: {
            guard FirebaseApp.app() != nil else { return }
            Messaging.messaging().deleteToken { error in
                if let error {
                    print("[PushNotification] FCM token deletion failed: \(error.localizedDescription)")
                }
            }
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                AuthDeepLinkHandler.shared.handle(url: url.absoluteString)
            }
    }
}
