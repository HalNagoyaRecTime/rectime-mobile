import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

@main
struct RecreationLiveActivityBundle: WidgetBundle {
    var body: some Widget { RecreationLiveActivity() }
}

// HTMLを参考にした試作。アプリから受け取ったテーマ色をロック画面に使う。
private enum LivePalette {
    static let orange = Color(red: 1, green: 64 / 255, blue: 0)
    static let cyan = Color(red: 42 / 255, green: 179 / 255, blue: 191 / 255)
    static let yellow = Color(red: 243 / 255, green: 181 / 255, blue: 0)
    static func color(_ argb: Int) -> Color {
        let value = UInt32(truncatingIfNeeded: argb)
        return Color(.sRGB, red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255,
                     opacity: Double((value >> 24) & 255) / 255)
    }
    static func surface(_ state: RecreationActivityAttributes.ContentState) -> Color {
        color(state.backgroundColor ?? 0xFFFFFFFF)
    }
}

struct RecreationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecreationActivityAttributes.self) { context in
            LiveActivityCard(state: context.state, isStale: context.isStale)
                .activityBackgroundTint(LivePalette.surface(context.state))
                .activitySystemActionForegroundColor(LivePalette.color(context.state.textColor ?? 0xFF20222A))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    LiveActivityCard(state: context.state, isStale: context.isStale, inDynamicIsland: true)
                        .environment(\.colorScheme, .dark)
                }
            } compactLeading: {
                LiveAppIcon().frame(width: 20, height: 20)
            } compactTrailing: {
                if !context.isStale, let event = context.state.featuredEvent {
                    EventCountdown(event: event)
                        .font(.system(size: 11, weight: .bold)).foregroundStyle(LivePalette.cyan)
                        .frame(width: 55)
                } else {
                    Image(systemName: context.isStale ? "arrow.clockwise" : "clock")
                        .foregroundStyle(LivePalette.cyan)
                }
            } minimal: {
                LiveAppIcon().frame(width: 20, height: 20)
            }
        }
    }
}

private struct LiveActivityCard: View {
    let state: RecreationActivityAttributes.ContentState
    let isStale: Bool
    var inDynamicIsland = false
    private var foreground: Color {
        inDynamicIsland ? .white : LivePalette.color(state.textColor ?? 0xFF20222A)
    }
    private var secondary: Color { foreground.opacity(0.65) }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 6) {
                LiveAppIcon().frame(width: 18, height: 18)
                Text("RE:CREATION").font(.system(size: 11, weight: .bold))
                    .lineLimit(1).minimumScaleFactor(0.8).layoutPriority(1)
                Spacer(minLength: 4)
                Text(isStale ? "更新待ち" : badge)
                    .font(.system(size: 11, weight: .bold)).lineLimit(1).minimumScaleFactor(0.8)
                    .foregroundStyle(LivePalette.orange)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(LivePalette.orange.opacity(0.12), in: Capsule())
            }
            if let event = state.featuredEvent {
                featured(event)
            }
            // 更新待ちでも保存済みの時間帯と進捗を消さない。
            if let event = state.progressEvent {
                currentEvent(event)
            } else if state.featuredEvent == nil {
                Text(state.message).font(.system(size: 13, weight: .semibold)).lineLimit(1)
            }
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 16).padding(.vertical, 8)
    }

    private var badge: String {
        guard let event = state.featuredEvent else { return "開催状況" }
        if event.start > Date.now {
            if let call = event.callTime, call <= Date.now { return "呼び出し中" }
            return "出場予定"
        }
        return "開催中"
    }

    private func featured(_ event: RecreationActivityAttributes.Event) -> some View {
        let now = Date.now
        let upcoming = event.start > now
        let beforeCall = upcoming && (event.callTime.map { $0 > now } ?? false)
        return VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .center, spacing: 8) {
                Text(event.title).font(.system(size: 18, weight: .heavy))
                    .lineLimit(1).truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    Text(beforeCall ? "集合まで" : upcoming ? "開始まで" : "終了まで")
                        .font(.system(size: 10, weight: .medium)).foregroundStyle(secondary)
                    EventCountdown(event: event).font(.system(size: 21, weight: .heavy))
                        .foregroundStyle(LivePalette.orange)
                }
                .frame(width: 80)
            }
            HStack(spacing: 5) {
                Image(systemName: "mappin.circle.fill").foregroundStyle(LivePalette.cyan)
                Text(event.venue.isEmpty ? "会場未設定" : event.venue)
                    .lineLimit(1).truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("開始").foregroundStyle(secondary)
                Text(event.start, style: .time).monospacedDigit().fixedSize()
            }
            .font(.system(size: 14, weight: .bold))
            if event.gatheringSpot?.isEmpty == false || event.participationRound != nil {
                HStack(spacing: 4) {
                    if let spot = event.gatheringSpot, !spot.isEmpty {
                        Text("集合").foregroundStyle(secondary)
                        Text(spot).lineLimit(1).truncationMode(.tail)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        if let call = event.callTime { Text(call, style: .time).monospacedDigit().fixedSize() }
                    } else { Spacer(minLength: 0) }
                    if let round = event.participationRound {
                        Text("出場：第\(round)R").foregroundStyle(LivePalette.orange).fixedSize()
                    }
                }
                .font(.system(size: 12, weight: .semibold))
            }
        }
    }

    private func currentEvent(_ event: RecreationActivityAttributes.Event) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 6) {
                // 同じ競技は名前を繰り返さず、自分の出場と競技全体を区別する。
                Text(state.featuredEvent?.id == event.id ? "競技全体" : event.title)
                    .font(.system(size: 11, weight: .bold))
                    .lineLimit(1).truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let progress = event.roundProgress {
                    let numbers = event.roundNumbers
                    let number = numbers.indices.contains(progress.current - 1) ? numbers[progress.current - 1] : progress.current
                    Text("第\(number)ラウンド／全\(progress.total)")
                        .font(.system(size: 11, weight: .semibold)).foregroundStyle(secondary)
                        .lineLimit(1).minimumScaleFactor(0.85)
                } else if !event.roundNumbers.isEmpty {
                    Text("開始前／全\(event.roundNumbers.count)ラウンド")
                        .font(.system(size: 11, weight: .semibold)).foregroundStyle(secondary)
                        .lineLimit(1).minimumScaleFactor(0.85)
                }
            }
            HStack(spacing: 3) {
                Text(event.start, style: .time)
                Text("〜")
                Text(event.end, style: .time)
            }
            .font(.system(size: 11, weight: .semibold)).monospacedDigit()
            .foregroundStyle(secondary)
            if !event.roundNumbers.isEmpty {
                RoundTrack(event: event, ownRound: state.featuredEvent?.id == event.id
                    ? state.featuredEvent?.participationRound : nil, foreground: foreground,
                    surface: inDynamicIsland ? .black : LivePalette.surface(state))
            } else {
                ProgressView(timerInterval: event.start...event.end, countsDown: false)
                    .tint(LivePalette.cyan).labelsHidden()
                    .scaleEffect(x: 1, y: 2, anchor: .center).frame(height: 8)
            }
        }
    }

}

