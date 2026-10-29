import SwiftUI
import UIKit

/// 월간 상단의 사진 묶음(기둥과 눈금자, 시안 2번).
/// - 1일이 맨 왼쪽, 가장 최근 날이 맨 오른쪽에 가는 기둥으로 서 있고, 고른 날만 넓게 펼쳐진다.
/// - 기둥 아래에 날마다 눈금, 일요일마다 긴 눈금, 5일마다 숫자를 둔다. 위의 삼각 표시가 고른 날을 가리킨다.
/// - 펼친 기둥은 사진 원본 비율대로 폭을 잡는다. 높이는 늘 같고, 폭 상한을 넘는 넓은 사진만 양옆을 잘라 보인다.
/// - 좌우로 끌면 스크롤처럼 넘어간다(오른쪽으로 끌면 지난 날, 왼쪽으로 끌면 최근 날).
///   사진 보정 눈금자처럼 하루마다 잠깐 걸렸다가 넘어가고, 하루를 넘을 때마다 햅틱을 준다.
///   튕기듯 떼면 감속하며 하루씩 더 넘어간 뒤 가까운 날에 붙는다.
/// - 펼친 사진을 누르면 그 사진의 피드로 간다. 다른 기둥을 누르면 그날을 펼친다.
struct RecordPhotoPillars: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let posts: [RecordPost]
    let images: [RecordPost.ID: UIImage]
    /// 고른 날의 순서(가장 최근 날이 0). 끄는 동안에는 소수로 움직인다.
    @Binding var position: CGFloat
    let onSelectPost: (RecordPost) -> Void

    @State private var drag: DragState?
    @State private var motion = RecordPillarMotion()
    /// 줄이 화면보다 길 때 지금 밀려 있는 거리. 펼친 기둥이 가장자리에 닿을 때만 바뀐다.
    @State private var rowShift: CGFloat?

    var body: some View {
        GeometryReader { proxy in
            let order = RecordPhotoStackOrder(month: month, posts: posts)
            let pillarHeight = RecordPillarLayout.pillarHeight(stackHeight: proxy.size.height)
            let openSizes = order.entries.map {
                RecordPillarLayout.openSize(ratio: ratio(of: $0), pillarHeight: pillarHeight, width: proxy.size.width)
            }
            let layout = RecordPillarLayout(
                entryCount: order.entries.count,
                position: position,
                width: proxy.size.width,
                previousShift: rowShift,
                openSizes: openSizes,
                pillarHeight: pillarHeight
            )

            ZStack(alignment: .topLeading) {
                ForEach(Array(order.entries.enumerated()), id: \.element.id) { index, entry in
                    pillar(entry, index: index, layout: layout, count: order.entries.count, width: proxy.size.width, openSizes: openSizes)
                }

                marker
                    .position(x: layout.focusX, y: Metrics.markerY)
            }
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture(count: order.entries.count, width: proxy.size.width, openSizes: openSizes))
            .onDisappear {
                motion.stop()
            }
            .sensoryFeedback(trigger: Int(position.rounded())) { _, _ in
                // 손으로 끌거나 관성으로 넘어갈 때만 낸다. 달력이나 탭으로 옮길 때는 내지 않는다.
                motion.isInteracting ? .selection : nil
            }
            .onChange(of: position) { _, newPosition in
                // 달력이나 모양 전환으로 위치가 바뀌어도 펼친 기둥이 화면 안에 오게 한다.
                rowShift = RecordPillarLayout(
                    entryCount: order.entries.count,
                    position: newPosition,
                    width: proxy.size.width,
                    previousShift: rowShift,
                    openSizes: openSizes
                ).shift
            }
        }
        .clipped()
    }

    // MARK: - 기둥

    private func pillar(
        _ entry: RecordPhotoStackEntry,
        index: Int,
        layout: RecordPillarLayout,
        count: Int,
        width: CGFloat,
        openSizes: [CGSize]
    ) -> some View {
        let distance = abs(CGFloat(index) - min(CGFloat(max(0, count - 1)), max(0, position)))
        let pillarWidth = layout.widths[index]
        let photoHeight = layout.heights[index]
        let x = layout.centers[index]
        let isFocused = distance < 0.5
        let radius = distance < 1 ? Metrics.focusRadius + (Metrics.pillarRadius - Metrics.focusRadius) * distance : Metrics.pillarRadius
        let shape = RoundedRectangle(cornerRadius: radius)

        return VStack(spacing: Metrics.tickTopSpacing) {
            Group {
                if let post = entry.post {
                    RecordPhotoImage(image: images[post.id])
                        .frame(width: pillarWidth, height: photoHeight)
                        .clipShape(shape)
                        // 피드가 이 사진에서 확대되어 열리고, 닫히면 이 사진으로 돌아온다.
                        .feedZoomSource(.record(post.postId), cornerRadius: radius)
                } else {
                    RecordEmptyFrame(day: pillarWidth > Metrics.emptyLabelMinimumWidth ? entry.day : nil)
                        .frame(width: pillarWidth, height: photoHeight)
                        .clipShape(shape)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                if isFocused, let post = entry.post {
                    onSelectPost(post)
                } else {
                    settle(at: CGFloat(index), count: count, width: width, openSizes: openSizes)
                }
            }
            .accessibilityElement()
            .accessibilityLabel(accessibilityLabel(for: entry))
            .accessibilityHint(isFocused && entry.post != nil ? "피드에서 봅니다" : "")
            .accessibilityAddTraits(.isButton)
            .accessibilityAddTraits(isFocused ? .isSelected : [])

            tick(for: entry, isFocused: isFocused)
        }
        .position(x: x, y: Metrics.pillarTop + (layout.pillarHeight + Metrics.tickAreaHeight + Metrics.tickTopSpacing) / 2)
        .opacity(x < -Metrics.cullMargin || x > layout.width + Metrics.cullMargin ? 0 : 1)
        .zIndex(Double(count) - Double(distance))
    }

    /// 날마다 눈금 하나. 일요일은 길게, 5일마다(그리고 1일) 숫자를 붙인다. 고른 날은 늘 숫자를 진하게 적는다.
    private func tick(for entry: RecordPhotoStackEntry, isFocused: Bool) -> some View {
        let isSunday = RecordMonth.calendar.component(.weekday, from: entry.date) == 1
        let showsNumber = isFocused || entry.day % 5 == 0 || entry.day == 1
        return ZStack(alignment: .top) {
            Rectangle()
                .fill(isSunday ? theme.colors.textSecondary : theme.colors.calendarCellBorder)
                .frame(width: Metrics.tickWidth, height: isSunday ? Metrics.sundayTickHeight : Metrics.tickHeight)

            if showsNumber {
                Text(verbatim: "\(entry.day)")
                    .font(theme.typography.caption)
                    .monospacedDigit()
                    .foregroundStyle(isFocused ? theme.colors.textPrimary : theme.colors.textMuted)
                    .fixedSize()
                    // 눈금 길이와 상관없이 숫자는 한 줄에 맞춘다.
                    .offset(y: Metrics.sundayTickHeight + Metrics.tickNumberSpacing)
            }
        }
        .frame(width: Metrics.tickWidth, height: Metrics.tickAreaHeight, alignment: .top)
        .accessibilityHidden(true)
    }

    private var marker: some View {
        RecordPillarMarker()
            .fill(theme.colors.inputCursor)
            .frame(width: Metrics.markerSize.width, height: Metrics.markerSize.height)
            .accessibilityHidden(true)
    }

    /// 사진의 가로/세로 비율. 기록이 없는 날은 세로 4 : 가로 3 프레임이고, 사진을 아직 받지 못했으면 nil이다.
    private func ratio(of entry: RecordPhotoStackEntry) -> CGFloat? {
        guard let post = entry.post else { return Metrics.emptyRatio }
        guard let size = images[post.id]?.size, size.width > 0, size.height > 0 else { return nil }
        return size.width / size.height
    }

    private func accessibilityLabel(for entry: RecordPhotoStackEntry) -> String {
        let date = RecordStackFormat.monthDay.string(from: entry.date)
        return entry.post == nil ? "\(date), 기록 없음" : "\(date) 사진"
    }

    // MARK: - 드래그

    /// 그 자리로 붙는다. 줄이 밀려야 하면 같은 움직임 안에서 함께 민다.
    private func settle(at target: CGFloat, count: Int, width: CGFloat, openSizes: [CGSize]) {
        motion.stop()
        let shift = RecordPillarLayout(
            entryCount: count,
            position: target,
            width: width,
            previousShift: rowShift,
            openSizes: openSizes
        ).shift
        withAnimation(Self.settle) {
            position = target
            rowShift = shift
        }
    }

    private func dragGesture(count: Int, width: CGFloat, openSizes: [CGSize]) -> some Gesture {
        let range: ClosedRange<CGFloat> = 0...CGFloat(max(0, count - 1))
        return DragGesture(minimumDistance: RecordStackMetrics.dragMinimumDistance)
            .onChanged { value in
                if drag == nil {
                    let isHorizontal = abs(value.translation.width) > abs(value.translation.height)
                    // 관성으로 넘어가는 중에 다시 잡으면 걸림을 풀기 전 위치에서 이어 간다.
                    drag = DragState(startPosition: motion.rawPosition ?? position, isHorizontal: isHorizontal)
                    if isHorizontal {
                        motion.stop()
                        motion.isInteracting = true
                    }
                }
                guard let drag, drag.isHorizontal else { return }
                // 순서는 최근 날이 0이고 최근 날이 오른쪽에 있다. 스크롤처럼 오른쪽으로 끌면 지난 날(왼쪽)로 가므로 순서가 커진다.
                let raw = drag.startPosition + value.translation.width / Metrics.dragStep
                emit(RecordPhotoStackOrder.rubberBanded(raw, in: range), count: count, width: width, openSizes: openSizes)
            }
            .onEnded { value in
                defer { drag = nil }
                guard let drag, drag.isHorizontal else { return }
                let raw = drag.startPosition + value.translation.width / Metrics.dragStep
                // 끝을 넘겨 끌었으면 굴리지 않고 끝 날로 되돌아간다.
                guard range.contains(raw) else {
                    settle(at: min(range.upperBound, max(range.lowerBound, raw)), count: count, width: width, openSizes: openSizes)
                    return
                }
                decelerate(
                    from: raw,
                    velocity: value.velocity.width / Metrics.dragStep,
                    range: range,
                    count: count,
                    width: width,
                    openSizes: openSizes
                )
            }
    }

    /// 걸림을 풀기 전 위치(`raw`)를 받아 하루마다 걸리게 바꿔 보여 준다.
    private func emit(_ raw: CGFloat, count: Int, width: CGFloat, openSizes: [CGSize]) {
        motion.rawPosition = raw
        let next = RecordPillarLayout.detented(raw, entryCount: count)
        position = next
        rowShift = RecordPillarLayout(
            entryCount: count,
            position: next,
            width: width,
            previousShift: rowShift,
            openSizes: openSizes
        ).shift
    }

    /// 사진 보정 눈금자처럼 손을 뗀 속도에서 감속하며 하루씩 넘어가고, 멈추면 가까운 날에 붙는다.
    /// - Parameter velocity: 초당 넘어가는 날 수.
    private func decelerate(
        from raw: CGFloat,
        velocity: CGFloat,
        range: ClosedRange<CGFloat>,
        count: Int,
        width: CGFloat,
        openSizes: [CGSize]
    ) {
        guard abs(velocity) > Metrics.minimumInertiaVelocity else {
            settle(at: raw.rounded(), count: count, width: width, openSizes: openSizes)
            return
        }
        motion.inertia = Task { @MainActor in
            var raw = raw
            var velocity = velocity
            var lastTime = ContinuousClock.now
            while !Task.isCancelled, abs(velocity) > Metrics.minimumInertiaVelocity {
                try? await Task.sleep(for: Metrics.inertiaFrameInterval)
                guard !Task.isCancelled else { return }
                let now = ContinuousClock.now
                let elapsed = CGFloat((now - lastTime) / .seconds(1))
                lastTime = now
                raw += velocity * elapsed
                velocity *= pow(Metrics.decelerationRate, elapsed * 1000)
                let clamped = min(range.upperBound, max(range.lowerBound, raw))
                emit(clamped, count: count, width: width, openSizes: openSizes)
                // 끝 날에 닿으면 더 가지 않는다.
                guard clamped == raw else { break }
            }
            guard !Task.isCancelled else { return }
            settle(at: min(range.upperBound, max(range.lowerBound, raw)).rounded(), count: count, width: width, openSizes: openSizes)
        }
    }

    private static let settle = Animation.spring(response: 0.38, dampingFraction: 0.82)

    private struct DragState {
        let startPosition: CGFloat
        let isHorizontal: Bool
    }
}

