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
                print("[PushNotification] FCM token fetch failed")
                return
            }
            IosPushTokenLifecycle.shared.onFirebaseTokenRefreshed(fcmToken: token)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[PushNotification] APNs registration failed")
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
        IosPushTokenLifecycle.shared.installDeletionHandler(handler: FirebaseMessagingTokenDeletionHandler())
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

private final class FirebaseMessagingTokenDeletionHandler: NSObject, IosFirebaseMessagingTokenDeletionHandler {
    func deleteToken(completion: @escaping (Bool) -> Void) {
        Messaging.messaging().deleteToken { error in
            completion(error == nil)
        }
    }
}

extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        IosPushTokenLifecycle.shared.onFirebaseTokenRefreshed(fcmToken: fcmToken)
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
