//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @State private var route: AppRoute = Self.initialRoute
    @State private var selectedTab: ChalkakBottomBarItem = .home
    @State private var isBottomBarCompact = false
    @State private var selectedFeed: FeedTarget?
    @State private var isNotificationInboxPresented = false
    @State private var feedZoomRegistry = FeedZoomRegistry()
    @State private var homeViewModel = Self.makeHomeViewModel()
    @State private var displayViewModel = Self.makeDisplayViewModel()
    @State private var settingsViewModel = Self.makeSettingsViewModel()
    @State private var recordViewModel = Self.makeRecordViewModel()
    @State private var notificationSetupViewModel = NotificationSetupViewModel()
    @State private var isNotificationSetupPresented = false
    @State private var hasPresentedInitialNotificationSetup = false
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
    private let analyticsTracker: any AnalyticsTracking

    init(analyticsTracker: any AnalyticsTracking = FirebaseAnalyticsTracker()) {
        self.analyticsTracker = analyticsTracker
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
                    onPrivacyPolicyView: showPrivacyPolicy
                )
            case .main:
                NavigationStack {
                    mainTab
                        .navigationDestination(isPresented: $isNotificationInboxPresented) {
                            NotificationScreen(onBackClick: { isNotificationInboxPresented = false })
                                .toolbar(.hidden, for: .navigationBar)
                                .background(InteractivePopGestureEnabler())
                        }
                }
                // 사진만 확대되며 열리도록 push 대신 탭 화면 위에 Feed를 덮어 띄운다.
                .overlay {
                    if let target = selectedFeed {
                        FeedScreen(
                            viewModel: makeFeedViewModel(target),
                            placeholder: target.placeholder,
                            zoomSource: target.zoomSource,
                            onBack: { selectedFeed = nil },
                            onDeleted: handleDeletedPost
                        )
                        .id(target.id)
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
                .interactiveDismissDisabled()
            }
        }
        .fullScreenCover(isPresented: $isNotificationSetupPresented) {
            NavigationStack {
                NotificationSetupScreen(
                    viewModel: notificationSetupViewModel,
                    onFinish: { isNotificationSetupPresented = false },
                    topPadding: theme.spacing.xl,
                    onDismiss: { isNotificationSetupPresented = false }
                )
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        ChalkakCloseButton {
                            isNotificationSetupPresented = false
                        }
                        .accessibilityLabel("닫기")
                        .accessibilityIdentifier("notificationSetup.close")
                    }
                    .chalkakNavigationBackground()
                }
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
        .onReceive(NotificationCenter.default.publisher(for: .dailyReminderNotificationTapped)) { _ in
            showMain()
        }
        .onChange(of: selectedFeed) { previousFeed, currentFeed in
            guard previousFeed != nil, currentFeed == nil else { return }
            Task {
                await displayViewModel.revalidate()
                await homeViewModel.resume()
            }
        }
        .overlay(alignment: .bottom) {
            if let message {
                Text(message)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(theme.colors.actionPrimary, in: Capsule())
                    .padding(.bottom, ContentMetrics.messageBottomPadding)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .accessibilityLabel(message)
            }
        }
        .overlay {
            if let storeURL = appVersionGate.requiredUpdateStoreURL {
                ChalkakConfirmDialog(
                    title: "업데이트가 필요해요",
                    message: "원활한 서비스 이용을 위해 최신 버전으로 업데이트해 주세요.",
                    confirmText: "업데이트",
                    confirmStyle: .primary,
                    isDismissible: false,
                    onConfirm: { openURL(storeURL) },
                    onDismiss: {}
                )
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .animation(.easeOut(duration: 0.16), value: appVersionGate.requiredUpdateStoreURL)
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
#if DEBUG
        .task {
            guard Self.isDailyReminderTapUITest else { return }
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            NotificationCenter.default.post(name: .dailyReminderNotificationTapped, object: nil)
        }
#endif
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await appVersionGate.checkForUpdate() }
            if route == .main, selectedTab == .home {
                Task { await homeViewModel.resume() }
            }
        }
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
                onLogin: showLogin,
                onPrivacyPolicy: { selectedLegalDocument = .privacyPolicy },
                onTerms: { selectedLegalDocument = .termsOfService },
                onOpenFeedback: openFeedback,
                onSignedOut: showLogin,
                onNavigateToBottomBar: select,
                onOpenPhotoUpload: { openPhotoUpload(from: .settings) },
                onOpenNotificationSetup: openNotificationSetup,
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
                onOpenDisplay: { date in openDisplay(at: date, sort: .popular) },
                onOpenFeed: openHomeFeed,
                onOpenNotifications: { isNotificationInboxPresented = true },
                onIssueClick: { showMessage("콘텐츠는 준비 중이에요") },
                bottomBarCompact: $isBottomBarCompact
            )
            .onAppear(perform: presentInitialNotificationSetupIfNeeded)
            .task(id: ObjectIdentifier(homeViewModel)) {
                await homeViewModel.resume()
            }
        }
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
            )
        )
    }

    private func selectBottomBarItem(_ item: ChalkakBottomBarItem) {
        if selectedTab == .home, item == .home {
            homeViewModel.scrollToTop()
            Task { await homeViewModel.refresh() }
        } else {
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

    private func showMain() {
        resetMainState()
        route = .main
    }

    private func showMainAfterAuthentication() {
        hasPresentedInitialNotificationSetup = false
        showMain()
    }

    private func presentInitialNotificationSetupIfNeeded() {
        guard route == .main, selectedTab == .home else { return }
#if DEBUG
        let isAuthenticated = KeychainSessionStore.hasAuthenticatedSession()
            || ProcessInfo.processInfo.arguments.contains("-show-notification-setup")
#else
        let isAuthenticated = KeychainSessionStore.hasAuthenticatedSession()
#endif
        guard AppRouteResolver.shouldPresentNotificationSetup(
            isAuthenticated: isAuthenticated,
            hasCompletedNotificationSetup: NotificationSetupStore().hasCompletedSetup,
            hasPresentedDuringSession: hasPresentedInitialNotificationSetup
        ) else { return }
        hasPresentedInitialNotificationSetup = true
        notificationSetupViewModel = NotificationSetupViewModel()
        isNotificationSetupPresented = true
    }

    private func openNotificationSetup() {
        notificationSetupViewModel = NotificationSetupViewModel()
        selectedTab = .settings
        isNotificationSetupPresented = true
    }

    private func showOnboarding() {
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
        homeViewModel.cancel()
        selectedTab = .home
        selectedFeed = nil
        isNotificationInboxPresented = false
        isNotificationSetupPresented = false
        isPhotoUploadPresented = false
        feedbackViewModel = nil
        isFeedbackPresented = false
        homeViewModel = Self.makeHomeViewModel()
        displayViewModel = Self.makeDisplayViewModel()
        recordViewModel = Self.makeRecordViewModel()
        settingsViewModel = Self.makeSettingsViewModel()
    }

    private static func makeHomeViewModel() -> HomeViewModel {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-test-home-preview") {
            return HomePreviewData.viewModel(state: HomePreviewData.contentState)
        }
        if isDailyReminderTapUITest {
            let loader = HomeDataLoader(
                today: { date in DisplayContent(topicDate: date, topic: "오늘의 주제", page: DisplayPage(photos: [], currentPage: 1, hasNext: false, randomSeed: nil)) },
                ranking: { _ in [] },
                calendar: { month in RecordCalendar(month: month, posts: []) },
                detail: { _ in throw FeedError.notFound },
                postStatus: { throw PhotoUploadAPIError.http(statusCode: 404, message: nil) }
            )
            return HomeViewModel(loader: loader, isAuthenticated: { false })
        }
#endif
        return HomeViewModel(loader: .live(baseURL: resolvedAPIBaseURL))
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

    private static var isDailyReminderTapUITest: Bool {
#if DEBUG
        ProcessInfo.processInfo.arguments.contains("-test-daily-reminder-tap")
#else
        false
#endif
    }

    private static var initialRoute: AppRoute {
#if DEBUG
        if isPhotoUploadEntryUITest || isFeedbackEntryUITest || isDailyReminderTapUITest
            || ProcessInfo.processInfo.arguments.contains("-test-home-preview") {
            return .main
        }
        if ProcessInfo.processInfo.arguments.contains(where: { $0.hasPrefix("-show-onboarding") }) {
            return .onboarding
        }
        if ProcessInfo.processInfo.arguments.contains("-show-notification-setup") {
            return .main
        }
#endif
        return AppRouteResolver.initialDestination(
            hasActiveSession: KeychainSessionStore.hasActiveSession()
        )
    }

    private static var isFeedbackEntryUITest: Bool {
#if DEBUG
        ProcessInfo.processInfo.arguments.contains("-test-feedback-entry")
#else
        false
#endif
    }

    private var currentAnalyticsScreen: AnalyticsScreen? {
        guard route == .main, !isPhotoUploadPresented else { return nil }
        if selectedFeed != nil {
            return AnalyticsScreen(name: "feed", screenClass: "Feed")
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

enum AppRouteResolver {
    static func initialDestination(hasActiveSession: Bool) -> AppRoute {
        hasActiveSession ? .main : .login
    }

    static func shouldPresentNotificationSetup(
        isAuthenticated: Bool,
        hasCompletedNotificationSetup: Bool,
        hasPresentedDuringSession: Bool
    ) -> Bool {
        isAuthenticated && !hasCompletedNotificationSetup && !hasPresentedDuringSession
    }
}

private struct AnalyticsScreen: Equatable {
    let name: String
    let screenClass: String
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
    static let messageBottomPadding: CGFloat = 32
}

#Preview {
    ContentView()
        .chalkakTheme(.light)
}