/// 기둥 줄의 너비와 가로 위치.
/// - 1일이 왼쪽 끝, 가장 최근 날(순서 0)이 오른쪽 끝이다.
/// - 한 달이 화면에 들어올 수 있으면 가는 기둥을 8pt까지 좁혀 1일부터 오늘까지 다 보이게 하고, 1일을 왼쪽 끝에 붙인다.
/// - 그래도 넘치면 펼친 기둥이 가장자리 가까이 갈 때만 줄을 민다(`previousShift`를 이어 쓴다).
/// - 끝을 넘겨 끌면 기둥 폭은 끝 그대로 두고 줄 전체가 손가락을 따라 밀린다.
/// - 펼친 기둥은 날마다 `openSizes`의 크기(사진 원본 비율)로 펼쳐지고, 멀어질수록 가는 기둥으로 줄어든다.
struct RecordPillarLayout {
    let widths: [CGFloat]
    /// 기둥에 그리는 높이. 모든 기둥이 기둥 높이로 같다.
    let heights: [CGFloat]
    /// 기둥 높이. 묶음이 커지면 함께 커진다.
    let pillarHeight: CGFloat
    let centers: [CGFloat]
    /// 고른 자리의 가로 위치. 삼각 표시가 여기를 가리킨다.
    let focusX: CGFloat
    /// 줄을 민 거리. 끝을 넘겨 끈 고무줄 거리는 넣지 않는다.
    let shift: CGFloat
    let width: CGFloat

