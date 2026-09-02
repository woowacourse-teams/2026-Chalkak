import SwiftUI

struct DisplayDateHeader: View {
    @Environment(\.chalkakTheme) private var theme
    let date: Date?
    let canGoPrevious: Bool
    let canGoNext: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            navigationButton(
                systemName: "chevron.left",
                label: "이전 날짜 전시",
                isEnabled: canGoPrevious,
                action: onPrevious
            )

            Text(dateText)
                .font(theme.typography.headline)
                .fontWeight(.semibold)
                .foregroundStyle(theme.colors.textPrimary)
                .frame(maxWidth: .infinity)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)

            navigationButton(
                systemName: "chevron.right",
                label: "다음 날짜 전시",
                isEnabled: canGoNext,
                action: onNext
            )
        }
        .frame(height: Metrics.height)
    }

    private func navigationButton(
        systemName: String,
        label: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: Metrics.chevronSize, weight: .semibold))
                .foregroundStyle(
                    isEnabled ? theme.colors.iconPrimary : theme.colors.textInactive
                )
                .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(label)
    }

    private var dateText: String {
        guard let date else { return "" }
        return DisplayDateFormatter.shared.string(from: date)
    }
}

private enum DisplayDateFormatter {
    static let shared: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy년 M월 d일"
        return formatter
    }()
}

private enum Metrics {
    static let height: CGFloat = 52
    static let chevronSize: CGFloat = 17
    static let touchSize: CGFloat = 44
}

#Preview("Date Header", traits: .sizeThatFitsLayout) {
    VStack(spacing: 24) {
        DisplayDateHeader(
            date: DisplayPreviewData.latestState.selectedDate,
            canGoPrevious: true,
            canGoNext: false,
            onPrevious: {},
            onNext: {}
        )
        DisplayDateHeader(
            date: DisplayPreviewData.archiveState.selectedDate,
            canGoPrevious: false,
            canGoNext: true,
            onPrevious: {},
            onNext: {}
        )
    }
    .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
    .background(ChalkakTheme.light.colors.background)
    .chalkakTheme(.light)
}
