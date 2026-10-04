import SwiftUI
import UIKit
import ComposeApp

// iOSで動きを調整するための起動演出。通信の完了には依存させない。
private enum SplashTiming {
    static let letters = Array("RE:CREATION")
    static let letterStart = 0.7
    static let letterInterval = 0.075
    static let turnStart = 2.0
    static let duration = 2.75
}

enum SplashOpening {
    case burst
    case sportsGathering
}

struct RecreationSplashView: View {
    var opening: SplashOpening = .burst
    var onFinished: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var startedAt = Date()
    @State private var finished = false
    @State private var playbackStarted = false
    @State private var pausedAt: Date?
    @State private var letterAppearances: [Double] = []
    private let particles = SplashParticle.makeBurst()

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: pausedAt != nil)) { timeline in
                let elapsed = playbackElapsed(at: timeline.date)
                let turn = reduceMotion ? 0 : splashEase((elapsed - SplashTiming.turnStart) / 0.75)
                ZStack {
                    splashFront(elapsed: elapsed, size: geometry.size)
                        .mask(SplashPageMask(progress: turn))
                    if turn > 0 {
                        SplashPaperFold(progress: turn)
                    }
                }
                .opacity(reduceMotion ? 1 - splashClamp((elapsed - 2.0) / 0.25) : 1)
            }
        }
        .accessibilityHidden(true)
        .task { await playSequence() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            // 権限ダイアログ中も、描画と文字の振動を同じ位置で止める。
            if pausedAt == nil { pausedAt = Date() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            if let pausedAt {
                startedAt = startedAt.addingTimeInterval(Date().timeIntervalSince(pausedAt))
                self.pausedAt = nil
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            // 復帰時に起動演出・文字の振動をやり直さない。
            finish()
        }
    }

    private func playbackElapsed(at date: Date = Date()) -> Double {
        guard playbackStarted else { return 0 }
        return max(0, (pausedAt ?? date).timeIntervalSince(startedAt))
    }

    @MainActor
    private func waitUntil(_ target: Double) async throws {
        while true {
            try Task.checkCancellation()
            if finished { throw CancellationError() }
            let remaining = target - playbackElapsed()
            // 起動時に取り込んだEnvironmentではなく、UIKitの現在の状態を確認する。
            let active = UIApplication.shared.applicationState == .active
            if active && remaining <= 0 { return }
            // 非アクティブ中は時間を進めず、復帰後に続きから再生する。
            try await Task.sleep(for: .seconds(active ? max(0.001, remaining) : 0.05))
        }
    }

    @MainActor
    private func playSequence() async {
        let feedback = UIImpactFeedbackGenerator(style: .medium)
        let accent = UIImpactFeedbackGenerator(style: .heavy)
        let vibrationEnabled = IosSplashPreferences().isVibrationEnabled()
        do {
            try await waitUntil(0)
            startedAt = Date()
            pausedAt = nil
            playbackStarted = true
            for index in SplashTiming.letters.indices {
                let generator = SplashTiming.letters[index] == ":" ? accent : feedback
                if vibrationEnabled { generator.prepare() }
                try await waitUntil(SplashTiming.letterStart + Double(index) * SplashTiming.letterInterval)
                // 同じ処理で表示開始と振動を確定し、描画側だけ先行させない。
                letterAppearances.append(playbackElapsed())
                if vibrationEnabled { generator.impactOccurred() }
            }
            try await waitUntil(SplashTiming.duration)
            finish()
        } catch {
            // 画面終了・バックグラウンド移行後に振動を再生しない。
        }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        onFinished()
    }

    private func splashFront(elapsed: Double, size: CGSize) -> some View {
        ZStack {
            Color("LaunchBackground")
            Canvas { context, canvasSize in
                let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2 - 35)
                let iconSize = min(220, canvasSize.width * 0.57)
                if !reduceMotion {
                    switch opening {
                    case .burst:
                        drawBurst(context: &context, center: center, size: iconSize, elapsed: elapsed)
                    case .sportsGathering:
                        drawSportsGathering(context: &context, center: center, size: canvasSize, iconSize: iconSize, elapsed: elapsed)
                    }
                }
                let logoAge = elapsed - (opening == .sportsGathering ? 0.28 : 0)
                let appearance = reduceMotion ? 1 : splashSpring(logoAge / 0.7)
                var logoContext = context
                logoContext.opacity = splashClamp(logoAge / 0.12)
                logoContext.translateBy(x: center.x, y: center.y)
                logoContext.scaleBy(x: iconSize / 1024 * appearance, y: iconSize / 1024 * appearance)
                logoContext.translateBy(x: -512, y: -512)
                SplashLogo.draw(in: &logoContext)

                // 全文字の実際の幅を先に確保し、出現中も文字間隔と中央位置を保つ。
                let fontSize = min(28, canvasSize.width / 14)
                let font = Font.system(size: fontSize, weight: .heavy)
                let glyphs = SplashTiming.letters.map { context.resolve(Text(String($0)).font(font)) }
                let widths = glyphs.map { $0.measure(in: CGSize(width: CGFloat.infinity, height: CGFloat.infinity)).width }
                let spacing = fontSize * 0.5 / 17
                let rowWidth = widths.reduce(0, +) + spacing * Double(widths.count - 1)
                var cursor = center.x - rowWidth / 2
                for (index, letter) in SplashTiming.letters.enumerated() {
                    let letterX = cursor + widths[index] / 2
                    cursor += widths[index] + spacing
                    guard index < letterAppearances.count else { continue }
                    let age = elapsed - letterAppearances[index]
                    let progress = splashClamp(age / 0.34)
                    // HTMLと同じ、小さく出現 → 少し拡大 → 元の大きさの順。
                    let growing = 1 - pow(1 - splashClamp(progress / 0.55), 3)
                    let settling = splashEase((progress - 0.55) / 0.45)
                    let scale = reduceMotion ? 1 : (progress < 0.55 ? 0.2 + 1.08 * growing : 1.28 - 0.28 * settling)
                    let rise = reduceMotion ? 0 : (progress < 0.55 ? -10 + 8 * growing : -2 + 2 * settling)
                    var letterContext = context
                    letterContext.opacity = reduceMotion ? 1 : splashClamp(progress / 0.15)
                    letterContext.translateBy(x: letterX, y: center.y + iconSize / 2 + 46 + rise)
                    letterContext.scaleBy(x: scale, y: scale)
                    if letter == ":" {
                        // 上の水色・下の黄色と、隣のオレンジのCで3色の顔にする。
                        let dotScale = fontSize / 24
                        for (offset, color) in [(-5.0, SplashLogo.teal), (5.0, SplashLogo.yellow)] {
                            let rect = CGRect(x: -3 * dotScale, y: (offset - 3) * dotScale, width: 6 * dotScale, height: 6 * dotScale)
                            letterContext.fill(Path(ellipseIn: rect), with: .color(color))
                        }
                    } else {
                        let color = index == 3 ? SplashLogo.orange : Color(red: 34.0 / 255, green: 34.0 / 255, blue: 34.0 / 255)
                        letterContext.draw(Text(String(letter)).font(.system(size: fontSize, weight: .heavy)).foregroundColor(color), at: .zero)
                    }
                }
            }
        }
    }

    private func drawSportsGathering(context: inout GraphicsContext, center: CGPoint, size: CGSize, iconSize: Double, elapsed: Double) {
        let symbolHeight = min(60, size.width * 0.15)
        let radiusX = min(size.width / 2 - symbolHeight * 0.7, iconSize * 0.76)
        let radiusY = iconSize * 0.87
        for (index, athlete) in SplashAthlete.all.enumerated() {
            let age = elapsed - 0.06 - Double(index) * 0.055
            guard age > 0 else { continue }
            let angle = athlete.angle * .pi / 180
            let arrival = splashEase((age - 0.08) / 0.85)
            // 画面サイズに合わせて、記号全体が画面外になる位置から入ってくる。
            let directionX = cos(angle)
            let directionY = sin(angle)
            let edgeX = (directionX > 0 ? size.width - center.x : center.x) / max(abs(directionX), 0.001)
            let edgeY = (directionY > 0 ? size.height - center.y : center.y) / max(abs(directionY), 0.001)
            let outsideDistance = min(edgeX, edgeY) + symbolHeight * 2
            let start = CGPoint(x: center.x + directionX * outsideDistance, y: center.y + directionY * outsideDistance)
            let destination = CGPoint(x: center.x + directionX * radiusX, y: center.y + directionY * radiusY)
            // 少しためて体を起こし、中央のロゴを囲む位置でふわっと止まる。
            let effort = sin(splashClamp(age / 0.3) * .pi)
            let pop = splashSpring(age / 0.38)
            let x = start.x + (destination.x - start.x) * arrival
            let y = start.y + (destination.y - start.y) * arrival + (1 - pop) * 28
            var person = context
            person.opacity = splashClamp(age / 0.12)
            person.translateBy(x: x, y: y)
            let lean = (index.isMultiple(of: 2) ? -1.0 : 1.0) * (1 - arrival) * 0.25
            person.rotate(by: .radians(lean + effort * 0.08))
            let scale = 0.62 + 0.38 * pop
            person.scaleBy(x: scale, y: scale)
            var image = person.resolve(Image(systemName: athlete.symbol).renderingMode(.template))
            guard image.size.height > 0 else { continue }
            image.shading = .color(athlete.color)
            let width = symbolHeight * image.size.width / image.size.height
            person.draw(image, in: CGRect(x: -width / 2, y: -symbolHeight / 2, width: width, height: symbolHeight))
        }
    }

    private func drawBurst(context: inout GraphicsContext, center: CGPoint, size: Double, elapsed: Double) {
        let progress = splashClamp(elapsed / 0.8)
        guard progress < 1 else { return }
        for particle in particles {
            let distance = (1 - pow(1 - progress, 3)) * size * particle.distance
            let point = CGPoint(x: center.x + cos(particle.angle) * distance,
                                y: center.y + sin(particle.angle) * distance + progress * progress * 24)
            var copy = context
            copy.opacity = pow(1 - progress, 1.5)
            copy.translateBy(x: point.x, y: point.y)
            copy.rotate(by: .radians(particle.angle + progress * particle.spin))
            let side = particle.size * (1 - progress * 0.4)
            let shape = Path(roundedRect: CGRect(x: -side / 2, y: -side / 2, width: side, height: side * 0.6), cornerRadius: 1.2)
            copy.fill(shape, with: .color(particle.color))
        }
        // 爆発の中心から薄い輪が広がる。
        var ring = context
        ring.opacity = (1 - progress) * 0.18
        let radius = progress * size * 0.7
        ring.stroke(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)), with: .color(SplashLogo.teal), lineWidth: 1.5)
    }

}

