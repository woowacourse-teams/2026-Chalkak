import SwiftUI

/// 조정 화면의 항목 목록과 눈금자. 항목을 고르고 눈금자를 밀어 값을 바꾼다.
///
/// 각 항목 테두리는 값만큼 채워진다. 양수는 시계 방향, 음수는 반시계 방향으로 채운다.
struct PhotoEditAdjustControls: View {
    @Environment(\.chalkakTheme) private var theme

    let selectedItem: PhotoEditAdjustItem
    let value: (PhotoEditAdjustItem) -> Double
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

            PhotoEditValueRuler(value: value(selectedItem), range: selectedItem.range, onChange: onValueChange)
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
private struct PhotoEditValueRuler: View {
    @Environment(\.chalkakTheme) private var theme
    /// 드래그를 시작할 때의 값. 이동 거리만큼 이 값에서 더하고 뺀다.
    @State private var dragStartValue: Double?

    let value: Double
    let range: ClosedRange<Double>
    let onChange: (Double) -> Void

    var body: some View {
        Canvas { context, size in
            let centerX = size.width / 2
            let tickCount = Int(((range.upperBound - range.lowerBound) * Metrics.ticksPerUnit).rounded()) + 1
            let startX = centerX - CGFloat((value - range.lowerBound) * Metrics.ticksPerUnit) * Metrics.tickSpacing

            for index in 0..<tickCount {
                let x = startX + CGFloat(index) * Metrics.tickSpacing
                guard x >= 0, x <= size.width else { continue }
                // 0과 10 단위 눈금을 길게 그린다.
                let isMajor = index.isMultiple(of: Metrics.majorTickInterval)
                let height = isMajor ? Metrics.majorTickHeight : Metrics.tickHeight
                let tick = CGRect(
                    x: x - Metrics.tickWidth / 2,
                    y: (size.height - height) / 2,
                    width: Metrics.tickWidth,
                    height: height
                )
                let opacity = isMajor ? Metrics.majorTickOpacity : Metrics.tickOpacity
                context.fill(
                    Path(roundedRect: tick, cornerRadius: Metrics.tickWidth / 2),
                    with: .color(theme.colors.textOnEditor.opacity(opacity))
                )
            }

            let indicator = CGRect(
                x: centerX - Metrics.indicatorWidth / 2,
                y: 0,
                width: Metrics.indicatorWidth,
                height: size.height
            )
            context.fill(
                Path(roundedRect: indicator, cornerRadius: Metrics.indicatorWidth / 2),
                with: .color(theme.colors.editorAccent)
            )
        }
        .frame(height: Metrics.height)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { gesture in
                    let startValue = dragStartValue ?? value
                    dragStartValue = startValue
                    let distance = Double(gesture.translation.width / Metrics.tickSpacing) / Metrics.ticksPerUnit
                    onChange(snapped(startValue - distance))
                }
                .onEnded { _ in
                    dragStartValue = nil
                }
        )
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

    /// 화면에 보이는 정수 값에 맞춰 자른다.
    private func snapped(_ value: Double) -> Double {
        (min(max(value, range.lowerBound), range.upperBound) * 100).rounded() / 100
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
    static let majorTickInterval = 5
    static let tickWidth: CGFloat = 1.5
    static let tickHeight: CGFloat = 20
    static let majorTickHeight: CGFloat = 26
    static let tickOpacity: Double = 0.35
    static let majorTickOpacity: Double = 0.6
    static let indicatorWidth: CGFloat = 2
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
        onSelect: { _ in },
        onValueChange: { _ in }
    )
    .contentMargins(.horizontal, ChalkakSpacing.lg, for: .scrollContent)
    .padding(.vertical, ChalkakSpacing.lg)
    .background(ChalkakColors.light.editorBackground)
    .chalkakTheme(.light)
}
