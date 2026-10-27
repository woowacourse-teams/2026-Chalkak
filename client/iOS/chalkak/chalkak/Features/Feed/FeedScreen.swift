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
    @State private var hasCompletedOpening = false
    @State private var screenDragOffset: CGFloat = 0
    @State private var photoFrame: CGRect?
    @State private var transitionSafeAreaInsets = EdgeInsets()
    @State private var isPhotoMoving = true
    @State private var isPhotoInteracting = false
    @State private var isBackSwiping = false
    @State private var backSwipeTracking = FeedBackSwipeTracking()
    @State private var isClosing = false
    @State private var hasStartedOpening = false
    @State private var hasPreparedOpening = false
    @State private var hasLaunchedOpening = false
    @State private var isPhotoReady = false
    @State private var hasContentError = false
    @State private var transitionSource: FeedZoomRegistry.Source?
    @State private var transitionViewport: CGRect?
    private let opensAIReviewOnEntry: Bool
    @State private var hasOpenedInitialAIReview = false
    private let usesPhotoTransition: Bool
    @State private var showsDeleteDialog = false
    @State private var showsAIReviewSheet = false
    // AI 피드백 시트가 닫히는 애니메이션까지 끝났는지. 시트 두 개를 겹쳐 띄우지 않으려고 따로 둔다.
    @State private var isAIReviewSheetOnScreen = false
    @State private var showsTitleEditDialog = false
    @State private var titleDraft = ""
    @State private var showsSignatureHint = false
    // 코드가 안내 시트를 닫는 중인지. 사용자가 닫은 경우와 구분해, 사용자가 닫았을 때만 다시 띄우지 않도록 기록한다.
    @State private var isHidingSignatureHintByCode = false
    private let signatureHint = AuthorPostsSignatureHint()
    // 원본 이미지가 로드되기 전까지 사진 자리에 보여줄 이미지.
    var placeholder: FeedPhotoPlaceholder?
    var zoomSource: FeedZoomSource?
    // 닫는 전환이 끝난 뒤 호출된다.
    var onBack: () -> Void = {}
    var onDeleted: (FeedPost.ID) -> Void = { _ in }
    // 사인을 눌러 작가 사진 목록을 연다. nil이면 사인을 누를 수 없다.
    var onOpenAuthorPosts: ((AuthorPostsTarget) -> Void)?
    // 이 피드의 좋아요 상태가 정해지거나 바뀔 때(상세 도착·토글). 작가 사진 목록 카드를 맞추는 데 쓴다.
    var onLikeChange: ((FeedLikeUpdate) -> Void)?
    // 다른 화면(작가 사진 목록 카드)에서 바뀐 좋아요. 이 피드의 게시물이면 반영한다.
    var externalLikeUpdate: FeedLikeUpdate?

    init(
        viewModel: FeedViewModel,
        placeholder: FeedPhotoPlaceholder? = nil,
        zoomSource: FeedZoomSource? = nil,
        transitionSource: FeedZoomRegistry.Source? = nil,
        usesPhotoTransition: Bool = true,
        opensAIReviewOnEntry: Bool = false,
        onBack: @escaping () -> Void = {},
        onDeleted: @escaping (FeedPost.ID) -> Void = { _ in },
        onOpenAuthorPosts: ((AuthorPostsTarget) -> Void)? = nil,
        onLikeChange: ((FeedLikeUpdate) -> Void)? = nil,
        externalLikeUpdate: FeedLikeUpdate? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        _transitionSource = State(initialValue: transitionSource)
        self.placeholder = placeholder
        self.zoomSource = zoomSource
        self.opensAIReviewOnEntry = opensAIReviewOnEntry
        self.usesPhotoTransition = usesPhotoTransition
        self.onBack = onBack
        self.onDeleted = onDeleted
        self.onOpenAuthorPosts = onOpenAuthorPosts
        self.onLikeChange = onLikeChange
        self.externalLikeUpdate = externalLikeUpdate
    }

    var body: some View {
        ZStack(alignment: .top) {
            theme.colors.background
                .ignoresSafeArea()
                .opacity(visibleContentProgress)

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .overlay(alignment: .top) {
            FeedTopBar(
                onBack: { close() },
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
            .opacity(visibleContentProgress)
        }
        .overlay(alignment: .bottomTrailing) {
            if canShowAIReview, let aiReview = viewModel.aiReview {
                FeedAIReviewButton(model: aiReview) { showsAIReviewSheet = true }
                    .padding(.trailing, theme.spacing.lg)
                    .padding(.bottom, theme.spacing.md)
                    .opacity(visibleContentProgress)
            }
        }
        .sheet(isPresented: $showsAIReviewSheet, onDismiss: { isAIReviewSheetOnScreen = false }) {
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
        // 아래로 쓸어내리거나 바깥을 눌러 닫으면 다시 띄우지 않는다.
        .sheet(isPresented: $showsSignatureHint, onDismiss: handleSignatureHintDismiss) {
            FeedSignatureHintSheet()
        }
        .onChange(of: showsAIReviewSheet) { _, shows in
            if shows { isAIReviewSheetOnScreen = true }
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
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: {
            if !isPhotoMoving || transitionViewport == nil {
                transitionViewport = $0
                startOpening()
            }
        }
        .onGeometryChange(for: EdgeInsets.self) { $0.safeAreaInsets } action: {
            if !hasStartedOpening || !isPhotoMoving { transitionSafeAreaInsets = $0 }
        }
        .modifier(FeedScreenReturnEffect(
            progress: usesScreenTransition ? photoProgress : 1,
            dragOffset: screenDragOffset,
            source: usesScreenTransition && hasStartedOpening ? movingSource : nil,
            photo: photoFrame,
            canvas: transitionViewport,
            safeAreaInsets: transitionSafeAreaInsets,
            revealsCoveredSource: usesScreenTransition
        ))
        // 측정 전 전체 화면이 순간 노출되지 않도록 레이아웃은 유지하고 표시만 기다린다.
        .opacity(usesScreenTransition && !hasStartedOpening ? 0 : 1)
        .animation(nil, value: hasStartedOpening)
        .background {
            // 비트맵과 좌표가 반영될 화면 갱신 간격을 확보한 뒤 상세 화면을 노출한다.
            // 아래 게이트는 노출된 출발 상태를 유지한 다음 확대를 시작한다.
            FeedOpeningFrameGate(isEnabled: hasPreparedOpening && !hasStartedOpening) {
                guard !hasStartedOpening, !isClosing else { return }
                zoomRegistry?.activeSource = usesScreenTransition ? zoomSource : nil
                zoomRegistry?.hidesWholeSource = usesScreenTransition
                zoomRegistry?.sourceBarOpacity = revealsSourceBottomBar ? 1 : 0
                hasStartedOpening = true
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .background {
            FeedOpeningFrameGate(isEnabled: hasStartedOpening && !hasLaunchedOpening) {
                guard !hasLaunchedOpening, !isClosing else { return }
                hasLaunchedOpening = true
                open()
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(backSwipeGesture, including: usesPhotoTransition ? .all : .none)
        .onAppear {
            // 전달받은 탭 순간의 좌표를 우선한다. 닫을 때는 현재 셀 위치를 다시 읽는다.
            if transitionSource == nil { captureTransitionSource() }
            // 공유 사진은 도착 프레임이 준비된 뒤 첫 프레임부터 이동한다.
            if movingSource == nil || !usesPhotoTransition { startOpening() }
        }
        .onDisappear {
            zoomRegistry?.sourceBarOpacity = 0
            zoomRegistry?.hidesWholeSource = false
            if zoomRegistry?.activeSource == zoomSource {
                zoomRegistry?.activeSource = nil
            }
        }
        .task {
            await viewModel.load()
        }
        .onChange(of: viewModel.viewState.contentStatus) { _, status in
            if case .error = status {
                hasContentError = true
                startOpening()
            }
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
        .onChange(of: likeState) { _, state in
            if let state { onLikeChange?(state) }
        }
        .onChange(of: externalLikeUpdate) { _, update in
            if let update { viewModel.applyLikeUpdate(update) }
        }
        .onChange(of: canShowSignatureHint, initial: true) { _, canShow in
            updateSignatureHint(canShow: canShow)
        }
        .alert("게시물을 삭제할까요?", isPresented: $showsDeleteDialog) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { viewModel.deletePost() }
        } message: {
            Text("삭제한 게시물은 피드에서 사라지며 다시 복구할 수 없어요.")
        }
        .alert("게시물 제목 수정", isPresented: $showsTitleEditDialog) {
            TextField("제목을 입력해 주세요", text: $titleDraft)
                .textInputAutocapitalization(.never)
            Button("취소", role: .cancel) { showsTitleEditDialog = false }
            Button("저장") { viewModel.updatePostTitle(titleDraft) }
                .disabled(viewModel.viewState.isUpdatingTitle)
        } message: {
            Text("새 제목을 입력해 저장하면 게시물에 바로 반영돼요.")
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
            .opacity(visibleContentProgress)
        case let .error(reason):
            centered { errorView(reason) }
                .opacity(visibleContentProgress)
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
                    .opacity(visibleContentProgress)

                FeedPhoto(
                    post: content?.post,
                    placeholder: placeholder,
                    isLikeEnabled: canLike,
                    zoom: FeedPhotoZoom(
                        photoProgress: usesScreenTransition ? 1 : photoProgress,
                        contentProgress: visibleContentProgress,
                        source: movingSource,
                        isSettled: !isPhotoMoving,
                        morphsAspectRatio: morphsPhotoAspectRatio,
                        viewport: usesScreenTransition ? nil : transitionViewport,
                        keepsOriginalVisible: usesScreenTransition && hasCompletedOpening,
                        screen: screenZoom
                    ),
                    onTransitionReady: {
                        isPhotoReady = true
                        startOpening()
                    },
                    onPhotoFrameChanged: { frame in
                        if !isPhotoMoving || !hasStartedOpening { photoFrame = frame }
                        startOpening()
                    },
                    onPhotoInteractionChanged: { isPhotoInteracting = $0 },
                    onSignatureTap: signatureTapHandler,
                    onLike: {
                        // 스와이프와 버튼의 손떼기 이벤트가 함께 와도 좋아요를 실행하지 않는다.
                        guard canLike else { return }
                        viewModel.toggleLike()
                    }
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

    // 지금 보이는 게시물의 좋아요 상태. 상세가 도착하거나 좋아요를 누를 때 바뀐다.
    private var likeState: FeedLikeUpdate? {
        viewModel.viewState.content.map {
            FeedLikeUpdate(postID: $0.post.id, isLiked: $0.post.isLiked, likeCount: $0.post.likeCount)
        }
    }

    // 작성자 ID를 받기 전(상세 로딩 중)이나 사인 탭을 쓰지 않는 화면에서는 nil이라 사인을 누를 수 없다.
    private var signatureTapHandler: (() -> Void)? {
        guard onOpenAuthorPosts != nil, viewModel.viewState.content?.post.authorID != nil else { return nil }
        return handleSignatureTap
    }

    private func handleSignatureTap() {
        guard !isBackSwiping, !isPhotoMoving, !isClosing else { return }
        // 사인을 한 번 눌러 봤으면 안내를 다시 띄우지 않는다.
        signatureHint.markShown()
        hideSignatureHint()
        switch viewModel.signatureTapAction() {
        case .returnToAuthorPosts:
            close()
        case let .openAuthorPosts(target):
            onOpenAuthorPosts?(target)
        case nil:
            break
        }
    }

    // 사인을 누를 수 있는 게시물(내 게시물 포함)의 사인이 화면에 자리 잡으면 안내 시트를 띄운다.
    // 비로그인도 띄운다(사인을 누르면 로그인 안내). 시트를 한 번 닫거나 사인을 누르면 다시 띄우지 않는다.
    // AI 피드백 시트가 떠 있거나 진입하자마자 열릴 예정이면, 그 시트가 완전히 닫힌 뒤에 띄운다.
    private var canShowSignatureHint: Bool {
        signatureTapHandler != nil
            && !isPhotoMoving && !isClosing && !isBackSwiping
            && !showsAIReviewSheet && !isAIReviewSheetOnScreen
            && !(opensAIReviewOnEntry && !hasOpenedInitialAIReview)
    }

    private func updateSignatureHint(canShow: Bool) {
        guard canShow else {
            hideSignatureHint()
            return
        }
        guard !showsSignatureHint, !signatureHint.hasShown else { return }
        showsSignatureHint = true
    }

    // 사진 이동·백 스와이프처럼 안내를 띄울 수 없는 상태가 되면 코드가 잠시 닫는다. 이때는 기록하지 않아 다시 띄울 수 있다.
    private func hideSignatureHint() {
        guard showsSignatureHint else { return }
        isHidingSignatureHintByCode = true
        showsSignatureHint = false
    }

    // 아래로 쓸어내리거나 바깥을 눌러 사용자가 닫았을 때만 기록한다.
    private func handleSignatureHintDismiss() {
        if !isHidingSignatureHintByCode { signatureHint.markShown() }
        isHidingSignatureHintByCode = false
    }

    private var canLike: Bool {
        viewModel.viewState.isLikeEnabled && !isBackSwiping && !isPhotoMoving && !isClosing
    }

    private var usesScreenTransition: Bool {
        usesPhotoTransition && movingSource != nil
    }

    // 화면이 출발 카드로 접히는 동안 출발 화면의 하단바를 함께 보여줄지.
    private var revealsSourceBottomBar: Bool {
        usesScreenTransition && zoomSource?.showsBottomBar == true
    }

    // FeedScreenReturnEffect와 같은 값으로 서명만 따로 출발 서명에 맞춘다.
    private var screenZoom: FeedScreenZoom? {
        guard usesScreenTransition, hasStartedOpening,
              let movingSource, let photoFrame, let transitionViewport else { return nil }
        return FeedScreenZoom(
            progress: photoProgress,
            dragOffset: screenDragOffset,
            source: movingSource,
            photo: photoFrame,
            canvas: transitionViewport,
            safeAreaInsets: transitionSafeAreaInsets,
            revealsCoveredSource: true
        )
    }

    // 열고 닫을 때 모두 불투명한 화면 하나를 확대·축소한다.
    private var visibleContentProgress: CGFloat {
        usesScreenTransition ? 1 : contentProgress
    }

    // MARK: - 줌 전환

    // 사진이 날아갈 출발 뷰. 동작 줄이기 설정이면 사진도 이동 없이 페이드된다.
    private var movingSource: FeedZoomRegistry.Source? {
        guard !reduceMotion, let zoomSource else { return nil }
        return transitionSource ?? zoomRegistry?.source(for: zoomSource)
    }

    private var morphsPhotoAspectRatio: Bool {
        switch zoomSource {
        case .homeYesterday, .homeTrending, .displayGrid, .displayFeatured, .authorGrid: true
        default: false
        }
    }

    private func startOpening() {
        guard !hasPreparedOpening, !isClosing else { return }
        if usesScreenTransition && !hasContentError {
            guard isPhotoReady, photoFrame != nil, transitionViewport != nil else { return }
        }
        hasPreparedOpening = true
        if !usesScreenTransition || hasContentError { hasStartedOpening = true }
    }

    // releaseVelocity: 백 스와이프를 놓은 순간의 사진 진행도 속도(초당). 없으면 멈춘 상태에서 출발한다.
    private func open(releaseVelocity: CGFloat? = nil) {
        guard usesPhotoTransition else {
            photoProgress = 1
            contentProgress = 1
            isPhotoMoving = false
            return
        }
        if transitionSource == nil { captureTransitionSource() }
        isPhotoMoving = true
        // 상세 사진이 원래 카드를 대신한다. 복귀가 끝날 때까지 뒤에 같은 사진을 그리지 않는다.
        zoomRegistry?.activeSource = usesScreenTransition ? zoomSource : nil
        withAnimation(photoAnimation(to: 1, releaseVelocity: releaseVelocity)) {
            zoomRegistry?.sourceBarOpacity = 0
            photoProgress = 1
            screenDragOffset = 0
        } completion: {
            guard !isClosing else { return }
            isPhotoMoving = false
            hasCompletedOpening = true
        }
        withAnimation(Metrics.contentAnimation) {
            contentProgress = 1
        }
    }

    private func close(releaseVelocity: CGFloat? = nil) {
        guard !isClosing else { return }
        guard usesPhotoTransition else {
            onBack()
            return
        }
        // 버튼은 현재 카드 위치를 읽고, 스와이프는 시작할 때 고정한 위치를 유지한다.
        // 손을 뗄 때 다시 측정하면 드래그와 마무리 애니메이션 사이에서 경로가 바뀔 수 있다.
        if releaseVelocity == nil { captureTransitionSource() }
        isClosing = true
        isPhotoMoving = true
        // 원본 카드 전체를 도착할 때까지 숨겨 먼저 나타나는 사진 조각이 없게 한다.
        zoomRegistry?.hidesWholeSource = true
        zoomRegistry?.activeSource = usesScreenTransition ? zoomSource : nil
        let animation = photoAnimation(to: 0, releaseVelocity: releaseVelocity)
        // 하단바는 사진이 카드에 거의 도착한 뒤에 드러낸다. 끄는 동안에는 띄우지 않는다.
        withAnimation(Metrics.sourceBarAnimation) {
            zoomRegistry?.sourceBarOpacity = revealsSourceBottomBar ? 1 : 0
        }
        withAnimation(animation) {
            photoProgress = 0
            screenDragOffset = 0
        } completion: {
            zoomRegistry?.activeSource = nil
            zoomRegistry?.hidesWholeSource = false
            zoomRegistry?.sourceBarOpacity = 0
            onBack()
        }
        // 화면 전체를 접는 동안에는 visibleContentProgress가 1을 유지한다.
        // 복귀 카드가 없는 대체 경로에서만 이 페이드를 사용한다.
        withAnimation(animation) {
            contentProgress = 0
        }
    }

    // 버튼으로 열고 닫을 때는 Android와 같은 곡선을 쓴다.
    // 스와이프를 놓았을 때는 그 속도를 이어받아야 사진이 멈칫했다가 다시 출발하지 않는다.
    private func photoAnimation(to target: CGFloat, releaseVelocity: CGFloat?) -> Animation {
        guard let releaseVelocity else { return Metrics.photoAnimation }
        return FeedBackSwipeRelease.animation(
            from: photoProgress,
            to: target,
            velocity: releaseVelocity,
            duration: Metrics.photoReleaseDuration
        )
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
                    zoomRegistry?.hidesWholeSource = true
                    backSwipeTracking = FeedBackSwipeTracking()
                    captureTransitionSource()
                    isPhotoMoving = true
                }
                zoomRegistry?.activeSource = usesScreenTransition ? zoomSource : nil
                backSwipeTracking.update(to: value.translation)
                let dragged = backSwipeTracking.distance / Metrics.backSwipeFullDistance
                if usesScreenTransition {
                    // 길게 끌어도 창 크기를 유지하고, 손을 놓은 뒤 카드로 접는다.
                    photoProgress = 1
                    screenDragOffset = backSwipeTracking.distance * 0.65
                } else {
                    photoProgress = 1 - min(dragged, 1)
                    contentProgress = photoProgress
                }
            }
            .onEnded { value in
                guard isBackSwiping else { return }
                isBackSwiping = false
                guard !isClosing else { return }
                backSwipeTracking.update(to: value.translation)
                // 거리가 늘면 진행도는 줄어든다. 끝까지 끈 뒤에는 진행도가 0에 멈춰 있어 속도도 없다.
                let releaseVelocity = backSwipeTracking.distance < Metrics.backSwipeFullDistance
                    ? -backSwipeTracking.horizontalVelocity(value.velocity) / Metrics.backSwipeFullDistance
                    : 0
                if backSwipeTracking.projectedDistance(to: value.predictedEndTranslation) >= Metrics.backSwipeCloseDistance {
                    close(releaseVelocity: releaseVelocity)
                } else {
                    open(releaseVelocity: releaseVelocity)
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

    /// 손을 뗀 순간의 가로 속도. 거리 계산과 같이 세로로 움직이며 놓은 속도는 반영하지 않는다.
    func horizontalVelocity(_ velocity: CGSize) -> CGFloat {
        abs(velocity.width) > abs(velocity.height) ? velocity.width : 0
    }
}

/// 백 스와이프를 놓은 뒤 남은 전환을 유한한 시간 안에 마무리한다.
enum FeedBackSwipeRelease {
    static func animation(from progress: CGFloat, to target: CGFloat, velocity: CGFloat, duration: TimeInterval) -> Animation {
        let timing = timing(from: progress, to: target, velocity: velocity, duration: duration)
        // x를 균등하게 두어 시간에 대한 초기 기울기가 손을 놓은 속도와 연결된다.
        // 제어점 y를 0...1 안에 두면 카드 위치를 지나쳤다가 돌아오지 않는다.
        return .timingCurve(1.0 / 3, timing.initialSlope / 3, 2.0 / 3, 1, duration: timing.duration)
    }

    static func timing(from progress: CGFloat, to target: CGFloat, velocity: CGFloat, duration: TimeInterval)
        -> (duration: TimeInterval, initialSlope: Double) {
        let remaining = Double(target - progress)
        guard abs(remaining) > 0.001, duration > 0 else { return (0, 0) }
        // 카드에 가까운 상태에서 놓으면 긴 감속 꼬리 없이 더 빨리 도착한다.
        let distanceDuration = duration * min(1, sqrt(abs(remaining)))
        let towardVelocity = max(0, Double(velocity) * (remaining > 0 ? 1 : -1))
        let velocityDuration = towardVelocity > 0 ? 2 * abs(remaining) / towardVelocity : duration
        let finishDuration = min(duration, max(0.08, min(distanceDuration, velocityDuration)))
        let slope = min(3, towardVelocity * finishDuration / abs(remaining))
        return (finishDuration, slope)
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
    // 스와이프를 놓은 뒤 남은 이동도 photoAnimation과 같은 0.3초 안팎에 끝낸다.
    static let photoReleaseDuration: TimeInterval = 0.3
    // 닫힘 전환(0.3초)의 뒤쪽 절반에서 하단바를 드러낸다.
    static let sourceBarAnimation: Animation = .easeIn(duration: 0.15).delay(0.15)
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