    /// - Parameters:
    ///   - openSizes: 날마다 펼쳤을 때의 크기. 비어 있으면 모든 날을 기본 크기로 펼친다.
    ///   - pillarHeight: 기둥 높이. `openSizes`와 같은 높이로 계산해 넘긴다.
    init(
        entryCount count: Int,
        position: CGFloat,
        width: CGFloat,
        previousShift: CGFloat?,
        openSizes: [CGSize] = [],
        pillarHeight: CGFloat = RecordPillarLayout.defaultPillarHeight
    ) {
        let last = CGFloat(max(0, count - 1))
        let clamped = min(last, max(0, position))
        let thin = Self.thinWidth(entryCount: count, width: width)
        let overscroll = -(position - clamped) * (thin + Metrics.gap)

        var widths = [CGFloat](repeating: thin, count: count)
        let heights = [CGFloat](repeating: pillarHeight, count: count)
        var rawCenters = [CGFloat](repeating: 0, count: count)
        var x: CGFloat = 0
        for index in stride(from: count - 1, through: 0, by: -1) {
            let distance = abs(CGFloat(index) - clamped)
            let open = openSizes.indices.contains(index) ? openSizes[index] : Self.openSize(ratio: nil, pillarHeight: pillarHeight)
            let w: CGFloat
            if distance < 1 {
                w = open.width + (Metrics.nearWidth - open.width) * distance
            } else if distance < 2 {
                w = Metrics.nearWidth + (thin - Metrics.nearWidth) * (distance - 1)
            } else {
                w = thin
            }
            widths[index] = w
            rawCenters[index] = x + w / 2
            x += w + Metrics.gap
        }

        let lower = Int(clamped.rounded(.down))
        let upper = min(max(0, count - 1), lower + 1)
        let fraction = clamped - CGFloat(lower)
        let focus = rawCenters.isEmpty ? 0 : rawCenters[lower] + (rawCenters[upper] - rawCenters[lower]) * fraction
        let row = max(0, x - Metrics.gap)
        let leading = Metrics.edgePadding
        let trailing = width - Metrics.edgePadding - row

        let shift: CGFloat
        if trailing >= leading {
            shift = leading
        } else {
            let focusWidth = rawCenters.isEmpty ? Metrics.openWidth : max(widths[lower], widths[upper])
            let edge = focusWidth / 2 + Metrics.scrollEdgeMargin
            var next = previousShift ?? width / 2 - focus
            if focus + next < edge { next = edge - focus }
            if focus + next > width - edge { next = width - edge - focus }
            shift = min(leading, max(trailing, next))
        }

        self.widths = widths
        self.heights = heights
        self.pillarHeight = pillarHeight
        self.centers = rawCenters.map { $0 + shift + overscroll }
        self.focusX = focus + shift + overscroll
        self.shift = shift
        self.width = width
    }

