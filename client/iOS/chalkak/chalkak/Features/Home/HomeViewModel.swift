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
    private var bannerLoadMoreTask: Task<Void, Never>?
    private static let bannerPageSize = 10
    private let aiReviewRepository: (any AIReviewRepository)?
    private let aiReviewNow: @MainActor () -> Date
    private let aiReviewSleep: @Sendable (Duration) async throws -> Void
    private var aiReviewTask: Task<Void, Never>?
    private var aiReviewPostID: String?
    private var aiReviewGeneration = 0
    private var aiReviewRevision = 0
    private var aiReviewDeadline: Date?

    var hasAIReviewRepository: Bool { aiReviewRepository != nil }

    init(
        initialState: HomeViewState? = nil,
        loader: HomeDataLoader,
        dateProvider: @escaping @MainActor () -> Date = { Date() },
        isAuthenticated: @escaping @MainActor () -> Bool = { KeychainSessionStore.hasAuthenticatedSession() },
        aiReviewRepository: (any AIReviewRepository)? = nil,
        aiReviewNow: @escaping @MainActor () -> Date = { Date() },
        aiReviewSleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        viewState = initialState ?? HomeViewState(date: dateProvider())
        self.loader = loader
        self.dateProvider = dateProvider
        self.isAuthenticated = isAuthenticated
        self.aiReviewRepository = aiReviewRepository
        self.aiReviewNow = aiReviewNow
        self.aiReviewSleep = aiReviewSleep
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
        cancelAIReview()
        cancelHomeLoading()
    }

    private func cancelHomeLoading() {
        generation += 1
        loadTask?.cancel()
        loadTask = nil
        bannerLoadMoreTask?.cancel()
        bannerLoadMoreTask = nil
        viewState.isLoadingMoreBanners = false
        viewState.isRefreshing = false
    }

    /// Kept alive by the screen's task so cancellation also ends polling.
    func observeAIReview(postID: String) async {
        guard let repository = aiReviewRepository else { return }
        let samePost = aiReviewPostID == postID
        cancelAIReview()
        aiReviewPostID = postID
        if !samePost { viewState.aiReview = HomeAIReviewState() }
        viewState.aiReview.isUnavailable = false
        viewState.aiReview.pollingTimedOut = false
        viewState.aiReview.isLoading = true
        aiReviewDeadline = aiReviewNow().addingTimeInterval(300)
        let session = aiReviewGeneration
        let task = Task { [weak self] in
            guard let self else { return }
            await self.fetchAIReview(repository: repository, postID: postID, session: session)
            while self.isCurrentAIReview(session) {
                do { try await self.aiReviewSleep(.seconds(5)) }
                catch { break }
                guard self.isCurrentAIReview(session) else { break }
                guard !self.viewState.aiReview.isUnavailable,
                      !self.viewState.aiReview.isSubmitting,
                      self.viewState.aiReview.review?.status == .processing else { continue }
                guard let deadline = self.aiReviewDeadline, self.aiReviewNow() < deadline else {
                    self.viewState.aiReview.pollingTimedOut = true
                    continue
                }
                await self.fetchAIReview(repository: repository, postID: postID, session: session, showsErrorMessage: false)
            }
        }
        aiReviewTask = task
        await withTaskCancellationHandler {
            await task.value
        } onCancel: {
            task.cancel()
        }
        if session == aiReviewGeneration {
            cancelAIReview()
        }
    }

    func requestAIReview() async {
        guard let repository = aiReviewRepository,
              let postID = aiReviewPostID,
              !viewState.aiReview.isLoading,
              !viewState.aiReview.isSubmitting,
              !viewState.aiReview.isUnavailable,
              let review = viewState.aiReview.review,
              review.status == .none || (review.status == .failed && review.canRequestAgain) else { return }
        let session = aiReviewGeneration
        aiReviewRevision += 1
        viewState.aiReview.isSubmitting = true
        viewState.aiReview.message = nil
        viewState.aiReview.pollingTimedOut = false
        aiReviewDeadline = aiReviewNow().addingTimeInterval(300)
        do {
            let response = try await repository.requestReview(postID: postID)
            guard isCurrentAIReview(session) else { return }
            viewState.aiReview.review = response
            updateAIReviewTimeout()
        } catch {
            guard isCurrentAIReview(session) else { return }
            handleAIReviewError(error)
            // A lost POST response can still mean the server accepted it.
            // Resolve uncertainty with GET; never send another POST automatically.
            let needsStatusCheck: Bool
            switch error as? AIReviewError {
            case .network?, .invalidResponse?: needsStatusCheck = true
            default: needsStatusCheck = false
            }
            if needsStatusCheck {
                await fetchAIReview(repository: repository, postID: postID, session: session, preserveMessage: true)
            }
        }
        guard isCurrentAIReview(session) else { return }
        viewState.aiReview.isSubmitting = false
    }

    func dismissAIReviewMessage() {
        viewState.aiReview.message = nil
    }

    private func cancelAIReview() {
        aiReviewGeneration += 1
        aiReviewRevision += 1
        aiReviewTask?.cancel()
        aiReviewTask = nil
        viewState.aiReview.isLoading = false
        viewState.aiReview.isSubmitting = false
    }

    private func isCurrentAIReview(_ session: Int) -> Bool {
        session == aiReviewGeneration && !Task.isCancelled && aiReviewTask?.isCancelled != true
    }

    private func fetchAIReview(
        repository: any AIReviewRepository,
        postID: String,
        session: Int,
        preserveMessage: Bool = false,
        showsErrorMessage: Bool = true
    ) async {
        let revision = aiReviewRevision
        do {
            let review = try await repository.fetchReview(postID: postID)
            guard isCurrentAIReview(session), revision == aiReviewRevision else { return }
            viewState.aiReview.review = review
            updateAIReviewTimeout()
            if !preserveMessage { viewState.aiReview.message = nil }
        } catch {
            guard isCurrentAIReview(session), revision == aiReviewRevision else { return }
            handleAIReviewError(error, showsMessage: showsErrorMessage)
        }
        guard isCurrentAIReview(session), revision == aiReviewRevision else { return }
        viewState.aiReview.isLoading = false
    }

    private func updateAIReviewTimeout() {
        viewState.aiReview.pollingTimedOut = viewState.aiReview.review?.status == .processing
            && aiReviewDeadline.map { aiReviewNow() >= $0 } == true
    }

    private func handleAIReviewError(_ error: Error, showsMessage: Bool = true) {
        guard !(error is CancellationError) else { return }
        if case .http(404, _) = error as? AIReviewError {
            viewState.aiReview.isUnavailable = true
        }
        guard showsMessage else { return }
        if let error = error as? AIReviewError {
            switch error {
            case .http(let statusCode, let message):
                if statusCode == 404 {
                    viewState.aiReview.message = "AI 리뷰를 확인할 수 없는 게시물입니다."
                } else {
                    viewState.aiReview.message = message ?? "AI 리뷰를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                }
            case .unauthorized:
                viewState.aiReview.message = "로그인이 필요합니다."
            case .network, .invalidResponse:
                viewState.aiReview.message = "AI 리뷰를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        } else {
            viewState.aiReview.message = "AI 리뷰를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }

    private func load(showRefreshIndicator: Bool) async {
        let date = RecordMonth.calendar.startOfDay(for: dateProvider())
        let authenticated = isAuthenticated()
        if loadTask != nil, date == viewState.date, authenticated == viewState.isAuthenticated { return }
        cancelHomeLoading()
        let request = generation
        if date != viewState.date || authenticated != viewState.isAuthenticated {
            cancelAIReview()
            viewState = HomeViewState(date: date)
        }
        viewState.isAuthenticated = authenticated
        viewState.isRefreshing = showRefreshIndicator
        viewState.bannerStatus = .loading
        viewState.bannerLoadMoreError = false
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
            let page = try await loader.banners(1, Self.bannerPageSize)
            guard isCurrent(request) else { return }
            viewState.banners = uniqueBanners(page.banners)
            viewState.bannerStatus = .ready
            viewState.bannerCurrentPage = page.currentPage
            viewState.bannerHasNext = page.hasNext && !page.banners.isEmpty
        } catch {
            guard isCurrent(request) else { return }
            viewState.bannerStatus = .error
        }
    }

    func loadMoreBanners() async {
        guard viewState.bannerStatus == .ready,
              viewState.bannerHasNext,
              !viewState.isRefreshing,
              !viewState.isLoadingMoreBanners else { return }
        let request = generation
        let nextPage = viewState.bannerCurrentPage + 1
        viewState.isLoadingMoreBanners = true
        viewState.bannerLoadMoreError = false
        let task = Task { [weak self] in
            guard let self else { return }
            do {
                let page = try await self.loader.banners(nextPage, Self.bannerPageSize)
                guard self.isCurrent(request) else { return }
                self.viewState.banners = self.uniqueBanners(self.viewState.banners + page.banners)
                self.viewState.bannerCurrentPage = page.currentPage
                self.viewState.bannerHasNext = page.hasNext && !page.banners.isEmpty && page.currentPage >= nextPage
            } catch {
                guard self.isCurrent(request) else { return }
                self.viewState.bannerLoadMoreError = true
            }
            self.viewState.isLoadingMoreBanners = false
            self.bannerLoadMoreTask = nil
        }
        bannerLoadMoreTask = task
        await withTaskCancellationHandler {
            await task.value
        } onCancel: {
            task.cancel()
        }
        if generation == request, task.isCancelled {
            bannerLoadMoreTask = nil
            viewState.isLoadingMoreBanners = false
        }
    }

    private func uniqueBanners(_ banners: [Banner]) -> [Banner] {
        var ids = Set<String>()
        return banners.filter { ids.insert($0.id).inserted }
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
        let fallback = HomeHeroPhoto(imageSource: record.thumbnailImageSource, thumbnailSource: record.thumbnailImageSource, signatureSource: nil, isPending: record.status == .pending, postID: record.postId)
        if record.status == .pending { return .photo(fallback) }
        guard record.status == .approved else { return .error }
        do {
            let detail = try await loader.detail(record.postId)
            return .photo(HomeHeroPhoto(imageSource: detail.post.originalImageSource, thumbnailSource: record.thumbnailImageSource, signatureSource: detail.post.signatureImageSource, isPending: false, postID: record.postId))
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
