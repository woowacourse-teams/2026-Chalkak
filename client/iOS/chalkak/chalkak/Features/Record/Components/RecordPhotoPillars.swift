import SwiftUI
import UIKit

/// 월간 상단의 사진 묶음(기둥과 눈금자, 시안 2번).
/// - 1일이 맨 왼쪽, 가장 최근 날이 맨 오른쪽에 가는 기둥으로 서 있고, 고른 날만 넓게 펼쳐진다.
/// - 기둥 아래에 날마다 눈금, 일요일마다 긴 눈금, 5일마다 숫자를 둔다. 위의 삼각 표시가 고른 날을 가리킨다.
/// - 펼친 기둥은 사진 비율과 상관없이 폭이 같아서, 끄는 동안 펼친 자리가 하루에 기둥 한 칸씩 곧게 움직인다.
/// - 좌우로 끌면 스크롤처럼 넘어간다(오른쪽으로 끌면 지난 날, 왼쪽으로 끌면 최근 날). 손을 떼면 하루 단위로 붙는다.
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
    /// 줄이 화면보다 길 때 지금 밀려 있는 거리. 펼친 기둥이 가장자리에 닿을 때만 바뀐다.
    @State private var rowShift: CGFloat?

    var body: some View {
        GeometryReader { proxy in
            let order = RecordPhotoStackOrder(month: month, posts: posts)
            let layout = RecordPillarLayout(
                entryCount: order.entries.count,
                position: position,
                width: proxy.size.width,
                previousShift: rowShift
            )

            ZStack(alignment: .topLeading) {
                ForEach(Array(order.entries.enumerated()), id: \.element.id) { index, entry in
                    pillar(entry, index: index, layout: layout, count: order.entries.count, width: proxy.size.width)
                }

                marker
                    .position(x: layout.focusX, y: Metrics.markerY)

                RecordStackMonthLabel(month: month)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .zIndex(RecordStackMetrics.overlayZIndex)
            }
            .contentShape(Rectangle())
            .simultaneousGesture(dragGesture(count: order.entries.count, width: proxy.size.width))
            .onChange(of: position) { _, newPosition in
                // 달력이나 모양 전환으로 위치가 바뀌어도 펼친 기둥이 화면 안에 오게 한다.
                rowShift = RecordPillarLayout(
                    entryCount: order.entries.count,
                    position: newPosition,
                    width: proxy.size.width,
                    previousShift: rowShift
                ).shift
            }
        }
        .frame(height: RecordStackMetrics.height)
        .clipped()
    }

    // MARK: - 기둥

    private func pillar(
        _ entry: RecordPhotoStackEntry,
        index: Int,
        layout: RecordPillarLayout,
        count: Int,
        width: CGFloat
    ) -> some View {
        let distance = abs(CGFloat(index) - min(CGFloat(max(0, count - 1)), max(0, position)))
        let pillarWidth = layout.widths[index]
        let x = layout.centers[index]
        let isFocused = distance < 0.5
        let radius = distance < 1 ? Metrics.focusRadius + (Metrics.pillarRadius - Metrics.focusRadius) * distance : Metrics.pillarRadius
        let shape = RoundedRectangle(cornerRadius: radius)

        return VStack(spacing: Metrics.tickTopSpacing) {
            Group {
                if let post = entry.post {
                    RecordPhotoImage(image: images[post.id])
                        .frame(width: pillarWidth, height: Metrics.pillarHeight)
                        .clipShape(shape)
                        // 피드가 이 사진에서 확대되어 열리고, 닫히면 이 사진으로 돌아온다.
                        .feedZoomSource(.record(post.postId), cornerRadius: radius)
                } else {
                    RecordEmptyFrame(day: pillarWidth > Metrics.emptyLabelMinimumWidth ? entry.day : nil)
                        .frame(width: pillarWidth, height: Metrics.pillarHeight)
                        .clipShape(shape)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                if isFocused, let post = entry.post {
                    onSelectPost(post)
                } else {
                    settle(at: CGFloat(index), count: count, width: width)
                }
            }
            .accessibilityElement()
            .accessibilityLabel(accessibilityLabel(for: entry))
            .accessibilityHint(isFocused && entry.post != nil ? "피드에서 봅니다" : "")
            .accessibilityAddTraits(.isButton)
            .accessibilityAddTraits(isFocused ? .isSelected : [])

            tick(for: entry, isFocused: isFocused)
        }
        .position(x: x, y: Metrics.pillarTop + (Metrics.pillarHeight + Metrics.tickAreaHeight + Metrics.tickTopSpacing) / 2)
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

    private func accessibilityLabel(for entry: RecordPhotoStackEntry) -> String {
        let date = RecordStackFormat.monthDay.string(from: entry.date)
        return entry.post == nil ? "\(date), 기록 없음" : "\(date) 사진"
    }

    // MARK: - 드래그

    /// 그 자리로 붙는다. 줄이 밀려야 하면 같은 움직임 안에서 함께 민다.
    private func settle(at target: CGFloat, count: Int, width: CGFloat) {
        let shift = RecordPillarLayout(entryCount: count, position: target, width: width, previousShift: rowShift).shift
        withAnimation(Self.settle) {
            position = target
            rowShift = shift
        }
    }

    private func dragGesture(count: Int, width: CGFloat) -> some Gesture {
        let range: ClosedRange<CGFloat> = 0...CGFloat(max(0, count - 1))
        // 손가락이 가는 기둥 한 칸만큼 움직이면 하루 옮겨 간다.
        let step = RecordPillarLayout.step(entryCount: count, width: width)
        return DragGesture(minimumDistance: RecordStackMetrics.dragMinimumDistance)
            .onChanged { value in
                if drag == nil {
                    let isHorizontal = abs(value.translation.width) > abs(value.translation.height)
                    drag = DragState(startPosition: position, isHorizontal: isHorizontal)
                }
                guard let drag, drag.isHorizontal else { return }
                // 순서는 최근 날이 0이고 최근 날이 오른쪽에 있다. 스크롤처럼 오른쪽으로 끌면 지난 날(왼쪽)로 가므로 순서가 커진다.
                let raw = drag.startPosition + value.translation.width / step
                let next = RecordPhotoStackOrder.rubberBanded(raw, in: range)
                position = next
                rowShift = RecordPillarLayout(entryCount: count, position: next, width: width, previousShift: rowShift).shift
            }
            .onEnded { value in
                defer { drag = nil }
                guard let drag, drag.isHorizontal else { return }
                let projected = drag.startPosition + value.predictedEndTranslation.width / step
                settle(at: min(range.upperBound, max(range.lowerBound, projected.rounded())), count: count, width: width)
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
struct RecordPillarLayout {
    let widths: [CGFloat]
    let centers: [CGFloat]
    /// 고른 자리의 가로 위치. 삼각 표시가 여기를 가리킨다.
    let focusX: CGFloat
    /// 줄을 민 거리. 끝을 넘겨 끈 고무줄 거리는 넣지 않는다.
    let shift: CGFloat
    let width: CGFloat

    init(entryCount count: Int, position: CGFloat, width: CGFloat, previousShift: CGFloat?) {
        let last = CGFloat(max(0, count - 1))
        let clamped = min(last, max(0, position))
        let thin = Self.thinWidth(entryCount: count, width: width)
        let overscroll = -(position - clamped) * (thin + Metrics.gap)

        var widths = [CGFloat](repeating: thin, count: count)
        var rawCenters = [CGFloat](repeating: 0, count: count)
        var x: CGFloat = 0
        for index in stride(from: count - 1, through: 0, by: -1) {
            let distance = abs(CGFloat(index) - clamped)
            let w: CGFloat
            if distance < 1 {
                w = Metrics.openWidth + (Metrics.nearWidth - Metrics.openWidth) * distance
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
            let edge = Metrics.openWidth / 2 + Metrics.scrollEdgeMargin
            var next = previousShift ?? width / 2 - focus
            if focus + next < edge { next = edge - focus }
            if focus + next > width - edge { next = width - edge - focus }
            shift = min(leading, max(trailing, next))
        }

        self.widths = widths
        self.centers = rawCenters.map { $0 + shift + overscroll }
        self.focusX = focus + shift + overscroll
        self.shift = shift
        self.width = width
    }

    /// 가는 기둥 폭. 펼친 기둥과 바로 옆 두 기둥을 빼고 남은 자리에 한 달이 다 들어가게 8~12pt 안에서 정한다.
    static func thinWidth(entryCount count: Int, width: CGFloat) -> CGFloat {
        let thinCount = count - 3
        guard thinCount > 0 else { return Metrics.farWidth }
        let room = width - Metrics.edgePadding * 2 - Metrics.openWidth - Metrics.nearWidth * 2 - Metrics.gap * CGFloat(count - 1)
        return min(Metrics.farWidth, max(Metrics.minFarWidth, room / CGFloat(thinCount)))
    }

    /// 하루를 옮겨 가는 데 필요한 손가락 거리. 가는 기둥 한 칸(폭 + 간격)이다.
    static func step(entryCount count: Int, width: CGFloat) -> CGFloat {
        thinWidth(entryCount: count, width: width) + Metrics.gap
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
    /// 뒤로 가기 버튼(48pt) 아래에서 시작한다.
    static let pillarTop: CGFloat = 62
    /// 펼친 기둥 폭. 사진 비율과 상관없이 같다.
    static let openWidth: CGFloat = 150
    static let nearWidth: CGFloat = 26
    static let farWidth: CGFloat = 12
    static let minFarWidth: CGFloat = 8
    static let gap: CGFloat = 6
    static let edgePadding: CGFloat = 20
    /// 줄이 넘칠 때 펼친 기둥이 가장자리에서 이만큼(펼친 폭의 절반 + 여유) 안쪽에 머문다.
    static let scrollEdgeMargin: CGFloat = 24
    static let focusRadius: CGFloat = 16
    static let pillarRadius: CGFloat = 6
    static let emptyLabelMinimumWidth: CGFloat = 40
    static let tickTopSpacing: CGFloat = 6
    static let tickWidth: CGFloat = 1
    static let tickHeight: CGFloat = 7
    static let sundayTickHeight: CGFloat = 13
    static let tickNumberSpacing: CGFloat = 3
    static let tickAreaHeight: CGFloat = 30
    static let markerY: CGFloat = 53
    static let markerSize = CGSize(width: 12, height: 8)
    static let cullMargin: CGFloat = 60
}
