import QuartzCore
import SwiftUI
import UIKit

/// 월간 상단의 사진 묶음(월간 책자). 한 달이 천 장정 하드커버 한 권이다.
/// 모양은 두 가지이고(`RecordBookLayout`), 오른쪽 위 책 선택 버튼으로 바꾼다.
/// - 펼친 책(시안 2번): 정면으로 세워 펼친다. 왼쪽 면에 요일·날짜·주제, 오른쪽 면에 사진을 둔다.
///   책 양 끝의 화살표로도 넘긴다.
/// - 한 면 책(시안 4번): 한 면만 크게 보여 준다. 가로 사진은 캡션을 아래에, 세로·정사각 사진은 캡션을 옆에 둔다.
///   화면 양 끝의 ‹ › 표시로 넘기는 자리를 알린다.
///
/// 두 모양 공통:
/// - 사진은 원래 비율 그대로 넣고 자르지 않는다.
/// - 화면 왼쪽 절반을 누르거나 오른쪽으로 끌면 전날, 오른쪽 절반을 누르거나 왼쪽으로 끌면 다음 날로 넘어간다.
/// - 사진을 누르면 그 사진의 피드로 간다.
/// - 이번 달 책에는 리본 책갈피가 늘 오늘 면에 끼워져 있고, 꼬리의 "오늘" 꼬리표를 누르면 오늘로 넘어간다.
/// - 속표지에는 찰캌 로고, "N월의 기록", 내 사인을 넣는다.
///
/// 위치(`position`)는 다른 모양과 같이 보고 있는 날의 순서다(가장 최근 날이 0).
/// 책 안에서는 넘긴 장 수(`spread`)로 다룬다. 0은 덮인 표지, 1은 속표지, d + 1은 d일이다.
struct RecordPhotoBook: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let month: RecordMonth
    let posts: [RecordPost]
    let images: [RecordPost.ID: UIImage]
    let topicTitles: [Date: String]
    let layout: RecordBookLayout
    /// 속표지에 넣을 내 사인. 아직 받지 못했거나 없으면 비워 둔다.
    var signatureURL: URL?
    @Binding var position: CGFloat
    let onSelectPost: (RecordPost) -> Void
    var now: Date = Date()

    @State private var drag: DragState?
    @State private var wiggleTrigger = 0
    @State private var hasWiggled = false

    var body: some View {
        GeometryReader { proxy in
            let model = RecordBookModel(month: month, posts: posts, now: now)
            let geometry = RecordBookGeometry(layout: layout)
            let scale = min(1, proxy.size.width / RecordBookGeometry.stageSize.width)
            let spread = model.spread(for: position)
            let cloth = clothColor(model: model)

            ZStack(alignment: .topLeading) {
                RecordBookStage(
                    spread: spread,
                    model: model,
                    geometry: geometry,
                    images: images,
                    topicTitles: topicTitles,
                    signatureURL: signatureURL,
                    cloth: cloth,
                    wiggleTrigger: wiggleTrigger
                )
                .projectionEffect(geometry.tilt)
                .allowsHitTesting(false)

                touchLayer(model: model, geometry: geometry, spread: spread)
            }
            .frame(width: RecordBookGeometry.stageSize.width, height: RecordBookGeometry.stageSize.height, alignment: .topLeading)
            .scaleEffect(scale, anchor: .top)
            .frame(width: proxy.size.width, height: RecordStackMetrics.height, alignment: .top)
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture(model: model, geometry: geometry, scale: scale))
            .onChange(of: position) { oldValue, newValue in
                // 오늘을 처음 떠날 때 리본을 한 번 흔들어 눌러 볼 수 있다는 걸 알린다.
                guard model.hasRibbon, !hasWiggled, !reduceMotion, oldValue < 0.5, newValue >= 0.5 else { return }
                hasWiggled = true
                wiggleTrigger += 1
            }
        }
        .frame(height: RecordStackMetrics.height)
        .clipped()
    }

    // MARK: - 누르는 자리

    /// 그려진 책 위에서 누를 자리. 기울여 그린 책은 화면에 그려진 위치를 직접 계산해 평면 위에 둔다.
    @ViewBuilder
    private func touchLayer(model: RecordBookModel, geometry: RecordBookGeometry, spread: CGFloat) -> some View {
        let settled = spread.rounded()
        let stage = RecordBookGeometry.stageSize
        ZStack(alignment: .topLeading) {
            // 화면 왼쪽 절반은 전날, 오른쪽 절반은 다음 날로 넘어간다.
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(coordinateSpace: .local) { location in
                    turn(by: location.x < stage.width / 2 ? -1 : 1, model: model)
                }
                .accessibilityElement()
                .accessibilityLabel(model.accessibilityLabel(atSpread: Int(settled)))
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: turn(by: 1, model: model)
                    case .decrement: turn(by: -1, model: model)
                    @unknown default: break
                    }
                }

            if let post = model.post(atSpread: Int(settled)), abs(spread - settled) < 0.05 {
                let photo = geometry.photoFrame(ratio: RecordPhotoAspect.widthRatio(of: images[post.id]))
                let frame = geometry.projectedFrame(of: photo)
                Button { onSelectPost(post) } label: {
                    Color.clear
                        .frame(width: frame.width, height: frame.height)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // 피드가 이 사진 자리에서 확대되어 열리고, 닫히면 이 자리로 돌아온다.
                .feedZoomSource(.record(post.postId))
                .offset(x: frame.minX, y: frame.minY)
                .accessibilityLabel("\(RecordStackFormat.monthDay.string(from: post.topicDate)) 사진")
                .accessibilityHint("피드에서 봅니다")
            }

            if model.hasRibbon {
                let tag = geometry.ribbonTouchFrame
                Button {
                    withAnimation(turnAnimation(distance: abs(position))) { position = 0 }
                } label: {
                    Color.clear
                        .frame(width: tag.width, height: tag.height)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .offset(x: tag.minX, y: tag.minY)
                .accessibilityLabel("오늘(\(model.todayDay)일)로 가기")
            }

            switch geometry.layout {
            case .spread:
                spreadArrows(model: model, geometry: geometry, settled: settled)
            case .single:
                singleEdgeMarks(geometry: geometry)
            }
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
    }

    /// 펼친 책 양 끝의 화살표. 처음(표지)과 끝(마지막 날)에서는 그쪽 화살표를 끈다.
    @ViewBuilder
    private func spreadArrows(model: RecordBookModel, geometry: RecordBookGeometry, settled: CGFloat) -> some View {
        let centerY = geometry.pageTop + geometry.pageSize.height / 2
        RecordGlassIconButton(title: "전날 보기", systemImage: "chevron.left", diameter: Metrics.arrowDiameter, controlSize: .regular) {
            turn(by: -1, model: model)
        }
        .disabled(settled <= 0)
        .position(x: geometry.arrowCenterX(leading: true), y: centerY)

        RecordGlassIconButton(title: "다음 날 보기", systemImage: "chevron.right", diameter: Metrics.arrowDiameter, controlSize: .regular) {
            turn(by: 1, model: model)
        }
        .disabled(settled >= CGFloat(model.maxSpread))
        .position(x: geometry.arrowCenterX(leading: false), y: centerY)
    }

    /// 한 면 책 양 끝의 ‹ › 표시. 넘기는 자리를 알리기만 하고, 누르는 것은 아래 판이 받는다.
    @ViewBuilder
    private func singleEdgeMarks(geometry: RecordBookGeometry) -> some View {
        let centerY = geometry.pageTop + geometry.pageSize.height / 2
        let stage = RecordBookGeometry.stageSize
        ForEach([true, false], id: \.self) { leading in
            Image(systemName: leading ? "chevron.left" : "chevron.right")
                .font(.system(size: Metrics.edgeMarkIconSize, weight: .semibold))
                .foregroundStyle(theme.colors.iconPrimary)
                .frame(width: Metrics.edgeMarkSize.width, height: Metrics.edgeMarkSize.height)
                .background(theme.colors.inputBackground.opacity(Metrics.edgeMarkOpacity), in: Capsule())
                .shadow(color: .black.opacity(Metrics.edgeMarkShadowOpacity), radius: 2, y: 1)
                .position(
                    x: leading ? Metrics.edgeMarkInset + Metrics.edgeMarkSize.width / 2 : stage.width - Metrics.edgeMarkInset - Metrics.edgeMarkSize.width / 2,
                    y: centerY
                )
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }

    private func turn(by pages: Int, model: RecordBookModel) {
        // 순서는 최근 날이 0이므로, 다음 날(장을 넘김)은 순서를 줄인다.
        let target = min(CGFloat(model.maxSpread), max(0, position.rounded() - CGFloat(pages)))
        withAnimation(turnAnimation(distance: abs(target - position))) { position = target }
    }

    // MARK: - 드래그

    private func dragGesture(model: RecordBookModel, geometry: RecordBookGeometry, scale: CGFloat) -> some Gesture {
        let range: ClosedRange<CGFloat> = 0...CGFloat(model.maxSpread)
        let step = geometry.dragStep * scale
        return DragGesture(minimumDistance: RecordStackMetrics.dragMinimumDistance)
            .onChanged { value in
                if drag == nil {
                    let isHorizontal = abs(value.translation.width) > abs(value.translation.height)
                    drag = DragState(startPosition: position, isHorizontal: isHorizontal)
                }
                guard let drag, drag.isHorizontal else { return }
                // 장 하나 폭만큼 끌면 한 장 넘어간다. 오른쪽으로 끌면 전날(순서가 커진다).
                position = RecordPhotoStackOrder.rubberBanded(drag.startPosition + value.translation.width / step, in: range)
            }
            .onEnded { value in
                defer { drag = nil }
                guard let drag, drag.isHorizontal else { return }
                let projected = drag.startPosition + value.predictedEndTranslation.width / step
                // 세게 튕겨도 한 번에 두 장까지만 넘긴다.
                let limited = min(drag.startPosition.rounded() + Self.maxFling, max(drag.startPosition.rounded() - Self.maxFling, projected))
                let target = min(range.upperBound, max(range.lowerBound, limited.rounded()))
                withAnimation(turnAnimation(distance: abs(target - position))) { position = target }
            }
    }

    /// 여러 장을 한꺼번에 넘길수록 조금 더 천천히 넘긴다.
    private func turnAnimation(distance: CGFloat) -> Animation {
        .spring(response: min(1.1, 0.55 + 0.05 * Double(distance)), dampingFraction: 0.86)
    }

    private static let maxFling: CGFloat = 2

    private struct DragState {
        let startPosition: CGFloat
        let isHorizontal: Bool
    }

    // MARK: - 표지 색

    /// 그 달 첫 사진의 대표색을 어둡게 눌러 천 색으로 쓴다. 사진을 아직 못 받았으면 달력 칸 테두리 색을 쓴다.
    private func clothColor(model: RecordBookModel) -> RecordBookCloth {
        guard let first = model.firstPhotoPost,
              let image = images[first.id]?.cgImage,
              let color = RecordPhotoColorExtractor.color(from: image)
        else { return RecordBookCloth(base: nil) }
        return RecordBookCloth(base: color)
    }

    private enum Metrics {
        /// 책 양 끝 화살표. 책과 화면 끝 사이 좁은 자리에 들어가도록 44pt 누를 자리 그대로 둔다.
        static let arrowDiameter: CGFloat = 44
        static let edgeMarkSize = CGSize(width: 22, height: 44)
        static let edgeMarkInset: CGFloat = 2
        static let edgeMarkIconSize: CGFloat = 13
        static let edgeMarkOpacity: Double = 0.85
        static let edgeMarkShadowOpacity: Double = 0.18
    }
}

// MARK: - 책 내용

/// 책 한 권의 장 구성. 화면 크기와 무관한 값만 다룬다.
struct RecordBookModel {
    let month: RecordMonth
    /// 1일부터 날짜순.
    let days: [RecordPhotoStackEntry]
    /// 이번 달이면 오늘 날짜, 아니면 nil. 리본은 이번 달에만 단다.
    let today: Int?

    init(month: RecordMonth, posts: [RecordPost], now: Date = Date()) {
        self.month = month
        let order = RecordPhotoStackOrder(month: month, posts: posts, elapsedDays: month.elapsedDayCount(now: now))
        days = order.entries.reversed()
        let isThisMonth = RecordMonth.current(now: now) == month
        today = isThisMonth && !days.isEmpty ? days.last?.day : nil
    }

    /// 마지막 날이 펼쳐지는 펼침면. 표지(0), 속표지(1) 다음에 날마다 한 장이다.
    var maxSpread: Int { days.count + 1 }
    var hasRibbon: Bool { today != nil }
    var todayDay: Int { today ?? 0 }
    var firstPhotoPost: RecordPost? { days.first { $0.post != nil }?.post }
    var photoCount: Int { days.filter { $0.post != nil }.count }

    /// 묶음 위치(최근 날이 0)를 넘긴 장 수로 바꾼다. 끝을 넘겨 끄는 동안에는 범위를 넘을 수 있다.
    func spread(for position: CGFloat) -> CGFloat {
        CGFloat(maxSpread) - position
    }

    /// 그 펼침면에서 오른쪽에 펼쳐진 날. 표지와 속표지는 nil.
    func day(atSpread spread: Int) -> RecordPhotoStackEntry? {
        let index = spread - 2
        return days.indices.contains(index) ? days[index] : nil
    }

    func post(atSpread spread: Int) -> RecordPost? {
        day(atSpread: spread)?.post
    }

    func accessibilityLabel(atSpread spread: Int) -> String {
        if spread <= 0 { return "\(month.formatted) 책 표지" }
        guard let day = day(atSpread: spread) else { return "\(month.month)월의 기록 속표지" }
        let date = RecordStackFormat.monthDay.string(from: day.date)
        return day.post == nil ? "\(date), 기록 없음" : "\(date) 사진"
    }
}

/// 천 장정 색. 사진 대표색을 어둡게 눌러 쓴다.
struct RecordBookCloth {
    let base: RecordPhotoColor?

    private static let darken: Double = 0.55
    private static let darkInk = (red: 30.0 / 255, green: 34.0 / 255, blue: 30.0 / 255)

    func color(fallback: Color) -> Color {
        guard let base else { return fallback }
        return Color(
            red: base.red + (Self.darkInk.red - base.red) * Self.darken,
            green: base.green + (Self.darkInk.green - base.green) * Self.darken,
            blue: base.blue + (Self.darkInk.blue - base.blue) * Self.darken
        )
    }
}

/// 책을 그리는 자리. 앱 묶음 자리(393×290)를 기준으로 하고 화면 폭에 맞춰 줄인다.
/// 위쪽 양 모서리에는 뒤로 가기·전환 버튼이 떠 있어, 책은 그 아래(`pageTop`)부터 그린다.
struct RecordBookGeometry {
    static let stageSize = CGSize(width: 393, height: 290)
    static let ribbonWidth: CGFloat = 7
    static let tagSize = CGSize(width: 42, height: 23)
    static let perspectiveDistance: CGFloat = 1200
    static let tiltAnchor = CGPoint(x: stageSize.width * 0.5, y: stageSize.height * 0.52)
    /// 가로 사진으로 볼 비율(너비 / 높이). 정사각에 가까운 사진은 세로처럼 다룬다.
    static let wideRatio: CGFloat = 1.02

    let layout: RecordBookLayout
    let pageSize: CGSize
    /// 낱장이 붙은 책등의 x. 펼친 책은 화면 가운데, 한 면 책은 면이 가운데 오도록 왼쪽에 둔다.
    let spineX: CGFloat
    let pageTop: CGFloat
    /// 책을 뒤로 눕힌 각도. 펼친 책은 정면이라 0이다.
    let tiltDegrees: CGFloat
    /// 책등에서 리본까지의 거리.
    let ribbonInset: CGFloat
    /// 책 아래로 늘어진 리본 길이(꼬리표 제외).
    let ribbonTail: CGFloat
    /// 이만큼 끌면 한 장 넘어간다.
    let dragStep: CGFloat

    init(layout: RecordBookLayout) {
        self.layout = layout
        switch layout {
        case .spread:
            // 양 끝에 화살표(44pt)가 들어갈 자리를 남긴다.
            pageSize = CGSize(width: 152, height: 198)
            spineX = Self.stageSize.width / 2
            pageTop = 60
            tiltDegrees = 0
            // 리본은 책등 바로 옆 골에 끼워 사진을 가리지 않는다.
            ribbonInset = 0
            ribbonTail = 10
            dragStep = 152
        case .single:
            pageSize = CGSize(width: 300, height: 200)
            spineX = (Self.stageSize.width - 300) / 2
            pageTop = 56
            tiltDegrees = 8
            // 한 면에 사진이 크게 놓이므로 리본은 바깥 가장자리에 끼운다.
            // 꼬리표가 덮인 책 아래에 숨지 않을 만큼만 늘어뜨린다.
            ribbonInset = 286
            ribbonTail = 8
            dragStep = 150
        }
    }

    /// 표지는 속지보다 위·아래·바깥쪽으로 같은 폭(`coverMargin`)만큼 크다. 그래야 표지 테두리 두께가 둘레 어디서나 같다.
    var coverSize: CGSize { CGSize(width: pageSize.width + Self.coverMargin, height: pageSize.height + Self.coverMargin * 2) }
    static let coverMargin: CGFloat = 4

    /// 책을 위쪽이 멀어지게 눕히는 변환.
    var tilt: ProjectionTransform {
        ProjectionTransform(tiltTransform)
    }

    private var tiltTransform: CATransform3D {
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / Self.perspectiveDistance
        let anchor = Self.tiltAnchor
        let toAnchor = CATransform3DMakeTranslation(-anchor.x, -anchor.y, 0)
        let rotation = CATransform3DMakeRotation(tiltDegrees * .pi / 180, 1, 0, 0)
        let back = CATransform3DMakeTranslation(anchor.x, anchor.y, 0)
        return CATransform3DConcat(CATransform3DConcat(CATransform3DConcat(toAnchor, rotation), perspective), back)
    }

    /// 책 평면의 점이 화면 어디에 그려지는지. `projectionEffect`가 쓰는 것과 같은 식이다.
    func project(_ point: CGPoint) -> CGPoint {
        let m = tiltTransform
        let x = point.x, y = point.y
        let w = x * m.m14 + y * m.m24 + m.m44
        return CGPoint(
            x: (x * m.m11 + y * m.m21 + m.m41) / w,
            y: (x * m.m12 + y * m.m22 + m.m42) / w
        )
    }

    /// 책 평면의 사각형이 화면에 그려진 영역을 감싸는 사각형.
    func projectedFrame(of rect: CGRect) -> CGRect {
        let corners = [
            CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
            CGPoint(x: rect.minX, y: rect.maxY), CGPoint(x: rect.maxX, y: rect.maxY)
        ].map(project)
        let xs = corners.map(\.x), ys = corners.map(\.y)
        return CGRect(x: xs.min()!, y: ys.min()!, width: xs.max()! - xs.min()!, height: ys.max()! - ys.min()!)
    }

    static func isWide(_ ratio: CGFloat) -> Bool { ratio > wideRatio }

    /// 사진 자리(책 평면). 원래 비율 그대로 아래 칸 안에 맞춘다.
    /// - 펼친 책: 오른쪽 면 안쪽 가운데. 예) 3:4는 132×176, 4:3은 132×99.
    /// - 한 면 책, 가로 사진: 아래 캡션 줄을 뺀 자리 가운데. 예) 4:3은 173×130.
    /// - 한 면 책, 세로·정사각 사진: 오른쪽 캡션 칸을 뺀 왼쪽 자리. 예) 3:4는 132×176, 9:16은 99×176.
    func photoFrame(ratio: CGFloat) -> CGRect {
        let page = CGRect(origin: CGPoint(x: spineX, y: pageTop), size: pageSize)
        switch layout {
        case .spread:
            let inset = RecordBookMetrics.spreadPhotoInset
            let size = Self.fit(ratio: ratio, in: CGSize(width: page.width - inset * 2, height: page.height - inset * 2))
            return CGRect(x: page.midX - size.width / 2, y: page.midY - size.height / 2, width: size.width, height: size.height)
        case .single:
            let inset = RecordBookMetrics.singleInset
            if Self.isWide(ratio) {
                let area = CGSize(width: page.width - inset * 2, height: page.height - inset * 2 - RecordBookMetrics.singleCaptionHeight)
                let size = Self.fit(ratio: ratio, in: area)
                return CGRect(x: page.midX - size.width / 2, y: page.minY + inset + (area.height - size.height) / 2, width: size.width, height: size.height)
            }
            let area = CGSize(
                width: page.width - inset * 2 - RecordBookMetrics.singleSideColumn - RecordBookMetrics.singleSideGap,
                height: page.height - inset * 2
            )
            let size = Self.fit(ratio: ratio, in: area)
            return CGRect(x: page.minX + inset, y: page.midY - size.height / 2, width: size.width, height: size.height)
        }
    }

    static func fit(ratio: CGFloat, in area: CGSize) -> CGSize {
        area.width / ratio <= area.height
            ? CGSize(width: area.width, height: area.width / ratio)
            : CGSize(width: area.height * ratio, height: area.height)
    }

    /// 펼친 책 양 끝 화살표의 가운데 x. 책 가장자리와 화면 끝 사이 가운데에 두되 화면 밖으로 나가지 않게 한다.
    func arrowCenterX(leading: Bool) -> CGFloat {
        let half = RecordBookMetrics.minimumTouch / 2
        let bookEdge = spineX - coverSize.width
        let center = max(half, bookEdge / 2)
        return leading ? center : Self.stageSize.width - center
    }

    /// 리본 꼬리와 꼬리표를 덮는 누를 자리. 리본이 가늘어서 44pt 폭으로 넓힌다.
    var ribbonTouchFrame: CGRect {
        let centerX = spineX + ribbonInset + Self.ribbonWidth / 2
        let tail = CGRect(x: centerX - 22, y: pageTop + pageSize.height, width: 44, height: ribbonTail + Self.tagSize.height)
        let frame = projectedFrame(of: tail)
        let minimum = RecordBookMetrics.minimumTouch
        return frame.insetBy(dx: min(0, (frame.width - minimum) / 2), dy: min(0, (frame.height - minimum) / 2))
    }
}

/// 책 무대. 넘긴 장 수를 애니메이션 값으로 받아 넘기는 중간 모습을 매 프레임 다시 계산한다.
private struct RecordBookStage: View, Animatable {
    @Environment(\.chalkakTheme) private var theme
    var spread: CGFloat
    let model: RecordBookModel
    let geometry: RecordBookGeometry
    let images: [RecordPost.ID: UIImage]
    let topicTitles: [Date: String]
    let signatureURL: URL?
    let cloth: RecordBookCloth
    let wiggleTrigger: Int

    var animatableData: CGFloat {
        get { spread }
        set { spread = newValue }
    }

    private typealias M = RecordBookMetrics
    private var g: RecordBookGeometry { geometry }
    private var isSpread: Bool { geometry.layout == .spread }

    var body: some View {
        let maxSpread = CGFloat(model.maxSpread)
        let settled = min(maxSpread, max(0, spread))
        let overscroll = spread - settled
        let open = min(1, settled)
        let top = Int(settled.rounded(.down))
        // 펼친 책은 덮였을 때 표지 가운데가 책등 자리에 오게 옮긴다. 한 면 책은 표지가 이미 가운데다.
        // 끝을 넘겨 끌면 책 전체가 조금 밀린다.
        let closedShift = isSpread ? -(1 - open) * g.coverSize.width / 2 : 0
        let shiftX = closedShift - overscroll * M.overscrollShift
        let coverColor = cloth.color(fallback: theme.colors.calendarCellBorder)

        ZStack(alignment: .topLeading) {
            base(open: open, settled: settled, coverColor: coverColor)

            ForEach(0...model.maxSpread, id: \.self) { leafIndex in
                if isLeafVisible(leafIndex, settled: settled, top: top) {
                    leaf(leafIndex, settled: settled, coverColor: coverColor)
                }
            }

            if model.hasRibbon {
                ribbon(settled: settled)
            }

            turnShadow(settled: settled, top: top)
        }
        .offset(x: shiftX)
        .frame(width: RecordBookGeometry.stageSize.width, height: RecordBookGeometry.stageSize.height, alignment: .topLeading)
    }

    /// 지금 모습에 보이는 낱장만 그린다.
    /// 한 면 책도 넘긴 장을 왼쪽에 그대로 그린다. 대부분 화면 밖으로 잘리고 책등 옆 일부만 보여 펼친 책으로 읽힌다.
    private func isLeafVisible(_ index: Int, settled: CGFloat, top: Int) -> Bool {
        index == 0 || abs(index - top) <= 2
    }

    // MARK: 바탕

    /// 책 그림자, 책등, 뒤표지. 정면에서 보는 책이라 종이 다발의 두께 면은 그리지 않는다.
    private func base(open: CGFloat, settled: CGFloat, coverColor: Color) -> some View {
        // 펼칠수록 왼쪽으로 그림자가 넓어진다.
        let shadowWidth = g.coverSize.width * (1 + open)
        let shadowX = g.spineX - g.coverSize.width * open

        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: M.shadowRadius)
                .fill(Color.black.opacity(M.deskShadowOpacity))
                .frame(width: shadowWidth + 12, height: g.coverSize.height)
                .blur(radius: M.deskShadowBlur)
                .offset(x: shadowX - 6, y: g.pageTop + 2)

            RoundedRectangle(cornerRadius: M.spineRadius)
                .fill(coverColor.mix(with: .black, by: M.spineDarken))
                .frame(width: M.spineWidth, height: g.coverSize.height)
                .offset(x: g.spineX - M.spineWidth / 2, y: g.pageTop - RecordBookGeometry.coverMargin)

            UnevenRoundedRectangle(topLeadingRadius: 2, bottomLeadingRadius: 2, bottomTrailingRadius: M.coverRadius, topTrailingRadius: M.coverRadius)
                .fill(coverColor)
                .frame(width: g.coverSize.width, height: g.coverSize.height)
                .offset(x: g.spineX, y: g.pageTop - RecordBookGeometry.coverMargin)
        }
    }

    // MARK: 낱장

    /// 책등에 붙은 낱장 한 장. 펼침면 번호보다 앞선 장은 왼쪽으로 넘어가 있고, 그 사이에서는 책등을 축으로 넘어간다.
    @ViewBuilder
    private func leaf(_ index: Int, settled: CGFloat, coverColor: Color) -> some View {
        let turn = min(1, max(0, settled - CGFloat(index)))
        let isCover = index == 0
        let size = isCover ? g.coverSize : g.pageSize
        let top = isCover ? g.pageTop - RecordBookGeometry.coverMargin : g.pageTop
        let showsFront = turn < 0.5
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: isCover ? 2 : 1,
            bottomLeadingRadius: isCover ? 2 : 1,
            bottomTrailingRadius: isCover ? M.coverRadius : 2,
            topTrailingRadius: isCover ? M.coverRadius : 2
        )

        ZStack {
            front(of: index, coverColor: coverColor)
                .opacity(showsFront ? 1 : 0)
            // 뒤집힌 면은 거울상이 되므로 미리 좌우를 바꿔 둔다.
            back(of: index, coverColor: coverColor)
                .scaleEffect(x: -1)
                .opacity(showsFront ? 0 : 1)
        }
        .frame(width: size.width, height: size.height)
        // 넘어가는 동안에는 빛을 덜 받아 어두워진다.
        .overlay { Color.black.opacity(M.turnShadeOpacity * sin(Double(turn) * .pi)) }
        .clipShape(shape)
        .rotation3DEffect(.degrees(-180 * Double(turn)), axis: (x: 0, y: 1, z: 0), anchor: .leading, perspective: M.leafPerspective)
        // 쌓인 장은 어긋내지 않고 제자리에 겹친다. 어긋내면 장이 쌓일수록 표지 테두리를 덮어 가장자리가 얇아진다.
        .offset(x: g.spineX, y: top)
        .zIndex(turn > 0 && turn < 1 ? M.turningZIndex : showsFront ? M.leafZIndex - Double(index) : M.leafZIndex + Double(index))
    }

    @ViewBuilder
    private func front(of index: Int, coverColor: Color) -> some View {
        if index == 0 {
            coverFront(coverColor)
        } else if index == 1 {
            titlePage
        } else if let day = model.day(atSpread: index) {
            if isSpread {
                photoPage(day)
            } else {
                singlePage(day)
            }
        } else {
            paper(isRight: true)
        }
    }

    /// 낱장 뒷면. 펼친 책은 다음 날의 왼쪽 면이고, 한 면 책은 빈 종이다.
    @ViewBuilder
    private func back(of index: Int, coverColor: Color) -> some View {
        if index == 0 {
            coverInside(coverColor)
        } else if isSpread, let day = model.day(atSpread: index + 1) {
            textPage(day)
        } else {
            paper(isRight: false)
        }
    }

    // MARK: 표지

    private func coverFront(_ color: Color) -> some View {
        ZStack(alignment: .top) {
            color
            // 책등을 감싼 띠.
            HStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.25), .white.opacity(0.08), .black.opacity(0.12)], startPoint: .leading, endPoint: .trailing)
                    .frame(width: M.spineBandWidth)
                Spacer(minLength: 0)
            }
            VStack(spacing: theme.spacing.md) {
                if let post = model.firstPhotoPost {
                    RecordPhotoImage(image: images[post.id])
                        .frame(width: M.tipPhotoSize, height: M.tipPhotoSize)
                        .clipped()
                        .padding(M.tipPadding)
                        .background(theme.colors.inputBackground)
                        .shadow(color: .black.opacity(0.25), radius: 1, y: 1)
                }
                Text(verbatim: "\(model.month.year)")
                    .font(theme.typography.caption)
                    .tracking(M.coverYearTracking)
                    .foregroundStyle(theme.colors.textOnImage.opacity(0.8))
                Text(verbatim: "\(model.month.month)월")
                    .font(theme.typography.title2)
                    .foregroundStyle(theme.colors.textOnImage)
            }
            .padding(.top, M.coverContentTop)
        }
    }

    /// 표지 안쪽 면지. 판 가장자리가 면지 바깥으로 조금 드러난다.
    private func coverInside(_ color: Color) -> some View {
        color
            .overlay {
                color.mix(with: .white, by: M.endpaperLighten)
                    .padding([.top, .bottom, .leading], M.endpaperInset)
            }
    }

    /// 속표지. 찰캌 로고, "N월의 기록", 그 아래 내 사인.
    private var titlePage: some View {
        ZStack {
            paper(isRight: true)
            VStack(spacing: theme.spacing.sm) {
                // 로고와 같은 글씨(Continuous)를 줄여 머리글처럼 쓴다.
                ChalkakLogo()
                    .scaleEffect(M.titleLogoScale)
                    .frame(height: M.titleLogoHeight)
                    .accessibilityHidden(true)
                Text(verbatim: "\(model.month.month)월의 기록")
                    .font(theme.typography.headline)
                    .foregroundStyle(theme.colors.textPrimary)
                if let signatureURL {
                    // 사인은 사진 위에 얹도록 흰 선으로 저장된다. 밝은 종이 위에서는 글자색으로 물들여 보이게 한다.
                    ChalkakImage(source: .remote(signatureURL), contentMode: .fit, showsLoadingSkeleton: false)
                        .colorMultiply(theme.colors.textPrimary)
                        .frame(width: M.signatureSize.width, height: M.signatureSize.height)
                        .padding(.top, theme.spacing.xs)
                }
            }
        }
    }

    // MARK: 속지 — 펼친 책

    /// 펼친 책의 오른쪽 면. 사진을 원래 비율 그대로 면 가운데에 붙인다.
    private func photoPage(_ day: RecordPhotoStackEntry) -> some View {
        ZStack(alignment: .topLeading) {
            paper(isRight: true)

            if let post = day.post {
                pagePhoto(post)
            } else {
                emptyNotice
                    .frame(width: g.pageSize.width, height: g.pageSize.height)
            }
        }
        .frame(width: g.pageSize.width, height: g.pageSize.height, alignment: .topLeading)
    }

    /// 펼친 책의 왼쪽 면. 요일, 날짜, 그날의 주제.
    private func textPage(_ day: RecordPhotoStackEntry) -> some View {
        ZStack(alignment: .topLeading) {
            paper(isRight: false)
            pageHeader("\(model.month.month)월의 기록")

            VStack(alignment: .leading, spacing: theme.spacing.none) {
                Text(RecordStackFormat.weekday.string(from: day.date))
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textMuted)

                Text(verbatim: "\(day.day)")
                    .font(theme.typography.title1)
                    .monospacedDigit()
                    .foregroundStyle(theme.colors.textPrimary)

                Rectangle()
                    .fill(theme.colors.calendarCellBorder)
                    .frame(width: M.ruleWidth, height: 1)
                    .padding(.vertical, theme.spacing.sm)

                if let topic = topicTitles[day.date] {
                    Text(topic)
                        .font(theme.typography.footnote)
                        .foregroundStyle(theme.colors.textSecondary)
                        .lineLimit(M.topicLineLimit)
                        .multilineTextAlignment(.leading)
                }
            }
            .padding(.leading, M.textInset)
            .padding(.trailing, M.textInset + 4)
            .padding(.top, M.textTop)
        }
        .frame(width: g.pageSize.width, height: g.pageSize.height, alignment: .topLeading)
    }

    // MARK: 속지 — 한 면 책

    /// 한 면 책의 면. 가로 사진은 캡션을 아래 한 줄에, 세로·정사각 사진은 사진 오른쪽 칸에 둔다.
    private func singlePage(_ day: RecordPhotoStackEntry) -> some View {
        let ratio = day.post.map { RecordPhotoAspect.widthRatio(of: images[$0.id]) }
        let isTall = ratio.map { !RecordBookGeometry.isWide($0) } ?? false
        let photo = g.photoFrame(ratio: ratio ?? RecordBookMetrics.emptyRatio)

        return ZStack(alignment: .topLeading) {
            paper(isRight: true)

            if let post = day.post {
                pagePhoto(post)
            } else {
                emptyNotice
                    .frame(width: photo.width, height: photo.height)
                    .offset(x: photo.minX - g.spineX, y: photo.minY - g.pageTop)
            }

            if isTall {
                // 사진 오른쪽 칸. 아래쪽에 맞춰 날짜, 달·요일, 주제 순서로 둔다.
                let columnX = photo.maxX - g.spineX + M.singleSideGap
                VStack(alignment: .leading, spacing: theme.spacing.xs) {
                    Spacer(minLength: 0)
                    dayNumber(day)
                    monthWeekday(day)
                    topic(day, lineLimit: M.sideTopicLineLimit)
                }
                .frame(width: g.pageSize.width - columnX - M.singleInset, alignment: .leading)
                .frame(maxHeight: .infinity, alignment: .bottomLeading)
                .padding(.vertical, M.singleInset)
                .offset(x: columnX)
            } else {
                // 사진 아래 한 줄.
                HStack(alignment: .center, spacing: theme.spacing.sm) {
                    dayNumber(day)
                    VStack(alignment: .leading, spacing: theme.spacing.none) {
                        monthWeekday(day)
                        topic(day, lineLimit: 1)
                    }
                    Spacer(minLength: 0)
                }
                .frame(width: g.pageSize.width - M.singleInset * 2, height: M.singleCaptionHeight)
                .offset(x: M.singleInset, y: g.pageSize.height - M.singleInset - M.singleCaptionHeight)
            }
        }
        .frame(width: g.pageSize.width, height: g.pageSize.height, alignment: .topLeading)
    }

    private func dayNumber(_ day: RecordPhotoStackEntry) -> some View {
        Text(verbatim: "\(day.day)")
            .font(theme.typography.title1)
            .monospacedDigit()
            .foregroundStyle(theme.colors.textPrimary)
    }

    private func monthWeekday(_ day: RecordPhotoStackEntry) -> some View {
        Text(verbatim: "\(model.month.month)월 · \(RecordStackFormat.weekday.string(from: day.date))")
            .font(theme.typography.caption)
            .foregroundStyle(theme.colors.textMuted)
    }

    @ViewBuilder
    private func topic(_ day: RecordPhotoStackEntry, lineLimit: Int) -> some View {
        if let topic = topicTitles[day.date] {
            Text(topic)
                .font(theme.typography.footnote)
                .foregroundStyle(theme.colors.textSecondary)
                .lineLimit(lineLimit)
                .multilineTextAlignment(.leading)
        }
    }

    // MARK: 속지 공통

    /// 면 위의 사진. 자리(`photoFrame`)는 책 평면 기준이라 면 왼쪽 위로 옮겨 붙인다.
    private func pagePhoto(_ post: RecordPost) -> some View {
        let frame = g.photoFrame(ratio: RecordPhotoAspect.widthRatio(of: images[post.id]))
        return RecordPhotoImage(image: images[post.id])
            .frame(width: frame.width, height: frame.height)
            .clipped()
            .overlay { Rectangle().strokeBorder(Color.black.opacity(0.08), lineWidth: 0.5) }
            .offset(x: frame.minX - g.spineX, y: frame.minY - g.pageTop)
    }

    /// 기록이 없는 날 사진 자리에 적는 말.
    private var emptyNotice: some View {
        VStack(spacing: theme.spacing.sm) {
            Rectangle()
                .fill(theme.colors.calendarCellBorder)
                .frame(width: M.ruleWidth, height: 1)
            Text("이날은 사진이 없어요")
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textInactive)
        }
    }

    private func pageHeader(_ text: String) -> some View {
        Text(verbatim: text)
            .font(theme.typography.caption)
            .foregroundStyle(theme.colors.textMuted)
            .frame(width: g.pageSize.width - M.headerInset * 2, alignment: .leading)
            .padding(.horizontal, M.headerInset)
            .padding(.top, M.headerTop)
    }

    /// 종이. 책등 쪽으로 갈수록 골로 빛이 빠져 어두워지고, 바깥쪽은 조금 밝다.
    private func paper(isRight: Bool) -> some View {
        theme.colors.inputBackground
            .overlay {
                LinearGradient(
                    stops: [
                        .init(color: .black.opacity(M.gutterDark), location: 0),
                        .init(color: .black.opacity(M.gutterDark * 0.35), location: 0.07),
                        .init(color: .black.opacity(0), location: 0.2),
                        .init(color: .black.opacity(0), location: 0.82),
                        .init(color: .black.opacity(M.gutterDark * 0.3), location: 1)
                    ],
                    startPoint: isRight ? .leading : .trailing,
                    endPoint: isRight ? .trailing : .leading
                )
            }
    }

    // MARK: 리본

    /// 오늘 면 사이에 끼워 둔 리본. 오늘보다 앞선 날을 볼 때는 넘기지 않은 장 아래로 들어가 꼬리만 보인다.
    private func ribbon(settled: CGFloat) -> some View {
        let isAtToday = settled > CGFloat(model.maxSpread) - 0.5
        let length = g.pageSize.height + 6 + g.ribbonTail
        let x = g.spineX + g.ribbonInset
        let tag = RecordBookGeometry.tagSize
        let ribbonWidth = RecordBookGeometry.ribbonWidth
        // 리본은 기둥 모양의 삼각 표시와 같은 강조색을 천처럼 어둡게 눌러 쓴다.
        let ribbonMid = theme.colors.inputCursor
        let ribbonDark = ribbonMid.mix(with: .black, by: M.ribbonDarken)

        return VStack(spacing: -M.tagOverlap) {
            RecordRibbonStrip()
                .fill(LinearGradient(colors: [ribbonDark, ribbonMid, ribbonDark], startPoint: .leading, endPoint: .trailing))
                .frame(width: ribbonWidth, height: length)
            HStack(spacing: 2) {
                Text("오늘")
                    .font(theme.typography.caption)
                Text(verbatim: "\(model.todayDay)")
                    .font(theme.typography.caption)
                    .fontWeight(.bold)
                    .monospacedDigit()
            }
            .foregroundStyle(ribbonDark)
            .frame(width: tag.width, height: tag.height)
            .background(theme.colors.inputBackground, in: RoundedRectangle(cornerRadius: 3))
            .overlay {
                RoundedRectangle(cornerRadius: 2)
                    .strokeBorder(ribbonDark.opacity(0.45), style: StrokeStyle(lineWidth: 0.8, dash: [2, 1.5]))
                    .padding(2)
            }
            .shadow(color: .black.opacity(0.28), radius: 1, y: 1)
        }
        .keyframeAnimator(initialValue: 0.0, trigger: wiggleTrigger) { content, angle in
            content.rotationEffect(.degrees(angle), anchor: .top)
        } keyframes: { _ in
            KeyframeTrack {
                SpringKeyframe(6, duration: 0.17)
                SpringKeyframe(-4.5, duration: 0.24)
                SpringKeyframe(2.6, duration: 0.24)
                SpringKeyframe(-1.2, duration: 0.24)
                SpringKeyframe(0, duration: 0.31)
            }
        }
        .rotationEffect(.degrees(1.5), anchor: .top)
        .offset(x: x + ribbonWidth / 2 - tag.width / 2, y: g.pageTop - 6)
        // 오늘 면을 보고 있으면 장 위에 놓이고, 아니면 넘기지 않은 장 아래로 들어간다.
        .zIndex(isAtToday ? M.ribbonOnTopZIndex : M.ribbonBelowZIndex)
        .accessibilityHidden(true)
    }

    // MARK: 넘기는 장의 그림자

    /// 넘어가는 장이 아래 면에 드리우는 그림자. 장 끝이 바닥에 비치는 자리까지 짙어진다.
    @ViewBuilder
    private func turnShadow(settled: CGFloat, top: Int) -> some View {
        let fraction = settled - CGFloat(top)
        let isRight = fraction < 0.5
        if fraction > 0, top >= 1 {
            let strength = sin(Double(fraction) * .pi) * M.castShadowOpacity
            let edge = abs(cos(Double(fraction) * .pi)) * g.pageSize.width
            LinearGradient(
                stops: [
                    .init(color: .black.opacity(0), location: 0),
                    .init(color: .black.opacity(strength), location: max(0, (edge - 6) / g.pageSize.width)),
                    .init(color: .black.opacity(0), location: min(1, (edge + 26) / g.pageSize.width))
                ],
                startPoint: isRight ? .leading : .trailing,
                endPoint: isRight ? .trailing : .leading
            )
            .frame(width: g.pageSize.width, height: g.pageSize.height)
            .offset(x: isRight ? g.spineX : g.spineX - g.pageSize.width, y: g.pageTop)
            .zIndex(M.turningZIndex - 1)
            .allowsHitTesting(false)
        }
    }
}

