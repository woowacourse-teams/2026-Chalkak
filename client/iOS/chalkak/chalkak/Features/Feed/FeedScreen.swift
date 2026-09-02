import SwiftUI

struct FeedScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: FeedViewModel

    init(viewModel: FeedViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        ZStack(alignment: .top) {
            theme.colors.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                FeedTopBar(onBack: { dismiss() })
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
                    onLike: { Task { await viewModel.toggleLike() } }
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
