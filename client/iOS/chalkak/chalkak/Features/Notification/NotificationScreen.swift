import SwiftUI

struct NotificationScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: NotificationInboxViewModel

    let onBackClick: () -> Void
    let onSelect: (NotificationItem) -> Void

    var body: some View {
        VStack(spacing: 0) {
            NotificationTopBar(
                isMarkAllReadEnabled: viewModel.canMarkAllRead,
                onBackClick: onBackClick,
                onMarkAllRead: {
                    Task { await viewModel.markAllRead() }
                }
            )

            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .chalkakToast(viewModel.message)
        .task {
            await viewModel.refresh()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.status {
        case .loading:
            NotificationLoadingView()
        case .failed:
            NotificationFailureView {
                Task { await viewModel.refresh() }
            }
        case .content:
            if viewModel.items.isEmpty {
                NotificationEmptyView()
            } else {
                NotificationListView(
                    items: viewModel.items,
                    isLoadingMore: viewModel.isLoadingMore,
                    hasNext: viewModel.hasNext,
                    onSelect: onSelect,
                    onLoadMore: {
                        Task { await viewModel.loadNextPage() }
                    },
                    onRetryLoadMore: {
                        Task { await viewModel.loadNextPage() }
                    }
                )
            }
        }
    }
}

private struct NotificationListView: View {
    @Environment(\.chalkakTheme) private var theme
    let items: [NotificationItem]
    let isLoadingMore: Bool
    let hasNext: Bool
    let onSelect: (NotificationItem) -> Void
    let onLoadMore: () -> Void
    let onRetryLoadMore: () -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(items) { notification in
                    Button {
                        onSelect(notification)
                    } label: {
                        NotificationListItem(item: notification)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("notification.item.\(notification.id)")

                    Rectangle()
                        .fill(theme.colors.border)
                        .frame(height: Metrics.dividerHeight)
                }

                if hasNext {
                    NotificationPaginationView(
                        isLoading: isLoadingMore,
                        onRetry: onRetryLoadMore
                    )
                    .onAppear(perform: onLoadMore)
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
        }
    }
}

private struct NotificationListItem: View {
    @Environment(\.chalkakTheme) private var theme
    let item: NotificationItem

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Circle()
                .fill(item.readAt == nil ? theme.colors.inputCursor : .clear)
                .frame(width: theme.spacing.sm, height: theme.spacing.sm)
                .frame(width: theme.spacing.md, alignment: .leading)
                .padding(.top, theme.spacing.xs)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: theme.spacing.sm) {
                Text(item.title)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textPrimary)
                    .lineLimit(2)

                if item.body.isEmpty == false {
                    Text(item.body)
                        .font(theme.typography.caption)
                        .foregroundStyle(theme.colors.textSecondary)
                        .lineLimit(2)
                }

                Text(NotificationDateFormatter.string(from: item.createdAt))
                    .font(theme.typography.caption)
                    .foregroundStyle(theme.colors.textInactive)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, theme.spacing.md)

            if item.thumbnailURL != nil {
                ChalkakImage(
                    source: .remote(item.thumbnailURL),
                    contentDescription: nil
                )
                .frame(width: Metrics.thumbnailSize, height: Metrics.thumbnailSize)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.thumbnailCornerRadius))
                .padding(.leading, theme.spacing.lg)
                .accessibilityHidden(true)
            }
        }
        .padding(.vertical, theme.spacing.xl)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.title), \(item.body)")
        .accessibilityValue(item.readAt == nil ? "읽지 않음" : "읽음")
    }
}

private struct NotificationLoadingView: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        VStack(spacing: theme.spacing.lg) {
            ProgressView()
                .tint(theme.colors.actionPrimary)
                .accessibilityIdentifier("notification.loading")

            Text("알림을 불러오는 중이에요")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct NotificationEmptyView: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.sm) {
                Image(systemName: "bell")
                    .font(.system(size: Metrics.emptyIconSize))
                    .foregroundStyle(theme.colors.iconSecondary)
                    .accessibilityHidden(true)

                Text("아직 알림이 없어요")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.textSecondary)

                Text("승인이나 반려 소식이 생기면 여기에 모아둘게요")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textMuted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, Metrics.emptyTopPadding)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("notification.empty")
        }
    }
}

private struct NotificationFailureView: View {
    @Environment(\.chalkakTheme) private var theme
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: theme.spacing.xl) {
            Text("알림을 불러오지 못했어요")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            Button(action: onRetry) {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .frame(width: Metrics.retryButtonSize, height: Metrics.retryButtonSize)
                    .background(theme.colors.actionPrimary, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("알림 다시 불러오기")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .accessibilityIdentifier("notification.failed")
    }
}

private struct NotificationPaginationView: View {
    @Environment(\.chalkakTheme) private var theme
    let isLoading: Bool
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: theme.spacing.md) {
            if isLoading {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("notification.loadingMore")
            } else {
                Button(action: onRetry) {
                    Label("더 불러오기", systemImage: "arrow.clockwise")
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("notification.loadMore.retry")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, theme.spacing.xl)
    }
}

private enum NotificationDateFormatter {
    static func string(from date: Date) -> String {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")

        if calendar.isDateInToday(date) {
            formatter.dateFormat = "HH:mm"
            return formatter.string(from: date)
        }
        if calendar.isDateInYesterday(date) {
            formatter.dateFormat = "HH:mm"
            return "어제 \(formatter.string(from: date))"
        }

        formatter.dateFormat = "M월 d일 HH:mm"
        return formatter.string(from: date)
    }
}

private enum Metrics {
    static let thumbnailSize: CGFloat = 44
    static let thumbnailCornerRadius: CGFloat = 6
    static let dividerHeight: CGFloat = 1
    static let emptyIconSize: CGFloat = 34
    static let emptyTopPadding: CGFloat = 144
    static let retryIconSize: CGFloat = 20
    static let retryButtonSize: CGFloat = 48
}

#Preview("Notification") {
    NotificationScreen(
        viewModel: NotificationInboxViewModel(
            loadPage: { page in
                NotificationPage(
                    currentPage: page,
                    pageSize: 20,
                    hasNext: false,
                    items: [
                        NotificationItem(
                            id: UUID().uuidString,
                            type: "POST_APPROVED",
                            sourceType: "POST",
                            sourceID: UUID().uuidString,
                            title: "사진이 전시 승인되었어요",
                            body: "오늘의 주제에 올린 사진을 확인해 보세요.",
                            thumbnailURL: nil,
                            readAt: nil,
                            createdAt: .now
                        )
                    ]
                )
            },
            loadUnread: { true },
            markRead: { _ in },
            markAllRead: {}
        ),
        onBackClick: {},
        onSelect: { _ in }
    )
    .chalkakTheme(.light)
}

#Preview("Empty Notification") {
    NotificationScreen(
        viewModel: NotificationInboxViewModel(
            loadPage: { page in
                NotificationPage(currentPage: page, pageSize: 20, hasNext: false, items: [])
            },
            loadUnread: { false },
            markRead: { _ in },
            markAllRead: {}
        ),
        onBackClick: {},
        onSelect: { _ in }
    )
    .chalkakTheme(.light)
}
