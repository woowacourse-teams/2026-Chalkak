import SwiftUI

struct FeedScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: FeedViewModel
    @State private var showsDeleteDialog = false
    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    var onDeleted: (FeedPost.ID) -> Void = { _ in }

    init(
        viewModel: FeedViewModel,
        onDeleted: @escaping (FeedPost.ID) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onDeleted = onDeleted
    }

    var body: some View {
        ZStack(alignment: .top) {
            theme.colors.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                FeedTopBar(
                    onBack: { dismiss() },
                    onDelete: { showsDeleteDialog = true },
                    isDeleteVisible: viewModel.viewState.content?.post.isOwnedByCurrentUser == true,
                    isDeleteEnabled: !viewModel.viewState.isDeleting
                )
                    .padding(.leading, Metrics.topBarLeading)
                    .padding(.trailing, Metrics.topBarTrailing)
                    .padding(.top, Metrics.topBarTop)
                    .padding(.bottom, Metrics.topBarBottom)

                content
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await viewModel.load()
        }
        .onChange(of: viewModel.viewState.deletedPostID) { _, postID in
            if let postID {
                onDeleted(postID)
            }
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
        .overlay {
            if showsDeleteDialog {
                ChalkakConfirmDialog(
                    title: "게시물 삭제",
                    message: "게시물을 삭제하시겠어요?",
                    confirmText: "삭제",
                    confirmStyle: .destructive,
                    onConfirm: {
                        showsDeleteDialog = false
                        viewModel.deletePost()
                    },
                    onDismiss: { showsDeleteDialog = false }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .overlay(alignment: .bottom) { toast }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.viewState.contentStatus {
        case .loading:
            centered {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("feed-loading")
            }
        case let .error(reason):
            centered { errorView(reason) }
        case .loaded:
            if let content = viewModel.viewState.content {
                loadedContent(content)
            }
        }
    }

    private func loadedContent(_ content: FeedContent) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                FeedTopic(dateLabel: content.dateLabel, topic: content.topic)
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, Metrics.topicTop)
                    .padding(.bottom, Metrics.topicBottom)

                FeedPhoto(
                    post: content.post,
                    isLikeEnabled: viewModel.viewState.isLikeEnabled,
                    onLike: { viewModel.toggleLike() }
                )

                FeedCaption(title: content.post.title)
                    .padding(.horizontal, Metrics.captionHorizontal)
                    .padding(.vertical, Metrics.captionVertical)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .top) {
                        Rectangle()
                            .fill(theme.colors.border)
                            .frame(height: Metrics.dividerHeight)
                            .accessibilityHidden(true)
                    }
            }
            .padding(.bottom, Metrics.contentBottom)
        }
    }

    private func errorView(_ reason: FeedError) -> some View {
        VStack(spacing: theme.spacing.xl) {
            Text(reason.message)
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            Button {
                Task { await viewModel.retry() }
            } label: {
                Text("다시 시도")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.actionPrimary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("다시 시도")
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .accessibilityIdentifier("feed-error")
    }

    @ViewBuilder
    private var toast: some View {
        if let message {
            Text(message)
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.onActionPrimary)
                .padding(.horizontal, theme.spacing.lg)
                .padding(.vertical, theme.spacing.md)
                .background(theme.colors.actionPrimary, in: Capsule())
                .padding(.bottom, Metrics.toastBottomPadding)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityLabel(message)
        }
    }

    private func handle(_ event: FeedEvent?) {
        guard let event else { return }
        switch event {
        case let .showDeleteFailure(error):
            showMessage(error.deleteMessage)
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task {
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }

    private func centered(@ViewBuilder _ inner: () -> some View) -> some View {
        VStack {
            Spacer(minLength: 0)
            inner()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private enum Metrics {
    static let topBarLeading: CGFloat = 8
    static let topBarTrailing: CGFloat = 12
    static let topBarTop: CGFloat = 10
    static let topBarBottom: CGFloat = 8
    static let topicTop: CGFloat = 16
    static let topicBottom: CGFloat = 40
    static let captionHorizontal: CGFloat = 20
    static let captionVertical: CGFloat = 5
    static let dividerHeight: CGFloat = 0.5
    static let contentBottom: CGFloat = 40
    static let toastBottomPadding: CGFloat = 32
}

#Preview("Feed Loaded") {
    NavigationStack {
        FeedScreen(viewModel: FeedPreviewData.viewModel(state: FeedPreviewData.loadedState))
    }
    .chalkakTheme(.light)
}

#Preview("Feed Loading") {
    NavigationStack {
        FeedScreen(viewModel: FeedPreviewData.viewModel(state: FeedPreviewData.loadingState))
    }
    .chalkakTheme(.light)
}

#Preview("Feed Error") {
    NavigationStack {
        FeedScreen(viewModel: FeedPreviewData.viewModel(state: FeedPreviewData.errorState))
    }
    .chalkakTheme(.light)
}
