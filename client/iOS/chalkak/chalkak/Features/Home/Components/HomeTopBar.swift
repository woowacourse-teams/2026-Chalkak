import SwiftUI

struct HomeTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    var onOpenNotifications: () -> Void = {}

    var body: some View {
        HStack(spacing: 0) {
            ChalkakLogo()
                .offset(y: Metrics.logoOpticalCenterOffset)

            Spacer(minLength: 0)

            Button(action: onOpenNotifications) {
                Image("ic_notification")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: Metrics.notificationIconSize, height: Metrics.notificationIconSize)
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: Metrics.notificationTouchSize, height: Metrics.notificationTouchSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("알림")
            .accessibilityIdentifier("home.notifications")
        }
        .frame(height: Metrics.height)
    }
}

private enum Metrics {
    static let height: CGFloat = 55
    static let notificationTouchSize: CGFloat = 44
    static let notificationIconSize: CGFloat = 24
    static let logoOpticalCenterOffset: CGFloat = 5
}

#Preview("Home Top Bar", traits: .sizeThatFitsLayout) {
    HomeTopBar()
        .padding(.horizontal, ChalkakSpacing.screenHorizontal)
        .chalkakTheme(.light)
}
