import SwiftUI

struct ChalkakOutlinedButton: View {
    @Environment(\.chalkakColors) private var colors

    let title: String
    let action: () -> Void
    var isEnabled = true

    var body: some View {
        Button(title, action: action)
            .font(ChalkakTypography.callout)
            .padding(.horizontal, ChalkakSpacing.extraLarge)
            .padding(.vertical, Metrics.verticalPadding)
            .foregroundStyle(
                isEnabled ? colors.textPrimary : colors.textMuted
            )
            .background(colors.surfaceElevated.opacity(0.001))
            .overlay {
                RoundedRectangle(cornerRadius: ChalkakShape.button)
                    .stroke(colors.border, lineWidth: Metrics.borderWidth)
            }
            .contentShape(RoundedRectangle(cornerRadius: ChalkakShape.button))
            .buttonStyle(.plain)
            .disabled(!isEnabled)
    }
}

private enum Metrics {
    static let verticalPadding: CGFloat = 17
    static let borderWidth: CGFloat = 1
}

#Preview("Outlined Button") {
    VStack(spacing: ChalkakSpacing.large) {
        ChalkakOutlinedButton(title: "다시 그리기", action: {})
        ChalkakOutlinedButton(
            title: "다시 그리기",
            action: {},
            isEnabled: false
        )
    }
    .padding()
}
