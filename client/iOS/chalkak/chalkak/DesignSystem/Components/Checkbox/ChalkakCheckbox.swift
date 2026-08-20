import SwiftUI

struct ChalkakCheckbox: View {
    @Environment(\.chalkakTheme) private var theme
    let isChecked: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: theme.shapes.small)
                .fill(isChecked ? theme.colors.actionPrimary : Color.clear)

            RoundedRectangle(cornerRadius: theme.shapes.small)
                .stroke(
                    isChecked ? theme.colors.actionPrimary : theme.colors.border,
                    lineWidth: Metrics.borderWidth
                )

            if isChecked {
                Image(systemName: "checkmark")
                    .font(.system(size: Metrics.iconSize, weight: .bold))
                    .foregroundStyle(theme.colors.onActionPrimary)
            }
        }
        .frame(width: Metrics.size, height: Metrics.size)
        .accessibilityHidden(true)
    }
}

private enum Metrics {
    static let size: CGFloat = 22
    static let iconSize: CGFloat = 11
    static let borderWidth: CGFloat = 1
}

#Preview("Checkbox") {
    HStack(spacing: ChalkakSpacing.lg) {
        ChalkakCheckbox(isChecked: true)
        ChalkakCheckbox(isChecked: false)
    }
    .padding()
}
