import SwiftUI

struct HomeTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    var onOpenNotifications: () -> Void = {}
    var hasUnreadNotifications = false

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            ChalkakLogo()
            Spacer(minLength: theme.spacing.none)
            Button(action: onOpenNotifications) {
                ZStack(alignment: .topTrailing) {
                    Image("ic_notification")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(theme.colors.iconPrimary)
                        .frame(width: Metrics.iconSize, height: Metrics.iconSize)

                    if hasUnreadNotifications {
                        Circle()
                            .fill(theme.colors.inputCursor)
                            .frame(width: Metrics.unreadDotSize, height: Metrics.unreadDotSize)
                            .offset(x: Metrics.unreadDotOffset, y: -Metrics.unreadDotOffset)
                            .accessibilityHidden(true)
                    }
                }
                .frame(width: Metrics.touchTarget, height: Metrics.touchTarget)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("알림")
            .accessibilityValue(hasUnreadNotifications ? "읽지 않은 알림 있음" : "읽지 않은 알림 없음")
            .accessibilityIdentifier("home.notifications")
        }
        .frame(height: HomeTopBarMetrics.height, alignment: .center)
        .accessibilityElement(children: .contain)
    }
}

enum HomeTopBarMetrics {
    static let height: CGFloat = 55
}

private enum Metrics {
    static let iconSize: CGFloat = 24
    static let unreadDotSize: CGFloat = 7
    static let unreadDotOffset: CGFloat = 2
    static let touchTarget: CGFloat = 48
}

#Preview("Home Top Bar", traits: .sizeThatFitsLayout) {
    HomeTopBar()
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
