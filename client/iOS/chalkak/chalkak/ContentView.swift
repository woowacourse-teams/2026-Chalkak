//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @State private var route: AppRoute = KeychainSessionStore.hasActiveSession() ? .home : .login
    @State private var selectedTab: ChalkakBottomBarItem = .today
    @State private var homeViewModel = Self.makeHomeViewModel()
    @State private var displayViewModel = Self.makeDisplayViewModel()

    var body: some View {
        Group {
            switch route {
            case .login:
                LoginView(
                    onAuthenticated: showHome,
                    onGuestAccessGranted: showHome
                )
            case .home:
                mainTab
            }
        }
        .animation(.default, value: route)
    }

    @ViewBuilder
    private var mainTab: some View {
        switch selectedTab {
        case .display:
            DisplayScreen(
                viewModel: displayViewModel,
                onSelectBottomBarItem: select
            )
        default:
            HomeScreen(
                viewModel: homeViewModel,
                onNavigateToBottomBar: select
            )
            .task {
                guard homeViewModel.viewState.contentStatus == .loading else { return }
                await homeViewModel.retry()
            }
        }
    }

    private func select(_ item: ChalkakBottomBarItem) {
        guard item == .today || item == .display else { return }
        selectedTab = item
    }

    private func showHome() {
        route = .home
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

    private static func makeDisplayViewModel() -> DisplayViewModel {
        DisplayViewModel(apiClient: DisplayAPIClient())
    }
}

private enum AppRoute: Equatable {
    case login
    case home
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
