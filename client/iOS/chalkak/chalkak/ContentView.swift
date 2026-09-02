//
//  ContentView.swift
//  chalkak
//
//  Created by 정찬 on 8/9/26.
//

import SwiftUI

struct ContentView: View {
    @State private var homeViewModel = Self.makeHomeViewModel()

    var body: some View {
        HomeScreen(viewModel: homeViewModel)
            .task {
                guard homeViewModel.viewState.contentStatus == .loading else { return }
                await homeViewModel.retry()
            }
    }

    private static func makeHomeViewModel() -> HomeViewModel {
        let apiClient = HomeAPIClient()
        return HomeViewModel(
            initialState: HomeViewState(),
            isAuthenticated: { false },
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
}

#Preview {
    HomeScreen(
        viewModel: HomeViewModel(initialState: HomePreviewData.contentState)
    )
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
