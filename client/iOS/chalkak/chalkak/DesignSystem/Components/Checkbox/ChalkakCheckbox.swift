import SwiftUI

struct ChalkakCheckbox: View {
    let isChecked: Bool

    var body: some View {
        Image(isChecked ? "ic_checkbox_selected" : "ic_checkbox_unselected")
            .renderingMode(.original)
            .frame(width: Metrics.size, height: Metrics.size)
            .accessibilityHidden(true)
    }
}

private enum Metrics {
    static let size: CGFloat = 22
}

#Preview("Checkbox") {
    HStack(spacing: ChalkakSpacing.lg) {
        ChalkakCheckbox(isChecked: true)
        ChalkakCheckbox(isChecked: false)
    }
    .padding()
}
