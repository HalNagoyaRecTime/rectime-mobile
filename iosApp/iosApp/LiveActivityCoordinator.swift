import ActivityKit
import UIKit

@MainActor
final class LiveActivityCoordinator {
    static let shared = LiveActivityCoordinator()
    private var state: RecreationActivityAttributes.ContentState?
    private var hasReceivedState = false
    private var task: Task<Void, Never>?
    private var revision = 0
    private var startedKey: String?
    private var accountId: String?

    func receive(_ json: String?, accountId: String?, ready: Bool) {
        if self.accountId != accountId { startedKey = nil }
        self.accountId = accountId
        if !ready {
            hasReceivedState = false
            synchronize()
            return
        }
        if let json, let data = json.data(using: .utf8) {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .secondsSince1970
            do { state = try decoder.decode(RecreationActivityAttributes.ContentState.self, from: data) }
            catch { print("[LiveActivity] Invalid state: \(error)"); return }
        } else { state = nil }
        hasReceivedState = true
        synchronize()
    }

    func setActive(_ active: Bool) {
        if active { synchronize() }
    }

    private func synchronize() {
        guard hasReceivedState || accountId != nil else { return }
        revision += 1
        let version = revision
        let previous = task
        task = Task {
            await previous?.value
            guard version == revision else { return }
            await reconcile(version: version)
        }
    }

    private func reconcile(version: Int) async {
        let existing = Activity<RecreationActivityAttributes>.activities
        if !hasReceivedState {
            // 同じ本人の復元待ちは残し、別利用者のActivityはデータ取得完了を待たずに終了する。
            for activity in existing where activity.attributes.accountId != accountId {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            return
        }
        guard let state, !state.accountId.isEmpty else {
            let endedByApp = existing.contains { $0.attributes.accountId == accountId &&
                ($0.activityState == .active || $0.activityState == .stale || $0.activityState == .ended) }
            for activity in existing { await activity.end(nil, dismissalPolicy: .immediate) }
            if accountId?.isEmpty != false || endedByApp { startedKey = nil }
            return
        }
        // ActivityKitの属性・状態は合計4KBまで。超過した内容は起動しない。
        let attributes = RecreationActivityAttributes(accountId: state.accountId)
        guard let data = try? JSONEncoder().encode(state),
              let attr = try? JSONEncoder().encode(attributes), data.count + attr.count < 4096 else { return }
        var current: Activity<RecreationActivityAttributes>?
        let expired = existing.contains { $0.attributes.accountId == state.accountId && $0.activityState == .ended }
        for activity in existing {
            if activity.attributes.accountId == state.accountId,
               activity.activityState == .active || activity.activityState == .stale,
               current == nil { current = activity }
            else { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        guard version == revision else { return }
        // 取得から15分ではなく、このカードが案内できる予定の区切りで再確認を促す。
        let content = ActivityContent(state: state, staleDate: state.presentationUntil ?? state.validUntil)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Tokyo")!
        let date = calendar.startOfDay(for: state.nextEvent?.start ?? state.matches.first?.start ?? state.nextGathering?.time ?? Date())
        let key = "\(state.accountId):\(date.timeIntervalSince1970)"
        if let current {
            startedKey = key
            if current.content.state != state { await current.update(content) }
        } else if (startedKey != key || expired), UIApplication.shared.applicationState == .active,
                  ActivityAuthorizationInfo().areActivitiesEnabled {
            do {
                _ = try Activity.request(attributes: attributes, content: content, pushType: nil)
                // 同日起動中にユーザーが閉じたActivityを再作成しない。
                startedKey = key
            } catch { print("[LiveActivity] Start failed: \(error)") }
        }
    }
}
