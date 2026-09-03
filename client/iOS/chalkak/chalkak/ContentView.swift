//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @State private var route: AppRoute = Self.initialRoute
    @State private var selectedTab: ChalkakBottomBarItem = .today
    @State private var selectedFeed: FeedTarget?
    @State private var homeViewModel = Self.makeHomeViewModel()
    @State private var displayViewModel = Self.makeDisplayViewModel()
    @State private var settingsViewModel = Self.makeSettingsViewModel()
    @State private var recordViewModel = Self.makeRecordViewModel()
    @State private var authRepository = APIAuthRepository(
        baseURL: AppConfiguration().apiBaseURL
    )
    @State private var selectedLegalDocument: LegalDocument?
    @State private var photoUploadViewModel: PhotoUploadViewModel?
    @State private var successSubmission: PhotoUploadSubmission?
    @State private var photoUploadReturnTab: ChalkakBottomBarItem = .today

    var body: some View {
        Group {
            switch route {
            case .login:
                LoginView(
                    authRepository: authRepository,
                    onAuthenticated: showHome,
                    onGuestAccessGranted: showHome,
                    onSignUpRequired: showOnboarding
                )
            case .onboarding:
                OnboardingRoute(
                    authRepository: authRepository,
                    onFinish: showHome,
                    onReauthenticationRequired: showLogin,
                    onServiceTermsView: showServiceTerms,
                    onPrivacyPolicyView: showPrivacyPolicy
                )
            case .home:
                NavigationStack {
                    mainTab
                        .navigationDestination(item: $selectedFeed) { target in
                            FeedScreen(viewModel: makeFeedViewModel(target))
                        }
                }
            case .photoUpload:
                if let photoUploadViewModel {
                    PhotoUploadRoute(
                        viewModel: photoUploadViewModel,
                        onBack: showPhotoUploadOrigin,
                        onSubmitted: showPhotoUploadSuccess,
                        onReauthenticationRequired: showLogin
                    )
                } else {
                    Color.clear
                        .task { showHome() }
                }
            case .photoUploadSuccess:
                if let successSubmission {
                    PhotoUploadSuccessScreen(
                        submission: successSubmission,
                        onConfirmClick: closePhotoUploadSuccess
                    )
                } else {
                    mainTab
                }
            }
        }
        .animation(.default, value: route)
        .sheet(item: $selectedLegalDocument) { document in
            LegalDocumentSheet(document: document)
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
        }
    }

    @ViewBuilder
    private var mainTab: some View {
        switch selectedTab {
        case .display:
            DisplayScreen(
                viewModel: displayViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .display) },
                onSelectBottomBarItem: select,
                onSelectPhoto: { selectedFeed = $0 }
            )
        case .settings:
            SettingsScreen(
                viewModel: settingsViewModel,
                onLogin: showLogin,
                onPrivacyPolicy: { selectedLegalDocument = .privacyPolicy },
                onTerms: { selectedLegalDocument = .termsOfService },
                onSignedOut: showLogin,
                onNavigateToBottomBar: select
            )
        case .record:
            RecordScreen(
                viewModel: recordViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .record) },
                onSelectBottomBarItem: select,
                onOpenDisplay: openDisplay,
                onOpenFeed: { selectedFeed = FeedTarget(postID: $0) },
                onNavigateToLogin: showLogin
            )
        default:
            HomeScreen(
                viewModel: homeViewModel,
                onOpenPhotoUpload: { openPhotoUpload(from: .today) },
                onNavigateToBottomBar: select,
                onSelectPhoto: { selectedFeed = $0 }
            )
            .task {
                guard homeViewModel.viewState.contentStatus == .loading else { return }
                await homeViewModel.retry()
            }
        }
    }

    private func makeFeedViewModel(_ target: FeedTarget) -> FeedViewModel {
        FeedViewModel(
            target: target,
            apiClient: FeedAPIClient(
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            )
        )
    }

    private func select(_ item: ChalkakBottomBarItem) {
        guard item == .today || item == .display || item == .record || item == .settings else { return }
        selectedTab = item
    }

    private func openDisplay(at date: Date) {
        displayViewModel = Self.makeDisplayViewModel(initialDate: date)
        selectedTab = .display
    }

    private func showHome() {
        resetMainState()
        route = .home
    }

    private func showOnboarding() {
        route = .onboarding
    }

    private func openPhotoUpload(from tab: ChalkakBottomBarItem) {
        photoUploadReturnTab = tab
        photoUploadViewModel = Self.makePhotoUploadViewModel(
            topicDate: PhotoUploadDate.today()
        )
        route = .photoUpload
    }

    private func showPhotoUploadSuccess(_ submission: PhotoUploadSubmission) {
        successSubmission = submission
        route = .photoUploadSuccess
    }

    private func closePhotoUploadSuccess() {
        successSubmission = nil
        photoUploadViewModel = nil
        showPhotoUploadOrigin()
    }

    private func showPhotoUploadOrigin() {
        route = .home
        selectedTab = photoUploadReturnTab
    }

    private func showServiceTerms() {
        selectedLegalDocument = .termsOfService
    }

    private func showPrivacyPolicy() {
        selectedLegalDocument = .privacyPolicy
    }

    private func showLogin() {
        selectedLegalDocument = nil
        photoUploadViewModel = nil
        successSubmission = nil
        resetMainState()
        route = .login
    }

    private func resetMainState() {
        selectedTab = .today
        selectedFeed = nil
        homeViewModel = Self.makeHomeViewModel()
        displayViewModel = Self.makeDisplayViewModel()
        recordViewModel = Self.makeRecordViewModel()
        settingsViewModel = Self.makeSettingsViewModel()
    }

    private static func makeHomeViewModel() -> HomeViewModel {
        let apiClient = HomeAPIClient(
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return HomeViewModel(
            initialState: HomeViewState(),
            isAuthenticated: { KeychainSessionStore.accessToken() != nil },
            refreshHandler: { sort in
                await apiResult {
                    try await apiClient.fetchHome(date: Date(), sort: sort)
                }
            },
            nextPageHandler: { state in
                await apiResult {
                    try await apiClient.fetchNextPage(state: state)
                }
            },
            likeHandler: { photoID, isLiked in
                await apiResult {
                    try await apiClient.updateLike(photoID: photoID, isLiked: isLiked)
                }
            }
        )
    }

    private static func makeDisplayViewModel(initialDate: Date? = nil) -> DisplayViewModel {
        DisplayViewModel(initialDate: initialDate, apiClient: DisplayAPIClient())
    }

    private static func makeRecordViewModel() -> RecordViewModel {
        RecordViewModel(
            apiClient: RecordAPIClient(
                accessTokenProvider: { KeychainSessionStore.accessToken() }
            )
        )
    }

    private static func makeSettingsViewModel() -> SettingsViewModel {
        let apiClient = SettingsAPIClient(
            baseURL: AppConfiguration().apiBaseURL,
            accessTokenProvider: { KeychainSessionStore.accessToken() }
        )
        return SettingsViewModel(
            initialState: SettingsViewState(version: SettingsScreen.appVersion),
            isAuthenticated: { KeychainSessionStore.accessToken() != nil },
            loadSignature: { try await apiClient.fetchSignature() },
            updateSignature: { try await apiClient.updateSignature(pngData: $0) },
            logout: { KeychainSessionStore.delete() },
            withdraw: {
                try await apiClient.withdraw()
                KeychainSessionStore.delete()
            }
        )
    }

    private static func makePhotoUploadViewModel(topicDate: Date) -> PhotoUploadViewModel {
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
            repository: .api(client: apiClient)
        )
    }

    private static var initialRoute: AppRoute {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains(where: { $0.hasPrefix("-show-onboarding") }) {
            return .onboarding
        }
#endif
        return KeychainSessionStore.hasActiveSession() ? .home : .login
    }
}

private enum AppRoute: Equatable {
    case login
    case onboarding
    case home
    case photoUpload
    case photoUploadSuccess
}

#Preview {
    ContentView()
        .chalkakTheme(.light)
}

@MainActor
private func apiResult<Value: Sendable>(
    _ operation: () async throws -> Value
) async -> Result<Value, HomeInitialError> {
    do {
        return .success(try await operation())
    } catch let error as HomeAPIError {
        return .failure(error.initialError)
    } catch {
        return .failure(.generic)
    }
}
