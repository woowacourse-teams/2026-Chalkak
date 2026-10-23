import SwiftUI

struct FeedScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.chalkakShowToast) private var showToast
    @Environment(\.feedZoomRegistry) private var zoomRegistry
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var viewModel: FeedViewModel
    // 사진 이동 진행도와 나머지 콘텐츠 페이드 진행도(0: 출발 화면, 1: 열림).
    // Android처럼 둘의 시간이 달라 따로 애니메이션한다.
    @State private var photoProgress: CGFloat = 0
    @State private var contentProgress: CGFloat = 0
    @State private var isPhotoMoving = true
    @State private var isPhotoInteracting = false
    @State private var isBackSwiping = false
    @State private var backSwipeTracking = FeedBackSwipeTracking()
    @State private var isClosing = false
    @State private var hasStartedOpening = false
    @State private var transitionSource: FeedZoomRegistry.Source?
    @State private var transitionViewport: CGRect?
    private let opensAIReviewOnEntry: Bool
    @State private var hasOpenedInitialAIReview = false
    private let usesPhotoTransition: Bool
    @State private var showsDeleteDialog = false
    @State private var showsAIReviewSheet = false
    @State private var showsTitleEditDialog = false
    @State private var titleDraft = ""
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
        transitionSource: FeedZoomRegistry.Source? = nil,
        usesPhotoTransition: Bool = true,
        opensAIReviewOnEntry: Bool = false,
        onBack: @escaping () -> Void = {},
        onDeleted: @escaping (FeedPost.ID) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        _transitionSource = State(initialValue: transitionSource)
        self.placeholder = placeholder
        self.zoomSource = zoomSource
        self.opensAIReviewOnEntry = opensAIReviewOnEntry
        self.usesPhotoTransition = usesPhotoTransition
        self.onBack = onBack
        self.onDeleted = onDeleted
    }

    var body: some View {
        ZStack(alignment: .top) {
            theme.colors.background
                .ignoresSafeArea()
                .opacity(contentProgress)

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .overlay(alignment: .top) {
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
        }
        .overlay(alignment: .bottomTrailing) {
            if canShowAIReview, let aiReview = viewModel.aiReview {
                FeedAIReviewButton(model: aiReview) { showsAIReviewSheet = true }
                    .padding(.trailing, theme.spacing.lg)
                    .padding(.bottom, theme.spacing.md)
                    .opacity(contentProgress)
            }
        }
        .sheet(isPresented: $showsAIReviewSheet) {
            if let aiReview = viewModel.aiReview {
                FeedAIReviewSheet(model: aiReview, onDismiss: { showsAIReviewSheet = false }) {
                    Task {
                        await aiReview.request()
                        if aiReview.review?.status == .processing || aiReview.review?.status == .completed {
                            showsAIReviewSheet = false
                        }
                    }
                }
                .presentationDetents(aiReviewSheetDetents(aiReview))
            }
        }
        .onChange(of: canObserveAIReview, initial: true) { _, canObserve in
            guard opensAIReviewOnEntry, canObserve, !hasOpenedInitialAIReview else { return }
            hasOpenedInitialAIReview = true
            showsAIReviewSheet = true
        }
        .task(id: scenePhase == .active && canObserveAIReview) {
            guard scenePhase == .active, canObserveAIReview else { return }
            await viewModel.aiReview?.observe()
        }
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { transitionViewport = $0 }
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(backSwipeGesture, including: usesPhotoTransition ? .all : .none)
        .onAppear {
            // 전달받은 탭 순간의 좌표를 우선한다. 닫을 때는 현재 셀 위치를 다시 읽는다.
            if transitionSource == nil { captureTransitionSource() }
            // 공유 사진은 도착 프레임이 준비된 뒤 첫 프레임부터 이동한다.
            if movingSource == nil || !usesPhotoTransition { startOpening() }
        }
        .onDisappear {
            if zoomRegistry?.activeSource == zoomSource {
                zoomRegistry?.activeSource = nil
            }
        }
        .task {
            await viewModel.load()
        }
        .onChange(of: viewModel.viewState.contentStatus) { _, status in
            if case .error = status { startOpening() }
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
    }

    // 시작 안내와 실패 이유는 내용이 짧아 낮은 높이로, 결과·진행 상태는 펼칠 수 있는 높이로 연다.
    private func aiReviewSheetDetents(_ aiReview: FeedAIReviewViewModel) -> Set<PresentationDetent> {
        if aiReview.canRequest { return [.height(Metrics.aiStartSheetHeight)] }
        if aiReview.failureReason != nil { return [.height(Metrics.aiFailureSheetHeight)] }
        return [.height(Metrics.aiStatusSheetHeight), .large]
    }

    private var canShowAIReview: Bool {
        canObserveAIReview && viewModel.aiReview?.isButtonVisible == true
    }

    private var canObserveAIReview: Bool {
        viewModel.aiReview != nil && viewModel.viewState.content?.post.isOwnedByCurrentUser == true
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
                        morphsAspectRatio: morphsPhotoAspectRatio,
                        viewport: transitionViewport
                    ),
                    onTransitionReady: startOpening,
                    onPhotoInteractionChanged: { isPhotoInteracting = $0 },
                    onLike: { viewModel.toggleLike() }
                )
                // 이동 중인 사진이 주제 위로 그려지게 한다.
                .zIndex(1)

            }
            .padding(.top, ChalkakNavigationButton.diameter + 2 * theme.spacing.md)
            .padding(.bottom, canShowAIReview ? Metrics.aiContentBottom : Metrics.contentBottom)
        }
        // 화면 전환과 사진 확대 중에는 사진이 스크롤 영역 밖에서도 보여야 한다.
        .scrollClipDisabled(isPhotoMoving || isPhotoInteracting)
        .scrollDisabled(isPhotoMoving || isPhotoInteracting)
    }

    // MARK: - 줌 전환

    // 사진이 날아갈 출발 뷰. 동작 줄이기 설정이면 사진도 이동 없이 페이드된다.
    private var movingSource: FeedZoomRegistry.Source? {
        guard !reduceMotion, let zoomSource else { return nil }
        return transitionSource ?? zoomRegistry?.source(for: zoomSource)
    }

    private var morphsPhotoAspectRatio: Bool {
        switch zoomSource {
        case .homeYesterday, .homeTrending, .displayGrid, .displayFeatured: true
        default: false
        }
    }

    private func startOpening() {
        guard !hasStartedOpening, !isClosing else { return }
        hasStartedOpening = true
        open()
    }

    private func open() {
        guard usesPhotoTransition else {
            photoProgress = 1
            contentProgress = 1
            isPhotoMoving = false
            return
        }
        if transitionSource == nil { captureTransitionSource() }
        isPhotoMoving = true
        // 열릴 때도 원래 사진을 유지해 이동한 사진 아래 검은 카드 바탕이 드러나지 않게 한다.
        zoomRegistry?.activeSource = nil
        withAnimation(Metrics.photoAnimation) {
            photoProgress = 1
        } completion: {
            guard !isClosing else { return }
            isPhotoMoving = false
        }
        withAnimation(Metrics.contentAnimation) {
            contentProgress = 1
        }
    }

    private func close() {
        guard !isClosing else { return }
        guard usesPhotoTransition else {
            onBack()
            return
        }
        captureTransitionSource()
        isClosing = true
        isPhotoMoving = true
        // 돌아갈 카드는 유지한다. 가려진 사진을 숨기면 반투명 하단바 아래 검은 바탕이 드러난다.
        zoomRegistry?.activeSource = nil
        withAnimation(Metrics.photoAnimation) {
            photoProgress = 0
        } completion: {
            zoomRegistry?.activeSource = nil
            onBack()
        }
        // 사진이 도착하면 화면을 제거한다. 페이드도 같은 시간에 끝나야 뒤 화면의 터치를 지연시키지 않는다.
        withAnimation(Metrics.photoAnimation) {
            contentProgress = 0
        }
    }

    private func captureTransitionSource() {
        guard let zoomSource else { return }
        transitionSource = zoomRegistry?.source(for: zoomSource) ?? transitionSource
    }

    // 화면 왼쪽 가장자리에서 시작한 스와이프로 닫는다. 끄는 동안 사진이 출발 뷰 쪽으로 따라간다.
    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.backSwipeMinimumDistance)
            .onChanged { value in
                guard !isClosing, !isPhotoInteracting else { return }
                if !isBackSwiping {
                    guard !isPhotoMoving,
                          value.startLocation.x <= Metrics.backSwipeEdgeWidth,
                          value.translation.width > abs(value.translation.height) else { return }
                    isBackSwiping = true
                    backSwipeTracking = FeedBackSwipeTracking()
                    captureTransitionSource()
                    isPhotoMoving = true
                }
                zoomRegistry?.activeSource = nil
                backSwipeTracking.update(to: value.translation)
                let dragged = backSwipeTracking.distance / Metrics.backSwipeFullDistance
                photoProgress = 1 - min(dragged, 1)
                contentProgress = photoProgress
            }
            .onEnded { value in
                guard isBackSwiping else { return }
                isBackSwiping = false
                guard !isClosing else { return }
                backSwipeTracking.update(to: value.translation)
                if backSwipeTracking.projectedDistance(to: value.predictedEndTranslation) >= Metrics.backSwipeCloseDistance {
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
        showToast(text)
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

struct FeedBackSwipeTracking {
    private(set) var distance: CGFloat = 0
    private var previousTranslation: CGSize = .zero

    mutating func update(to translation: CGSize) {
        let delta = CGSize(width: translation.width - previousTranslation.width, height: translation.height - previousTranslation.height)
        // 무시한 세로 움직임의 가로 흔들림이 다음 가로 이동에 한꺼번에 반영되지 않게 한다.
        previousTranslation = translation
        guard abs(delta.width) > abs(delta.height) else { return }
        distance = max(0, distance + delta.width)
    }

    func projectedDistance(to translation: CGSize) -> CGFloat {
        let delta = CGSize(width: translation.width - previousTranslation.width, height: translation.height - previousTranslation.height)
        guard abs(delta.width) > abs(delta.height) else { return distance }
        return max(0, distance + delta.width)
    }
}

private enum Metrics {
    static let topicTop: CGFloat = 16
    static let topicBottom: CGFloat = 40
    static let contentBottom: CGFloat = 40
    static let aiContentBottom: CGFloat = 90
    static let aiStartSheetHeight: CGFloat = 320
    static let aiFailureSheetHeight: CGFloat = 240
    static let aiStatusSheetHeight: CGFloat = 510
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
