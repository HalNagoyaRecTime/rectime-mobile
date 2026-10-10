import ActivityKit
import Foundation

struct RecreationActivityAttributes: ActivityAttributes {
    struct Round: Codable, Hashable {
        let number: Int
        let callTime: Date
        var estimatedStart: Date { callTime.addingTimeInterval(15 * 60) }
    }
    struct Event: Codable, Hashable {
        let id: Int
        let title: String
        let venue: String
        let start: Date
        let end: Date
        // 旧Activityの保存済み状態も読めるよう、追加項目は任意にする。
        var participating: Bool? = nil
        var callTime: Date? = nil
        var gatheringSpot: String? = nil
        // 集合時刻＋15分を暫定の開始時刻とする。実際の試合進行ではない。
        var rounds: [Round]? = nil
        var currentRound: Int? = nil
        var totalRounds: Int? = nil
        var participationRound: Int? = nil
        var roundNumbers: [Int] {
            if let rounds { return Set(rounds.map(\.number).filter { $0 > 0 }).sorted() }
            guard let totalRounds, totalRounds > 0, totalRounds <= 99 else { return [] }
            return Array(1...totalRounds)
        }
        var roundProgress: (current: Int, total: Int)? {
            if let rounds, !rounds.isEmpty {
                let numbers = Set(rounds.map(\.number).filter { $0 > 0 }).sorted()
                guard !numbers.isEmpty else { return nil }
                let started = rounds.filter { $0.number > 0 && $0.estimatedStart <= Date.now }
                guard let number = started.map(\.number).max(), let index = numbers.firstIndex(of: number) else { return nil }
                return (index + 1, numbers.count)
            }
            guard let currentRound, let totalRounds, currentRound > 0,
                  totalRounds >= currentRound else { return nil }
            return (currentRound, totalRounds)
        }
    }

    struct ContentState: Codable, Hashable {
        let current: Event?
        let nextParticipation: Event?
        let message: String
        var activeEvents: [Event]? = nil
        var activeEventCount: Int? = nil
        // アプリで解決したテーマ色を共有し、OSの外観だけでは切り替えない。
        var backgroundColor: Int? = nil
        var textColor: Int? = nil
        var currentParticipation: Event? = nil
        // 開始前も当日の次の競技の時間帯と進捗を表示する。
        var scheduledEvent: Event? = nil

        var progressEvent: Event? { current ?? scheduledEvent }

        var visibleEvents: [Event] { activeEvents ?? current.map { [$0] } ?? [] }
        var currentCount: Int { activeEventCount ?? visibleEvents.count }
        var featuredEvent: Event? {
            if let currentParticipation { return currentParticipation }
            if let current, current.participating == true { return current }
            return nextParticipation
        }
    }

    // 同じアカウントのActivityだけを再利用し、切り替え時は終了する。
    let accountId: String
}
