import SwiftUI

struct FeedScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.feedZoomRegistry) private var zoomRegistry
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var viewModel: FeedViewModel
    // 사진 이동 진행도와 나머지 콘텐츠 페이드 진행도(0: 출발 화면, 1: 열림).
    // Android처럼 둘의 시간이 달라 따로 애니메이션한다.
    @State private var photoProgress: CGFloat = 0
    @State private var contentProgress: CGFloat = 0
    @State private var isPhotoMoving = true
    @State private var isPhotoHidden = false
    @State private var isClosing = false
    @State private var showsDeleteDialog = false
    @State private var showsTitleEditDialog = false
    @State private var titleDraft = ""
    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    // 원본 이미지가 로드되기 전까지 사진 자리에 보여줄 이미지.
    var placeholder: FeedPhotoPlaceholder?
    var zoomSource: FeedZoomSource?
    // 닫는 전환이 끝난 뒤 호출된다.
    var onBack: () -> Void = {}
    var onDeleted: (FeedPost.ID) -> Void = { _ in }

    init(
        viewModel: FeedViewModel,
        placeholder: FeedPhotoPlaceholder? = nil,
        zoomSource: FeedZoomSource? = nil,
        onBack: @escaping () -> Void = {},
        onDeleted: @escaping (FeedPost.ID) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.placeholder = placeholder
        self.zoomSource = zoomSource
        self.onBack = onBack
        self.onDeleted = onDeleted
    }

    var body: some View {
        ZStack(alignment: .top) {
            theme.colors.background
                .ignoresSafeArea()
                .opacity(contentProgress)

            VStack(spacing: 0) {
                FeedTopBar(
                    onBack: close,
                    onEdit: {
                        titleDraft = viewModel.viewState.content?.post.title ?? ""
                        showsTitleEditDialog = true
                    },
                    onDelete: { showsDeleteDialog = true },
                    isEditVisible: viewModel.viewState.content.map {
                        $0.post.isOwnedByCurrentUser && FeedDateLabel.isToday($0.topicDate)
                    } == true,
                    isDeleteVisible: viewModel.viewState.content?.post.isOwnedByCurrentUser == true,
                    isEditEnabled: !viewModel.viewState.isDeleting &&
                        !viewModel.viewState.isUpdatingTitle &&
                        viewModel.viewState.content.map { FeedDateLabel.isToday($0.topicDate) } == true,
                    isDeleteEnabled: !viewModel.viewState.isDeleting && !viewModel.viewState.isUpdatingTitle
                )
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .opacity(contentProgress)

                content
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(backSwipeGesture)
        .onAppear(perform: open)
        .onDisappear {
            if zoomRegistry?.activeSource == zoomSource {
                zoomRegistry?.activeSource = nil
            }
        }
        .task {
            await viewModel.load()
        }
        .onChange(of: viewModel.viewState.deletedPostID) { _, postID in
            if let postID {
                onDeleted(postID)
            }
        }
        .onChange(of: viewModel.viewState.titleUpdateVersion) { _, _ in
            showsTitleEditDialog = false
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
        .overlay {
            if showsTitleEditDialog {
                FeedTitleEditDialog(
                    title: $titleDraft,
                    isSubmitting: viewModel.viewState.isUpdatingTitle,
                    onConfirm: { viewModel.updatePostTitle(titleDraft) },
                    onDismiss: { showsTitleEditDialog = false }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .overlay(alignment: .bottom) { toast }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.viewState.contentStatus {
        case .loading where placeholder == nil:
            centered {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("feed-loading")
            }
            .opacity(contentProgress)
        case let .error(reason):
            centered { errorView(reason) }
                .opacity(contentProgress)
        case .loading, .loaded:
            // placeholder가 있으면 상세를 받기 전에도 사진을 먼저 보여준다.
            // 로딩과 로드 완료가 같은 분기를 써야 상세 도착 시 사진 뷰가 다시 만들어지지 않는다.
            if viewModel.viewState.content != nil || placeholder != nil {
                feedContent(viewModel.viewState.content)
            }
        }
    }

    private func feedContent(_ content: FeedContent?) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // 상세를 받기 전에도 사진 위치가 달라지지 않도록 주제 영역의 높이를 유지한다.
                FeedTopic(dateLabel: content?.dateLabel ?? " ", topic: content?.topic ?? " ")
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .padding(.top, Metrics.topicTop)
                    .padding(.bottom, Metrics.topicBottom)
                    .opacity(contentProgress)

                FeedPhoto(
                    post: content?.post,
                    placeholder: placeholder,
                    isLikeEnabled: viewModel.viewState.isLikeEnabled,
                    zoom: FeedPhotoZoom(
                        photoProgress: photoProgress,
                        contentProgress: contentProgress,
                        source: movingSource,
                        isSettled: !isPhotoMoving,
                        isPhotoHidden: isPhotoHidden
                    ),
                    onLike: { viewModel.toggleLike() }
                )
                // 이동 중인 사진이 주제·캡션 위로 그려지게 한다.
                .zIndex(1)

                if let content {
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
                        .opacity(contentProgress)
                }
            }
            .padding(.bottom, Metrics.contentBottom)
        }
        // 전환 중에는 사진이 스크롤 영역 밖(출발 뷰 위치)에서도 보여야 한다.
        .scrollClipDisabled(isPhotoMoving)
    }

    // MARK: - 줌 전환

    // 사진이 날아갈 출발 뷰. 동작 줄이기 설정이면 사진도 이동 없이 페이드된다.
    private var movingSource: FeedZoomRegistry.Source? {
        guard !reduceMotion, let zoomSource else { return nil }
        return zoomRegistry?.source(for: zoomSource)
    }

    private func open() {
        isPhotoMoving = true
        hideSourceWhilePhotoMoves()
        withAnimation(Metrics.photoAnimation) {
            photoProgress = 1
        } completion: {
            guard !isClosing else { return }
            isPhotoMoving = false
        }
        withAnimation(Metrics.contentAnimation) {
            contentProgress = 1
        } completion: {
            // 피드 배경이 아직 반투명할 때 출발 뷰가 다시 보이면 피드 너머로 비쳐 깜빡여 보인다.
            guard !isClosing else { return }
            zoomRegistry?.activeSource = nil
        }
    }

    private func close() {
        guard !isClosing else { return }
        isClosing = true
        isPhotoMoving = true
        hideSourceWhilePhotoMoves()
        withAnimation(Metrics.photoAnimation) {
            photoProgress = 0
        } completion: {
            // 사진이 출발 뷰에 도착하면 같은 프레임에 출발 뷰와 교대한다.
            isPhotoHidden = true
            zoomRegistry?.activeSource = nil
        }
        withAnimation(Metrics.contentAnimation) {
            contentProgress = 0
        } completion: {
            onBack()
        }
    }

    private func hideSourceWhilePhotoMoves() {
        guard movingSource != nil else { return }
        zoomRegistry?.activeSource = zoomSource
    }

    // 화면 왼쪽 가장자리에서 시작한 스와이프로 닫는다. 끄는 동안 사진이 출발 뷰 쪽으로 따라간다.
    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.backSwipeMinimumDistance)
            .onChanged { value in
                guard value.startLocation.x <= Metrics.backSwipeEdgeWidth, !isClosing else { return }
                isPhotoMoving = true
                hideSourceWhilePhotoMoves()
                let dragged = max(0, value.translation.width) / Metrics.backSwipeFullDistance
                photoProgress = 1 - min(dragged, 1)
                contentProgress = photoProgress
            }
            .onEnded { value in
                guard value.startLocation.x <= Metrics.backSwipeEdgeWidth, !isClosing else { return }
                if value.predictedEndTranslation.width >= Metrics.backSwipeCloseDistance {
                    close()
                } else {
                    open()
                }
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
        case .showTitleUpdateSuccess:
            showMessage("제목을 수정했어요")
        case let .showTitleUpdateFailure(error):
            showMessage(error.titleUpdateMessage)
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
    static let topicTop: CGFloat = 16
    static let topicBottom: CGFloat = 40
    static let captionHorizontal: CGFloat = 20
    static let captionVertical: CGFloat = 5
    static let dividerHeight: CGFloat = 0.5
    static let contentBottom: CGFloat = 40
    static let toastBottomPadding: CGFloat = 32
    // Android PhotoTransition의 boundsTransform: tween(300, FastOutSlowInEasing).
    // 스프링이 아닌 고정 시간 곡선이라 실제 이동 시간도 0.3초로 같다.
    static let photoAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.3)
    // Android NavHost 기본 화면 전환: fadeIn/fadeOut(tween(700)), 기본 easing FastOutSlowInEasing.
    static let contentAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.7)
    static let backSwipeEdgeWidth: CGFloat = 24
    static let backSwipeMinimumDistance: CGFloat = 12
    static let backSwipeFullDistance: CGFloat = 300
    static let backSwipeCloseDistance: CGFloat = 120
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
