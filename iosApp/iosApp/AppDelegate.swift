import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import ComposeApp

final class AppDelegate: NSObject, UIApplicationDelegate {
    private var isFirebaseConfigured = false

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        configureFirebase()
        registerForRemoteNotificationsIfAuthorized(application: application)
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        registerForRemoteNotificationsIfAuthorized(application: application)
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        guard isFirebaseConfigured else { return }

        // SwiftUIではFirebaseのAppDelegate swizzlingを無効化しているため、
        // APNs tokenを明示的にFCMへ関連付ける。
        Messaging.messaging().apnsToken = deviceToken
        Messaging.messaging().token { token, error in
            if let error {
                print("[PushNotification] FCM token fetch failed: \(error.localizedDescription)")
                return
            }
            IosPushTokenRegistrar.shared.onTokenRefreshed(fcmToken: token)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[PushNotification] APNs registration failed: \(error.localizedDescription)")
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if isFirebaseConfigured {
            Messaging.messaging().appDidReceiveMessage(userInfo)
        }

        // 表示通知はOSが表示し、画面遷移はユーザーが通知をタップした時だけ行う。
        // content-available付き通知ではここまで到達し、Payloadを安全に解釈できる。
        _ = NotificationPayloadBridge.stringPayload(from: userInfo)
        completionHandler(.newData)
    }

    private func configureFirebase() {
        guard
            let configurationPath = Bundle.main.path(
                forResource: "GoogleService-Info",
                ofType: "plist"
            ),
            let options = FirebaseOptions(contentsOfFile: configurationPath)
        else {
            print("[PushNotification] GoogleService-Info.plist is missing; FCM is disabled")
            return
        }

        FirebaseApp.configure(options: options)
        Messaging.messaging().delegate = self
        isFirebaseConfigured = true
    }

    private func registerForRemoteNotificationsIfAuthorized(application: UIApplication) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            guard
                settings.authorizationStatus == .authorized ||
                    settings.authorizationStatus == .provisional ||
                    settings.authorizationStatus == .ephemeral
            else {
                return
            }

            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
    }
}

extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        IosPushTokenRegistrar.shared.onTokenRefreshed(fcmToken: fcmToken)
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if isFirebaseConfigured {
            Messaging.messaging().appDidReceiveMessage(
                notification.request.content.userInfo
            )
        }
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
        if isFirebaseConfigured {
            Messaging.messaging().appDidReceiveMessage(userInfo)
        }

        DispatchQueue.main.async {
            NotificationPayloadBridge.handleNotificationTap(userInfo: userInfo)
            completionHandler()
        }
    }
}
