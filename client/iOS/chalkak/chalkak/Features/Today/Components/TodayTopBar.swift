import SwiftUI

struct TodayTopBar: View {
    @Environment(\.chalkakTheme) private var theme
    var onOpenNotifications: () -> Void = {}

    var body: some View {
        HStack(spacing: theme.spacing.none) {
            ChalkakLogo()
            Spacer(minLength: theme.spacing.none)
            Button(action: onOpenNotifications) {
                Image("ic_notification")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(theme.colors.iconPrimary)
                    .frame(width: Metrics.iconSize, height: Metrics.iconSize)
                    .frame(width: Metrics.touchTarget, height: Metrics.touchTarget)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("알림")
            .accessibilityIdentifier("today.notifications")
        }
        .frame(height: TodayTopBarMetrics.height, alignment: .center)
        .accessibilityElement(children: .contain)
    }
}

enum TodayTopBarMetrics {
    static let height: CGFloat = 55
}

private enum Metrics {
    static let iconSize: CGFloat = 24
    static let touchTarget: CGFloat = 48
}

#Preview("Today Top Bar", traits: .sizeThatFitsLayout) {
    TodayTopBar()
        .padding(.horizontal, ChalkakTheme.light.spacing.screenHorizontal)
        .background(ChalkakTheme.light.colors.background)
        .chalkakTheme(.light)
}
