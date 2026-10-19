import SwiftUI

/// 조정 화면의 항목 목록과 눈금자. 항목을 고르고 눈금자를 밀어 값을 바꾼다.
///
/// 각 항목 테두리는 값만큼 채워진다. 양수는 시계 방향, 음수는 반시계 방향으로 채운다.
struct PhotoEditAdjustControls: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedItem: PhotoEditAdjustItem
    let value: (PhotoEditAdjustItem) -> Double
    let automaticValue: (PhotoEditAdjustItem) -> Double?
    let onSelect: (PhotoEditAdjustItem) -> Void
    let onValueChange: (Double) -> Void

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text(selectedItem.title)
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textOnEditorMuted)
                .accessibilityHidden(true)

            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: theme.spacing.md) {
                        ForEach(PhotoEditAdjustItem.allCases) { item in
                            itemButton(item)
                                .id(item)
                        }
                    }
                }
                .onChange(of: selectedItem) { _, item in
                    withAnimation {
                        proxy.scrollTo(item, anchor: .center)
                    }
                }
            }

            PhotoEditValueRuler(
                value: value(selectedItem),
                range: selectedItem.range,
                automaticValue: automaticValue(selectedItem),
                onChange: onValueChange
            )
                // 항목을 바꾸면 드래그 상태를 새로 시작한다.
                .id(selectedItem)
                .accessibilityLabel("\(selectedItem.title) 값")
                .accessibilityValue("\(Self.percent(value(selectedItem)))")
                .accessibilityIdentifier("photoEdit.adjust.ruler")
        }
    }

    private func itemButton(_ item: PhotoEditAdjustItem) -> some View {
        let itemValue = value(item)
        let isSelected = item == selectedItem

        return Button {
            onSelect(item)
        } label: {
            ZStack {
                // 자동은 숫자 대신 아이콘을 두고, 켜지면 강조색으로 바꿔 적용 여부를 보여 준다.
                if isSelected, item != .auto {
                    Text("\(Self.percent(itemValue))")
                        .font(theme.typography.callout)
                        .monospacedDigit()
                } else {
                    Image(systemName: item.systemImage)
                        .font(.system(size: Metrics.iconSize))
                        .symbolRenderingMode(.monochrome)
                }
            }
            .foregroundStyle(item == .auto && itemValue > 0 ? theme.colors.editorAccent : theme.colors.textOnEditor)
            .frame(width: Metrics.itemSize, height: Metrics.itemSize)
            .background(theme.colors.editorControl, in: Circle())
            .overlay {
                Circle()
                    .inset(by: Metrics.ringWidth / 2)
                    .stroke(
                        theme.colors.textOnEditorMuted.opacity(isSelected ? 1 : Metrics.ringTrackOpacity),
                        lineWidth: Metrics.ringWidth
                    )
                Circle()
                    .inset(by: Metrics.ringWidth / 2)
                    .trim(from: 0, to: CGFloat(abs(itemValue)))
                    .stroke(
                        isSelected ? theme.colors.textOnEditor : theme.colors.editorAccent,
                        style: StrokeStyle(lineWidth: Metrics.ringWidth, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .scaleEffect(x: itemValue < 0 ? -1 : 1)
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.title)
        .accessibilityValue("\(Self.percent(itemValue))")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("photoEdit.adjust.\(Self.identifier(of: item))")
    }

    private static func identifier(of item: PhotoEditAdjustItem) -> String {
        switch item {
        case .auto: "auto"
        case let .adjustment(adjustment): "\(adjustment)"
        }
    }

    private static func percent(_ value: Double) -> Int {
        Int((value * 100).rounded())
    }
}

/// 사진 앱의 조정 눈금자처럼 가운데 표시선 아래로 눈금을 밀어 값을 고른다. 왼쪽으로 밀면 값이 커진다.
///
/// 표시선을 지난 눈금은 잠깐 솟았다가 가라앉고, 손을 튕기듯 떼면 감속하며 더 굴러간다.
/// 자동 보정이 정한 값은 점으로 표시하고, 그 값에서는 조금 더 밀어야 벗어나도록 멈춘다.
private struct PhotoEditValueRuler: View {
    @Environment(\.chalkakTheme) private var theme
    @State private var motion = PhotoEditRulerMotion()
    /// 표시선을 지난 눈금과 지난 시각.
    @State private var passedTicks: [Int: Date] = [:]

    let value: Double
    let range: ClosedRange<Double>
    let automaticValue: Double?
    let onChange: (Double) -> Void

    /// 멈춤과 강한 햅틱을 주는 값. 자동 보정 값이 있으면 그 값, 없으면 0이다.
    private var anchor: Double {
        min(max(automaticValue ?? 0, range.lowerBound), range.upperBound)
    }

    var body: some View {
        TimelineView(.animation(paused: passedTicks.isEmpty)) { timeline in
            Canvas { context, size in
                draw(in: &context, size: size, at: timeline.date)
            }
        }
        .frame(height: Metrics.height)
        .contentShape(Rectangle())
        .gesture(dragGesture)
        .onChange(of: value) { oldValue, newValue in
            recordPassedTicks(from: oldValue, to: newValue)
        }
        .task(id: passedTicks) {
            // 잔상이 다 가라앉으면 비워서 매 프레임 다시 그리기를 멈춘다.
            try? await Task.sleep(for: .seconds(Metrics.liftDuration))
            let now = Date()
            passedTicks = passedTicks.filter { now.timeIntervalSince($0.value) < Metrics.liftDuration }
        }
        .onDisappear {
            motion.stop()
        }
        .sensoryFeedback(trigger: PhotoEditRulerStep(value: value, anchor: anchor)) { _, step in
            guard motion.isInteracting else { return nil }
            return step.isAnchor ? .impact(weight: .medium) : .selection
        }
        .accessibilityElement()
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                onChange(snapped(value + Metrics.accessibilityStep))
            case .decrement:
                onChange(snapped(value - Metrics.accessibilityStep))
            @unknown default:
                break
            }
        }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { gesture in
                let startPosition = motion.dragStartPosition ?? dragPosition(of: value)
                if motion.dragStartPosition == nil {
                    motion.stop()
                    motion.dragStartPosition = startPosition
                    motion.isInteracting = true
                }
                emit(at: startPosition - Double(gesture.translation.width) / Metrics.unitWidth)
            }
            .onEnded { gesture in
                let startPosition = motion.dragStartPosition ?? dragPosition(of: value)
                motion.dragStartPosition = nil
                decelerate(
                    from: startPosition - Double(gesture.translation.width) / Metrics.unitWidth,
                    velocity: -Double(gesture.velocity.width) / Metrics.unitWidth
                )
            }
    }

    // MARK: - 그리기

    private func draw(in context: inout GraphicsContext, size: CGSize, at date: Date) {
        let centerX = size.width / 2
        let tickCount = Int(((range.upperBound - range.lowerBound) * Metrics.ticksPerUnit).rounded()) + 1
        let startX = centerX - CGFloat(value - range.lowerBound) * Metrics.unitWidth

        for index in 0..<tickCount {
            let x = startX + CGFloat(index) * Metrics.tickSpacing
            guard x >= 0, x <= size.width else { continue }
            let lift = liftAmount(of: index, at: date) * proximity(of: x, to: centerX)
            let height = Metrics.tickHeight + (Metrics.liftedTickHeight - Metrics.tickHeight) * lift
            // 10칸마다와 양 끝 눈금을 밝게 그린다.
            let baseOpacity = index.isMultiple(of: Metrics.majorTickInterval) ? Metrics.majorTickOpacity : Metrics.tickOpacity
            let tick = CGRect(
                x: x - Metrics.tickWidth / 2,
                y: size.height - height,
                width: Metrics.tickWidth,
                height: height
            )
            context.fill(
                Path(roundedRect: tick, cornerRadius: Metrics.tickWidth / 2),
                with: .color(theme.colors.textOnEditor.opacity(baseOpacity + (1 - baseOpacity) * lift))
            )
        }

        if let automaticValue {
            let x = centerX + CGFloat(automaticValue - value) * Metrics.unitWidth
            let mark = CGRect(
                x: x - Metrics.markSize / 2,
                y: size.height - Metrics.markCenterHeight - Metrics.markSize / 2,
                width: Metrics.markSize,
                height: Metrics.markSize
            )
            context.fill(Path(ellipseIn: mark), with: .color(theme.colors.textOnEditorMuted))
        }

        let indicator = CGRect(
            x: centerX - Metrics.indicatorWidth / 2,
            y: size.height - Metrics.liftedTickHeight,
            width: Metrics.indicatorWidth,
            height: Metrics.liftedTickHeight
        )
        context.fill(
            Path(roundedRect: indicator, cornerRadius: Metrics.indicatorWidth / 2),
            with: .color(theme.colors.editorAccent)
        )
    }

    /// 눈금이 표시선을 지난 직후 1이고 시간이 지나면 0으로 줄어든다.
    private func liftAmount(of index: Int, at date: Date) -> CGFloat {
        guard let passedAt = passedTicks[index] else { return 0 }
        let progress = date.timeIntervalSince(passedAt) / Metrics.liftDuration
        return CGFloat(max(0, 1 - progress))
    }

    /// 표시선 바로 옆이 1이고 멀어질수록 줄어 잔상이 표시선 가까이에만 남는다.
    private func proximity(of x: CGFloat, to centerX: CGFloat) -> CGFloat {
        max(0, 1 - abs(x - centerX) / (Metrics.tickSpacing * Metrics.liftedTickSpan))
    }

    /// 손으로 움직이는 동안 표시선을 지난 눈금을 기록한다.
    private func recordPassedTicks(from oldValue: Double, to newValue: Double) {
        guard motion.isInteracting else { return }
        let oldPosition = (oldValue - range.lowerBound) * Metrics.ticksPerUnit
        let newPosition = (newValue - range.lowerBound) * Metrics.ticksPerUnit
        let first = Int(min(oldPosition, newPosition).rounded(.up))
        let last = Int(max(oldPosition, newPosition).rounded(.down))
        guard first <= last else { return }
        let now = Date()
        for index in first...last {
            passedTicks[index] = now
        }
    }

    // MARK: - 값 계산

    /// 드래그 거리를 값으로 바꿀 때 쓰는 위치. 기준값 앞뒤에 멈춤 구간만큼 간격을 벌려 둔다.
    private func dragPosition(of value: Double) -> Double {
        if abs(value - anchor) < Metrics.anchorTolerance { return anchor }
        return value > anchor ? value + Metrics.detentHalfWidth : value - Metrics.detentHalfWidth
    }

    private func adjustedValue(at position: Double) -> Double {
        if position > anchor + Metrics.detentHalfWidth { return position - Metrics.detentHalfWidth }
        if position < anchor - Metrics.detentHalfWidth { return position + Metrics.detentHalfWidth }
        return anchor
    }

    /// 위치에 맞는 값을 알린다. 범위 끝에 닿으면 false를 돌려준다.
    @discardableResult
    private func emit(at position: Double) -> Bool {
        let lower = dragPosition(of: range.lowerBound)
        let upper = dragPosition(of: range.upperBound)
        let clamped = min(max(position, lower), upper)
        let newValue = adjustedValue(at: clamped)
        // 기준값은 반올림하지 않아야 자동 보정 위에 더한 값이 정확히 0이 된다.
        onChange(newValue == anchor ? anchor : snapped(newValue))
        return clamped == position
    }

    /// 스크롤 뷰처럼 손을 뗀 속도에서 감속하며 굴러간다.
    private func decelerate(from position: Double, velocity: Double) {
        guard abs(velocity) > Metrics.minimumInertiaVelocity else {
            motion.isInteracting = false
            return
        }
        motion.inertia = Task { @MainActor in
            var position = position
            var velocity = velocity
            var lastTime = ContinuousClock.now
            while !Task.isCancelled, abs(velocity) > Metrics.minimumInertiaVelocity {
                try? await Task.sleep(for: Metrics.inertiaFrameInterval)
                guard !Task.isCancelled else { return }
                let now = ContinuousClock.now
                let elapsed = (now - lastTime) / .seconds(1)
                lastTime = now
                position += velocity * elapsed
                velocity *= pow(Metrics.decelerationRate, elapsed * 1000)
                guard emit(at: position) else { break }
            }
            motion.isInteracting = false
        }
    }

    /// 화면에 보이는 정수 값에 맞춰 자른다.
    private func snapped(_ value: Double) -> Double {
        (min(max(value, range.lowerBound), range.upperBound) * 100).rounded() / 100
    }
}

