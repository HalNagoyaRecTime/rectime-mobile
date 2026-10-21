import Foundation

// 表示中のシーンだけを基準に、停止時間を演出の経過時間から除く。
struct SplashScenePlayback {
    private(set) var isActive = false
    private(set) var finished = false
    private var pausedAt: TimeInterval?
    private var pausedDuration: TimeInterval = 0

    mutating func setActive(_ active: Bool, at now: TimeInterval) {
        guard !finished else { return }
        if active {
            if let pausedAt { pausedDuration += max(0, now - pausedAt) }
            pausedAt = nil
        } else if pausedAt == nil {
            pausedAt = now
        }
        isActive = active
    }

    func elapsed(since start: TimeInterval, at now: TimeInterval) -> TimeInterval {
        max(0, (pausedAt ?? now) - start - pausedDuration)
    }

    mutating func finish() {
        finished = true
        isActive = false
    }
}