// 番号付きの丸を一本のバーに重ねる。多ラウンド時は番号を間引き、重なりを避ける。
private struct RoundTrack: View {
    let event: RecreationActivityAttributes.Event
    let ownRound: Int?
    let foreground: Color
    let surface: Color

    var body: some View {
        GeometryReader { geometry in
            let numbers = event.roundNumbers
            let currentIndex = max(0, (event.roundProgress?.current ?? 0) - 1)
            let capacity = max(2, Int(geometry.size.width / 28))
            let stride = max(1, Int(ceil(Double(max(1, numbers.count - 1)) / Double(capacity - 1))))
            let sampled = numbers.indices.filter { $0 % stride == 0 || $0 == numbers.count - 1 }
            // 間引いても現在と自分のラウンド番号は必ず残す。
            let ownIndex = ownRound.flatMap { numbers.firstIndex(of: $0) }
            let indices = Set(sampled + [currentIndex] + (ownIndex.map { [$0] } ?? [])).sorted()
            let currentPosition = indices.firstIndex(of: currentIndex) ?? 0
            let fraction = event.roundProgress == nil ? 0 : (indices.count > 1
                ? Double(currentPosition) / Double(indices.count - 1) : 1)
            ZStack {
                Capsule().fill(foreground.opacity(0.12)).frame(height: 8)
                GeometryReader { track in
                    Capsule().fill(LivePalette.cyan)
                        .frame(width: track.size.width * min(1, max(0, fraction)), height: 8)
                }.frame(height: 8)
                ForEach(indices, id: \.self) { index in
                    let highlighted = numbers[index] == ownRound
                    let reached = event.roundProgress != nil && index <= currentIndex
                    Text("\(numbers[index])").font(.system(size: 10, weight: .bold))
                        .foregroundStyle(reached ? Color.white : foreground)
                        .frame(width: 22, height: 22)
                        .background(reached ? LivePalette.cyan : surface, in: Circle())
                        .overlay(Circle().stroke(highlighted ? LivePalette.orange : LivePalette.cyan, lineWidth: highlighted ? 2.5 : 1))
                        .position(x: 11 + (geometry.size.width - 22) * (indices.count > 1
                            ? Double(indices.firstIndex(of: index) ?? 0) / Double(indices.count - 1) : 0.5), y: 11)
                }
            }
        }
        .frame(height: 22)
    }
}

