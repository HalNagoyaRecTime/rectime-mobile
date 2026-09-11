import Foundation
import ComposeApp

enum NotificationPayloadBridge {
    static func stringPayload(from userInfo: [AnyHashable: Any]) -> [String: String] {
        userInfo.reduce(into: [String: String]()) { result, entry in
            guard let key = entry.key as? String else { return }

            if let value = entry.value as? String {
                result[key] = value
            } else if let value = entry.value as? NSNumber {
                result[key] = value.stringValue
            }
        }
    }

    static func handleNotificationTap(userInfo: [AnyHashable: Any]) {
        let payload = stringPayload(from: userInfo)
        NotificationNavigationHandler.shared.handle(data: payload)
    }
}
