//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI
import UIKit
import UserNotifications

struct ContentView: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @State private var route: AppRoute = Self.initialRoute
    @State private var selectedTab: ChalkakBottomBarItem = .home
    @State private var isBottomBarCompact = false
    @State private var selectedFeed: FeedTarget?
    // 피드에서 사인을 눌러 연 작가 사진 목록과, 그 목록에서 연 피드. 피드 overlay 위에 차례로 쌓인다.
    @State private var selectedAuthorPosts: AuthorPostsViewModel?
    @State private var selectedAuthorFeed: FeedTarget?
    @State private var selectedIssueURL: URL?
    @State private var onboardingAnalyticsScreen = AnalyticsScreen(name: "terms", screenClass: "Terms")
    @State private var signatureAnalyticsScreen: AnalyticsScreen?
    @State private var notificationPath: [NotificationRoute] = []
    @State private var notificationNavigationID: String?
    @State private var notificationNavigationTask: Task<Void, Never>?
    @State private var notificationInboxViewModel = Self.makeNotificationInboxViewModel()
    @State private var feedZoomRegistry = FeedZoomRegistry()
    @State private var homeViewModel = Self.makeHomeViewModel()
    @State private var displayViewModel = Self.makeDisplayViewModel()
    @State private var settingsViewModel = Self.makeSettingsViewModel()
    @State private var notificationSettingsViewModel = Self.makeNotificationSettingsViewModel()
    @State private var recordViewModel = Self.makeRecordViewModel()
    @State private var authRepository = APIAuthRepository(
        baseURL: AppConfiguration().apiBaseURL
    )
    @State private var selectedLegalDocument: LegalDocument?
    @State private var photoUploadViewModel: PhotoUploadViewModel?
    @State private var isPhotoUploadPresented = false
    @State private var feedbackViewModel: FeedbackViewModel?
    @State private var isFeedbackPresented = false
    @State private var photoUploadEntryTask: Task<Void, Never>?
    @State private var photoUploadEntryTaskID: UUID?
    @State private var successSubmission: PhotoUploadSubmission?
    @State private var photoUploadReturnTab: ChalkakBottomBarItem = .home
    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?
    @State private var appVersionGate = AppVersionGateViewModel()
    @AppStorage("notifications.permissionSettingsGuidanceShown")
    private var hasShownNotificationPermissionPrompt = false
    @State private var isNotificationSettingsGuidancePresented = false
    @State private var hasHandledNotificationPermissionAtHomeEntry = false
    private let analyticsTracker: any AnalyticsTracking
    private let pushTapRouter: PushTapRouter
    private let pushDeviceRegistrar: PushDeviceRegistrar

    init(
        analyticsTracker: any AnalyticsTracking = FirebaseAnalyticsTracker(),
        pushTapRouter: PushTapRouter = .shared,
        pushDeviceRegistrar: PushDeviceRegistrar = .shared
    ) {
        self.analyticsTracker = analyticsTracker
        self.pushTapRouter = pushTapRouter
        self.pushDeviceRegistrar = pushDeviceRegistrar
    }

    var body: some View {
        Group {
            switch route {
            case .login:
                LoginView(
                    authRepository: authRepository,
                    onAuthenticated: showMainAfterAuthentication,
                    onGuestAccessGranted: showMain,
                    onSignUpRequired: showOnboarding
                )
            case .onboarding:
                OnboardingRoute(
                    authRepository: authRepository,
                    onFinish: showMainAfterAuthentication,
                    onReauthenticationRequired: showLogin,
                    onServiceTermsView: showServiceTerms,
                    onPrivacyPolicyView: showPrivacyPolicy,
                    onScreenChange: { onboardingAnalyticsScreen = $0 }
                )
            case .main:
                NavigationStack(path: $notificationPath) {
                    mainTab
                        .navigationDestination(item: $selectedIssueURL) { url in
                            IssueWebScreen(url: url, onBack: { selectedIssueURL = nil })
                                .background(InteractivePopGestureEnabler())
                        }
                        .navigationDestination(for: NotificationRoute.self) { destination in
                            switch destination {
                            case .inbox:
                                NotificationScreen(
                                    viewModel: notificationInboxViewModel,
                                    onBackClick: { closeNotificationDestination(destination) },
                                    onSelect: openInboxNotification
                                )
                                .toolbar(.hidden, for: .navigationBar)
                                .background(InteractivePopGestureEnabler())
                            case let .approved(postID), let .aiReview(postID):
                                FeedScreen(
                                    viewModel: makeFeedViewModel(FeedTarget(
                                        postID: postID,
                                        isOwnedByCurrentUser: destination == .aiReview(postID)
                                    )),
                                    usesPhotoTransition: false,
                                    opensAIReviewOnEntry: destination == .aiReview(postID),
                                    onBack: { closeNotificationDestination(destination) },
                                    onDeleted: { postID in
                                        closeNotificationDestination(destination)
                                        handleDeletedPost(postID)
                                    },
                                    onOpenAuthorPosts: openAuthorPosts
                                )
                                .id(postID)
                                .background(InteractivePopGestureEnabler())
                            case let .rejected(target), let .notice(target):
                                NotificationDetailScreen(
                                    viewModel: Self.makeNotificationDetailViewModel(target),
                                    onBackClick: { closeNotificationDestination(destination) },
                                    onNotFound: {
                                        Task { await notificationInboxViewModel.refresh() }
                                    }
                                )
                                .id(target.id)
                                .toolbar(.hidden, for: .navigationBar)
                                .background(InteractivePopGestureEnabler())
                            }
                        }
                }
                // 사진만 확대되며 열리도록 push 대신 탭 화면 위에 Feed를 덮어 띄운다.
                .overlay {
                    if let target = selectedFeed {
                        FeedScreen(
                            viewModel: makeFeedViewModel(target),
                            placeholder: target.placeholder,
                            zoomSource: target.zoomSource,
                            transitionSource: target.transitionSource,
                            onBack: { selectedFeed = nil },
                            onDeleted: handleDeletedPost,
                            onOpenAuthorPosts: openAuthorPosts
                        )
                        .id(target.id)
                        .transition(.identity)
                        .accessibilityAddTraits(.isModal)
                    }
                }
                // 피드 위에 작가 사진 목록을 새 층으로 띄운다. 닫으면 아래 피드가 그대로 남아 있다.
                .overlay {
                    if let authorPostsViewModel = selectedAuthorPosts {
                        // 사인 자리에서 펼치고 접는 전환과 백 스와이프는 화면이 스스로 한다.
                        AuthorPostsScreen(
                            viewModel: authorPostsViewModel,
                            onBack: { selectedAuthorPosts = nil },
                            onSelectPost: { selectedAuthorFeed = $0 }
                        )
                        .id(ObjectIdentifier(authorPostsViewModel))
                        .transition(.identity)
                        .accessibilityAddTraits(.isModal)
                    }
                }
                // 목록에서 연 피드는 목록 위의 또 다른 층이다. 닫으면 목록이 스크롤 위치 그대로 보인다.
                .overlay {
                    if let target = selectedAuthorFeed {
                        FeedScreen(
                            viewModel: makeFeedViewModel(target),
                            placeholder: target.placeholder,
                            zoomSource: target.zoomSource,
                            transitionSource: target.transitionSource,
                            onBack: { selectedAuthorFeed = nil },
                            onDeleted: handleDeletedAuthorPost,
                            onOpenAuthorPosts: openAuthorPosts
                        )
                        .id(target.id)
                        .transition(.identity)
                        .accessibilityAddTraits(.isModal)
                    }
                }
                .environment(\.feedZoomRegistry, feedZoomRegistry)
            case .photoUploadSuccess:
                if let successSubmission {
                    PhotoUploadSuccessScreen(
                        submission: successSubmission,
                        onConfirmClick: showDisplayAfterPhotoUpload
                    )
                } else {
                    mainTab
                }
            }
        }
        .animation(.default, value: route)
        .fullScreenCover(isPresented: $isPhotoUploadPresented, onDismiss: {
            photoUploadViewModel = nil
        }) { [photoUploadViewModel] in
            if let photoUploadViewModel {
                NavigationStack {
                    PhotoUploadRoute(
                        viewModel: photoUploadViewModel,
                        onBack: showPhotoUploadOrigin,
                        onSubmitted: showPhotoUploadSuccess,
                        onReauthenticationRequired: showLogin
                    )
                }
                .chalkakToastHost()
                .interactiveDismissDisabled()
            }
        }
        .fullScreenCover(isPresented: $isFeedbackPresented, onDismiss: {
            feedbackViewModel = nil
        }) { [feedbackViewModel] in
            if let feedbackViewModel {
                NavigationStack {
                    FeedbackScreen(
                        viewModel: feedbackViewModel,
                        onDismiss: closeFeedback,
                        onSubmitted: handleFeedbackSubmitted,
                        onReauthenticationRequired: showLogin
                    )
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            ChalkakCloseButton(action: closeFeedback)
                                .disabled(feedbackViewModel.viewState.isSubmitting)
                                .accessibilityLabel("닫기")
                                .accessibilityIdentifier("feedback.close")
                        }
                        .chalkakNavigationBackground()
                    }
                }
                .chalkakToastHost()
                .interactiveDismissDisabled(feedbackViewModel.viewState.isSubmitting)
            }
        }
        .sheet(item: $selectedLegalDocument) { document in
            LegalDocumentSheet(document: document)
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
        }
        .onReceive(NotificationCenter.default.publisher(for: .authSessionDidRequireReauthentication)) { _ in
            showLogin()
        }
        .onChange(of: pushTapRouter.pendingTap, initial: true) { _, _ in
            handlePendingPushTap()
        }
        .onChange(of: canOpenPushDestination) { _, _ in
            handlePendingPushTap()
        }
        .onChange(of: selectedFeed) { previousFeed, currentFeed in
            guard previousFeed != nil, currentFeed == nil else { return }
            Task {
                await displayViewModel.revalidate()
                await homeViewModel.resume()
            }
        }
        .chalkakToast(message)
        .environment(\.chalkakShowToast, showMessage)
        .alert(
            "업데이트가 필요해요",
            isPresented: Binding(
                get: { appVersionGate.requiredUpdateStoreURL != nil },
                set: { _ in }
            )
        ) {
            if let storeURL = appVersionGate.requiredUpdateStoreURL {
                Button("업데이트") { openURL(storeURL) }
            }
        } message: {
            Text("최신 버전으로 업데이트하면 찰칵을 계속 이용할 수 있어요.")
        }
        .onAppear {
            trackCurrentScreen()
        }
        .onChange(of: currentAnalyticsScreen) { _, _ in
            trackCurrentScreen()
        }
        .onChange(of: selectedTab) { _, _ in
            guard isBottomBarCompact else { return }
            withAnimation(.smooth(duration: 0.75)) {
                isBottomBarCompact = false
            }
        }
        .task {
            await appVersionGate.checkForUpdate()
        }
        .task(id: route) {
            await handleNotificationPermissionAtHomeEntry()
        }
        .task(id: NotificationUnreadRefreshID(
            modelID: ObjectIdentifier(notificationInboxViewModel), isHome: route == .main
        )) {
            guard route == .main, KeychainSessionStore.hasAuthenticatedSession() else { return }
            await notificationInboxViewModel.refreshUnread()
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task {
                await appVersionGate.checkForUpdate()
                guard route == .main, KeychainSessionStore.hasAuthenticatedSession() else { return }
                await notificationInboxViewModel.refreshUnread()
                if isNotificationInboxPresented { await notificationInboxViewModel.refresh() }
            }
            if route == .main, selectedTab == .home {
#if DEBUG
                if ProcessInfo.processInfo.arguments.contains("-test-home-hero-state") || ProcessInfo.processInfo.arguments.contains("-test-home-banner-state") { return }
#endif
                Task { await homeViewModel.resume() }
            }
        }
    }

    private var isNotificationInboxPresented: Bool {
        get { notificationPath.contains(.inbox) }
        nonmutating set {
            withAnimation {
                notificationPath = newValue ? [.inbox] : []
            }
        }
    }

    @MainActor
    private func handleNotificationPermissionAtHomeEntry() async {
        guard route == .main,
              KeychainSessionStore.hasAuthenticatedSession(),
              !hasHandledNotificationPermissionAtHomeEntry else { return }

        hasHandledNotificationPermissionAtHomeEntry = true
        guard !hasShownNotificationPermissionPrompt else { return }

        let model = notificationSettingsViewModel
        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard model === notificationSettingsViewModel,
              route == .main,
              KeychainSessionStore.hasAuthenticatedSession() else { return }

        switch NotificationPermissionPromptAction.resolve(
            status: status,
            hasShownPrompt: hasShownNotificationPermissionPrompt
        ) {
        case .requestSystemPermission:
            do {
                let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
                hasShownNotificationPermissionPrompt = true
                guard model === notificationSettingsViewModel,
                      route == .main,
                      KeychainSessionStore.hasAuthenticatedSession() else { return }
                await applyInitialNotificationChoice(enabled: granted)
            } catch {
                showMessage("알림 권한을 요청하지 못했어요. 기기 설정에서 확인해 주세요.")
            }
        case .showAppPrompt:
            hasShownNotificationPermissionPrompt = true
            isNotificationSettingsGuidancePresented = true
        case .none:
            break
        }
    }

    @MainActor
    private func applyInitialNotificationChoice(enabled: Bool) async {
        let model = notificationSettingsViewModel
        let saved = await model.setAllPushEnabled(enabled)
        guard model === notificationSettingsViewModel,
              route == .main,
              KeychainSessionStore.hasAuthenticatedSession() else { return }

        guard saved else {
            showMessage("알림 설정을 저장하지 못했어요. 앱 설정에서 다시 시도해 주세요.")
            return
        }
        guard enabled else { return }

        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard model === notificationSettingsViewModel,
              route == .main,
              KeychainSessionStore.hasAuthenticatedSession() else { return }
        switch status {
        case .notDetermined:
            do {
                _ = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            } catch {
                showMessage("알림 권한을 요청하지 못했어요. 기기 설정에서 확인해 주세요.")
            }
        case .denied:
            openNotificationSettings()
        case .authorized, .provisional, .ephemeral:
            break
        @unknown default:
            break
        }
    }

    private func openNotificationSettings() {
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        openURL(url)
    }

    private var mainTab: some View {
        ZStack {
            tabContent
        }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                ChalkakBottomBar(
                    selectedItem: selectedTab,
                    isCompact: $isBottomBarCompact,
                    onSelect: selectBottomBarItem,
                    onAdd: { openPhotoUpload(from: selectedTab) }
                )
                .padding(
                    .horizontal,
                    bottomBarHorizontalPadding
                )
                .padding(.bottom, bottomBarBottomPadding)
                .background(bottomBarBackground)
                .animation(.smooth(duration: 0.75), value: isBottomBarCompact)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: {
                    feedZoomRegistry.bottomBarFrame = $0
                }
            }
            .alert("알림을 켤까요?", isPresented: $isNotificationSettingsGuidancePresented) {
                Button("나중에", role: .cancel) {
                    Task { await applyInitialNotificationChoice(enabled: false) }
                }
                Button("알림 켜기") {
                    Task { await applyInitialNotificationChoice(enabled: true) }
                }
            } message: {
                Text("알림을 켜면 오늘의 주제와 게시물 승인·반려 소식을 받아볼 수 있어요.")
            }
    }

    private var bottomBarHorizontalPadding: CGFloat {
        if ChalkakPlatformAppearance.usesLiquidGlass {
            (isBottomBarCompact ? theme.spacing.xxl : theme.spacing.lg) + theme.spacing.xs
        } else {
            0
        }
    }

    private var bottomBarBottomPadding: CGFloat {
        if ChalkakPlatformAppearance.usesLiquidGlass { theme.spacing.sm } else { 0 }
    }

    private var bottomBarBackground: Color {
        if ChalkakPlatformAppearance.usesLiquidGlass { .clear } else { theme.colors.surfaceElevated }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .display:
            DisplayScreen(
                viewModel: displayViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .display) },
                onSelectBottomBarItem: select,
                onSelectPhoto: { selectedFeed = $0 },
                bottomBarCompact: $isBottomBarCompact
            )
        case .settings:
            SettingsScreen(
                viewModel: settingsViewModel,
                notificationSettingsViewModel: notificationSettingsViewModel,
                onLogin: showLogin,
                onPrivacyPolicy: { selectedLegalDocument = .privacyPolicy },
                onTerms: { selectedLegalDocument = .termsOfService },
                onOpenFeedback: openFeedback,
                onSignedOut: showLogin,
                onNavigateToBottomBar: select,
                onOpenPhotoUpload: { openPhotoUpload(from: .settings) },
                onSignatureScreenChange: { signatureAnalyticsScreen = $0 },
                bottomBarCompact: $isBottomBarCompact
            )
        case .record:
            RecordScreen(
                viewModel: recordViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .record) },
                onSelectBottomBarItem: select,
                onOpenDisplay: { openDisplay(at: $0) },
                onOpenFeed: { post in
                    selectedFeed = FeedTarget(
                        postID: post.postId,
                        isOwnedByCurrentUser: true,
                        zoomSource: .record(post.postId),
                        placeholder: FeedPhotoPlaceholder(imageSource: post.thumbnailImageSource)
                    )
                },
                onNavigateToLogin: showLogin,
                bottomBarCompact: $isBottomBarCompact
            )
        default:
            HomeScreen(
                viewModel: homeViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .home) },
                onOpenRecord: { select(.record) },
                onOpenRecordDate: openRecord(at:),
                onOpenDisplay: { date in openDisplay(at: date, sort: .popular) },
                onOpenFeed: openHomeFeed,
                onOpenNotifications: {
                    guard KeychainSessionStore.hasAuthenticatedSession() else {
                        showMessage("로그인 후 알림을 확인할 수 있어요")
                        return
                    }
                    isNotificationInboxPresented = true
                },
                hasUnreadNotifications: notificationInboxViewModel.hasUnread,
                onIssueClick: { selectedIssueURL = $0 },
                bottomBarCompact: $isBottomBarCompact,
                isActive: selectedFeed == nil && notificationPath.isEmpty
                    && selectedIssueURL == nil && !isNotificationInboxPresented && !isFeedbackPresented
            )
            .task(id: ObjectIdentifier(homeViewModel)) {
#if DEBUG
                if ProcessInfo.processInfo.arguments.contains("-test-home-hero-state") || ProcessInfo.processInfo.arguments.contains("-test-home-banner-state") { return }
#endif
                await homeViewModel.resume()
            }
        }
    }

    private func openRecord(at date: Date) {
        guard KeychainSessionStore.hasAuthenticatedSession() else {
            showMessage("기록을 보려면 로그인이 필요해요")
            return
        }
        // 뷰모델을 새로 만들지 않고 날짜만 넘긴다. 새로 만들면 탭 전환 중 재사용된 기록 화면이 불러오지 않아 빈 달력에 멈춘다.
        Task { await recordViewModel.open(date: date) }
        select(.record)
    }

    private func openHomeFeed(_ photo: DisplayPhoto, zoomSource: FeedZoomSource) {
        var isAuthenticated = KeychainSessionStore.hasAuthenticatedSession()
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-test-home-preview") {
            isAuthenticated = false
        }
