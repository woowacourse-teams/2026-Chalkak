import SwiftUI

struct ChalkakButton: View {
    @Environment(\.chalkakColors) private var colors

    let title: String
    let action: () -> Void
    var isEnabled = true

    var body: some View {
        Button(title, action: action)
            .font(ChalkakTypography.callout)
            .padding(.horizontal, ChalkakSpacing.xl)
            .padding(.vertical, Metrics.verticalPadding)
            .foregroundStyle(
                isEnabled ? colors.onActionPrimary : colors.textPrimary
            )
            .background(
                isEnabled
                    ? colors.actionPrimary
                    : colors.actionPrimary.opacity(0.12),
                in: RoundedRectangle(cornerRadius: ChalkakShape.button)
            )
            .contentShape(RoundedRectangle(cornerRadius: ChalkakShape.button))
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
