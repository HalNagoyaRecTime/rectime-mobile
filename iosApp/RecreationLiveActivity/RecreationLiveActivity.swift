import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

@main
struct RecreationLiveActivityBundle: WidgetBundle {
    var body: some Widget { RecreationLiveActivity() }
}

private enum LivePalette {
    static let orange = Color(red: 1, green: 0.25, blue: 0)
    static let cyan = Color(red: 42 / 255, green: 179 / 255, blue: 191 / 255)
    static let foreground = Color(red: 32 / 255, green: 34 / 255, blue: 42 / 255)
    static let track = Color(red: 228 / 255, green: 231 / 255, blue: 239 / 255)
    static let notice = "最新の予定はアプリでご確認ください"

    static func surface(dark: Bool) -> Color {
        dark ? Color(red: 28 / 255, green: 28 / 255, blue: 30 / 255) : .white
    }

    static func outerTint(dark: Bool) -> Color {
        dark ? Color(red: 42 / 255, green: 42 / 255, blue: 45 / 255)
             : Color(red: 230 / 255, green: 231 / 255, blue: 234 / 255)
    }

    static func secondary(dark: Bool) -> Color {
        dark ? Color(red: 174 / 255, green: 176 / 255, blue: 185 / 255)
             : Color(red: 104 / 255, green: 109 / 255, blue: 124 / 255)
    }
}

private extension RecreationActivityAttributes.ContentState {
    func requiresRefresh(stale: Bool) -> Bool {
        displayMode == "refresh" || (stale && (presentationUntil.map { $0 <= Date.now } ?? true))
    }
}

struct RecreationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecreationActivityAttributes.self) { context in
            LiveActivityCard(state: context.state, stale: context.isStale)
                .activitySystemActionForegroundColor(LivePalette.orange)
                .widgetURL(URL(string: "com.rectime.mobile://schedule"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    LiveActivityCard(state: context.state, stale: context.isStale, island: true)
                }
            } compactLeading: {
                LiveAppIcon().frame(width: 20, height: 20)
            } compactTrailing: {
                if context.state.displayMode == "finished" {
                    Text("終了").font(.caption2)
                } else if context.state.displayMode == "refresh" || context.isStale {
                    Text("アプリで確認").font(.caption2)
                } else if let next = context.state.nextGathering {
                    Text(next.time, style: .time)
                        .font(.system(size: 12, weight: .semibold))
                        .monospacedDigit().foregroundStyle(LivePalette.orange)
                } else {
                    Text("開催状況").font(.caption2)
                }
            } minimal: {
                LiveAppIcon().frame(width: 20, height: 20)
            }
            .widgetURL(URL(string: "com.rectime.mobile://schedule"))
        }
    }
}

/// 開催日・取得範囲・次の本人予定の選択は共通モデルに任せる。
private struct LiveActivityCard: View {
    let state: RecreationActivityAttributes.ContentState
    let stale: Bool
    var island = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced
    private var event: RecreationActivityAttributes.Match? { state.matches.first ?? state.nextEvent }
    // ロック画面の外観が壁紙に引かれるOSでは、本体が取得したシステム設定を使う。
    private var dark: Bool {
        island || isLuminanceReduced || (state.systemAppearance.map { $0 == "dark" } ?? (colorScheme == .dark))
    }
    private var foreground: Color { dark ? .white : LivePalette.foreground }
    // ウィジェット自身が受け取る外観で解決し、UIKit側の別のtraitには依存しない。
    private var surface: Color { island ? .black : LivePalette.surface(dark: dark) }

    // 保存された表示対象の期限と取得鮮度を区別する。15分経過だけでは内容を消さない。
    private var requiresRefresh: Bool {
        state.requiresRefresh(stale: stale)
    }

