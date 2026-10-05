import Foundation
import Observation

@MainActor
@Observable
final class DisplayViewModel {
    typealias DateProvider = @MainActor @Sendable () -> Date
    typealias FirstPageHandler = @MainActor @Sendable (
        Date,
        DisplaySort
    ) async -> Result<DisplayContent, DisplayError>
    typealias NextPageHandler = @MainActor @Sendable (
        DisplayPageRequest
    ) async -> Result<DisplayPage, DisplayError>

    typealias LikeHandler = @MainActor @Sendable (String, Bool) async -> Result<FeedLikeUpdate, FeedError>

    private(set) var likingPhotoIDs: Set<String> = []
    private var likeVersion = 0
    private var likeUpdates: [String: (version: Int, update: FeedLikeUpdate)] = [:]
    private let likeHandler: LikeHandler

    private(set) var viewState: DisplayViewState
    private(set) var event: DisplayEvent?

    private let initialDate: Date?
    private let dateProvider: DateProvider
    private let firstPageHandler: FirstPageHandler
    private let nextPageHandler: NextPageHandler
    private var selectedLatestSort: DisplaySort
    private var loadedTopicDate: Date?
    private var generation = 0
    private var isEndThresholdReached = false
    private var isRevalidating = false
    private var displayCache: [DisplayCacheKey: DisplayCacheEntry] = [:]

