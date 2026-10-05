import ActivityKit
import SwiftUI
import WidgetKit

@main
struct RecreationLiveActivityBundle: WidgetBundle {
    var body: some Widget { RecreationLiveActivity() }
}

struct RecreationLiveActivity: Widget {
    private let accent = Color(red: 0.1, green: 0.7, blue: 0.9)
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecreationActivityAttributes.self) { context in
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("RE:CREATION").font(.caption.bold()).foregroundStyle(accent)
                    Spacer()
                    Text(context.isStale ? "更新待ち" : "開催状況").font(.caption).foregroundStyle(.secondary)
                }
                if context.isStale {
                    Text("最新の開催状況はアプリで確認できます").font(.subheadline)
                } else {
                    if let event = context.state.current {
                        eventLabel(event, label: "開催中")
                        ProgressView(timerInterval: event.start...event.end, countsDown: false)
                            .tint(accent).labelsHidden()
                    } else { Text(context.state.message).font(.subheadline) }
                    if let next = context.state.nextParticipation {
                        HStack(alignment: .center, spacing: 12) {
                            eventLabel(next, label: "次の出場")
                            Spacer(minLength: 0)
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("開始まで").font(.caption).foregroundStyle(.secondary)
                                Text(timerInterval: Date.now...max(Date.now, next.start), countsDown: true)
                                    .monospacedDigit().font(.headline).frame(maxWidth: 90)
                            }
                        }
                    } else { Text("この後の出場予定はありません").font(.caption).foregroundStyle(.secondary) }
                }
            }
            .padding(14)
            .activityBackgroundTint(Color(.secondarySystemBackground))
            .activitySystemActionForegroundColor(.primary)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text("RE:CREATION").font(.caption.bold()).foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if context.isStale { Text("最新の開催状況はアプリで確認できます").font(.caption) }
                    else {
                        VStack(alignment: .leading, spacing: 8) {
                            if let event = context.state.current {
                                eventLabel(event, label: "開催中")
                                ProgressView(timerInterval: event.start...event.end, countsDown: false).tint(accent).labelsHidden()
                            }
                            if let next = context.state.nextParticipation {
                                HStack {
                                    Text("次の出場：\(next.title)").lineLimit(1)
                                    Spacer()
                                    Text(timerInterval: Date.now...max(Date.now, next.start), countsDown: true).monospacedDigit().frame(width: 75)
                                }.font(.caption)
                            }
                            if context.state.current == nil && context.state.nextParticipation == nil { Text(context.state.message).font(.caption) }
                        }
                    }
                }
            } compactLeading: {
                Image(systemName: "figure.run").foregroundStyle(accent)
            } compactTrailing: {
                if !context.isStale, let current = context.state.current {
                    Text(timerInterval: Date.now...max(Date.now, current.end), countsDown: true)
                        .monospacedDigit().font(.caption).frame(width: 55)
                } else { Image(systemName: context.isStale ? "arrow.clockwise" : "clock") }
            } minimal: {
                Image(systemName: "figure.run").foregroundStyle(accent)
            }
        }
    }

    private func eventLabel(_ event: RecreationActivityAttributes.Event, label: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text(label).font(.caption2).foregroundStyle(.secondary)
                if !event.venue.isEmpty { Text(event.venue).font(.caption2).foregroundStyle(.secondary).lineLimit(1) }
            }
            Text(event.title).font(.subheadline.bold()).lineLimit(1)
        }
    }
}
