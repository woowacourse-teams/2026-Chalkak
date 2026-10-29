import Foundation
import Observation

@MainActor
@Observable
final class RecordViewModel {
    typealias MonthProvider = @MainActor @Sendable () -> RecordMonth
    typealias CalendarHandler = @MainActor @Sendable (RecordMonth) async -> Result<RecordCalendar, RecordError>
    typealias MonthListHandler = @MainActor @Sendable () async -> Result<[RecordMonth], RecordError>
    typealias TopicHandler = @MainActor @Sendable (Date) async -> String?
    typealias PhotoColorHandler = @MainActor @Sendable (RecordPost) async -> RecordPhotoColor?
    /// 내 사인 주소. 받지 못하면 nil.
    typealias SignatureHandler = @MainActor @Sendable () async -> URL?

    private(set) var viewState: RecordViewState
    private(set) var event: RecordEvent?

    private let monthProvider: MonthProvider
    private let calendarHandler: CalendarHandler
    private let monthListHandler: MonthListHandler
    private let topicHandler: TopicHandler
    private let photoColorHandler: PhotoColorHandler
    private let signatureHandler: SignatureHandler
    private var loadingYears: Set<Int> = []
    // 홈 주간 기록에서 연 날짜. 재검증해도 이 날짜를 다시 고르고, 사용자가 날짜나 달을 바꾸면 비운다.
    private var initialDate: Date?
    private var generation = 0
    private var isRevalidating = false
    private var shouldRetryMonthList = false
    // 진행 중인 load() 수. 홈 주간 기록에서 열어 이미 불러오는 중이면 화면이 나타날 때 같은 요청을 다시 보내지 않는다.
    private var activeLoadCount = 0
    // 한 번 받은 달의 기록. 같은 달로 돌아오면 빈 화면·로딩 없이 바로 보여주고 뒤에서 최신 값으로 교체한다.
    // 이 뷰모델과 수명이 같아서 로그아웃 등으로 뷰모델을 새로 만들면 함께 비워진다.
    private var calendarCache: [RecordMonth: RecordCalendar] = [:]

    init(
        initialState: RecordViewState? = nil,
        initialDate: Date? = nil,
        monthProvider: @escaping MonthProvider = { .current() },
        monthListHandler: @escaping MonthListHandler = { .success([]) },
        calendarHandler: @escaping CalendarHandler = { _ in .failure(.generic) },
        topicHandler: @escaping TopicHandler = { _ in nil },
        photoColorHandler: @escaping PhotoColorHandler = { _ in nil },
        signatureHandler: @escaping SignatureHandler = { nil }
    ) {
        let latestMonth = monthProvider()
        self.initialDate = initialDate
        self.viewState = initialState ?? RecordViewState(
            month: initialDate.map { RecordMonth.current(now: $0) } ?? latestMonth,
            latestMonth: latestMonth
        )
        self.monthProvider = monthProvider
        self.calendarHandler = calendarHandler
        self.monthListHandler = monthListHandler
        self.topicHandler = topicHandler
        self.photoColorHandler = photoColorHandler
        self.signatureHandler = signatureHandler
    }

