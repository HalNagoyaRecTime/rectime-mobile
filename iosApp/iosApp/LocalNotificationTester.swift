import Foundation
import UserNotifications

final class LocalNotificationTester {
    static let shared = LocalNotificationTester()

    private let requestIdentifier = "rectime.local-notification-test"

    private init() {}

    func scheduleTestNotification() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { [weak self] granted, error in
            if let error {
                print("[LocalNotificationTester] authorization failed: \(error)")
                return
            }

            guard granted else {
                print("[LocalNotificationTester] notification permission was not granted")
                return
            }

            let content = UNMutableNotificationContent()
            content.title = "REC TIME ローカル通知"
            content.body = "iOSのローカル通知テストです。"
            content.sound = .default
            content.userInfo = [
                "type": "manual",
                // Use the same payload shape as a production manual notification.
                // The detail screen can show its loading/error state even when the
                // local API has no matching notification record.
                "notificationId": "15",
            ]

            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
            let request = UNNotificationRequest(
                identifier: self?.requestIdentifier ?? "rectime.local-notification-test",
                content: content,
                trigger: trigger
            )

            center.removePendingNotificationRequests(withIdentifiers: [request.identifier])
            center.add(request) { error in
                if let error {
                    print("[LocalNotificationTester] scheduling failed: \(error)")
                } else {
                    print("[LocalNotificationTester] notification scheduled")
                }
            }
        }
    }
}
