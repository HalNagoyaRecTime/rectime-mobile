import ActivityKit
import Foundation

struct RecreationActivityAttributes: ActivityAttributes {
    struct Event: Codable, Hashable {
        let id: Int
        let title: String
        let venue: String
        let start: Date
        let end: Date
    }

    struct ContentState: Codable, Hashable {
        let current: Event?
        let nextParticipation: Event?
        let message: String
    }

    // 同じアカウントのActivityだけを再利用し、切り替え時は終了する。
    let accountId: String
}
