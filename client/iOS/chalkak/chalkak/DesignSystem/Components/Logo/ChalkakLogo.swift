import SwiftUI

struct ChalkakLogo: View {
    @Environment(\.chalkakTheme) private var theme
    var body: some View {
        Text("Chalkak")
            .font(theme.typography.brand)
            .tracking(-0.42)
            .foregroundStyle(theme.colors.textPrimary)
            .accessibilityLabel("찰칵")
    }
}

#Preview("Logo") {
    ChalkakLogo()
        .padding()
}
