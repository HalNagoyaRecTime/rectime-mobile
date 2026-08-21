import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        requestNotificationAuthorization()
        return true
    }

    private func requestNotificationAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { _, error in
            if let error {
                print("[PushNotification] notification authorization failed: \(error.localizedDescription)")
            }
        }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
            completionHandler()
            return
        }

        let userInfo = response.notification.request.content.userInfo
        DispatchQueue.main.async {
            NotificationPayloadBridge.handleNotificationTap(userInfo: userInfo)
            completionHandler()
        }
    }
}
