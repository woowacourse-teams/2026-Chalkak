import SwiftUI

struct NotificationTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    let onBackClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            ChalkakNavigationButton(kind: .back, action: onBackClick)
                .accessibilityIdentifier("notification.back")

            Text("알림")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("notification.title")

            Color.clear
                .frame(
                    width: ChalkakNavigationButton.diameter,
                    height: ChalkakNavigationButton.diameter
                )
                .accessibilityHidden(true)
        }
        .padding(.horizontal, Metrics.horizontalPadding)
        .frame(height: Metrics.height)
    }
}

private enum Metrics {
    static let height: CGFloat = 72
    static let horizontalPadding: CGFloat = 20
}

#Preview("Notification Top Bar") {
    NotificationTopBar(onBackClick: {})
        .chalkakTheme(.light)
}