/// 드래그와 관성 상태. 바뀌어도 다시 그릴 필요가 없어 관찰하지 않는 참조 타입으로 둔다.
@MainActor
private final class PhotoEditRulerMotion {
    /// 드래그를 시작할 때의 위치. 이동 거리만큼 이 위치에서 더하고 뺀다.
    var dragStartPosition: Double?
    /// 손으로 움직이는 중이거나 관성으로 굴러가는 중이다. 이때만 잔상과 햅틱을 낸다.
    var isInteracting = false
    var inertia: Task<Void, Never>?

    func stop() {
        inertia?.cancel()
        inertia = nil
        isInteracting = false
    }
}

/// 햅틱을 낼 눈금 단위. 눈금을 하나 넘거나 기준값에 닿고 벗어날 때 바뀐다.
private struct PhotoEditRulerStep: Equatable {
    let tick: Int
    let isAnchor: Bool

    init(value: Double, anchor: Double) {
        tick = Int((value * Metrics.ticksPerUnit).rounded(.down))
        isAnchor = abs(value - anchor) < Metrics.anchorTolerance
    }
}

private enum Metrics {
    static let itemSize: CGFloat = 56
    static let iconSize: CGFloat = 20
    static let ringWidth: CGFloat = 3
    static let ringTrackOpacity: Double = 0.4