private struct SplashAthlete {
    let symbol: String
    let angle: Double
    let color: Color

    // 人型のスポーツ記号をロゴの上と左右に集め、下の文字列には重ねない。
    static let all: [SplashAthlete] = [
        .init(symbol: "figure.basketball", angle: -160, color: SplashLogo.orange),
        .init(symbol: "figure.volleyball", angle: -130, color: SplashLogo.teal),
        .init(symbol: "figure.soccer", angle: -100, color: Color(white: 0.25)),
        .init(symbol: "figure.badminton", angle: -70, color: SplashLogo.orange),
        .init(symbol: "figure.table.tennis", angle: -40, color: SplashLogo.teal),
        .init(symbol: "figure.tennis", angle: -10, color: Color(white: 0.25)),
        .init(symbol: "figure.baseball", angle: 20, color: SplashLogo.orange),
        .init(symbol: "figure.run", angle: 160, color: SplashLogo.teal),
    ]
}

private struct SplashParticle {
    let angle: Double
    let distance: Double
    let spin: Double
    let size: Double
    let color: Color

    static func makeBurst() -> [SplashParticle] {
        // 固定シード相当の配列を一度作り、描画中に乱数生成・再配置しない。
        (0..<96).map { index in
            let value = Double(index)
            return SplashParticle(angle: value * 2.399963,
                                  distance: 0.45 + Double((index * 37) % 100) / 100 * 0.8,
                                  spin: Double((index * 13) % 9) - 4,
                                  size: 3 + Double((index * 7) % 6),
                                  color: [SplashLogo.orange, SplashLogo.yellow, SplashLogo.teal][index % 3])
        }
    }
}