    /// 기본 묶음 높이(`RecordStackMetrics.height`)일 때의 기둥 높이.
    static let defaultPillarHeight = Metrics.pillarHeight

    /// 묶음 높이에서 기둥 높이를 정한다. 기둥 위아래(버튼, 눈금, 아래 여백)는 높이가 그대로이고 남는 만큼 기둥이 커진다.
    /// 예: 기본 묶음 높이 300 → 126, 410 → 236.
    static func pillarHeight(stackHeight: CGFloat) -> CGFloat {
        max(Metrics.pillarHeight, stackHeight - (RecordStackMetrics.height - Metrics.pillarHeight))
    }

    /// 펼친 기둥 크기. 높이는 늘 기둥 높이이고, 폭을 사진 비율대로 잡는다.
    /// 폭이 최대 폭을 넘는 넓은 사진은 최대 폭에서 멈추고 양옆이 잘린다(사진은 기둥을 꽉 채워 그린다).
    /// 최대 폭은 기본 240pt를 기둥이 커진 만큼 키우되, 옆 기둥이 보일 자리(`openSideRoom`)는 남긴다.
    /// 예(기둥 126): 4:3 → 168×126, 3:4 → 94.5×126, 3:1 → 240×126(양옆 잘림). 비율이 없으면(사진을 받기 전) 150×126.
    static func openSize(
        ratio: CGFloat?,
        pillarHeight: CGFloat = RecordPillarLayout.defaultPillarHeight,
        width: CGFloat = .infinity
    ) -> CGSize {
        let scale = pillarHeight / Metrics.pillarHeight
        guard let ratio, ratio > 0 else { return CGSize(width: Metrics.openWidth * scale, height: pillarHeight) }
        let maxWidth = max(Metrics.maxOpenWidth, min(Metrics.maxOpenWidth * scale, width - Metrics.openSideRoom))
        return CGSize(width: min(pillarHeight * ratio, maxWidth), height: pillarHeight)
    }