    var body: some View {
        GeometryReader { geometry in
            if state.displayMode == "finished" {
                LiveFinishedMessage(dark: dark).frame(width: geometry.size.width, height: 160)
            } else if state.displayMode == "refresh" || requiresRefresh {
                LiveRefreshMessage()
                    .frame(width: geometry.size.width, height: 160)
            } else {
                VStack(spacing: 0) {
                    if let event {
                        LiveEventPanel(event: event, width: geometry.size.width,
                                       foreground: foreground, surface: surface,
                                       dark: dark)
                    } else {
                        Text("開催情報はアプリでご確認ください")
                            .font(.system(size: 12, weight: .semibold))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    LivePersonalPanel(state: state, width: geometry.size.width)
                }
            }
        }
        .frame(height: 160)
        .foregroundStyle(foreground)
        .background {
            if state.displayMode == "finished" || state.displayMode == "refresh" || requiresRefresh {
                surface
            } else {
                // 内側の角丸より下はオレンジで埋め、外枠の下端に白がのぞくのを防ぐ。
                VStack(spacing: 0) {
                    surface.frame(height: 106)
                    LivePalette.orange.frame(height: 54)
                }
            }
        }
        .activityBackgroundTint(state.displayMode == "finished" || requiresRefresh
                                ? surface : island ? .black : LivePalette.outerTint(dark: dark))
        .environment(\.timeZone, TimeZone(identifier: "Asia/Tokyo")!)
    }
}

private struct LiveEventPanel: View {
    let event: RecreationActivityAttributes.Match
    let width: CGFloat
    let foreground: Color
    let surface: Color
    let dark: Bool
    private var status: String { Date.now < event.start ? "開始前" : Date.now >= event.end ? "終了" : "開催中" }

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 5) {
                LiveTitle(text: event.title)
                LiveStatusTag(text: status, personal: false, dark: dark)
            }.frame(height: 18)
            HStack(spacing: 5) {
                HStack(spacing: 3) {
                    Text(event.start, style: .time)
                    Text("〜")
                    Text(event.end, style: .time)
                }
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(foreground)
                Spacer(minLength: 2)
                Text(event.venue).lineLimit(1).minimumScaleFactor(0.85)
            }
            .font(.system(size: 11)).monospacedDigit().foregroundStyle(LivePalette.secondary(dark: dark))
            .frame(height: 16)
            LiveRoundTimeline(event: event, foreground: foreground, surface: surface, dark: dark)
                .frame(height: 22)
            HStack(alignment: .center, spacing: 4) {
                if event.roundCount > 0 {
                    Text("全\(event.roundCount)ラウンド")
                        .lineLimit(1).layoutPriority(1)
                }
                Spacer(minLength: 0)
                Text(LivePalette.notice).multilineTextAlignment(.trailing).lineLimit(2)
            }
            .font(.system(size: 9)).foregroundStyle(LivePalette.secondary(dark: dark))
            .frame(maxWidth: .infinity, minHeight: 14, maxHeight: 14)
        }
        .padding(.horizontal, 14).padding(.top, 5).padding(.bottom, 2)
        .frame(width: width, height: 84, alignment: .top)
    }
}

private struct LivePersonalPanel: View {
    let state: RecreationActivityAttributes.ContentState
    let width: CGFloat

    var body: some View {
        VStack(spacing: 2) {
            if let next = state.nextGathering {
                HStack(spacing: 5) {
                    LiveTitle(text: next.title)
                    if let count = state.remainingGatheringCount, count > 0 {
                        Text("このあと\(count)件").font(.system(size: 9)).lineLimit(1)
                    }
                    LiveStatusTag(text: next.time <= Date.now ? "集合時刻" : "出場予定", personal: true)
                }.frame(height: 18)
                HStack(alignment: .center, spacing: 8) {
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(next.time, style: .time).font(.system(size: 22, weight: .heavy))
                        Text("集合").font(.system(size: 13, weight: .semibold))
                    }.monospacedDigit().fixedSize(horizontal: true, vertical: false)
                    Spacer(minLength: 0)
                    if Date.now >= next.time {
                        Text("集合時間です").font(.system(size: 14, weight: .semibold))
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .multilineTextAlignment(.trailing)
                    } else {
                        // 動的な日付Textは横幅を広く取るため、接頭辞も同じTextにして右へそろえる。
                        LiveGatheringCountdown(time: next.time)
                            .monospacedDigit().font(.system(size: 14, weight: .semibold)).lineLimit(1)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .bottom, spacing: 6) {
                        destinationText(next).fixedSize(horizontal: true, vertical: false)
                        Spacer(minLength: 0)
                        LiveBrand()
                    }
                    // まずブランドを外し、それでも収まらない場所だけ末尾を省略する。
                    destinationText(next)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }.frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Spacer(minLength: 0)
                Text(emptyMessage).font(.system(size: 12, weight: .semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                Spacer(minLength: 0)
                LiveBrand().frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .padding(.horizontal, 14).padding(.top, 6).padding(.bottom, 8)
        .frame(width: width, height: 76)
        .foregroundStyle(.white)
        .background(LivePalette.orange, in: UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22))
    }

    private var emptyMessage: String {
        switch state.personalState {
        case "none": "本日の出場予定はありません"
        case "noUpcoming": "このあとの集合予定はありません"
        default: "自分の予定はアプリでご確認ください"
        }
    }

    private func destination(_ next: RecreationActivityAttributes.Gathering) -> String {
        let venue = next.venue.flatMap { $0.isEmpty ? nil : $0 }
        return (["第\(next.round)ラウンド"] + (venue.map { [$0] } ?? []) + [next.spot])
            .filter { !$0.isEmpty }.joined(separator: "・")
    }

    private func destinationText(_ next: RecreationActivityAttributes.Gathering) -> some View {
        Text(destination(next))
            .font(.system(size: 11, weight: .medium))
            .lineLimit(1).truncationMode(.tail)
    }
}

private struct LiveGatheringCountdown: View {
    let time: Date
    var body: some View {
        if #available(iOS 18.0, *) {
            // 標準の時間データと有界タイマーで、最上位の一単位だけ表示し、0で止める。
            Text("集合まで") + Text(.currentDate, format: .timer(
                countingDownIn: min(Date.now, time)..<time,
                showsHours: true, maxFieldCount: 1, maxPrecision: .seconds(60)
            ).locale(Locale(identifier: "ja_JP")))
        } else {
            Text("集合まで") + Text(timerInterval: min(Date.now, time)...time, countsDown: true)
        }
    }
}

private struct LiveTitle: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 15, weight: .bold))
            .lineLimit(1).truncationMode(.tail)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct LiveStatusTag: View {
    let text: String
    let personal: Bool
    var dark = false
    var body: some View {
        Text(text).font(.system(size: 10, weight: .bold)).lineLimit(1)
            .padding(.horizontal, 6).padding(.vertical, 1)
            .foregroundStyle(personal ? Color(red: 0.61, green: 0.15, blue: 0) : dark ? Color(red: 0.58, green: 0.89, blue: 0.91) : Color(red: 0.09, green: 0.42, blue: 0.45))
            .background(personal ? .white : LivePalette.cyan.opacity(dark ? 0.2 : 0.1), in: Capsule())
    }
}

