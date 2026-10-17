import ActivityKit
import Foundation

struct RecreationActivityAttributes: ActivityAttributes {
    struct PersonalPlan: Codable, Hashable {
        let eventId: Int
        let title: String
        let rounds: [Int]
        let venue: String
        let spot: String
    }
    struct ContentState: Codable, Hashable {
        let accountId: String
        // 旧バージョンの保存済みActivityも読み込み、次回前面復帰で新しい内容へ更新する。
        let plan: PersonalPlan?
        let remainingEventCount: Int?
    }
    let accountId: String
}