#endif
        guard isAuthenticated else {
            showMessage("상세를 보려면 로그인이 필요해요")
            return
        }
        selectedFeed = FeedTarget(
            postID: photo.id,
            isOwnedByCurrentUser: photo.isOwnedByCurrentUser,
            zoomSource: zoomSource,
            placeholder: FeedPhotoPlaceholder(
                imageSource: photo.thumbnailImageSource,
                signatureImageSource: nil
            )
        )
    }

    private func makeFeedViewModel(_ target: FeedTarget) -> FeedViewModel {
        let baseURL = Self.resolvedAPIBaseURL
        return FeedViewModel(
            target: target,
            apiClient: FeedAPIClient(
                configuration: FeedAPIConfiguration(baseURL: baseURL),
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            ),
            aiReviewRepository: APIAIReviewRepository(
                client: AIReviewAPIClient(baseURL: baseURL)
            )
        )
    }

    private func selectBottomBarItem(_ item: ChalkakBottomBarItem) {
        if selectedTab == .home, item == .home {
            homeViewModel.scrollToTop()
            Task { await homeViewModel.refresh() }
        } else {
            if selectedTab == .display, item == .display {
                displayViewModel.scrollToTop()
            }
            select(item)
        }
    }

    private func select(_ item: ChalkakBottomBarItem) {
        guard item == .home || item == .display || item == .record || item == .settings else { return }

        if item == .record, !KeychainSessionStore.hasAuthenticatedSession() {
            showMessage("기록을 보려면 로그인이 필요해요")
            return
        }

        analyticsTracker.trackBottomNavigationSelection(destination: item.rawValue)

        let shouldRevalidateDisplay = item == .display
            && displayViewModel.viewState.contentStatus != .loading
        let shouldRevalidateRecord = item == .record
            && recordViewModel.viewState.contentStatus != .loading

        selectedTab = item

        if shouldRevalidateDisplay {
            Task { await displayViewModel.revalidate() }
        } else if shouldRevalidateRecord {
            Task { await recordViewModel.revalidate() }
        }
    }

    private func openDisplay(at date: Date, sort: DisplaySort = .latest) {
        displayViewModel = Self.makeDisplayViewModel(initialDate: date, initialSort: sort)
        selectedTab = .display
    }

    private func handleDeletedPost(_ postID: FeedPost.ID) {
        selectedFeed = nil
        showMessage("게시물이 삭제됐어요")
        recordViewModel.removeDeletedPost(postID)
    }

    private func openAuthorPosts(_ target: AuthorPostsTarget) {
        // 작가 사진 목록은 로그인이 필요하다(POST-007). 비로그인 피드에서도 사인은 보이므로 안내만 띄운다.
        guard KeychainSessionStore.hasAuthenticatedSession() else {
            showMessage("다른 사진도 보려면 로그인이 필요해요")
            return
        }
        let viewModel = AuthorPostsViewModel(
            target: target,
            apiClient: AuthorPostsAPIClient(
                configuration: AuthorPostsAPIConfiguration(baseURL: Self.resolvedAPIBaseURL),
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            )
        )
        // 목록에서 연 피드의 사인은 같은 작가라 돌아가기로 처리된다. 여기 오는 건 피드에서 처음 여는 경우다.
        selectedAuthorFeed = nil
        selectedAuthorPosts = viewModel
    }

    // 목록에서 연 피드에서 내 사진을 지웠을 때. 목록에서 빼고, 목록을 연 피드도 같은 사진이면 함께 닫는다.
    private func handleDeletedAuthorPost(_ postID: FeedPost.ID) {
        selectedAuthorFeed = nil
        selectedAuthorPosts?.removePost(postID)
        if selectedFeed?.id == postID {
            selectedAuthorPosts = nil
            handleDeletedPost(postID)
            return
        }
        if let destination = notificationPath.last,
           destination == .approved(postID) || destination == .aiReview(postID) {
            selectedAuthorPosts = nil
            closeNotificationDestination(destination)
        }
        showMessage("게시물이 삭제됐어요")
        recordViewModel.removeDeletedPost(postID)
    }

    private func showMain() {
        resetMainState()
        route = .main
    }

    private func showMainAfterAuthentication() {
        pushDeviceRegistrar.registerCurrentDevice()
        showMain()
    }

    // 작성 중인 화면을 닫지 않도록, 홈이 보이고 다른 전체 화면이 없을 때만 푸시의 화면으로 이동한다.
    private var canOpenPushDestination: Bool {
        route == .main
            && !isPhotoUploadPresented
            && !isFeedbackPresented
    }

    private func handlePendingPushTap() {
        guard pushTapRouter.pendingTap != nil else { return }
        // 로그아웃 상태의 클릭은 이동 정책이 정해지지 않아 화면을 바꾸지 않는다.
        guard KeychainSessionStore.hasAuthenticatedSession() else {
            _ = pushTapRouter.consume()
            return
        }
        guard canOpenPushDestination, let tap = pushTapRouter.consume() else { return }

        if tap == .topic {
            notificationNavigationTask?.cancel()
            notificationNavigationTask = nil
            notificationNavigationID = nil
            openNotificationDestination(tap, fromInbox: false)
            return
        }
        guard let notificationID = tap.notificationID,
              notificationNavigationID != notificationID else { return }
        notificationNavigationID = notificationID
        let inbox = notificationInboxViewModel
        notificationNavigationTask?.cancel()
        notificationNavigationTask = Task {
            defer {
                if notificationNavigationID == notificationID { notificationNavigationID = nil }
            }
            let canNavigate = await inbox.readForNavigation(notificationID)
            guard !Task.isCancelled, inbox === notificationInboxViewModel, route == .main,
                  KeychainSessionStore.hasAuthenticatedSession() else { return }
            guard canNavigate else {
                if let message = inbox.message { showMessage(message) }
                return
            }
            openNotificationDestination(tap, fromInbox: false)
            if let message = inbox.message { showMessage(message) }
        }
    }

    private func openInboxNotification(_ item: NotificationItem) {
        guard let tap = item.destination else {
            showMessage("이 알림은 아직 열 수 없어요")
            return
        }
        guard let notificationID = tap.notificationID,
              notificationNavigationID != notificationID else { return }
        notificationNavigationID = notificationID
        let inbox = notificationInboxViewModel
        notificationNavigationTask?.cancel()
        notificationNavigationTask = Task {
            defer {
                if notificationNavigationID == notificationID { notificationNavigationID = nil }
            }
            let canNavigate = await inbox.readForNavigation(item.id)
            guard !Task.isCancelled, inbox === notificationInboxViewModel, route == .main,
                  isNotificationInboxPresented, KeychainSessionStore.hasAuthenticatedSession() else { return }
            guard canNavigate else { return }
            openNotificationDestination(tap, fromInbox: true)
        }
    }

    private func closeNotificationDestination(_ destination: NotificationRoute) {
        guard notificationPath.last == destination else { return }
        withAnimation {
            notificationPath.removeLast()
        }
    }

    private func openNotificationDestination(_ tap: PushNotificationTap, fromInbox: Bool) {
        selectedFeed = nil
        selectedAuthorPosts = nil
        selectedAuthorFeed = nil
        withAnimation {
            notificationPath = fromInbox ? [.inbox] : []
            switch tap {
            case .topic:
                // 주제 푸시는 클릭한 날의 오늘 주제가 보이는 홈으로 이동한다.
                notificationPath = []
                selectedTab = .home
                homeViewModel.scrollToTop()
                Task { await homeViewModel.refresh() }
            case let .postApproved(postID, _):
                notificationPath.append(.approved(postID))
            case let .aiReview(postID, _):
                notificationPath.append(.aiReview(postID))
            case let .postRejected(notificationID):
                notificationPath.append(.rejected(NotificationDetailTarget(id: notificationID)))
            case let .notice(notificationID):
                notificationPath.append(.notice(NotificationDetailTarget(id: notificationID)))
            }
        }
    }

    private func showOnboarding() {
        onboardingAnalyticsScreen = AnalyticsScreen(name: "terms", screenClass: "Terms")
        route = .onboarding
    }

    private func openPhotoUpload(from tab: ChalkakBottomBarItem) {
        guard KeychainSessionStore.hasAuthenticatedSession() || Self.isPhotoUploadEntryUITest else {
            showMessage("게시물을 추가하려면 로그인이 필요해요")
            return
        }

        guard photoUploadEntryTask == nil else { return }

        let entryGate = Self.makePhotoUploadEntryGate()
        let taskID = UUID()
        photoUploadEntryTaskID = taskID
        photoUploadEntryTask = Task { @MainActor in
            let outcome = await entryGate.check()

            guard photoUploadEntryTaskID == taskID else { return }
            photoUploadEntryTask = nil
            photoUploadEntryTaskID = nil

            guard Task.isCancelled == false else { return }
            handlePhotoUploadEntry(outcome, from: tab)
        }
    }

    private func handlePhotoUploadEntry(
        _ outcome: PhotoUploadEntryOutcome,
        from tab: ChalkakBottomBarItem
    ) {
        switch outcome {
        case let .allowed(topicDate):
            photoUploadReturnTab = tab
            photoUploadViewModel = Self.makePhotoUploadViewModel(topicDate: topicDate) { message in
                showMessage(message)
            }
            isPhotoUploadPresented = true
        case .reauthenticationRequired:
            showLogin()
        case .cancelled:
            break
        case .alreadyPosted, .noActiveTopic, .suspended, .failed:
            if let message = outcome.message {
                showMessage(message)
            }
        }
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

    private func showPhotoUploadSuccess(_ submission: PhotoUploadSubmission) {
        successSubmission = submission
        isPhotoUploadPresented = false
        route = .photoUploadSuccess
    }

    private func showDisplayAfterPhotoUpload() {
        guard let submission = successSubmission else { return }

        displayViewModel = Self.makeDisplayViewModel(
            initialDate: submission.content.date
        )
        successSubmission = nil
        photoUploadViewModel = nil
        isPhotoUploadPresented = false
        selectedTab = .display
        route = .main
    }

    private func showPhotoUploadOrigin() {
        isPhotoUploadPresented = false
        route = .main
        selectedTab = photoUploadReturnTab
    }

    private func openFeedback() {
        guard KeychainSessionStore.hasAuthenticatedSession() || Self.isFeedbackEntryUITest else {
            showMessage("피드백을 보내려면 로그인이 필요해요")
            return
        }

        feedbackViewModel = Self.makeFeedbackViewModel()
        isFeedbackPresented = true
    }

    private func closeFeedback() {
        isFeedbackPresented = false
    }

    private func handleFeedbackSubmitted() {
        closeFeedback()
        showMessage("피드백을 보내주셔서 감사해요.")
    }

    private func showServiceTerms() {
        selectedLegalDocument = .termsOfService
    }

    private func showPrivacyPolicy() {
        selectedLegalDocument = .privacyPolicy
    }

    private func showLogin() {
        hasHandledNotificationPermissionAtHomeEntry = false
        photoUploadEntryTask?.cancel()
        photoUploadEntryTask = nil
        photoUploadEntryTaskID = nil
        selectedLegalDocument = nil
        photoUploadViewModel = nil
        isPhotoUploadPresented = false
        feedbackViewModel = nil
        isFeedbackPresented = false
        successSubmission = nil
        resetMainState()
        route = .login
    }

    private func resetMainState() {
        notificationNavigationTask?.cancel()
        notificationNavigationTask = nil
        notificationNavigationID = nil
        homeViewModel.cancel()
        selectedTab = .home
        selectedFeed = nil
        selectedAuthorPosts = nil
        selectedAuthorFeed = nil
        selectedIssueURL = nil
        signatureAnalyticsScreen = nil
        isNotificationInboxPresented = false
        isPhotoUploadPresented = false
        feedbackViewModel = nil
        isFeedbackPresented = false
        homeViewModel = Self.makeHomeViewModel()
        displayViewModel = Self.makeDisplayViewModel()
        recordViewModel = Self.makeRecordViewModel()
        notificationSettingsViewModel.reset()
        settingsViewModel = Self.makeSettingsViewModel()
        notificationSettingsViewModel = Self.makeNotificationSettingsViewModel()
        notificationInboxViewModel = Self.makeNotificationInboxViewModel()
    }

    private static func makeNotificationSettingsViewModel() -> NotificationSettingsViewModel {
        let client = NotificationSettingsAPIClient(
            baseURL: AppConfiguration().apiBaseURL,
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return NotificationSettingsViewModel(
            loadSettings: { try await client.fetch() },
            saveSettings: { topic, moderation, aiReview in
                try await client.update(topicPushEnabled: topic, moderationPushEnabled: moderation, aiReviewPushEnabled: aiReview)
            },
        )
    }

    private static func makeHomeViewModel() -> HomeViewModel {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-test-home-preview") {
            var state = HomePreviewData.contentState
            var loader = HomePreviewData.loader
            let arguments = ProcessInfo.processInfo.arguments
            if let index = arguments.firstIndex(of: "-test-home-banner-state"), arguments.indices.contains(index + 1) {
                state.banners = []
                switch arguments[index + 1] {
                case "loading": state.bannerStatus = .loading
                case "error": state.bannerStatus = .error
                case "pagination":
                    let firstPage = HomePreviewData.paginatedBannerPage(page: 1, pageSize: 10)
                    state.banners = firstPage.banners
                    state.bannerStatus = .ready
                    state.bannerCurrentPage = firstPage.currentPage
                    state.bannerHasNext = firstPage.hasNext
                    loader.banners = { page, pageSize in
                        await HomePreviewData.paginatedBannerPage(page: page, pageSize: pageSize)
                    }
                default: state.bannerStatus = .ready
                }
            }
            if let index = arguments.firstIndex(of: "-test-home-hero-state"), arguments.indices.contains(index + 1) {
                switch arguments[index + 1] {
                case "empty": state.hero = .empty
                case "loading": state.hero = .loading
                case "processing": state.hero = .processing
                case "error": state.hero = .error
                case "no-open-topic": state.hero = .noOpenTopic
                case "rejected-open": state.hero = .rejected(canResubmit: true)
                case "rejected-closed": state.hero = .rejected(canResubmit: false)
                case "pending":
                    let photo = HomePreviewData.heroPhoto
                    state.hero = .photo(HomeHeroPhoto(imageSource: photo.imageSource, thumbnailSource: photo.thumbnailSource, signatureSource: nil, isPending: true))
                default: break
                }
                switch state.hero {
                case .photo:
                    break
                default:
                    state.week = state.week.map { day in
                        RecordMonth.calendar.isDate(day.date, inSameDayAs: state.date)
                            ? HomeWeekDay(date: day.date)
                            : day
                    }
                }
            }
            return HomePreviewData.viewModel(state: state, loader: loader)
        }
#endif
        return HomeViewModel(
            loader: .live(baseURL: resolvedAPIBaseURL),
            aiReviewRepository: APIAIReviewRepository(
                client: AIReviewAPIClient(baseURL: resolvedAPIBaseURL)
            )
        )
    }

    private static func makeDisplayViewModel(initialDate: Date? = nil, initialSort: DisplaySort = .latest) -> DisplayViewModel {
        DisplayViewModel(
            initialDate: initialDate,
            initialSort: initialSort,
            apiClient: DisplayAPIClient(
                configuration: DisplayAPIConfiguration(baseURL: resolvedAPIBaseURL),
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            )
        )
    }

    private static func makeRecordViewModel() -> RecordViewModel {
        RecordViewModel(
            apiClient: RecordAPIClient(
                configuration: RecordAPIConfiguration(baseURL: resolvedAPIBaseURL),
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            )
        )
    }

    private static func makeSettingsViewModel() -> SettingsViewModel {
#if DEBUG
        if isPhotoUploadEntryUITest {
            return SettingsViewModel(
                initialState: SettingsViewState(
                    isLoading: false,
                    isLoggedIn: true,
                    version: AppVersion.currentString()
                ),
                isAuthenticated: { true }
            )
        }
#endif

        let configuration = AppConfiguration()
        let apiClient = SettingsAPIClient(
            baseURL: configuration.apiBaseURL,
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        let authRepository = APIAuthRepository(baseURL: configuration.apiBaseURL)
        return SettingsViewModel(
            initialState: SettingsViewState(version: AppVersion.currentString()),
            isAuthenticated: { KeychainSessionStore.hasAuthenticatedSession() },
            loadSignature: { try await apiClient.fetchSignature() },
            updateSignature: { try await apiClient.updateSignature(pngData: $0) },
            logout: { await authRepository.logout() },
            withdraw: {
                try await apiClient.withdraw()
                KeychainSessionStore.delete()
            }
        )
    }

    private static func makeNotificationAPIClient() -> NotificationAPIClient {
        NotificationAPIClient(
            baseURL: AppConfiguration().apiBaseURL,
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
    }

    private static func makeNotificationInboxViewModel() -> NotificationInboxViewModel {
        let client = makeNotificationAPIClient()
        return NotificationInboxViewModel(
            loadPage: { try await client.fetchPage(page: $0) },
            loadUnread: { try await client.fetchUnreadStatus() },
            markRead: { try await client.markRead(notificationID: $0) }
        )
    }

    private static func makeNotificationDetailViewModel(
        _ target: NotificationDetailTarget
    ) -> NotificationDetailViewModel {
        let apiClient = makeNotificationAPIClient()
        return NotificationDetailViewModel(
            loadDetail: { try await apiClient.fetchDetail(notificationID: target.id) }
        )
    }

    private static func makeFeedbackViewModel() -> FeedbackViewModel {
#if DEBUG
        if isFeedbackEntryUITest {
            return FeedbackViewModel(submitFeedback: { _ in })
        }
#endif
        let apiClient = FeedbackAPIClient(
            configuration: FeedbackAPIConfiguration(baseURL: resolvedAPIBaseURL),
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return FeedbackViewModel(
            submitFeedback: { content in
                _ = try await apiClient.submitFeedback(content: content)
            }
        )
    }

    private static func makePhotoUploadViewModel(
        topicDate: Date,
        onCameraImageSaveFailure: @escaping PhotoUploadViewModel.CameraImageSaveFailureHandler
    ) -> PhotoUploadViewModel {
#if DEBUG
        if isPhotoUploadEntryUITest {
            return PhotoUploadViewModel(
                topicDate: topicDate,
                repository: PhotoUploadRepository(
                    getCreationTopic: { date in
                        .success(PhotoUploadTopic(id: "ui-test-topic", title: "테스트", date: date))
                    }
                ),
                onCameraImageSaveFailure: onCameraImageSaveFailure
            )
        }
#endif

        let appConfiguration = AppConfiguration()
        let apiClient = PhotoUploadAPIClient(
            configuration: PhotoUploadAPIConfiguration(
                baseURL: appConfiguration.apiBaseURL
                    ?? PhotoUploadAPIConfiguration.development.baseURL
            ),
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return PhotoUploadViewModel(
            topicDate: topicDate,
            repository: .api(client: apiClient),
            onCameraImageSaveFailure: onCameraImageSaveFailure
        )
    }

    private static func makePhotoUploadEntryGate() -> PhotoUploadEntryGate {
#if DEBUG
        if isPhotoUploadEntryUITest {
            return PhotoUploadEntryGate {
                PhotoUploadTodayPostStatus(
                    topicDate: PhotoUploadDate.today(),
                    isPosted: false,
                    postID: nil,
                    moderationStatus: nil
                )
            }
        }
#endif

        let appConfiguration = AppConfiguration()
        let apiClient = PhotoUploadAPIClient(
            configuration: PhotoUploadAPIConfiguration(
                baseURL: appConfiguration.apiBaseURL
                    ?? PhotoUploadAPIConfiguration.development.baseURL
            ),
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return .api(client: apiClient)
    }

    private static var resolvedAPIBaseURL: URL {
        AppConfiguration().apiBaseURL ?? PhotoUploadAPIConfiguration.development.baseURL
    }

    private static var isPhotoUploadEntryUITest: Bool {
#if DEBUG
        ProcessInfo.processInfo.arguments.contains("-test-photo-upload-entry")
#else
        false
#endif
    }

    private static var initialRoute: AppRoute {
#if DEBUG
        if isPhotoUploadEntryUITest || isFeedbackEntryUITest
            || ProcessInfo.processInfo.arguments.contains("-test-home-preview") {
            return .main
        }
        if ProcessInfo.processInfo.arguments.contains(where: { $0.hasPrefix("-show-onboarding") }) {
            return .onboarding
        }
#endif
        guard KeychainSessionStore.hasActiveSession() else { return .login }
        return .main
    }

    private static var isFeedbackEntryUITest: Bool {
#if DEBUG
        ProcessInfo.processInfo.arguments.contains("-test-feedback-entry")
#else
        false
#endif
    }

    private var currentAnalyticsScreen: AnalyticsScreen? {
        if let selectedLegalDocument {
            return switch selectedLegalDocument {
            case .privacyPolicy: AnalyticsScreen(name: "privacy_policy", screenClass: "PrivacyPolicy")
            case .termsOfService: AnalyticsScreen(name: "terms_of_service", screenClass: "TermsOfService")
            }
        }
        if isFeedbackPresented {
            return AnalyticsScreen(name: "feedback", screenClass: "Feedback")
        }
        if isPhotoUploadPresented {
            return AnalyticsScreen(name: "photo_upload", screenClass: "PhotoUpload")
        }
        switch route {
        case .login:
            return AnalyticsScreen(name: "login", screenClass: "Login")
        case .onboarding:
            return onboardingAnalyticsScreen
        case .photoUploadSuccess:
            return successSubmission == nil
                ? selectedTab.analyticsScreen
                : AnalyticsScreen(name: "photo_upload_success", screenClass: "PhotoUploadSuccess")
        case .main:
            break
        }
        if let signatureAnalyticsScreen {
            return signatureAnalyticsScreen
        }
        if selectedAuthorFeed != nil {
            return AnalyticsScreen(name: "feed", screenClass: "Feed")
        }
        if selectedAuthorPosts != nil {
            return AnalyticsScreen(name: "author_posts", screenClass: "AuthorPosts")
        }
        if selectedFeed != nil {
            return AnalyticsScreen(name: "feed", screenClass: "Feed")
        }
        if let notificationDestination = notificationPath.last {
            switch notificationDestination {
            case .inbox:
                return AnalyticsScreen(name: "notifications", screenClass: "Notifications")
            case .approved, .aiReview:
                return AnalyticsScreen(name: "feed", screenClass: "Feed")
            case .rejected, .notice:
                return AnalyticsScreen(name: "notification_detail", screenClass: "NotificationDetail")
            }
        }
        if selectedIssueURL != nil {
            return AnalyticsScreen(name: "issue", screenClass: "IssueWeb")
        }
        return selectedTab.analyticsScreen
    }

    private func trackCurrentScreen() {
        guard let screen = currentAnalyticsScreen else { return }
        analyticsTracker.trackScreenView(
            screenName: screen.name,
            screenClass: screen.screenClass
        )
    }
}

enum AppRoute: Equatable {
    case login
    case onboarding
    case main
    case photoUploadSuccess
}

private extension ChalkakBottomBarItem {
    var analyticsScreen: AnalyticsScreen {
        switch self {
        case .home:
            AnalyticsScreen(name: "home", screenClass: "Home")
        case .display:
            AnalyticsScreen(name: "display", screenClass: "Display")
        case .record:
            AnalyticsScreen(name: "record", screenClass: "Record")
        case .settings:
            AnalyticsScreen(name: "settings", screenClass: "Settings")
        }
    }
}

private enum ContentMetrics {
}

#Preview {
    ContentView()
        .chalkakTheme(.light)
}

private struct NotificationUnreadRefreshID: Hashable {
    let modelID: ObjectIdentifier
    let isHome: Bool
}

private enum NotificationRoute: Hashable {
    case inbox
    case approved(String)
    case aiReview(String)
    case rejected(NotificationDetailTarget)
    case notice(NotificationDetailTarget)
}


enum NotificationPermissionPromptAction: Equatable {
    case requestSystemPermission
    case showAppPrompt
    case none

    static func resolve(
        status: UNAuthorizationStatus,
        hasShownPrompt: Bool
    ) -> NotificationPermissionPromptAction {
        guard !hasShownPrompt else { return .none }

        return switch status {
        case .notDetermined:
            .requestSystemPermission
        case .denied, .authorized, .provisional, .ephemeral:
            .showAppPrompt
        @unknown default:
            .none
        }
    }
}
