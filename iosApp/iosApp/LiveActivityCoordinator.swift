import ActivityKit
import UIKit

private struct LiveSchedule: Decodable {
    struct Event: Decodable {
        let id: Int
        let title: String
        let venue: String
        let start: Int
        let end: Int
        let participating: Bool
    }
    let accountId: String
    let events: [Event]
    let eventDate: String?
}

@MainActor
final class LiveActivityCoordinator {
    static let shared = LiveActivityCoordinator()
    private var schedule: LiveSchedule?
    private var hasReceivedSchedule = false
    private var task: Task<Void, Never>?
    private var revision = 0
    private var timer: Timer?
    private var startedKey: String?

    func receive(_ json: String?) {
        if let json, let data = json.data(using: .utf8) {
            do { schedule = try JSONDecoder().decode(LiveSchedule.self, from: data) }
            catch { print("[LiveActivity] Invalid schedule: \(error)"); return }
        } else { schedule = nil }
        hasReceivedSchedule = true
        synchronize()
    }

    func setActive(_ active: Bool) {
        timer?.invalidate()
        timer = nil
        if active {
            synchronize()
            // 前面表示中だけ競技の切り替わりを確認する。通信は行わない。
            timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { _ in
                Task { @MainActor in self.synchronize() }
            }
        }
    }

    private func synchronize() {
        // 起動直後の認証・予定復元待ちは、保存済みActivityを終了させない。
        guard hasReceivedSchedule else { return }
        revision += 1
        let version = revision
        let previous = task
        task = Task {
            // 開始・更新・終了を直列化し、古い更新がログアウト後に復活しないようにする。
            await previous?.value
            guard version == revision else { return }
            await reconcile(version: version)
        }
    }

    private func reconcile(version: Int) async {
        let now = Date()
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        let date = calendar.dateComponents([.year, .month, .day], from: now)
        let day = String(format: "%04d-%02d-%02d", date.year!, date.month!, date.day!)
        #if DEBUG
        let eligible = true
        #else
        // 開催日のAPI契約が入るまでReleaseで毎日表示しない。
        let eligible = schedule?.eventDate == day
        #endif
        guard let schedule, eligible, !schedule.events.isEmpty else {
            for activity in Activity<RecreationActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            startedKey = nil
            return
        }
        let midnight = calendar.startOfDay(for: now)
        func event(_ item: LiveSchedule.Event) -> RecreationActivityAttributes.Event {
            .init(id: item.id, title: String(item.title.prefix(100)), venue: String(item.venue.prefix(100)),
                  start: calendar.date(byAdding: .minute, value: item.start, to: midnight)!,
                  end: calendar.date(byAdding: .minute, value: item.end, to: midnight)!)
        }
        let valid = schedule.events.filter { $0.start >= 0 && $0.end <= 1440 && $0.end > $0.start }
            .sorted { ($0.start, $0.id) < ($1.start, $1.id) }
        let current = valid.filter { event($0).start <= now && now < event($0).end }
            .sorted { lhs, rhs in
                if lhs.participating != rhs.participating { return lhs.participating }
                return (lhs.start, lhs.id) < (rhs.start, rhs.id)
            }.first.map(event)
        let next = valid.first { $0.participating && event($0).start > now }.map(event)
        let following = valid.first { event($0).start > now }.map(event)
        let boundary = [current?.end, following?.start, next?.start, calendar.date(byAdding: .day, value: 1, to: midnight)]
            .compactMap { $0 }.filter { $0 > now }.min()
        let state = RecreationActivityAttributes.ContentState(
            current: current, nextParticipation: next,
            message: following == nil ? "本日の競技は終了しました" : "次の競技を待っています")
        let content = ActivityContent(state: state, staleDate: boundary)
        var existing: Activity<RecreationActivityAttributes>?
        for activity in Activity<RecreationActivityAttributes>.activities {
            if activity.attributes.accountId == schedule.accountId,
               activity.activityState == .active || activity.activityState == .stale,
               existing == nil { existing = activity }
            else { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        guard version == revision else { return }
        let key = "\(schedule.accountId):\(day)"
        if let existing {
            startedKey = key
            if existing.content.state != state || existing.content.staleDate != boundary { await existing.update(content) }
        } else if startedKey != key, UIApplication.shared.applicationState == .active,
                  ActivityAuthorizationInfo().areActivitiesEnabled {
            do {
                _ = try Activity.request(attributes: RecreationActivityAttributes(accountId: schedule.accountId),
                                         content: content, pushType: nil)
                // ユーザーが閉じたActivityを15秒ごとに再作成しない。
                startedKey = key
            } catch { print("[LiveActivity] Start failed: \(error)") }
        }
    }
}
