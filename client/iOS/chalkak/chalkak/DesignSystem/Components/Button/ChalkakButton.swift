import SwiftUI

struct ChalkakButton: View {
    @Environment(\.chalkakTheme) private var theme
    let title: String
    let action: () -> Void
    var isEnabled = true
    var fillsWidth = false
    var verticalPadding: CGFloat = 17

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(theme.typography.callout)
                .frame(maxWidth: fillsWidth ? .infinity : nil)
                .padding(.horizontal, theme.spacing.xl)
                .padding(.vertical, verticalPadding)
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
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

#Preview("Filled Button") {
    VStack(spacing: ChalkakSpacing.lg) {
        ChalkakButton(title: "전시하기", action: {})
        ChalkakButton(title: "전시하기", action: {}, isEnabled: false)
    }
    .padding()
}
