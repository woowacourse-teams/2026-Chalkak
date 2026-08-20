import SwiftUI

struct ChalkakOutlinedButton: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    let action: () -> Void
    var isEnabled = true

    var body: some View {
        Button(title, action: action)
            .font(theme.typography.callout)
            .padding(.horizontal, theme.spacing.xl)
            .padding(.vertical, Metrics.verticalPadding)
            .foregroundStyle(
                isEnabled ? theme.colors.textPrimary : theme.colors.textMuted
            )
            .background(theme.colors.surfaceElevated.opacity(0.001))
            .overlay {
                RoundedRectangle(cornerRadius: theme.shapes.button)
                    .stroke(theme.colors.border, lineWidth: Metrics.borderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: theme.shapes.button))
            .buttonStyle(.plain)
            .disabled(!isEnabled)
    }
}

private enum Metrics {
    static let verticalPadding: CGFloat = 17
    static let borderWidth: CGFloat = 1
}

#Preview("Outlined Button") {
    VStack(spacing: ChalkakSpacing.lg) {
        ChalkakOutlinedButton(title: "다시 그리기", action: {})
        ChalkakOutlinedButton(
            title: "다시 그리기",
            action: {},
            isEnabled: false
        )
    }
    .padding()
}