private enum SplashLogo {
    static let orange = Color(red: 1, green: 64.0 / 255, blue: 0)
    static let yellow = Color(red: 252.0 / 255, green: 177.0 / 255, blue: 0)
    static let teal = Color(red: 42.0 / 255, green: 179.0 / 255, blue: 191.0 / 255)

    // AppIcon.iconの元SVGと同じ図形。SwiftUIのPathとして描画する。
    static let tealPath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 913.1463013, y: 614.4452515))
        p.addCurve(to: CGPoint(x: 543.9395752, y: 55.2339859), control1: CGPoint(x: 529.8710938, y: 815.4335938), control2: CGPoint(x: 341.6216431, y: 416.9945374))
        p.addCurve(to: CGPoint(x: 913.1463013, y: 614.4452515), control1: CGPoint(x: 122.5555267, y: 577.1070557), control2: CGPoint(x: 655.2057495, y: 1251.5168457))
        p.closeSubpath()
        return p
    }()
    static let orangePath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 84.3696899, y: 537.4138794))
        p.addCurve(to: CGPoint(x: 623.0041503, y: 641.1792603), control1: CGPoint(x: 367.2463378, y: 191.7315979), control2: CGPoint(x: 1344.7815551, y: 165.3688355))
        p.addCurve(to: CGPoint(x: 84.3696899, y: 537.4138794), control1: CGPoint(x: 823.8117676, y: 208.8714752), control2: CGPoint(x: 184.197998, y: 424.7926331))
        p.closeSubpath()
        return p
    }()
    static let yellowPath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 620.7169189, y: 197.2753601))
        p.addLine(to: CGPoint(x: 147.8849945, y: 871.6322021))
        p.addLine(to: CGPoint(x: 400.0417786, y: 827.9714355))
        p.closeSubpath()
        return p
    }()
    static func draw(in context: inout GraphicsContext) {
        context.opacity *= 0.8
        context.fill(orangePath, with: .color(orange))
        context.fill(yellowPath, with: .color(yellow))
        context.fill(tealPath, with: .color(teal))
    }
}

