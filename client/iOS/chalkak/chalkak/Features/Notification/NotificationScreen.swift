import SwiftUI

struct NotificationScreen: View {
    @Environment(\.chalkakTheme) private var theme

    let onBackClick: () -> Void
    var notifications: [NotificationItem] = NotificationItem.sample

    var body: some View {
        VStack(spacing: 0) {
            topBar

            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(notifications) { notification in
                        NotificationListItem(item: notification)

                        Rectangle()
                            .fill(theme.colors.border)
                            .frame(height: Metrics.dividerHeight)
                    }
                }
                .padding(.horizontal, theme.spacing.screenHorizontal)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }

    private var topBar: some View {
        HStack(spacing: 0) {
            Button(action: onBackClick) {
                Image(systemName: "arrow.left")
                    .font(.system(size: Metrics.backIconSize))
                    .foregroundStyle(theme.colors.iconPrimary)
                    .padding(.leading, Metrics.backIconLeadingPadding)
                    .frame(
                        width: Metrics.actionSize,
                        height: Metrics.actionSize,
                        alignment: .leading
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("뒤로 가기")
            .accessibilityIdentifier("notification.back")

            Text("알림")
                .font(theme.typography.headline)
                .foregroundStyle(theme.colors.textPrimary)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("notification.title")

            Color.clear
                .frame(width: Metrics.actionSize, height: Metrics.actionSize)
                .accessibilityHidden(true)
        }
        .frame(height: Metrics.topBarHeight)
    }
}

struct NotificationItem: Identifiable {
    let id: String
    let title: String
    let timeText: String
    let isUnread: Bool
    let thumbnailName: String?

    // Android 알림 화면과 동일한 임시 UI 데이터. 서버 알림함이나 주제 알림의 정책 구현이 아니다.
    static let sample: [NotificationItem] = [
        NotificationItem(
            id: "today-topic-2026-09-22",
            title: "9월 22일 오늘의 주제를 확인해보세요.",
            timeText: "18:00",
            isUnread: true,
            thumbnailName: nil
        ),
        NotificationItem(
            id: "today-topic-2026-09-21",
            title: "9월 21일 오늘의 주제를 확인해보세요.",
            timeText: "어제 21:10",
            isUnread: true,
            thumbnailName: "preview_photo"
        )
    ]
}

private struct NotificationListItem: View {
    @Environment(\.chalkakTheme) private var theme
    let item: NotificationItem

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Circle()
                .fill(item.isUnread ? theme.colors.inputCursor : .clear)
                .frame(width: theme.spacing.sm, height: theme.spacing.sm)
                .frame(width: theme.spacing.md, alignment: .leading)
                .padding(.top, theme.spacing.xs)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: theme.spacing.md) {
                Text(item.title)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textPrimary)
                    .lineLimit(2)

                Text(item.timeText)
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textInactive)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, theme.spacing.md)

            if let thumbnailName = item.thumbnailName {
                Image(thumbnailName)
                    .resizable()
                    .scaledToFill()
                    .frame(width: Metrics.thumbnailSize, height: Metrics.thumbnailSize)
                    .clipped()
                    .padding(.leading, theme.spacing.lg)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, theme.spacing.xl)
        .accessibilityElement(children: .contain)
        .accessibilityValue(item.isUnread ? "읽지 않음" : "읽음")
    }
}

private enum Metrics {
    static let topBarHeight: CGFloat = 72
    static let actionSize: CGFloat = 56
    static let backIconSize: CGFloat = 24
    static let backIconLeadingPadding: CGFloat = 20
    static let thumbnailSize: CGFloat = 44
    static let dividerHeight: CGFloat = 1
}

#Preview("Notification") {
    NotificationScreen(onBackClick: {})
        .chalkakTheme(.light)
}

#Preview("Empty Notification") {
    NotificationScreen(onBackClick: {}, notifications: [])
        .chalkakTheme(.light)
}