/// 끝이 V자로 파인 리본 띠.
private struct RecordRibbonStrip: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY - 6))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

enum RecordBookMetrics {
    static let minimumTouch: CGFloat = 44
    /// 펼친 책 오른쪽 면에서 사진 둘레 여백.
    static let spreadPhotoInset: CGFloat = 10
    /// 한 면 책 면 둘레 여백.
    static let singleInset: CGFloat = 12
    /// 한 면 책에서 가로 사진 아래 캡션 줄 높이.
    static let singleCaptionHeight: CGFloat = 46
    /// 한 면 책에서 세로 사진 옆 캡션 칸 폭과 사진과의 간격.
    static let singleSideColumn: CGFloat = 104
    static let singleSideGap: CGFloat = 14
    /// 사진이 없는 날 한 면 책의 빈 자리 비율(가로 4:3).
    static let emptyRatio: CGFloat = 4.0 / 3.0
    fileprivate static let overscrollShift: CGFloat = 28
    fileprivate static let shadowRadius: CGFloat = 10
    fileprivate static let deskShadowOpacity: Double = 0.22
    fileprivate static let deskShadowBlur: CGFloat = 10
    fileprivate static let spineWidth: CGFloat = 10
    fileprivate static let spineRadius: CGFloat = 3
    fileprivate static let spineDarken: Double = 0.25
    fileprivate static let coverRadius: CGFloat = 5
    fileprivate static let leafPerspective: CGFloat = 0.3
    fileprivate static let turnShadeOpacity: Double = 0.16
    fileprivate static let castShadowOpacity: Double = 0.28
    fileprivate static let leafZIndex: Double = 100
    fileprivate static let turningZIndex: Double = 400
    fileprivate static let ribbonOnTopZIndex: Double = 300
    fileprivate static let ribbonBelowZIndex: Double = 1
    fileprivate static let spineBandWidth: CGFloat = 7
    fileprivate static let tipPhotoSize: CGFloat = 76
    fileprivate static let tipPadding: CGFloat = 4
    fileprivate static let coverContentTop: CGFloat = 24
    fileprivate static let coverYearTracking: CGFloat = 2.6
    /// 속표지 로고. 로고 글씨(30pt)를 이만큼 줄인다.
    fileprivate static let titleLogoScale: CGFloat = 0.6
    fileprivate static let titleLogoHeight: CGFloat = 24
    fileprivate static let signatureSize = CGSize(width: 72, height: 40)
    /// 면지 둘레로 드러나는 표지 판 폭. 바깥 표지 테두리와 같게 둔다.
    fileprivate static let endpaperInset: CGFloat = RecordBookGeometry.coverMargin
    fileprivate static let endpaperLighten: Double = 0.78
    fileprivate static let ruleWidth: CGFloat = 22
    fileprivate static let topicLineLimit = 3
    fileprivate static let sideTopicLineLimit = 4
    fileprivate static let textInset: CGFloat = 16
    fileprivate static let textTop: CGFloat = 32
    fileprivate static let headerInset: CGFloat = 14
    fileprivate static let headerTop: CGFloat = 12
    fileprivate static let gutterDark: Double = 0.18
    fileprivate static let tagOverlap: CGFloat = 9
    fileprivate static let ribbonDarken: Double = 0.3
}