// 右下から折り目が左上へ移動する。画面比率に関係なく最後は全体が退場する。
private struct SplashPageMask: Shape {
    var progress: Double
    func path(in rect: CGRect) -> Path {
        splashPolygonPath(splashClipPage(size: rect.size, crease: splashCrease(size: rect.size, progress: progress), keepFront: true))
    }
}

private struct SplashPaperFold: View {
    let progress: Double
    var body: some View {
        Canvas { context, size in
            let crease = splashCrease(size: size, progress: progress)
            let removed = splashClipPage(size: size, crease: crease, keepFront: false)
            // 切り取った角を折り目に対して反転し、紙の裏面として本当に重ねる。
            let folded = removed.map { CGPoint(x: crease - $0.y, y: crease - $0.x) }
            let paper = splashPolygonPath(folded)
            let center = CGPoint(x: min(size.width, max(0, crease / 2)), y: crease - min(size.width, max(0, crease / 2)))
            let depth = min(70, max(1, (size.width + size.height - crease) * 0.16))
            let start = CGPoint(x: center.x - depth, y: center.y - depth)
            let end = CGPoint(x: center.x + 3, y: center.y + 3)
            var shadow = context
            shadow.addFilter(.shadow(color: .black.opacity(0.2), radius: 14, x: 7, y: 7))
            shadow.fill(paper, with: .color(.white))
            context.fill(paper, with: .linearGradient(Gradient(stops: [
                .init(color: Color(white: 0.96), location: 0),
                .init(color: .white, location: 0.42),
                .init(color: Color(white: 0.9), location: 0.78),
                .init(color: Color(white: 0.7), location: 0.94),
                .init(color: Color(white: 0.98), location: 1),
            ]), startPoint: start, endPoint: end))
            // 折り目の細い明部と曲線を重ね、単なる対角線の影にしない。
            let edge = splashCreaseIntersections(size: size, crease: crease)
            if edge.count == 2 {
                var lip = Path()
                lip.move(to: edge[0])
                let midpoint = CGPoint(x: (edge[0].x + edge[1].x) / 2 - depth * 0.12,
                                       y: (edge[0].y + edge[1].y) / 2 - depth * 0.12)
                lip.addQuadCurve(to: edge[1], control: midpoint)
                context.stroke(lip, with: .color(.white.opacity(0.85)), lineWidth: 1.5)
            }
        }
        .clipped()
    }
}

private func splashCrease(size: CGSize, progress: Double) -> Double {
    let span = size.width + size.height
    return span - progress * (span + max(size.width, size.height) * 0.2)
}

private func splashClipPage(size: CGSize, crease: Double, keepFront: Bool) -> [CGPoint] {
    let corners = [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: size.width, y: size.height), CGPoint(x: 0, y: size.height)]
    var result: [CGPoint] = []
    for index in corners.indices {
        let a = corners[index]
        let b = corners[(index + 1) % corners.count]
        let da = a.x + a.y - crease
        let db = b.x + b.y - crease
        let aInside = keepFront ? da <= 0 : da >= 0
        let bInside = keepFront ? db <= 0 : db >= 0
        if aInside { result.append(a) }
        if aInside != bInside {
            let fraction = da / (da - db)
            result.append(CGPoint(x: a.x + (b.x - a.x) * fraction, y: a.y + (b.y - a.y) * fraction))
        }
    }
    return result
}

private func splashPolygonPath(_ points: [CGPoint]) -> Path {
    var path = Path()
    guard let first = points.first else { return path }
    path.move(to: first)
    for point in points.dropFirst() { path.addLine(to: point) }
    path.closeSubpath()
    return path
}

private func splashCreaseIntersections(size: CGSize, crease: Double) -> [CGPoint] {
    let candidates = [CGPoint(x: crease, y: 0), CGPoint(x: size.width, y: crease - size.width), CGPoint(x: crease - size.height, y: size.height), CGPoint(x: 0, y: crease)]
    return candidates.filter { $0.x >= 0 && $0.x <= size.width && $0.y >= 0 && $0.y <= size.height }
}

private func splashClamp(_ value: Double) -> Double { min(1, max(0, value)) }
private func splashEase(_ value: Double) -> Double { let p = splashClamp(value); return p * p * (3 - 2 * p) }
private func splashSpring(_ value: Double) -> Double {
    let p = splashClamp(value)
    return p == 1 ? 1 : 1 - exp(-7 * p) * cos(11 * p)
}
