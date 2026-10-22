import SwiftUI

struct AIReviewStartSheet: View {
    @Environment(\.chalkakTheme) private var theme

    let onStart: () -> Void
    var durationNote = "결과가 나오기까지 몇 분 정도 소요될 수 있어요"
    var completionNote = "분석이 끝나면 알림으로 알려드릴게요"
    var completionSymbol = "bell"
    var message: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            Text("AI 피드백을 시작할까요?")
                .font(theme.typography.title3)
                .foregroundStyle(theme.colors.textPrimary)

            VStack(alignment: .leading, spacing: theme.spacing.lg) {
                note("clock", durationNote)
                note("iphone", "앱을 나가도 분석은 계속되니 걱정마세요")
                note(completionSymbol, completionNote)
            }
            .padding(theme.spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.colors.background, in: RoundedRectangle(cornerRadius: theme.shapes.large))

            if let message {
                Text(message)
                    .font(theme.typography.footnote)
                    .foregroundStyle(theme.colors.textSecondary)
            }

            ChalkakButton(title: "분석 시작하기", action: onStart, fillsWidth: true)
        }
        .padding(.horizontal, theme.spacing.xl)
        .padding(.top, theme.spacing.xxl)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(ChalkakPlatformAppearance.usesLiquidGlass ? Color.clear : theme.colors.surfaceElevated)
    }

    private func note(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: theme.spacing.md) {
            Image(systemName: symbol)
                .font(.system(size: Metrics.noteIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .frame(width: Metrics.noteCircleSize, height: Metrics.noteCircleSize)
                .background(theme.colors.surfaceElevated, in: Circle())
                .accessibilityHidden(true)
            Text(text)
                .font(theme.typography.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(theme.colors.textPrimary)
        }
    }
    private enum Metrics {
        static let noteIconSize: CGFloat = 17
        static let noteCircleSize: CGFloat = 36
    }
}

