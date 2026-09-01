import Foundation
import Observation

@MainActor
@Observable
final class HomeViewModel {
    typealias RefreshHandler = @MainActor @Sendable (HomePostSort) async -> Result<HomeViewState, HomeInitialError>
    typealias NextPageHandler = @MainActor @Sendable (HomeViewState) async -> Result<HomePage, HomeInitialError>
    typealias LikeHandler = @MainActor @Sendable (HomePhoto.ID, Bool) async -> Result<HomeLikeUpdate, HomeInitialError>
    typealias AuthenticationProvider = @MainActor @Sendable () -> Bool

    private(set) var viewState: HomeViewState
    private(set) var event: HomeEvent?

    private let refreshHandler: RefreshHandler
    private let nextPageHandler: NextPageHandler
    private let likeHandler: LikeHandler
    private let isAuthenticated: AuthenticationProvider

    private var isEndThresholdReached = false
    private var likingPhotoIDs: Set<HomePhoto.ID> = []

    init(
        initialState: HomeViewState,
        isAuthenticated: @escaping AuthenticationProvider = { false },
        refreshHandler: @escaping RefreshHandler = { _ in .failure(.generic) },
        nextPageHandler: @escaping NextPageHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) }
    ) {
        self.viewState = initialState
        self.isAuthenticated = isAuthenticated
        self.refreshHandler = refreshHandler
        self.nextPageHandler = nextPageHandler
        self.likeHandler = likeHandler
    }

    func retry() async {
        await loadFirstPage(sort: viewState.selectedSort, preservesContent: false)
    }

    func refresh() async {
        guard viewState.contentStatus != .loading, !viewState.isRefreshing else { return }
        await loadFirstPage(sort: .random, preservesContent: viewState.contentStatus == .content)
    }

    func didReachEndThreshold(_ isReached: Bool) async {
        guard isReached else {
            isEndThresholdReached = false
            return
        }
        guard !isEndThresholdReached else { return }

        isEndThresholdReached = true
        await loadNextPage()
    }

    func toggleLike(photoID: HomePhoto.ID) async {
        guard viewState.areLikesEnabled else { return }
        guard isAuthenticated() else {
            publish(.showGuestLikeMessage)
            return
        }
        guard let photoIndex = viewState.photos.firstIndex(where: { $0.id == photoID }) else { return }
        guard likingPhotoIDs.insert(photoID).inserted else { return }
        defer { likingPhotoIDs.remove(photoID) }

        let previousPhoto = viewState.photos[photoIndex]
        let wasLiked = viewState.likedPhotoIDs.contains(photoID)
        let isLiked = !wasLiked
        let optimisticCount = max(0, previousPhoto.likeCount + (isLiked ? 1 : -1))

        viewState.photos[photoIndex].likeCount = optimisticCount
        setLiked(isLiked, photoID: photoID)

        let result = await likeHandler(photoID, isLiked)
        switch result {
        case let .success(update):
            guard let updatedIndex = viewState.photos.firstIndex(where: { $0.id == update.photoID }) else { return }
            viewState.photos[updatedIndex].likeCount = update.likeCount
            setLiked(update.isLiked, photoID: update.photoID)
        case .failure:
            guard let currentIndex = viewState.photos.firstIndex(where: { $0.id == photoID }) else { return }
            viewState.photos[currentIndex] = previousPhoto
            setLiked(wasLiked, photoID: photoID)
        }
    }

    func selectBottomBarItem(_ item: ChalkakBottomBarItem) async {
        if item == .today {
            await refresh()
        } else {
            publish(.navigateToBottomBar(item))
        }
    }

    func openPhotoUpload() {
        publish(.openPhotoUpload)
    }

    func consumeEvent() {
        event = nil
    }

    private func loadFirstPage(sort: HomePostSort, preservesContent: Bool) async {
        isEndThresholdReached = false

        if preservesContent {
            viewState.isRefreshing = true
            viewState.isLoadingNext = false
            viewState.areLikesEnabled = false
        } else {
            viewState = HomeViewState(
                contentStatus: .loading,
                selectedSort: sort,
                areLikesEnabled: false
            )
        }

        let result = await refreshHandler(sort)
        switch result {
        case var .success(newState):
            newState.contentStatus = .content
            newState.selectedSort = sort
            newState.isRefreshing = false
            newState.isLoadingNext = false
            newState.areLikesEnabled = true
            viewState = newState
        case let .failure(reason):
            if preservesContent {
                viewState.isRefreshing = false
                viewState.isLoadingNext = false
                viewState.areLikesEnabled = true
                publish(.showRefreshFailure(reason))
            } else {
                viewState = HomeViewState(
                    contentStatus: .error(reason),
                    selectedSort: sort,
                    areLikesEnabled: false
                )
            }
        }
    }

    private func loadNextPage() async {
        guard viewState.contentStatus == .content,
              viewState.hasNext,
              !viewState.isLoadingNext
        else { return }

        viewState.isLoadingNext = true

        let result = await nextPageHandler(viewState)
        switch result {
        case let .success(page):
            append(page)
        case .failure:
            viewState.isLoadingNext = false
            isEndThresholdReached = false
        }
    }

    private func append(_ page: HomePage) {
        let existingIDs = Set(viewState.photos.map(\.id))
        let newPhotos = page.photos.filter { !existingIDs.contains($0.id) }
        let newPhotoIDs = Set(newPhotos.map(\.id))

        viewState.photos.append(contentsOf: newPhotos)
        viewState.likedPhotoIDs.subtract(newPhotoIDs)
        viewState.likedPhotoIDs.formUnion(page.likedPhotoIDs.intersection(newPhotoIDs))
        viewState.currentPage = page.currentPage
        viewState.hasNext = page.hasNext
        viewState.randomSeed = viewState.selectedSort == .random
            ? viewState.randomSeed ?? page.randomSeed
            : nil
        viewState.isLoadingNext = false
        isEndThresholdReached = false
    }

    private func setLiked(_ isLiked: Bool, photoID: HomePhoto.ID) {
        if isLiked {
            viewState.likedPhotoIDs.insert(photoID)
        } else {
            viewState.likedPhotoIDs.remove(photoID)
        }
    }

    private func publish(_ event: HomeEvent) {
        self.event = event
    }
}