private struct LiveBrand: View {
    var body: some View {
        HStack(alignment: .center, spacing: 3) {
            Text("RE:CREATION").font(.system(size: 11, weight: .medium))
            LiveAppIcon().frame(width: 13, height: 13)
        }.fixedSize()
    }
}

private struct LiveRefreshMessage: View {
    var body: some View {
        VStack(spacing: 10) {
            LiveAppIcon().frame(width: 25, height: 25)
            Text("最新の情報を表示するには\nRE:CREATIONを開いてください")
                .font(.system(size: 14, weight: .semibold)).multilineTextAlignment(.center)
        }.frame(maxWidth: .infinity, maxHeight: .infinity).padding(14)
    }
}

/// 終了時刻と保持時間の判定は後続の共通状態から受け取る。
private struct LiveFinishedMessage: View {
    let dark: Bool
    var body: some View {
        VStack(spacing: 10) {
            LiveAppIcon().frame(width: 25, height: 25)
            Text("本日のイベントは終了しました")
                .font(.system(size: 14, weight: .semibold))
            Text("RE:CREATIONをご利用いただき、\nありがとうございました。")
                .font(.system(size: 11)).foregroundStyle(LivePalette.secondary(dark: dark))
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, maxHeight: .infinity).padding(14)
    }
}

/// APIの時刻を保持したまま、省略区間だけ短く配置する。番号や時刻を合成しない。
private struct LiveTimelinePoint: Identifiable {
    let round: RecreationActivityAttributes.Round
    let x: CGFloat
    var id: Int { round.number }
}

private enum LiveTimelineLayout {
    static func points(rounds: [RecreationActivityAttributes.Round], width: CGFloat, now: Date) -> [LiveTimelinePoint] {
        let sorted = rounds.sorted { $0.number < $1.number }
        guard !sorted.isEmpty else { return [] }
        let capacity = max(5, Int(max(0, width - 20) / 27) + 1)
        let current = sorted.lastIndex { ($0.start ?? .distantFuture) <= now } ?? 0
        var indices = Array(sorted.indices)
        if sorted.count > capacity {
            // 終盤も過去側で枠を埋める。序盤では最初のラウンドを消さない。
            let first = current <= 1 ? 0 : max(1, min(current - 1, sorted.count - capacity))
            let own = sorted.indices.filter { $0 >= first && sorted[$0].participating }.prefix(2)
            var length = max(1, capacity - 1 - own.count)
            var selected = Set(Array(first..<min(sorted.count, first + length)) + Array(own) + [sorted.count - 1])
            while selected.count < capacity && first + length < sorted.count {
                length += 1
                selected = Set(Array(first..<min(sorted.count, first + length)) + Array(own) + [sorted.count - 1])
            }
            indices = selected.sorted()
            // 本人の優先で省略箇所が増えすぎる場合は連続窓＋最後へ戻す。
            let gaps = zip(indices, indices.dropFirst()).filter { pair in pair.1 > pair.0 + 1 }.count
            if gaps > 2 { indices = Array(first..<min(sorted.count, first + capacity - 1)) + [sorted.count - 1] }
        }
        let weights = zip(indices, indices.dropFirst()).map { pair in sorted[pair.1].number > sorted[pair.0].number + 1 ? CGFloat(1.2) : 1 }
        let sum = max(1, weights.reduce(0, +))
        var position: CGFloat = 0
        return indices.enumerated().map { offset, index in
            if offset > 0 { position += weights[offset - 1] }
            return LiveTimelinePoint(round: sorted[index], x: 10 + position / sum * max(0, width - 20))
        }
    }
}

