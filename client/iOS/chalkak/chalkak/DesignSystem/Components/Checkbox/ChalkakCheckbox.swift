import SwiftUI

struct ChalkakCheckbox: View {
    @Environment(\.chalkakColors) private var colors

    let isChecked: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: ChalkakShape.small)
                .fill(isChecked ? colors.actionPrimary : Color.clear)

            RoundedRectangle(cornerRadius: ChalkakShape.small)
                .stroke(
                    isChecked ? colors.actionPrimary : colors.border,
                    lineWidth: Metrics.borderWidth
                )

            if isChecked {
                Image(systemName: "checkmark")
                    .font(.system(size: Metrics.iconSize, weight: .bold))
                    .foregroundStyle(colors.onActionPrimary)
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
