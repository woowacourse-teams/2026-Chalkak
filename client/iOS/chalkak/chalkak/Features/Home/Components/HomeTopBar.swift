import SwiftUI

struct HomeTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            ChalkakLogo()
            Spacer(minLength: theme.spacing.none)
        }
        .frame(height: HomeTopBarMetrics.contentHeight, alignment: .center)
        .padding(.bottom, HomeTopBarMetrics.bottomPadding)
        .accessibilityElement(children: .contain)
    }
}

enum HomeTopBarMetrics {
    static let contentHeight: CGFloat = 15
    static let bottomPadding: CGFloat = 15
    static let height = contentHeight + bottomPadding
}

#Preview("Home Top Bar", traits: .sizeThatFitsLayout) {
    HomeTopBar()
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
