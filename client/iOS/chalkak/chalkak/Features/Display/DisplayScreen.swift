import SwiftUI

struct DisplayScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: DisplayViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onSelectBottomBarItem: (ChalkakBottomBarItem) -> Void = { _ in }
    var onSelectPhoto: (FeedTarget) -> Void = { _ in }

    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?

    private func feedTarget(for photo: DisplayPhoto) -> FeedTarget {
        let dateLabel = viewModel.viewState.selectedDate.map(FeedDateLabel.make(from:)) ?? ""
        return FeedTarget(
            seed: FeedContent(
                dateLabel: dateLabel,
                topic: viewModel.viewState.topic,
                post: FeedPost(
                    id: photo.id,
                    originalImageSource: photo.originalImageSource,
                    signatureImageSource: photo.signatureOriginalImageSource,
                    contentDescription: photo.contentDescription,
                    title: photo.title,
                    likeCount: photo.likeCount,
                    isLiked: false
                )
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakBottomBar(
                selectedItem: .display,
                onSelect: onSelectBottomBarItem,
                onAdd: onOpenPhotoUpload
            )
        }
        .overlay(alignment: .bottom) { toast }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .task {
            guard viewModel.viewState.contentStatus == .loading else { return }
            await viewModel.load()
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            DisplayDateHeader(
                date: viewModel.viewState.selectedDate,
                canGoPrevious: viewModel.viewState.canGoPrevious,
                canGoNext: viewModel.viewState.canGoNext,
                onPrevious: { Task { await viewModel.moveToPreviousDate() } },
                onNext: { Task { await viewModel.moveToNextDate() } }
            )

            if isLoaded {
                DisplayTopic(
                    topic: viewModel.viewState.topic,
                    description: topicDescription
                )
                .padding(.top, Metrics.dateToTopicSpacing)
                .padding(.bottom, Metrics.topicToDividerSpacing)
            }
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .background(theme.colors.background)
        .overlay(alignment: .bottom) {
            if isLoaded {
                Rectangle()
                    .fill(theme.colors.border)
                    .frame(height: Metrics.dividerHeight)
                    .accessibilityHidden(true)
            }
        }
    }

    private var isLoaded: Bool {
        viewModel.viewState.contentStatus == .latest
            || viewModel.viewState.contentStatus == .archive
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.viewState.contentStatus {
        case .loading:
            centeredContainer {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("display-loading")
            }
        case let .error(reason):
            centeredContainer {
                VStack(spacing: theme.spacing.xl) {
                    Text(reason.message)
                        .font(theme.typography.body)
                        .foregroundStyle(theme.colors.textSecondary)
                        .multilineTextAlignment(.center)

                    Button {
                        Task { await viewModel.retry() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                            .foregroundStyle(theme.colors.onActionPrimary)
                            .frame(width: Metrics.retryButtonSize, height: Metrics.retryButtonSize)
                            .background(theme.colors.actionPrimary, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("전시 새로고침")
                }
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .accessibilityIdentifier("display-error")
            }
        case .latest, .archive:
            loadedContent
        }
    }

    private var loadedContent: some View {
        ScrollView {
            VStack(spacing: 0) {
                if viewModel.viewState.contentStatus == .archive,
                   !viewModel.viewState.featuredPhotos.isEmpty {
                    DisplayFeaturedCarousel(
                        photos: viewModel.viewState.featuredPhotos,
                        currentPage: viewModel.viewState.featuredPage,
                        onPageChange: { viewModel.updateFeaturedPage($0) }
                    )
                    // 페이저가 이웃 카드를 화면 폭까지 peek 하도록 화면 좌우 여백을 상쇄한다.
                    .padding(.horizontal, -theme.spacing.screenHorizontal)
                    .padding(.top, Metrics.featuredTopSpacing)
                    .padding(.bottom, Metrics.featuredBottomSpacing)
                }

                if viewModel.viewState.contentStatus == .latest {
                    // ChalkakSortSelector는 44pt 최소 터치 높이로 텍스트를 세로 중앙 정렬하므로
                    // 구분선 바로 아래에 붙이면 Android 필터 top 12dp와 맞는다.
                    ChalkakSortSelector(
                        options: DisplaySort.allCases,
                        selectedOption: viewModel.viewState.selectedSort,
                        label: { $0.label },
                        onSelect: { sort in Task { await viewModel.selectSort(sort) } }
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if viewModel.viewState.photos.isEmpty {
                    emptyContent
                } else {
                    DisplayPhotoGrid(
                        photos: viewModel.viewState.photos,
                        isLoadingNext: viewModel.viewState.isLoadingNext,
                        onEndThreshold: { isReached in
                            Task { await viewModel.didReachEndThreshold(isReached) }
                        },
                        onSelect: { photo in onSelectPhoto(feedTarget(for: photo)) }
                    )
                    .padding(.top, gridTopSpacing)
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.bottom, theme.spacing.xxl)
        }
        .background(theme.colors.background)
    }

    // 구분선/featured 아래 그리드 시작 간격. Android 값과 맞춘다.
    private var gridTopSpacing: CGFloat {
        viewModel.viewState.contentStatus == .archive
            ? Metrics.gridTopAfterFeatured
            : Metrics.gridTopAfterFilter
    }

    private var emptyContent: some View {
        VStack(spacing: theme.spacing.sm) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: Metrics.emptyIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .accessibilityHidden(true)

            Text("이 날의 전시가 아직 없어요")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Metrics.emptyTopPadding)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("display-empty")
    }

    private var topicDescription: String {
        switch viewModel.viewState.contentStatus {
        case .archive:
            "가장 사람들이 좋아했던 사진들이에요"
        default:
            "같은 주제에서 다른 시선을 느껴보세요"
        }
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

    private func centeredContainer(@ViewBuilder _ content: () -> some View) -> some View {
        VStack {
            Spacer(minLength: 0)
            content()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func handle(_ event: DisplayEvent?) {
        guard let event else { return }
        switch event {
        case let .showFailure(reason):
            showMessage(reason.message)
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
}

private enum Metrics {
    static let retryIconSize: CGFloat = 20
    static let retryButtonSize: CGFloat = 48
    static let emptyIconSize: CGFloat = 34
    static let emptyTopPadding: CGFloat = 96
    static let toastBottomPadding: CGFloat = 88
    static let dividerHeight: CGFloat = 0.5
    // Android DisplayDateHeader / DisplayScreen 간격.
    static let dateToTopicSpacing: CGFloat = 15
    static let topicToDividerSpacing: CGFloat = 24
    // 필터 텍스트→그리드 22dp. SortSelector 내부 중앙정렬(~11)을 고려한 보정값.
    static let gridTopAfterFilter: CGFloat = 11
    static let featuredTopSpacing: CGFloat = 15
    static let featuredBottomSpacing: CGFloat = 8
    static let gridTopAfterFeatured: CGFloat = 4
}

private extension DisplaySort {
    var label: String {
        switch self {
        case .latest:
            "최신순"
        case .popular:
            "인기순"
        case .random:
            "랜덤순"
        }
    }
}

#Preview("Display Latest") {
    DisplayScreen(viewModel: DisplayViewModel(initialState: DisplayPreviewData.latestState))
        .chalkakTheme(.light)
}

#Preview("Display Archive") {
    DisplayScreen(viewModel: DisplayViewModel(initialState: DisplayPreviewData.archiveState))
        .chalkakTheme(.light)
}

#Preview("Display Loading") {
    DisplayScreen(viewModel: DisplayViewModel(initialState: DisplayPreviewData.loadingState))
        .chalkakTheme(.light)
}

#Preview("Display Error") {
    DisplayScreen(viewModel: DisplayViewModel(initialState: DisplayPreviewData.errorState))
        .chalkakTheme(.light)
}
