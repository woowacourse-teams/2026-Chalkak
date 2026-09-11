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

    init(
        initialState: DisplayViewState? = nil,
        initialDate: Date? = nil,
        dateProvider: @escaping DateProvider = { Date() },
        firstPageHandler: @escaping FirstPageHandler = { _, _ in .failure(.generic) },
        nextPageHandler: @escaping NextPageHandler = { _ in .failure(.generic) }
    ) {
        let initialState = initialState ?? DisplayViewState()
        self.viewState = initialState
        self.initialDate = initialDate
        self.dateProvider = dateProvider
        self.firstPageHandler = firstPageHandler
        self.nextPageHandler = nextPageHandler
        self.selectedLatestSort = initialState.selectedSort
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

        selectedLatestSort = sort
        await loadFirstPage(
            date: selectedDate,
            latestDate: latestDate,
            preserves: viewState
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

    func consumeEvent() {
        event = nil
    }

    private func loadFirstPage(
        date: Date,
        latestDate: Date,
        preserves previousState: DisplayViewState?,
        isPreviousDateRequest: Bool = false
    ) async {
        generation += 1
        let requestGeneration = generation
        isEndThresholdReached = false
        let requestedDate = Self.startOfDay(date)
        let requestSort: DisplaySort = requestedDate < latestDate ? .popular : selectedLatestSort

        viewState.selectedDate = requestedDate
        viewState.latestDate = latestDate
        viewState.contentStatus = .loading
        viewState.isLoadingNext = false
        viewState.transientError = nil

        let result = await firstPageHandler(requestedDate, requestSort)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(content):
            apply(content, latestDate: latestDate)
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
        loadedTopicDate = canonicalDate
        viewState = DisplayViewState(
            contentStatus: isArchive ? .archive : .latest,
            selectedDate: canonicalDate,
            latestDate: latestDate,
            earliestDate: viewState.earliestDate,
            topic: content.topic,
            selectedSort: isArchive ? .popular : selectedLatestSort,
            photos: content.page.photos,
            featuredPhotos: isArchive ? Array(content.page.photos.prefix(5)) : [],
            featuredPage: 0,
            currentPage: content.page.currentPage,
            hasNext: content.page.hasNext,
            randomSeed: isArchive ? nil : content.page.randomSeed,
            isLoadingNext: false
        )
    }

    private func loadNextPage() async {
        guard viewState.contentStatus == .latest || viewState.contentStatus == .archive,
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
        let result = await nextPageHandler(request)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(page):
            append(page, sort: sort)
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