private struct EventCountdown: View {
    let event: RecreationActivityAttributes.Event
    var body: some View {
        // 開始前は開始まで、開始後は終了までをOSのタイマーで表示する。
        let now = Date.now
        let target = event.start > now ? (event.callTime.flatMap { $0 > now ? $0 : nil } ?? event.start) : event.end
        Text(timerInterval: now...max(now, target),
             countsDown: true).monospacedDigit().lineLimit(1).minimumScaleFactor(0.8)
    }
}

// 本体と同じAppIcon.iconを拡張でもコンパイルして読み込む。ロゴの別描画は作らない。
private struct LiveAppIcon: View {
    private static let image: UIImage? = {
        let bundle = Bundle.main
        for key in ["CFBundleIcons", "CFBundleIcons~ipad"] {
            let icons = bundle.object(forInfoDictionaryKey: key) as? [String: Any]
            let primary = icons?["CFBundlePrimaryIcon"] as? [String: Any]
            let files = primary?["CFBundleIconFiles"] as? [String] ?? []
            for name in files.reversed() {
                if let image = UIImage(named: name, in: bundle, compatibleWith: nil) { return image }
            }
        }
        return nil
    }()

    var body: some View {
        if let image = Self.image {
            Image(uiImage: image).resizable().scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}

#if DEBUG
// 比較専用データ。実際のスケジュールやAPIへのデータ注入は行わない。
private enum LiveActivityPreview {
    static var darkState: RecreationActivityAttributes.ContentState {
        var value = state
        value.backgroundColor = 0xFF0F131C
        value.textColor = 0xFFFFFFFF
        return value
    }
    static var state: RecreationActivityAttributes.ContentState {
        let now = Date.now
        let relay = RecreationActivityAttributes.Event(id: 1, title: "クラス対抗リレー", venue: "第1グラウンド",
            start: now.addingTimeInterval(-1200), end: now.addingTimeInterval(600))
        let tableTennis = RecreationActivityAttributes.Event(id: 2, title: "卓球トーナメント", venue: "第1体育館",
            start: now.addingTimeInterval(-600), end: now.addingTimeInterval(1800))
        let basketball = RecreationActivityAttributes.Event(id: 3, title: "バスケットボール", venue: "第2体育館",
            start: now.addingTimeInterval(720), end: now.addingTimeInterval(2520), participating: true)
        return .init(current: relay, nextParticipation: basketball, message: "",
                     activeEvents: [relay, tableTennis], activeEventCount: 2)
    }
}

#Preview("ライト・開始前") {
    LiveActivityCard(state: LiveActivityPreview.state, isStale: false)
        .frame(width: 360).background(LivePalette.surface(LiveActivityPreview.state))
        .clipShape(RoundedRectangle(cornerRadius: 28)).preferredColorScheme(.light)
}

#Preview("ダーク・開始前") {
    LiveActivityCard(state: LiveActivityPreview.darkState, isStale: false)
        .frame(width: 360).background(LivePalette.surface(LiveActivityPreview.darkState))
        .clipShape(RoundedRectangle(cornerRadius: 28)).preferredColorScheme(.dark)
}

#Preview("更新待ち") {
    LiveActivityCard(state: LiveActivityPreview.darkState, isStale: true)
        .frame(width: 320).background(LivePalette.surface(LiveActivityPreview.darkState))
        .clipShape(RoundedRectangle(cornerRadius: 28)).preferredColorScheme(.dark)
}

#Preview("ライト・出場中・同時開催") {
    let now = Date.now
    let mine = RecreationActivityAttributes.Event(id: 4, title: "バスケットボール決勝", venue: "第2体育館",
        start: now.addingTimeInterval(-600), end: now.addingTimeInterval(900), participating: true)
    let events = [mine] + LiveActivityPreview.state.visibleEvents
    LiveActivityCard(state: .init(current: mine, nextParticipation: nil, message: "",
        activeEvents: events, activeEventCount: 5), isStale: false)
        .frame(width: 360).background(LivePalette.surface(LiveActivityPreview.state))
        .clipShape(RoundedRectangle(cornerRadius: 28)).preferredColorScheme(.light)
}
// DBや認証に依存せず、実際のWidget枠と複数の状態をCanvasで比較する。
#Preview("ロック画面・状態切り替え", as: .content,
         using: RecreationActivityAttributes(accountId: "preview")) {
    RecreationLiveActivity()
} contentStates: {
    LiveActivityPreview.state
    LiveActivityPreview.darkState
    RecreationActivityAttributes.ContentState(current: nil, nextParticipation: nil,
        message: "本日の競技は終了しました", activeEvents: [], activeEventCount: 0)
}

#Preview("Dynamic Island", as: .dynamicIsland(.expanded),
         using: RecreationActivityAttributes(accountId: "preview")) {
    RecreationLiveActivity()
} contentStates: {
    LiveActivityPreview.state
    LiveActivityPreview.darkState
}
#endif
