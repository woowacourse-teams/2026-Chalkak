import SwiftUI

struct ChalkakButton: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    let action: () -> Void
    var isEnabled = true
    var fillsWidth = false

    var body: some View {
        Button(title, action: action)
            .font(theme.typography.callout)
            .padding(.horizontal, theme.spacing.xl)
            .padding(.vertical, Metrics.verticalPadding)
            .frame(maxWidth: fillsWidth ? .infinity : nil)
            .foregroundStyle(
                isEnabled ? theme.colors.onActionPrimary : theme.colors.textPrimary
            )
            .background(
                isEnabled
                    ? theme.colors.actionPrimary
                    : theme.colors.actionPrimary.opacity(0.12),
                in: RoundedRectangle(cornerRadius: theme.shapes.button)
            )
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.button))
            .buttonStyle(.plain)
            .disabled(!isEnabled)
    }
}

private enum Metrics {
    static let verticalPadding: CGFloat = 17
}

#Preview("Filled Button") {
    VStack(spacing: ChalkakSpacing.lg) {
        ChalkakButton(title: "전시하기", action: {})
        ChalkakButton(title: "전시하기", action: {}, isEnabled: false)
    }
    .padding()
}
