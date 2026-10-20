import Foundation
import Observation

@MainActor
@Observable
final class HomeViewModel {
    private(set) var viewState: HomeViewState
    private(set) var scrollToTopRequestID = 0
    private let loader: HomeDataLoader
    private let dateProvider: @MainActor () -> Date
    private let isAuthenticated: @MainActor () -> Bool
    private var generation = 0
    private var loadTask: Task<Void, Never>?

    init(
        initialState: HomeViewState? = nil,
        loader: HomeDataLoader,
        dateProvider: @escaping @MainActor () -> Date = { Date() },
        isAuthenticated: @escaping @MainActor () -> Bool = { KeychainSessionStore.hasAuthenticatedSession() }
    ) {
        viewState = initialState ?? HomeViewState(date: dateProvider())
        self.loader = loader
        self.dateProvider = dateProvider
        self.isAuthenticated = isAuthenticated
    }

    func resume() async {
        await load(showRefreshIndicator: false)
    }

    func refresh() async {
        guard loadTask == nil else { return }
        await load(showRefreshIndicator: true)
    }

    func scrollToTop() {
        scrollToTopRequestID += 1
    }

    func cancel() {
        generation += 1
        loadTask?.cancel()
        loadTask = nil
    }

    private func load(showRefreshIndicator: Bool) async {
        let date = RecordMonth.calendar.startOfDay(for: dateProvider())
        let authenticated = isAuthenticated()
        if loadTask != nil, date == viewState.date, authenticated == viewState.isAuthenticated { return }
        cancel()
        let request = generation
        if date != viewState.date || authenticated != viewState.isAuthenticated {
            viewState = HomeViewState(date: date)
        }
        viewState.isAuthenticated = authenticated
        viewState.isRefreshing = showRefreshIndicator
        let task = Task { [weak self] in
            guard let self else { return }
            async let today: Void = self.loadToday(date, request: request)
            async let yesterday: Void = self.loadYesterday(date, request: request)
            async let personal: Void = self.loadPersonal(date, authenticated: authenticated, request: request)
            async let banner: Void = self.loadBanner(request: request)
            _ = await (today, yesterday, personal, banner)
            guard self.isCurrent(request) else { return }
            self.viewState.isRefreshing = false
            self.loadTask = nil
        }
        loadTask = task
        await withTaskCancellationHandler {
            await task.value
        } onCancel: {
            task.cancel()
        }
        if generation == request, task.isCancelled {
            loadTask = nil
            viewState.isRefreshing = false
        }
    }

    private func isCurrent(_ request: Int) -> Bool {
        request == generation && !Task.isCancelled
    }

    private func loadBanner(request: Int) async {
        do {
            let page = try await loader.banners()
            guard isCurrent(request) else { return }
            viewState.banner = page.banners.first
            viewState.bannerStatus = .ready
        } catch {
            guard isCurrent(request) else { return }
            viewState.bannerStatus = .error
        }
    }

    private func loadToday(_ date: Date, request: Int) async {
        do {
            let content = try await loader.today(date)
            guard isCurrent(request) else { return }
            viewState.topic = content.topic
            viewState.topicStatus = .ready
            viewState.trending = HomeRankingState(status: .ready, photos: Array(content.page.photos.prefix(5)))
        } catch {
            guard isCurrent(request) else { return }
            let missing = (error as? DisplayAPIError)?.displayError == .topicNotFound
            viewState.topic = ""
            viewState.topicStatus = missing ? .ready : .error
            viewState.trending = HomeRankingState(status: missing ? .ready : .error)
        }
    }

    private func loadYesterday(_ date: Date, request: Int) async {
        let yesterday = RecordMonth.calendar.date(byAdding: .day, value: -1, to: date)!
        do {
            let photos = try await loader.ranking(yesterday)
            guard isCurrent(request) else { return }
            viewState.yesterday = HomeRankingState(status: .ready, photos: Array(photos.prefix(5)))
        } catch {
            guard isCurrent(request) else { return }
            let missing = (error as? DisplayAPIError)?.displayError == .topicNotFound
            viewState.yesterday = HomeRankingState(status: missing ? .ready : .error)
        }
    }

    private func loadPersonal(_ date: Date, authenticated: Bool, request: Int) async {
        guard authenticated else {
            guard isCurrent(request) else { return }
            viewState.hero = .empty
            viewState.recordsStatus = .ready
            return
        }
        let dates = HomeViewState.weekDates(date)
        let months = Set(dates.map { RecordMonth.current(now: $0) })
        let loader = loader
        let results = await withTaskGroup(of: RecordCalendar?.self) { group in
            for month in months {
                group.addTask { try? await loader.calendar(month) }
            }
            var results: [RecordCalendar?] = []
            for await result in group { results.append(result) }
            return results
        }
        guard isCurrent(request) else { return }
        let posts = results.compactMap { $0 }.flatMap(\.posts)
        let failed = results.contains { $0 == nil }
        viewState.week = dates.map { day in
            HomeWeekDay(date: day, post: posts.first { RecordMonth.calendar.isDate($0.topicDate, inSameDayAs: day) })
        }
        viewState.recordsStatus = failed ? .error : .ready
        let record = posts.first { RecordMonth.calendar.isDate($0.topicDate, inSameDayAs: date) }
        let hero: HomeHeroState
        if let record {
            hero = await loadHero(record)
        } else if failed {
            hero = .error
        } else {
            hero = await loadMissingHero(date)
        }
        guard isCurrent(request) else { return }
        viewState.hero = hero
    }

    private func loadHero(_ record: RecordPost) async -> HomeHeroState {
        let fallback = HomeHeroPhoto(imageSource: record.thumbnailImageSource, thumbnailSource: record.thumbnailImageSource, signatureSource: nil, isPending: record.status == .pending)
        if record.status == .pending { return .photo(fallback) }
        guard record.status == .approved else { return .error }
        do {
            let detail = try await loader.detail(record.postId)
            return .photo(HomeHeroPhoto(imageSource: detail.post.originalImageSource, thumbnailSource: record.thumbnailImageSource, signatureSource: detail.post.signatureImageSource, isPending: false))
        } catch {
            return .photo(fallback)
        }
    }

    private func loadMissingHero(_ date: Date) async -> HomeHeroState {
        do {
            let status = try await loader.postStatus()
            guard RecordMonth.calendar.isDate(status.topicDate, inSameDayAs: date), status.isPosted else { return .empty }
            return status.moderationStatus == .validating ? .processing : .error
        } catch let error as PhotoUploadAPIError {
            if case .http(404, _) = error { return .noOpenTopic }
            return .error
        } catch {
            return .error
        }
    }
}