    init(
        initialState: DisplayViewState? = nil,
        initialDate: Date? = nil,
        dateProvider: @escaping DateProvider = { Date() },
        firstPageHandler: @escaping FirstPageHandler = { _, _ in .failure(.generic) },
        nextPageHandler: @escaping NextPageHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) }
    ) {
        let initialState = initialState ?? DisplayViewState()
        self.viewState = initialState
        self.initialDate = initialDate
        self.dateProvider = dateProvider
        self.firstPageHandler = firstPageHandler
        self.nextPageHandler = nextPageHandler
        self.likeHandler = likeHandler
        self.selectedLatestSort = initialState.selectedSort
        if initialState.hasLoadedContent, let selectedDate = initialState.selectedDate {
            let sort: DisplaySort = initialState.contentStatus == .archive ? .popular : initialState.selectedSort
            displayCache[DisplayCacheKey(date: Self.startOfDay(selectedDate), sort: sort)] = DisplayCacheEntry(
                state: initialState,
                firstPagePhotoIDs: Set(initialState.photos.map(\.id))
            )
        }
    }

    convenience init(
        initialDate: Date? = nil,
        dateProvider: @escaping DateProvider = { Date() },
        apiClient: DisplayAPIClient
    ) {
        self.init(
            initialDate: initialDate,
            dateProvider: dateProvider,
            firstPageHandler: { date, sort in
                do {
                    return .success(try await apiClient.fetchDisplay(date: date, sort: sort))
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as DisplayAPIError {
                    return .failure(error.displayError)
                } catch {
                    return .failure(.generic)
                }
            },
            nextPageHandler: { request in
                do {
                    return .success(try await apiClient.fetchPage(request))
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as DisplayAPIError {
                    return .failure(error.displayError)
                } catch {
                    return .failure(.generic)
                }
            },
            likeHandler: { postID, isLiked in
                do {
                    return .success(try await apiClient.updateLike(postID: postID, isLiked: isLiked))
                } catch {
                    return .failure(.generic)
                }
            }
        )
    }

    func load() async {
        let latestDate = Self.startOfDay(dateProvider())
        let requestedDate = Self.startOfDay(initialDate ?? latestDate)
        await loadFirstPage(
            date: requestedDate,
            latestDate: latestDate,
            preserves: nil
        )
    }

    func retry() async {
        let previous = viewState.hasLoadedContent ? viewState : nil
        let providerDate = Self.startOfDay(dateProvider())
        let latestDate = viewState.contentStatus == .loading
            ? providerDate
            : viewState.latestDate ?? providerDate
        let requestedDate = viewState.contentStatus == .loading
            ? initialDate ?? latestDate
            : viewState.selectedDate ?? initialDate ?? latestDate
        await loadFirstPage(date: requestedDate, latestDate: latestDate, preserves: previous)
    }

    /// 현재 화면의 콘텐츠를 유지한 채 같은 전시를 최신 데이터로 갱신한다.
    func revalidate() async {
        guard viewState.contentStatus != .loading, !isRevalidating else { return }

        let previous = viewState.hasLoadedContent ? viewState : nil
        let latestDate = Self.startOfDay(dateProvider())
        let requestedDate = viewState.selectedDate ?? initialDate ?? latestDate
        await loadFirstPage(
            date: requestedDate,
            latestDate: latestDate,
            preserves: previous,
            keepsContentVisible: previous != nil
        )
    }

    func moveToPreviousDate() async {
        guard viewState.contentStatus != .loading,
              let selectedDate = viewState.selectedDate
        else { return }
        let previousDate = Self.calendar.date(byAdding: .day, value: -1, to: selectedDate)!
        let targetDate = viewState.earliestDate.map { max(previousDate, $0) } ?? previousDate
        guard targetDate != selectedDate else { return }

        await loadFirstPage(
            date: targetDate,
            latestDate: viewState.latestDate ?? Self.startOfDay(dateProvider()),
            preserves: viewState,
            isPreviousDateRequest: true
        )
    }

    func moveToNextDate() async {
        guard viewState.contentStatus != .loading,
              let selectedDate = viewState.selectedDate,
              let latestDate = viewState.latestDate
        else { return }
        let nextDate = Self.calendar.date(byAdding: .day, value: 1, to: selectedDate)!
        let targetDate = min(nextDate, latestDate)
        guard targetDate != selectedDate else { return }

        await loadFirstPage(
            date: targetDate,
            latestDate: latestDate,
            preserves: viewState
        )
    }

    func selectSort(_ sort: DisplaySort) async {
        guard viewState.contentStatus == .latest,
              sort != viewState.selectedSort,
              let selectedDate = viewState.selectedDate,
              let latestDate = viewState.latestDate
        else { return }

        let previousState = viewState
        selectedLatestSort = sort
        let cacheKey = DisplayCacheKey(date: Self.startOfDay(selectedDate), sort: sort)
        let cachedState = displayCache[cacheKey].map { entry in
            var cached = entry.state
            cached.latestDate = latestDate
            cached.earliestDate = previousState.earliestDate
            cached.transientError = nil
            return cached
        }
        if let cachedState {
            viewState = cachedState
        } else {
            viewState.selectedSort = sort
            viewState.isLoadingNext = false
            viewState.transientError = nil
        }
        await loadFirstPage(
            date: selectedDate,
            latestDate: latestDate,
            preserves: cachedState ?? previousState,
            keepsContentVisible: true
        )
    }

    func updateFeaturedPage(_ page: Int) {
        guard viewState.contentStatus == .archive else { return }
        viewState.featuredPage = page.coerced(
            to: 0...max(0, viewState.featuredPhotos.count - 1)
        )
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

    func toggleLike(photoID: String) async {
        guard viewState.hasLoadedContent,
              !likingPhotoIDs.contains(photoID),
              let photo = viewState.photos.first(where: { $0.id == photoID })
        else { return }
        likingPhotoIDs.insert(photoID)
        defer { likingPhotoIDs.remove(photoID) }
        switch await likeHandler(photoID, !photo.isLiked) {
        case let .success(update):
            guard update.postID == photoID, update.likeCount >= 0 else {
                event = .likeFailed
                return
            }
            likeVersion += 1
            likeUpdates[photoID] = (likeVersion, update)
            applyLikeUpdates(to: &viewState, after: likeVersion - 1)
            for key in Array(displayCache.keys) {
                guard var entry = displayCache[key] else { continue }
                applyLikeUpdates(to: &entry.state, after: likeVersion - 1)
                displayCache[key] = entry
            }
        case .failure:
            event = .likeFailed
        }
    }

    private func applyLikeUpdates(to state: inout DisplayViewState, after version: Int) {
        func updated(_ photos: [DisplayPhoto]) -> [DisplayPhoto] {
            photos.map { photo in
                guard let record = likeUpdates[photo.id], record.version > version else { return photo }
                var photo = photo
                photo.isLiked = record.update.isLiked
                photo.likeCount = record.update.likeCount
                return photo
            }
        }
        state.photos = updated(state.photos)
        state.featuredPhotos = updated(state.featuredPhotos)
    }

    func consumeEvent() {
        event = nil
    }

    private func loadFirstPage(
        date: Date,
        latestDate: Date,
        preserves previousState: DisplayViewState?,
        isPreviousDateRequest: Bool = false,
        keepsContentVisible: Bool = false
    ) async {
        generation += 1
        let requestGeneration = generation
        if keepsContentVisible {
            isRevalidating = true
        }
        defer {
            if requestGeneration == generation {
                isRevalidating = false
            }
        }
        isEndThresholdReached = false
        let requestedDate = Self.startOfDay(date)
        let requestSort: DisplaySort = requestedDate < latestDate ? .popular : selectedLatestSort

        if keepsContentVisible {
            viewState.latestDate = latestDate
            viewState.isLoadingNext = false
            viewState.transientError = nil
        } else {
            viewState.selectedDate = requestedDate
            viewState.latestDate = latestDate
            viewState.contentStatus = .loading
            viewState.isLoadingNext = false
            viewState.transientError = nil
        }

        let requestLikeVersion = likeVersion
        let result = await firstPageHandler(requestedDate, requestSort)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(content):
            apply(content, latestDate: latestDate)
            applyLikeUpdates(to: &viewState, after: requestLikeVersion)
            cacheCurrentState()
        case let .failure(error):
            loadedTopicDate = previousState.flatMap { _ in loadedTopicDate }
            if var restored = previousState {
                if isPreviousDateRequest, error == .topicNotFound {
                    restored.earliestDate = restored.selectedDate
                }
                selectedLatestSort = restored.contentStatus == .latest
                    ? restored.selectedSort
                    : selectedLatestSort
                restored.latestDate = latestDate
                restored.isLoadingNext = false
                restored.transientError = error
                applyLikeUpdates(to: &restored, after: requestLikeVersion)
                viewState = restored
                event = .showFailure(error)
            } else {
                viewState = DisplayViewState(
                    contentStatus: .error(error),
                    selectedDate: requestedDate,
                    latestDate: latestDate,
                    selectedSort: selectedLatestSort
                )
            }
        }
    }

    private func apply(_ content: DisplayContent, latestDate: Date) {
        let canonicalDate = Self.startOfDay(content.topicDate)
        let isArchive = canonicalDate < latestDate
        let cacheKey = DisplayCacheKey(
            date: canonicalDate,
            sort: isArchive ? .popular : selectedLatestSort
        )
        let cachedEntry = displayCache[cacheKey]
        let canReuseCachedTail = cacheKey.sort != .random ||
            (content.page.randomSeed?.isEmpty == false &&
                cachedEntry?.state.randomSeed == content.page.randomSeed)
        let freshPhotoIDs = Set(content.page.photos.map(\.id))
        let cachedFirstPagePhotoIDs = canReuseCachedTail ? cachedEntry?.firstPagePhotoIDs ?? [] : []
        let cachedTail = canReuseCachedTail
            ? (cachedEntry?.state.photos.filter {
                !cachedFirstPagePhotoIDs.contains($0.id) && !freshPhotoIDs.contains($0.id)
            } ?? [])
            : []
        let mergedPhotos = content.page.photos + cachedTail
        let featuredPhotos = isArchive ? Array(mergedPhotos.prefix(5)) : []
        // Android DisplayViewModel과 같이, 같은 날짜의 지난 전시를 다시 불러오면 보던 추천 카드 페이지를 유지한다.
        let featuredPage = viewState.contentStatus == .archive && viewState.selectedDate == canonicalDate
            ? min(viewState.featuredPage, max(featuredPhotos.count - 1, 0))
            : 0
        loadedTopicDate = canonicalDate
        viewState = DisplayViewState(
            contentStatus: isArchive ? .archive : .latest,
            selectedDate: canonicalDate,
            latestDate: latestDate,
            earliestDate: viewState.earliestDate,
            topic: content.topic,
            selectedSort: isArchive ? .popular : selectedLatestSort,
            photos: mergedPhotos,
            featuredPhotos: featuredPhotos,
            featuredPage: featuredPage,
            currentPage: cachedTail.isEmpty
                ? content.page.currentPage
                : cachedEntry?.state.currentPage ?? content.page.currentPage,
            hasNext: cachedTail.isEmpty
                ? content.page.hasNext
                : cachedEntry?.state.hasNext ?? content.page.hasNext,
            randomSeed: isArchive ? nil : content.page.randomSeed,
            isLoadingNext: false
        )
        displayCache[cacheKey] = DisplayCacheEntry(
            state: viewState,
            firstPagePhotoIDs: freshPhotoIDs
        )
    }

    private func loadNextPage() async {
        guard !isRevalidating,
              viewState.contentStatus == .latest || viewState.contentStatus == .archive,
              viewState.hasNext,
              !viewState.isLoadingNext,
              let loadedTopicDate
        else {
            isEndThresholdReached = false
            return
        }

        let sort: DisplaySort = viewState.contentStatus == .archive ? .popular : viewState.selectedSort
        let randomSeed = sort == .random ? viewState.randomSeed : nil
        if sort == .random, randomSeed?.isEmpty != false {
            viewState.hasNext = false
            isEndThresholdReached = false
            return
        }

        let requestGeneration = generation
        let request = DisplayPageRequest(
            topicDate: loadedTopicDate,
            sort: sort,
            page: viewState.currentPage + 1,
            randomSeed: randomSeed
        )
        viewState.isLoadingNext = true
        let requestLikeVersion = likeVersion
        let result = await nextPageHandler(request)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(page):
            append(page, sort: sort)
            applyLikeUpdates(to: &viewState, after: requestLikeVersion)
            cacheCurrentState()
        case let .failure(error):
            viewState.isLoadingNext = false
            viewState.transientError = error
            isEndThresholdReached = false
            event = .showFailure(error)
        }
    }

    private func append(_ page: DisplayPage, sort: DisplaySort) {
        var existingIDs = Set(viewState.photos.map(\.id))
        let newPhotos = page.photos.filter { existingIDs.insert($0.id).inserted }
        viewState.photos.append(contentsOf: newPhotos)
        viewState.currentPage = page.currentPage
        viewState.hasNext = page.hasNext
        viewState.randomSeed = sort == .random ? viewState.randomSeed ?? page.randomSeed : nil
        viewState.isLoadingNext = false
        isEndThresholdReached = false
        cacheCurrentState()
    }

    private func cacheCurrentState() {
        guard viewState.hasLoadedContent, let selectedDate = viewState.selectedDate else { return }
        let sort: DisplaySort = viewState.contentStatus == .archive ? .popular : viewState.selectedSort
        var cachedState = viewState
        cachedState.transientError = nil
        let key = DisplayCacheKey(date: Self.startOfDay(selectedDate), sort: sort)
        displayCache[key] = DisplayCacheEntry(
            state: cachedState,
            firstPagePhotoIDs: displayCache[key]?.firstPagePhotoIDs ?? Set(cachedState.photos.map(\.id))
        )
    }

    private static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar
    }

    private static func startOfDay(_ date: Date) -> Date {
        calendar.startOfDay(for: date)
    }
}

private struct DisplayCacheKey: Hashable {
    let date: Date
    let sort: DisplaySort
}

private struct DisplayCacheEntry {
    var state: DisplayViewState
    let firstPagePhotoIDs: Set<DisplayPhoto.ID>
}

private extension DisplayViewState {
    var hasLoadedContent: Bool {
        contentStatus == .latest || contentStatus == .archive
    }
}

private extension Int {
    func coerced(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
