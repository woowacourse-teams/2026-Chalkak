import SwiftUI

struct FeedTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "arrow.backward")
                    .font(.system(size: Metrics.iconSize))
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: Metrics.touchSize, height: Metrics.touchSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")

            Spacer(minLength: 0)
        }
    }
}

private enum Metrics {
    static let iconSize: CGFloat = 24
    static let touchSize: CGFloat = 44
}

#Preview("Feed Top Bar", traits: .sizeThatFitsLayout) {
    FeedTopBar(onBack: {})
        .padding(.horizontal, 8)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
