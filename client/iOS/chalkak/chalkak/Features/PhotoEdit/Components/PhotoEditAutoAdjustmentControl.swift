import SwiftUI

/// 조정 화면의 자동 보정 버튼과 강도 눈금자. 버튼으로 켜고 끄며, 눈금자를 밀어 강도(0...1)를 바꾼다.
struct PhotoEditAutoAdjustmentControl: View {
    @Environment(\.chalkakTheme) private var theme

    let intensity: Double
    let onToggle: () -> Void
    let onIntensityChange: (Double) -> Void

    var body: some View {
        VStack(spacing: theme.spacing.md) {
            Text("자동 보정 \(percent)")
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textOnEditorMuted)
                .monospacedDigit()
                .accessibilityHidden(true)

            toggleButton

            PhotoEditValueRuler(value: intensity, onChange: onIntensityChange)
                .accessibilityLabel("자동 보정 강도")
                .accessibilityValue("\(percent)")
                .accessibilityIdentifier("photoEdit.autoAdjustment.intensity")
        }
    }

    private var percent: Int {
        Int((intensity * 100).rounded())
    }

    private var toggleButton: some View {
        let isOn = intensity > 0

        return Button(action: onToggle) {
            Image(systemName: "wand.and.stars")
                .font(.system(size: Metrics.iconSize))
                .foregroundStyle(theme.colors.textOnEditor)
                .frame(width: Metrics.buttonSize, height: Metrics.buttonSize)
                .background(theme.colors.editorControl, in: Circle())
                .overlay {
                    // 테두리를 강도만큼 채워 지금 강도를 보여 준다.
                    Circle()
                        .inset(by: Metrics.ringWidth / 2)
                        .stroke(theme.colors.textOnEditorMuted.opacity(Metrics.ringTrackOpacity), lineWidth: Metrics.ringWidth)
                    Circle()
                        .inset(by: Metrics.ringWidth / 2)
                        .trim(from: 0, to: CGFloat(intensity))
                        .stroke(theme.colors.editorAccent, style: StrokeStyle(lineWidth: Metrics.ringWidth, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("자동 보정")
        .accessibilityAddTraits(isOn ? .isSelected : [])
        .accessibilityIdentifier("photoEdit.autoAdjustment")
    }
}

/// 사진 앱의 조정 눈금자처럼 가운데 표시선 아래로 눈금을 밀어 값을 고른다. 왼쪽으로 밀면 값이 커진다.
private struct PhotoEditValueRuler: View {
    @Environment(\.chalkakTheme) private var theme
    /// 드래그를 시작할 때의 값. 이동 거리만큼 이 값에서 더하고 뺀다.
    @State private var dragStartValue: Double?

    let value: Double
    let onChange: (Double) -> Void

    var body: some View {
        Canvas { context, size in
            let centerX = size.width / 2
            let contentWidth = CGFloat(Metrics.tickCount - 1) * Metrics.tickSpacing
            let startX = centerX - CGFloat(value) * contentWidth

            for index in 0..<Metrics.tickCount {
                let x = startX + CGFloat(index) * Metrics.tickSpacing
                guard x >= 0, x <= size.width else { continue }
                let isMajor = index.isMultiple(of: Metrics.majorTickInterval)
                let height = isMajor ? Metrics.majorTickHeight : Metrics.tickHeight
                let tick = CGRect(
                    x: x - Metrics.tickWidth / 2,
                    y: (size.height - height) / 2,
                    width: Metrics.tickWidth,
                    height: height
                )
                let opacity = isMajor ? Metrics.majorTickOpacity : Metrics.tickOpacity
                context.fill(Path(roundedRect: tick, cornerRadius: Metrics.tickWidth / 2), with: .color(theme.colors.textOnEditor.opacity(opacity)))
            }

            let indicator = CGRect(
                x: centerX - Metrics.indicatorWidth / 2,
                y: 0,
                width: Metrics.indicatorWidth,
                height: size.height
            )
            context.fill(Path(roundedRect: indicator, cornerRadius: Metrics.indicatorWidth / 2), with: .color(theme.colors.editorAccent))
        }
        .frame(height: Metrics.height)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { gesture in
                    let startValue = dragStartValue ?? value
                    dragStartValue = startValue
                    let contentWidth = CGFloat(Metrics.tickCount - 1) * Metrics.tickSpacing
                    let newValue = startValue - Double(gesture.translation.width / contentWidth)
                    onChange(Self.snapped(newValue))
                }
                .onEnded { _ in
                    dragStartValue = nil
                }
        )
        .accessibilityElement()
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                onChange(Self.snapped(value + Metrics.accessibilityStep))
            case .decrement:
                onChange(Self.snapped(value - Metrics.accessibilityStep))
            @unknown default:
                break
            }
        }
    }

    /// 화면에 보이는 정수 퍼센트에 맞춰 값을 자른다.
    private static func snapped(_ value: Double) -> Double {
        (min(max(value, 0), 1) * 100).rounded() / 100
    }
}

private enum Metrics {
    static let buttonSize: CGFloat = 56
    static let iconSize: CGFloat = 22
    static let ringWidth: CGFloat = 3
    static let ringTrackOpacity: Double = 0.4

    static let height: CGFloat = 44
    /// 0부터 100까지 2 단위 눈금.
    static let tickCount = 51
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

#Preview("Photo Edit Auto Adjustment Control") {
    PhotoEditAutoAdjustmentControl(intensity: 0.6, onToggle: {}, onIntensityChange: { _ in })
        .padding(.vertical, ChalkakSpacing.lg)
        .background(ChalkakColors.light.editorBackground)
        .chalkakTheme(.light)
}