    static let height: CGFloat = 44
    /// 값 1.0을 50칸(2 단위 눈금)으로 나눈다.
    static let ticksPerUnit: Double = 50
    static let tickSpacing: CGFloat = 8
    /// 값 1.0이 차지하는 너비.
    static let unitWidth = tickSpacing * CGFloat(ticksPerUnit)
    static let majorTickInterval = 10
    static let tickWidth: CGFloat = 2
    static let tickHeight: CGFloat = 20
    /// 표시선을 막 지난 눈금과 표시선의 높이.
    static let liftedTickHeight: CGFloat = 40
    static let liftDuration: TimeInterval = 0.3
    /// 잔상이 남는 눈금 수.
    static let liftedTickSpan: CGFloat = 8
    static let tickOpacity: Double = 0.4
    static let majorTickOpacity: Double = 0.75
    static let indicatorWidth: CGFloat = 2
    static let markSize: CGFloat = 6
    static let markCenterHeight: CGFloat = 32

    /// 기준값에서 멈추는 구간의 절반. 손가락으로 8pt를 더 밀어야 벗어난다.
    static let detentHalfWidth = 8 / Double(unitWidth)
    static let anchorTolerance = 1e-9
    /// 1ms마다 속도에 곱하는 비율. 손을 뗀 속도로 약 0.2초 동안 간 만큼 더 굴러간다.
    static let decelerationRate = 0.995
    /// 값 기준 초당 속도. 이보다 느리면 관성을 멈춘다.
    static let minimumInertiaVelocity = 0.02
    static let inertiaFrameInterval: Duration = .milliseconds(8)
    static let accessibilityStep: Double = 0.1
}

#Preview("Photo Edit Adjust Controls") {
    PhotoEditAdjustControls(
        selectedItem: .adjustment(.exposure),
        value: { item in
            switch item {
            case .auto: 1
            case .adjustment(.exposure): 0.24
            case .adjustment(.highlights): -0.3
            default: 0
            }
        },
        automaticValue: { item in
            item == .adjustment(.exposure) ? 0.1 : nil
        },
        onSelect: { _ in },
        onValueChange: { _ in }
    )
    .contentMargins(.horizontal, ChalkakSpacing.lg, for: .scrollContent)
    .padding(.vertical, ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
