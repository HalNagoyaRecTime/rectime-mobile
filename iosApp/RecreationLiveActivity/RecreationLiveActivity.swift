import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

@main
struct RecreationLiveActivityBundle: WidgetBundle {
    var body: some Widget { RecreationLiveActivity() }
}

private enum PersonalActivityStyle {
    static let orange = Color(red: 1, green: 0.25, blue: 0)
    static let tagText = Color(red: 165 / 255, green: 41 / 255, blue: 0)
    static func url(for state: RecreationActivityAttributes.ContentState) -> URL {
        guard let id = state.plan?.eventId, id > 0 else { return URL(string: "com.rectime.mobile://schedule")! }
        return URL(string: "com.rectime.mobile://live-activity?eventId=\(id)")!
    }
}

struct RecreationLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecreationActivityAttributes.self) { context in
            PersonalActivityCard(state: context.state)
                .activityBackgroundTint(PersonalActivityStyle.orange)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(PersonalActivityStyle.url(for: context.state))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.center) {
                    PersonalActivityCard(state: context.state)
                }
            } compactLeading: {
                LiveAppIcon().frame(width: 20, height: 20)
            } compactTrailing: {
                Text("自分の予定").font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(PersonalActivityStyle.orange)
            } minimal: {
                LiveAppIcon().frame(width: 20, height: 20)
            }
            .widgetURL(PersonalActivityStyle.url(for: context.state))
        }
    }
}

/// 時計を参照しない本人予定の表示。期限切れ・開催中・カウントダウンへの切替は行わない。
private struct PersonalActivityCard: View {
    let state: RecreationActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(tag).font(.system(size: 10, weight: .bold))
                .foregroundStyle(PersonalActivityStyle.tagText)
                .padding(.horizontal, 7).padding(.vertical, 1)
                .background(.white, in: Capsule())
                .frame(height: 18)
            VStack(alignment: .leading, spacing: 3) {
                Text(state.plan?.title ?? "自分の予定")
                    .font(.system(size: 21, weight: .heavy)).lineLimit(1).truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let plan = state.plan {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            roundText(plan).fixedSize()
                            locationText(plan).fixedSize()
                        }
                        VStack(alignment: .leading, spacing: 0) {
                            roundText(plan).lineLimit(1).truncationMode(.tail)
                            locationText(plan).lineLimit(1).truncationMode(.tail)
                        }
                    }
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    tapText.fixedSize()
                    Spacer(minLength: 0)
                    PersonalActivityBrand().fixedSize()
                }
                tapText
            }.frame(maxWidth: .infinity, minHeight: 24, maxHeight: 24, alignment: .leading)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(height: 160)
        .foregroundStyle(.white)
        .background(PersonalActivityStyle.orange)
    }

    private var tag: String {
        guard let count = state.remainingEventCount, count > 0 else { return "自分の予定" }
        return "自分の予定 / あと\(count)イベント"
    }

    private var tapText: some View {
        Text("時間を確認するにはタップ").font(.system(size: 18, weight: .heavy)).lineLimit(1)
    }

    private func roundText(_ plan: RecreationActivityAttributes.PersonalPlan) -> some View {
        Text(plan.rounds.isEmpty ? "" : "第\(plan.rounds.map(String.init).joined(separator: "・"))ラウンド")
            .font(.system(size: 14, weight: .bold))
    }

    private func locationText(_ plan: RecreationActivityAttributes.PersonalPlan) -> some View {
        Text([plan.venue, plan.spot].filter { !$0.isEmpty }.joined(separator: "・"))
            .font(.system(size: 13))
    }
}

private struct PersonalActivityBrand: View {
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text("RE:CREATION").font(.system(size: 9, weight: .semibold))
            LiveAppIcon().frame(width: 16, height: 16)
                .alignmentGuide(.firstTextBaseline) { $0[.bottom] - 2 }
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
            Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}
