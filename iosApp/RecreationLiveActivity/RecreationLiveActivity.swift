import ActivityKit
import SwiftUI
import WidgetKit

@main
struct RecreationLiveActivityBundle: WidgetBundle {
    var body: some Widget { RecreationLiveActivity() }
}

private enum LivePalette {
    static let orange = Color(red: 1, green: 0.25, blue: 0)
    static let cyan = Color(red: 42 / 255, green: 179 / 255, blue: 191 / 255)
    static let yellow = Color(red: 1, green: 248 / 255, blue: 225 / 255)
}

struct RecreationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecreationActivityAttributes.self) { context in
            LiveActivityCard(state: context.state, stale: context.isStale)
                .activityBackgroundTint(.white)
                .activitySystemActionForegroundColor(LivePalette.orange)
                .widgetURL(URL(string: "com.rectime.mobile://schedule"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    LiveActivityCard(state: context.state, stale: context.isStale, compact: true)
                }
            } compactLeading: {
                LiveAppIcon().frame(width: 20, height: 20)
            } compactTrailing: {
                if let next = context.state.nextGathering {
                    Text(next.time, style: .time).monospacedDigit().foregroundStyle(LivePalette.orange)
                } else { Text("開催状況").font(.caption2) }
            } minimal: {
                LiveAppIcon().frame(width: 20, height: 20)
            }
            .widgetURL(URL(string: "com.rectime.mobile://schedule"))
        }
    }
}

private struct LiveActivityCard: View {
    let state: RecreationActivityAttributes.ContentState
    let stale: Bool
    var compact = false
    private var foreground: Color { compact ? .white : Color(red: 32 / 255, green: 34 / 255, blue: 42 / 255) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                LiveAppIcon().frame(width: 16, height: 16)
                Text("RE:CREATION").font(.system(size: 11, weight: .bold))
                Spacer(minLength: 4)
                Text(stale || state.isCached ? "更新待ち" : state.matchCount > 1 ? "開催状況・ほか\(state.matchCount - 1)件" : "開催状況")
                    .font(.system(size: 10, weight: .semibold)).foregroundStyle(LivePalette.cyan)
            }
            if !state.matches.isEmpty {
                ForEach(Array(state.matches.prefix(1))) { event in
                    match(event)
                }

            } else if let event = state.nextEvent {
                HStack {
                    Text("次の開催").font(.system(size: 10)).foregroundStyle(.secondary)
                    Text(event.title).font(.system(size: 13, weight: .bold)).lineLimit(1)
                    Spacer(minLength: 0)
                    Text(event.start, style: .time).font(.system(size: 12, weight: .semibold)).monospacedDigit()
                }
            }
            if let next = state.nextGathering {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 5) {
                        Text("次の集合").font(.system(size: 10, weight: .bold)).foregroundStyle(LivePalette.orange)
                        Text(next.title).font(.system(size: 12, weight: .semibold)).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(next.time, style: .time).font(.system(size: 20, weight: .heavy)).monospacedDigit()
                    }
                    HStack(spacing: 5) {
                        Text("第\(String(next.round))ラウンド・集合\(String(next.number))番")
                            .font(.system(size: 10, weight: .semibold)).foregroundStyle(LivePalette.orange)
                            .lineLimit(1).minimumScaleFactor(0.8).layoutPriority(1)
                        Image(systemName: "mappin.circle").font(.system(size: 11))
                        Text(next.spot).font(.system(size: 11)).lineLimit(1).truncationMode(.tail)
                    }
                }
                .padding(.horizontal, 8).padding(.vertical, 5)
                .background(compact ? Color.white.opacity(0.10) : LivePalette.yellow, in: RoundedRectangle(cornerRadius: 12))
            }
            Text(state.notice).font(.system(size: 9)).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
        }
        .foregroundStyle(foreground)
        .environment(\.colorScheme, compact ? .dark : .light)
        .environment(\.timeZone, TimeZone(identifier: "Asia/Tokyo")!)
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    private func match(_ event: RecreationActivityAttributes.Match) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 5) {
                Text(event.title).font(.system(size: 14, weight: .bold)).lineLimit(1).truncationMode(.tail)
                Spacer(minLength: 0)
                Text(event.venue).font(.system(size: 10)).lineLimit(1).truncationMode(.tail)
            }
            HStack(spacing: 3) {
                Text(event.start, style: .time)
                Text("〜")
                Text(event.end, style: .time)
                if event.roundCount > 0 {
                    Text("全\(event.roundCount)ラウンド").padding(.leading, 4)
                }
            }.font(.system(size: 10)).monospacedDigit().foregroundStyle(.secondary)
            if !event.rounds.isEmpty {
                HStack(spacing: 4) {
                    Text("開始予定").font(.system(size: 9)).foregroundStyle(.secondary)
                    ForEach(event.rounds, id: \.number) { round in
                        Text("\(round.number)").font(.system(size: 10, weight: .bold))
                            .foregroundStyle(round.participating ? LivePalette.orange : foreground)
                            .frame(minWidth: 14)
                            .padding(.vertical, 2)
                            .background(round.start.map { $0 <= Date.now } == true ? LivePalette.cyan.opacity(0.3) : Color.gray.opacity(0.15), in: Capsule())
                    }
                }
            } else {
                ProgressView(timerInterval: event.start...event.end, countsDown: false)
                    .tint(LivePalette.cyan).labelsHidden().frame(height: 4)
            }
        }
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
            Image(uiImage: image).resizable().scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}
