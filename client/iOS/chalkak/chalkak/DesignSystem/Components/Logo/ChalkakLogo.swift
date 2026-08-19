import SwiftUI

struct ChalkakLogo: View {
    @Environment(\.chalkakColors) private var colors

    var body: some View {
        Text("Chalkak")
            .font(ChalkakTypography.brand)
            .tracking(-0.42)
            .foregroundStyle(colors.textPrimary)
            .accessibilityLabel("찰칵")
    }
}

#Preview("Logo") {
    ChalkakLogo()
        .padding()
}