    convenience init(
        monthProvider: @escaping MonthProvider = { .current() },
        apiClient: RecordAPIClient,
        signatureHandler: @escaping SignatureHandler = { nil }
    ) {
        self.init(
            monthProvider: monthProvider,
            monthListHandler: {
                do {
                    return .success(try await apiClient.fetchCalendarMonths())
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as RecordError {
                    return .failure(error)
                } catch {
                    return .failure(.generic)
                }
            },
            calendarHandler: { month in
                do {
                    return .success(try await apiClient.fetchCalendar(month: month))
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as RecordError {
                    return .failure(error)
                } catch {
                    return .failure(.generic)
                }
            },
            topicHandler: { date in
                try? await apiClient.fetchTopicTitle(date: date)
            },
            photoColorHandler: { post in
                await RecordPhotoColorLoader.color(for: post)
            },
            signatureHandler: signatureHandler
        )
    }

    /// 월간 책자 속표지에 넣을 내 사인을 받는다. 받지 못하면 전에 받은 사인을 그대로 둔다.
    func loadSignature() async {
        guard let url = await signatureHandler(), url != viewState.signatureURL else { return }
        viewState.signatureURL = url
    }

    /// 화면이 나타날 때 부른다. 아직 기록을 보여주지 못했고 불러오는 중도 아니면 처음부터 불러온다.
    func loadIfNeeded() async {
        guard viewState.contentStatus == .loading, activeLoadCount == 0 else { return }
        await load()
    }

    /// 월 목록과 첫 달의 기록을 불러온다. 홈 주간 기록에서 연 날짜가 있으면 그 달을 연다.
    /// 화면을 벗어나 작업이 취소되면 실패로 바꾸지 않고 `.loading`으로 남겨, 다시 나타날 때 처음부터 불러오게 한다.
    func load() async {
        activeLoadCount += 1
        defer { activeLoadCount -= 1 }
        generation += 1
        let requestGeneration = generation
        switch await monthListHandler() {
        case let .success(months):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = false
            let availableMonths = monthsIncludingCurrent(months)
            let initialMonth = initialDate.map { RecordMonth.current(now: $0) } ?? monthProvider()
            viewState.availableMonths = availableMonths
            await loadCalendar(month: initialMonth)
        case .failure:
            guard requestGeneration == generation, !Task.isCancelled else { return }
            shouldRetryMonthList = true
            viewState.availableMonths = monthsIncludingCurrent([])
            await loadCalendar(month: viewState.month)
        }
    }

    /// 홈 주간 기록에서 고른 날짜의 달을 열고 그 날짜의 사진을 선택한다.
    /// 뷰모델을 새로 만들지 않아 이미 받은 달은 캐시로 바로 보여주고 뒤에서 최신 값으로 교체한다.
    /// 월 목록을 아직 받지 못했으면 처음부터 불러온다.
    /// - Parameter date: 열 날짜. 그 날짜에 사진이 없으면 다른 사진을 대신 고르지 않는다.
    func open(date: Date) async {
        initialDate = date
        let month = RecordMonth.current(now: date)
        if shouldRetryMonthList || viewState.availableMonths.isEmpty {
            viewState.month = month
            await load()
        } else {
            await loadCalendar(month: month)
        }
    }

    func moveToPreviousMonth() async {
        guard viewState.canGoPrevious else { return }
        guard let previousMonth = viewState.availableMonths.filter({ $0 < viewState.month }).max() else { return }
        initialDate = nil
        await loadCalendar(month: previousMonth)
    }

    func moveToNextMonth() async {
        guard viewState.canGoNext else { return }
        guard let nextMonth = viewState.availableMonths.filter({ $0 > viewState.month }).min() else { return }
        initialDate = nil
        await loadCalendar(month: nextMonth)
    }

    /// 년/월 휠에서 고른 달로 이동한다. 기록이 있는 달(+이번 달)만 받고, 같은 달이면 다시 불러오지 않는다.
    func moveToMonth(_ month: RecordMonth) async {
        guard viewState.contentStatus != .loading,
              month != viewState.month,
              viewState.availableMonths.contains(month)
        else { return }
        initialDate = nil
        await loadCalendar(month: month)
    }

    func selectDate(_ date: Date) {
        guard viewState.posts.contains(where: { $0.topicDate == date }) else { return }
        initialDate = nil
        viewState.selectedDate = date
    }

    // MARK: - 주간 보기

    /// 주간 보기에 들어갈 때 아직 모르는 날짜의 주제 제목을 함께 불러온다.
    /// 실패한 날짜는 비워 두고 다음에 주간을 다시 열 때 재시도한다.
    func loadTopicTitles(for dates: [Date]) async {
        let missingDates = Set(dates).subtracting(viewState.topicTitles.keys)
        guard !missingDates.isEmpty else { return }

        let handler = topicHandler
        let titles = await withTaskGroup(of: (Date, String?).self) { group in
            for date in missingDates {
                group.addTask { (date, await handler(date)) }
            }
            var titles: [Date: String] = [:]
            for await (date, title) in group {
                if let title { titles[date] = title }
            }
            return titles
        }
        viewState.topicTitles.merge(titles) { _, new in new }
    }

    // MARK: - 연간 달력

    /// 연간 달력에서 고른 달을 바로 보여준다. 이미 불러온 달이면 그 게시물로 먼저 그리고, 이어서 최신화한다.
    func showMonth(_ month: RecordMonth) {
        let month = min(month, viewState.latestMonth)
        generation += 1
        let cached = viewState.yearPosts[month]
        viewState.contentStatus = cached == nil ? .loading : .loaded
        viewState.month = month
        viewState.posts = cached ?? []
        viewState.selectedDate = cached?.max { $0.topicDate < $1.topicDate }?.topicDate
    }

    /// `showMonth` 뒤에 호출해 고른 달을 서버 값으로 맞춘다.
    func refreshShownMonth() async {
        let previous = viewState.contentStatus == .loaded ? viewState : nil
        await loadCalendar(month: viewState.month, preserving: previous)
    }

    /// 해당 연도의 기록이 있는 지난 달을 불러오고, 사진 색을 채운다.
    func loadYear(_ year: Int) async {
        guard !loadingYears.contains(year) else { return }
        loadingYears.insert(year)
        defer { loadingYears.remove(year) }

        let latestMonth = viewState.latestMonth
        let months = viewState.availableMonths.filter {
            $0.year == year && $0 <= latestMonth && viewState.yearPosts[$0] == nil
        }

        let handler = calendarHandler
        let loaded = await withTaskGroup(of: (RecordMonth, [RecordPost]?).self) { group in
            for month in months {
                group.addTask {
                    guard case let .success(calendar) = await handler(month) else { return (month, nil) }
                    return (month, calendar.posts)
                }
            }
            var loaded: [RecordMonth: [RecordPost]] = [:]
            for await (month, posts) in group {
                if let posts { loaded[month] = posts }
            }
            return loaded
        }
        viewState.yearPosts.merge(loaded) { _, new in new }

        let posts = viewState.yearPosts
            .filter { $0.key.year == year }
            .flatMap(\.value)
        await loadPhotoColors(for: posts)
    }

    /// 아직 색을 모르는 사진만 몇 장씩 나눠 구하고, 묶음마다 화면에 반영해 색이 차례로 채워지게 한다.
    func loadPhotoColors(for posts: [RecordPost]) async {
        let missing = posts.filter { viewState.photoColors[$0.postId] == nil }
        let handler = photoColorHandler
        for start in stride(from: 0, to: missing.count, by: Self.photoColorBatchSize) {
            let chunk = Array(missing[start..<min(start + Self.photoColorBatchSize, missing.count)])
            let colors = await withTaskGroup(of: (RecordPost.ID, RecordPhotoColor?).self) { group in
                for post in chunk {
                    group.addTask { (post.postId, await handler(post)) }
                }
                var colors: [RecordPost.ID: RecordPhotoColor] = [:]
                for await (id, color) in group {
                    if let color { colors[id] = color }
                }
                return colors
            }
            viewState.photoColors.merge(colors) { _, new in new }
        }
    }

    private static let photoColorBatchSize = 8

    func retry() async {
        if shouldRetryMonthList || viewState.availableMonths.isEmpty {
            await load()
        } else {
            await loadCalendar(month: viewState.month)
        }
    }

    /// 현재 달력 데이터를 유지한 채 같은 월을 최신 데이터로 갱신한다.
    func revalidate() async {
        guard viewState.contentStatus != .loading, !isRevalidating else { return }

        isRevalidating = true
        defer { isRevalidating = false }
        // 탭에 다시 들어올 때 설정에서 바꾼 사인도 함께 새로 받는다. 기록 재검증을 기다리게 하지 않는다.
        Task { await loadSignature() }

        generation += 1
        let requestGeneration = generation
        switch await monthListHandler() {
        case let .success(months):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = false
            viewState.availableMonths = monthsIncludingCurrent(months)
        case let .failure(error):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = true
            viewState.availableMonths = monthsIncludingCurrent(viewState.availableMonths)
            event = .showToast(error.message)
        }

        let previous = viewState.contentStatus == .loaded ? viewState : nil
        await loadCalendar(month: viewState.month, preserving: previous)
    }

    func onCalendarImageSaved(_ saved: Bool) {
        event = .showToast(saved ? "달력을 이미지로 저장했어요" : "이미지 저장에 실패했어요")
    }

    func onPhotoLibraryPermissionDenied() {
        event = .showToast("이미지 저장 권한이 필요해요")
    }

    func consumeEvent() {
        event = nil
    }

    func removeDeletedPost(_ postID: RecordPost.ID) {
        // 다른 달로 갔다 돌아왔을 때 캐시에서 지운 게시물이 다시 보이지 않게 한다.
        calendarCache = calendarCache.mapValues { calendar in
            RecordCalendar(month: calendar.month, posts: calendar.posts.filter { $0.postId != postID })
        }
        viewState.posts.removeAll { $0.postId == postID }
        for month in viewState.yearPosts.keys {
            viewState.yearPosts[month]?.removeAll { $0.postId == postID }
        }
        if viewState.month != monthProvider(),
           !viewState.posts.contains(where: { viewState.month.contains($0.topicDate) }) {
            viewState.availableMonths.removeAll { $0 == viewState.month }
        }
        viewState.availableMonths = monthsIncludingCurrent(viewState.availableMonths)
        if viewState.selectedPost == nil {
            initialDate = nil
            viewState.selectedDate = viewState.posts.max { $0.topicDate < $1.topicDate }?.topicDate
        }
    }

    /// `month`의 기록을 받아 화면에 반영한다.
    /// - Parameter previousState: 요청하는 동안 유지할 화면. nil이고 `month`를 캐시해 뒀으면 캐시로 만든 화면을 쓴다.
    ///   유지할 화면이 있으면 실패해도 그 화면을 남기고 토스트만 띄우며, 없으면 로딩 화면에서 시작해 실패 시 오류 화면을 보여준다.
    private func loadCalendar(
        month: RecordMonth,
        preserving previousState: RecordViewState? = nil
    ) async {
        generation += 1
        let requestGeneration = generation
        let latestMonth = max(viewState.latestMonth, monthProvider())
        let availableMonths = monthsIncludingCurrent(viewState.availableMonths)
        viewState.availableMonths = availableMonths

        let previousState = previousState ?? calendarCache[month].map { calendar in
            if let requestedDate = initialDate {
                return loadedState(
                    for: calendar,
                    latestMonth: latestMonth,
                    availableMonths: availableMonths,
                    opening: requestedDate
                )
            }
            return loadedState(
                for: calendar,
                latestMonth: latestMonth,
                availableMonths: availableMonths,
                selectedDate: nil
            )
        }

        if let previousState {
            viewState = previousState
            viewState.latestMonth = latestMonth
        } else {
            viewState = RecordViewState(
                contentStatus: .loading,
                month: month,
                latestMonth: latestMonth,
                availableMonths: availableMonths,
                posts: [],
                selectedDate: nil,
                topicTitles: viewState.topicTitles,
                yearPosts: viewState.yearPosts,
                photoColors: viewState.photoColors
            )
        }

        let selectedDateBeforeRequest = viewState.selectedDate
        let result = await calendarHandler(month)
        // 그사이 다른 달로 넘어갔어도 받은 기록은 다음에 돌아올 때 쓰도록 남긴다.
        if case let .success(calendar) = result {
            calendarCache[month] = calendar
        }
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(calendar):
            if let requestedDate = initialDate {
                viewState = loadedState(
                    for: calendar,
                    latestMonth: latestMonth,
                    availableMonths: availableMonths,
                    opening: requestedDate
                )
            } else {
                // 요청하는 동안 사용자가 직접 고른 날짜만 유지한다. 그대로면 새로 올라온 최근 사진을 고르게 둔다.
                let userSelectedDate = viewState.selectedDate != selectedDateBeforeRequest ? viewState.selectedDate : nil
                viewState = loadedState(
                    for: calendar,
                    latestMonth: latestMonth,
                    availableMonths: availableMonths,
                    selectedDate: userSelectedDate
                )
            }
        case let .failure(error):
            // 화면을 벗어나 취소된 요청은 실패가 아니다. 지금 화면(로딩 또는 캐시)을 그대로 둔다.
            guard !Task.isCancelled else { return }
            if var previousState {
                previousState.latestMonth = latestMonth
                previousState.availableMonths = availableMonths
                viewState = previousState
                event = .showToast(error.message)
            } else {
                viewState = RecordViewState(
                    contentStatus: .error(error),
                    month: month,
                    latestMonth: latestMonth,
                    availableMonths: availableMonths,
                    posts: [],
                    selectedDate: nil,
                    topicTitles: viewState.topicTitles,
                    yearPosts: viewState.yearPosts,
                    photoColors: viewState.photoColors
                )
            }
        }
    }

    /// 받은 기록으로 화면 상태를 만든다.
    /// - Parameter selectedDate: 이 날짜의 게시물이 남아 있으면 선택을 유지하고, nil이거나 없으면 가장 최근 날짜를 고른다.
    private func loadedState(
        for calendar: RecordCalendar,
        latestMonth: RecordMonth,
        availableMonths: [RecordMonth],
        selectedDate: Date?
    ) -> RecordViewState {
        let keepsSelection = selectedDate.map { date in
            calendar.posts.contains { $0.topicDate == date }
        } ?? false
        // 연간 달력·주제·사진 색은 달을 옮겨도 남기고, 받은 달은 연간 달력에도 채운다.
        var yearPosts = viewState.yearPosts
        yearPosts[calendar.month] = calendar.posts
        return RecordViewState(
            contentStatus: .loaded,
            month: calendar.month,
            latestMonth: latestMonth,
            availableMonths: availableMonths,
            posts: calendar.posts,
            selectedDate: keepsSelection
                ? selectedDate
                : calendar.posts.max { $0.topicDate < $1.topicDate }?.topicDate,
            topicTitles: viewState.topicTitles,
            yearPosts: yearPosts,
            photoColors: viewState.photoColors
        )
    }

    /// 홈 주간 기록에서 고른 날짜를 선택한 화면 상태를 만든다.
    /// - Parameter requestedDate: 선택할 날짜. 그 날짜 사진이 없으면 다른 사진을 대신 고르지 않고 선택을 비운다.
    private func loadedState(
        for calendar: RecordCalendar,
        latestMonth: RecordMonth,
        availableMonths: [RecordMonth],
        opening requestedDate: Date
    ) -> RecordViewState {
        var state = loadedState(
            for: calendar,
            latestMonth: latestMonth,
            availableMonths: availableMonths,
            selectedDate: nil
        )
        state.selectedDate = calendar.posts.first {
            RecordMonth.calendar.isDate($0.topicDate, inSameDayAs: requestedDate)
        }?.topicDate
        return state
    }

    private func monthsIncludingCurrent(_ months: [RecordMonth]) -> [RecordMonth] {
        let requestedMonths = initialDate.map { [RecordMonth.current(now: $0)] } ?? []
        return Array(Set(months + [monthProvider()] + requestedMonths)).sorted(by: >)
    }
}
