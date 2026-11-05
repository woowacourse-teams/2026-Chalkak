import SwiftUI

/// 설정 → 고객센터. 내가 보낸 문의 목록과 하단 고정 "문의하기" 버튼(내용 2줄 + 접수 시각)을 보여준다.
struct FeedbackListScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: FeedbackListViewModel

    let onBackClick: () -> Void
    /// 문의 작성 화면(`FeedbackScreen`)을 연다.
    let onCompose: () -> Void
    let onSelect: (FeedbackSummary) -> Void

    var body: some View {
        VStack(spacing: 0) {
            FeedbackTopBar(title: "고객센터", onBackClick: onBackClick)

            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakButton(title: "문의하기", action: onCompose, fillsWidth: true)
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .padding(.top, theme.spacing.sm)
                .padding(.bottom, theme.spacing.lg)
                .background(theme.colors.background)
                .accessibilityIdentifier("feedbackList.compose")
        }
        .task {
            await viewModel.refresh()
        }
        .accessibilityIdentifier("feedbackListScreen")
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.viewState.status {
        case .loading:
            ProgressView()
                .tint(theme.colors.actionPrimary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("feedbackList.loading")
        case .failed:
            FeedbackListFailureView {
                Task { await viewModel.refresh() }
            }
        case .empty:
            FeedbackListEmptyView()
        case .content:
            FeedbackListView(
                viewState: viewModel.viewState,
                onSelect: onSelect,
                onLoadMore: { Task { await viewModel.loadNextPage() } },
                onRefresh: { await viewModel.refresh() }
            )
        }
    }
}

private struct FeedbackListView: View {
    @Environment(\.chalkakTheme) private var theme
    let viewState: FeedbackListViewState
    let onSelect: (FeedbackSummary) -> Void
    let onLoadMore: () -> Void
    let onRefresh: () async -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(viewState.items) { item in
                    Button {
                        onSelect(item)
                    } label: {
                        FeedbackListRow(item: item)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("feedbackList.item.\(item.id)")

                    Rectangle()
                        .fill(theme.colors.border)
                        .frame(height: Metrics.dividerHeight)
                        .accessibilityHidden(true)
                }

                if viewState.hasNext {
                    FeedbackListPaginationView(
                        isLoading: viewState.isLoadingMore,
                        onRetry: onLoadMore
                    )
                    .onAppear {
                        // 실패한 뒤에는 저절로 다시 부르지 않고 사람이 다시 시도를 누르게 한다.
                        if !viewState.loadMoreFailed { onLoadMore() }
                    }
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
        }
        .refreshable { await onRefresh() }
    }
}

private struct FeedbackListRow: View {
    @Environment(\.chalkakTheme) private var theme
    let item: FeedbackSummary

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.sm) {
            Text(item.content)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            Text(ChalkakTimestampFormatter.string(from: item.createdAt))
                .font(theme.typography.caption)
                .foregroundStyle(theme.colors.textInactive)
        }
        .frame(maxWidth: .infinity, minHeight: Metrics.minimumRowHeight, alignment: .leading)
        .padding(.vertical, theme.spacing.lg)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private struct FeedbackListEmptyView: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(spacing: theme.spacing.sm) {
                Text("아직 보낸 문의가 없어요")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.textSecondary)

                Text("불편한 점이나 궁금한 점을 문의하기로 알려 주세요")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textMuted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.top, Metrics.emptyTopPadding)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("feedbackList.empty")
        }
    }
}

private struct FeedbackListFailureView: View {
    @Environment(\.chalkakTheme) private var theme
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: theme.spacing.lg) {
            Text("문의를 불러오지 못했어요")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            ChalkakOutlinedButton(title: "다시 시도", action: onRetry)
                .accessibilityIdentifier("feedbackList.retry")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .accessibilityIdentifier("feedbackList.failed")
    }
}

private struct FeedbackListPaginationView: View {
    @Environment(\.chalkakTheme) private var theme
    let isLoading: Bool
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: theme.spacing.md) {
            if isLoading {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
            } else {
                Button(action: onRetry) {
                    Label("더 불러오기", systemImage: "arrow.clockwise")
                        .font(theme.typography.subheadline)
                        .foregroundStyle(theme.colors.textSecondary)
                        .frame(minHeight: Metrics.minimumTouchHeight)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("feedbackList.loadMore")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, theme.spacing.lg)
    }
}

private enum Metrics {
    static let dividerHeight: CGFloat = 1
    static let emptyTopPadding: CGFloat = 120
    static let minimumTouchHeight: CGFloat = 44
    static let minimumRowHeight: CGFloat = 44
}

#Preview("Feedback List") {
    FeedbackListScreen(
        viewModel: FeedbackListViewModel(
            loadPage: { page in
                FeedbackPage(currentPage: page, pageSize: 20, hasNext: false, items: [
                    FeedbackSummary(
                        id: UUID().uuidString,
                        content: "사진 업로드 후 화면이 멈춰요. 업로드 버튼을 누르면 한참 동안 아무 반응이 없다가 앱이 꺼져요.",
                        createdAt: .now
                    ),
                    FeedbackSummary(
                        id: UUID().uuidString,
                        content: "알림이 늦게 와요.",
                        createdAt: .now.addingTimeInterval(-86_400)
                    ),
                ])
            },
            deleteFeedback: { _ in }
        ),
        onBackClick: {},
        onCompose: {},
        onSelect: { _ in }
    )
    .chalkakTheme(.light)
}

#Preview("Empty Feedback List") {
    FeedbackListScreen(
        viewModel: FeedbackListViewModel(
            loadPage: { FeedbackPage(currentPage: $0, pageSize: 20, hasNext: false, items: []) },
            deleteFeedback: { _ in }
        ),
        onBackClick: {},
        onCompose: {},
        onSelect: { _ in }
    )
    .chalkakTheme(.light)
}
