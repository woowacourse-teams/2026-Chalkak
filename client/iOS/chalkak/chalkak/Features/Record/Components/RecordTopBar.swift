import SwiftUI

/// 달력 상단의 연월 표시 + 월 이동 컨트롤.
/// Android `RecordTopBar`(padding top 20 / start 8, 화살표 48 터치·24 아이콘)와 맞춘다.
struct RecordTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    let month: RecordMonth
    let canGoPrevious: Bool
    let canGoNext: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            arrowButton(
                systemName: "chevron.left",
                label: "이전 달",
                isEnabled: canGoPrevious,
                action: onPrevious
            )

            Text(month.formatted)
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)

            arrowButton(
                systemName: "chevron.right",
                label: "다음 달",
                isEnabled: canGoNext,
                action: onNext
            )

            Spacer(minLength: 0)
        }
        .padding(.leading, Metrics.leadingPadding)
        .padding(.top, Metrics.topPadding)
    }

    private func arrowButton(
        systemName: String,
        label: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: Metrics.chevronSize, weight: .medium))
                .foregroundStyle(
                    theme.colors.iconSecondary.opacity(isEnabled ? 1 : Metrics.disabledOpacity)
                )
                .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(label)
    }
}

private enum Metrics {
    static let leadingPadding: CGFloat = 8
    static let topPadding: CGFloat = 20
    static let touchSize: CGFloat = 48
    static let chevronSize: CGFloat = 18
    static let disabledOpacity: CGFloat = 0.35
}

#Preview("Record Top Bar", traits: .sizeThatFitsLayout) {
    VStack(spacing: 24) {
        RecordTopBar(
            month: .current(),
            canGoPrevious: true,
            canGoNext: false,
            onPrevious: {},
            onNext: {}
        )
        RecordTopBar(
            month: RecordMonth(year: 2026, month: 8),
            canGoPrevious: true,
            canGoNext: true,
            onPrevious: {},
            onNext: {}
        )
    }
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