    /// 가는 기둥 폭. 펼친 기둥과 바로 옆 두 기둥을 빼고 남은 자리에 한 달이 다 들어가게 8~12pt 안에서 정한다.
    static func thinWidth(entryCount count: Int, width: CGFloat) -> CGFloat {
        let thinCount = count - 3
        guard thinCount > 0 else { return Metrics.farWidth }
        let room = width - Metrics.edgePadding * 2 - Metrics.openWidth - Metrics.nearWidth * 2 - Metrics.gap * CGFloat(count - 1)
        return min(Metrics.farWidth, max(Metrics.minFarWidth, room / CGFloat(thinCount)))
    }

    /// 끄는 위치를 하루마다 걸리게 바꾼다. 날짜 가까이(앞뒤 `detentHalfWidth`)에서는 그 날에 멈춰 있다가,
    /// 그 사이 구간에서만 다음 날로 넘어간다. 범위 밖(끝을 넘겨 끈 고무줄)은 그대로 둔다.
    /// 예: 3.2 → 3, 3.5 → 3.5, 3.8 → 4.
    static func detented(_ raw: CGFloat, entryCount count: Int) -> CGFloat {
        let last = CGFloat(max(0, count - 1))
        guard raw > 0, raw < last else { return raw }
        let day = raw.rounded(.down)
        let fraction = raw - day
        let hold = Metrics.detentHalfWidth
        if fraction <= hold { return day }
        if fraction >= 1 - hold { return day + 1 }
        return day + (fraction - hold) / (1 - hold * 2)
    }

