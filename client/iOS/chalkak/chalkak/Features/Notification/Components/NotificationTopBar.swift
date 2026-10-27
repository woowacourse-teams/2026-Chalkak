import SwiftUI

struct NotificationTopBar: View {
    @Environment(\.chalkakTheme) private var theme

    var isMarkAllReadEnabled = false
    let onBackClick: () -> Void
    /// 알림함에서만 전달하며, 없으면 모두 읽음 버튼을 표시하지 않는다.
    var onMarkAllRead: (() -> Void)?

    var body: some View {
        ZStack {
            Text("알림")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .padding(.horizontal, ChalkakNavigationButton.diameter)
                .accessibilityIdentifier("notification.title")

            HStack(spacing: 0) {
                ChalkakNavigationButton(kind: .back, action: onBackClick)
                    .accessibilityIdentifier("notification.back")

                Spacer(minLength: 0)

                if let onMarkAllRead {
                    markAllReadButton(action: onMarkAllRead)
                }
            }
        }
        .padding(.horizontal, Metrics.horizontalPadding)
        .frame(height: Metrics.height)
    }

    private func markAllReadButton(action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("모두 읽음")
                .font(theme.typography.subheadline)
                .foregroundStyle(
                    isMarkAllReadEnabled ? theme.colors.textPrimary : theme.colors.textInactive
                )
                .frame(minHeight: ChalkakNavigationButton.diameter)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isMarkAllReadEnabled)
        .accessibilityIdentifier("notification.markAllRead")
    }
}

private enum Metrics {
    static let height: CGFloat = 72
    static let horizontalPadding: CGFloat = 20
}

#Preview("Notification Top Bar") {
    NotificationTopBar(isMarkAllReadEnabled: true, onBackClick: {}, onMarkAllRead: {})
        .chalkakTheme(.light)
}
