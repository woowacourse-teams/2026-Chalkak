import SwiftUI

struct ChalkakLogo: View {
    var body: some View {
        Text("Chalkak")
            .font(ChalkakTypography.brand)
            .tracking(-0.42)
            .foregroundStyle(ChalkakColor.textPrimary)
            .accessibilityLabel("찰칵")
    }
}

#Preview("Logo") {
    ChalkakLogo()
        .padding()
}