private struct LiveRoundTimeline: View {
    let event: RecreationActivityAttributes.Match
    let foreground: Color
    let surface: Color
    let dark: Bool

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let timedRounds = (event.timelineRounds ?? event.rounds).filter { $0.start != nil }
            let points = LiveTimelineLayout.points(rounds: timedRounds, width: width, now: .now)
            if points.isEmpty {
                ProgressView(timerInterval: event.start...event.end, countsDown: false)
                    .progressViewStyle(.linear).tint(LivePalette.cyan).labelsHidden()
                    .scaleEffect(x: 1, y: 1.5)
                    .frame(height: 22)
            } else {
                ZStack(alignment: .topLeading) {
                    ForEach(points.indices, id: \.self) { index in
                        let point = points[index]
                        let previous = index == 0 ? nil : points[index - 1]
                        let left = previous?.x ?? 0
                        let start: Date? = previous == nil ? event.start : previous?.round.start
                        let end = point.round.start
                        LiveTimelineSegment(start: start, end: end, skipped: previous.map { point.id > $0.id + 1 } ?? false,
                                            dark: dark)
                            .frame(width: max(0, point.x - left), height: 22)
                            .offset(x: left)
                    }
                    if let last = points.last {
                        LiveTimelineSegment(start: last.round.start, end: event.end, skipped: false,
                                            dark: dark)
                            .frame(width: max(0, width - last.x), height: 22).offset(x: last.x)
                    }
                    ForEach(points) { point in
                        marker(point.round).position(x: point.x, y: 11)
                    }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("全\(event.roundCount)ラウンドの開始予定")
    }

    private func marker(_ round: RecreationActivityAttributes.Round) -> some View {
        let started = round.start.map { $0 <= Date.now } ?? false
        let fill = round.participating ? LivePalette.orange : started ? LivePalette.cyan : surface
        let border = round.participating ? LivePalette.orange : started ? LivePalette.cyan : dark ? Color.gray.opacity(0.5) : LivePalette.track
        return Text("\(round.number)").font(.system(size: 9, weight: .bold))
            .foregroundStyle(round.participating ? .white : started ? (dark ? LivePalette.foreground : .white) : foreground.opacity(0.65))
            .frame(width: 18, height: 18).background(fill, in: Circle()).overlay(Circle().stroke(border, lineWidth: 1.5))
    }
}

private struct LiveTimelineSegment: View {
    let start: Date?
    let end: Date?
    let skipped: Bool
    let dark: Bool

    var body: some View {
        GeometryReader { geometry in
            if let start, let end, start < end {
                if skipped {
                    // 波線は省略区間の印。塗りは受信状態の描画時点までに限る。
                    let fraction = min(1, max(0, Date.now.timeIntervalSince(start) / end.timeIntervalSince(start)))
                    ZStack(alignment: .leading) {
                        LiveSkippedPath().stroke(dark ? Color.gray.opacity(0.5) : LivePalette.track, lineWidth: 6)
                        LiveSkippedPath().stroke(LivePalette.cyan, lineWidth: 6)
                            .mask(alignment: .leading) { Rectangle().frame(width: geometry.size.width * fraction) }
                    }
                } else {
                    // 通常区間はOSの時間ベース進捗。バックグラウンド通信やJSのタイマーは使わない。
                    ProgressView(timerInterval: start...end, countsDown: false)
                        .progressViewStyle(.linear).tint(LivePalette.cyan).labelsHidden()
                        .scaleEffect(x: 1, y: 1.5)
                        .frame(height: 22)
                }
            } else if let start, let end, start == end, end <= Date.now {
                Rectangle().fill(LivePalette.cyan).frame(height: 6)
                    .position(x: geometry.size.width / 2, y: 11)
            } else {
                Rectangle().fill(dark ? Color.gray.opacity(0.5) : LivePalette.track)
                    .frame(height: 6).position(x: geometry.size.width / 2, y: 11)
            }
        }
    }
}

private struct LiveSkippedPath: Shape {
    func path(in rect: CGRect) -> Path {
        let middle = rect.midX, half = min(9, rect.width / 3), y = rect.midY
        var path = Path()
        path.move(to: CGPoint(x: 0, y: y))
        path.addLine(to: CGPoint(x: middle - half, y: y))
        path.addQuadCurve(to: CGPoint(x: middle, y: y), control: CGPoint(x: middle - half / 2, y: y - 6))
        path.addQuadCurve(to: CGPoint(x: middle + half, y: y), control: CGPoint(x: middle + half / 2, y: y + 6))
        path.addLine(to: CGPoint(x: rect.maxX, y: y))
        return path
    }
}

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
            Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}
