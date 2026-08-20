import SwiftUI

struct ChalkakCheckbox: View {
    let isChecked: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: ChalkakShape.small)
                .fill(isChecked ? ChalkakColor.actionPrimary : Color.clear)

            RoundedRectangle(cornerRadius: ChalkakShape.small)
                .stroke(
                    isChecked ? ChalkakColor.actionPrimary : ChalkakColor.border,
                    lineWidth: Metrics.borderWidth
                )

            if isChecked {
                Image(systemName: "checkmark")
                    .font(.system(size: Metrics.iconSize, weight: .bold))
                    .foregroundStyle(ChalkakColor.onActionPrimary)
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