    /// 펼친 자리가 하루 옮겨 갈 때 움직이는 거리. 가는 기둥 한 칸(폭 + 간격)이다.
    static func step(entryCount count: Int, width: CGFloat) -> CGFloat {
        thinWidth(entryCount: count, width: width) + Metrics.gap
    }
}

/// 끌기와 관성 상태. 바뀌어도 다시 그릴 필요가 없어 관찰하지 않는 참조 타입으로 둔다.
@MainActor
private final class RecordPillarMotion {
    /// 걸림을 풀기 전 위치. 관성 중에 다시 잡으면 여기서 이어 간다.
    var rawPosition: CGFloat?
    /// 손으로 끄는 중이거나 관성으로 넘어가는 중이다. 이때만 햅틱을 낸다.
    var isInteracting = false
    var inertia: Task<Void, Never>?

    func stop() {
        inertia?.cancel()
        inertia = nil
        rawPosition = nil
        isInteracting = false
    }
}

private struct RecordPillarMarker: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

private enum Metrics {
    static let pillarHeight: CGFloat = 126
    /// 위 버튼 줄 아래 묶음 공통 간격만큼 띄워 시작한다.
    static let pillarTop: CGFloat = RecordStackMetrics.contentTop
    /// 비율을 모를 때(사진을 받기 전) 펼친 기둥 폭.
    static let openWidth: CGFloat = 150
    /// 넓은 사진을 펼칠 때 넘지 않는 폭. 이보다 넓으면 양옆을 자른다.
    static let maxOpenWidth: CGFloat = 240
    /// 기둥이 커져도 펼친 사진 옆에 남기는 폭. 양쪽 여백과 옆 기둥 한두 개가 보이게 한다.
    /// 커진 기둥(236pt)에서도 4:3 사진(315pt)이 낮아지지 않고 다 들어가는 폭이다.
    static let openSideRoom: CGFloat = 72
    static let nearWidth: CGFloat = 26
    static let farWidth: CGFloat = 12
    static let minFarWidth: CGFloat = 8
    static let gap: CGFloat = 6
    static let edgePadding: CGFloat = 20
    /// 줄이 넘칠 때 펼친 기둥이 가장자리에서 이만큼(펼친 폭의 절반 + 여유) 안쪽에 머문다.
    static let scrollEdgeMargin: CGFloat = 24
    static let focusRadius: CGFloat = 16
    static let pillarRadius: CGFloat = 6
    /// 기록이 없는 날을 펼친 프레임 비율(가로/세로). 세로 4 : 가로 3이다.
    static let emptyRatio: CGFloat = 3.0 / 4.0
    static let emptyLabelMinimumWidth: CGFloat = 40
    static let tickTopSpacing: CGFloat = 6
    static let tickWidth: CGFloat = 1
    static let tickHeight: CGFloat = 7
    static let sundayTickHeight: CGFloat = 13
    static let tickNumberSpacing: CGFloat = 3
    static let tickAreaHeight: CGFloat = 30
    /// 삼각 표시는 기둥 바로 위(9pt)에 둔다.
    static let markerY: CGFloat = pillarTop - 9
    static let markerSize = CGSize(width: 12, height: 8)
    static let cullMargin: CGFloat = 60

    /// 하루를 넘기는 데 필요한 손가락 거리. 가는 기둥 한 칸보다 넉넉히 두어 천천히 넘어간다.
    static let dragStep: CGFloat = 32
    /// 날짜 앞뒤로 멈춰 있는 구간(하루의 비율). 하루 32pt 중 앞뒤 8pt씩 걸려 있다.
    static let detentHalfWidth: CGFloat = 0.25
    /// 1ms마다 속도에 곱하는 비율. 사진 보정 눈금자와 같다(손을 뗀 속도로 약 0.2초 간 만큼 더 넘어간다).
    static let decelerationRate: CGFloat = 0.995
    /// 초당 넘어가는 날 수. 이보다 느리면 관성을 멈추고 가까운 날에 붙는다.
    static let minimumInertiaVelocity: CGFloat = 1.5
    static let inertiaFrameInterval: Duration = .milliseconds(8)
}
