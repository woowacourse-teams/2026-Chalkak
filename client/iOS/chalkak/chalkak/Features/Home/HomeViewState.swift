import Foundation

enum HomeSectionStatus: Equatable, Sendable {
    case loading, ready, error
}

struct HomeHeroPhoto: Equatable, Sendable {
    let imageSource: ChalkakImageSource
    let thumbnailSource: ChalkakImageSource
    let signatureSource: ChalkakImageSource?
    let isPending: Bool
    var postID: String? = nil
}

enum HomeHeroState: Equatable, Sendable {
    case loading, empty, loginRequired, noOpenTopic, processing, error
    case rejected(canResubmit: Bool)
    case photo(HomeHeroPhoto)
}

struct HomeWeekDay: Identifiable, Equatable, Sendable {
    let date: Date
    var post: RecordPost?
    var id: Date { date }
}

struct HomeRankingState: Equatable, Sendable {
    var status: HomeSectionStatus = .loading
    var photos: [DisplayPhoto] = []
}

struct HomeAIReviewState: Equatable, Sendable {
    var review: AIReview?
    var isLoading = false
    var isSubmitting = false
    var pollingTimedOut = false
    var message: String?
    var isUnavailable = false
}

/// 홈 피드백 카드를 눌렀을 때 화면이 할 일(`HomeViewModel.feedbackTapAction`).
enum HomeFeedbackTapAction: Equatable, Sendable {
    /// 아무것도 하지 않는다. 진행 중·완료·안내 상태처럼 누를 일이 없는 카드다.
    case none
    /// AI 리뷰 상태를 다시 조회한다(`loadError`).
    case retryStatus
    /// 피드백 시작 안내 시트를 연다(`ready`). 시트에서 시작해야 AI 리뷰를 요청한다.
    case presentStartSheet
    /// 실패한 AI 리뷰를 바로 다시 요청한다(`failed(canRequestAgain: true)`).
    case requestAgain
}

struct HomeViewState: Equatable, Sendable {
    var date: Date
    var topic = ""
    var topicStatus: HomeSectionStatus = .loading
    var aiReview = HomeAIReviewState()
    var hero: HomeHeroState = .loading
    var week: [HomeWeekDay]
    var recordsStatus: HomeSectionStatus = .loading
    var yesterday = HomeRankingState()
    var trending = HomeRankingState()
    var banners: [Banner] = []
    var bannerStatus: HomeSectionStatus = .loading
    var bannerCurrentPage = 0
    var bannerHasNext = false
    var isLoadingMoreBanners = false
    var bannerLoadMoreError = false
    var isIssueOnTop = false
    var isIssueLayoutResolved = true
    var isRefreshing = false
    var isAuthenticated = false

    var showsIssue: Bool {
        // 배치가 정해지기 전에는 ISSUE를 숨겨 아래에서 위로 튀지 않게 한다.
        isIssueLayoutResolved && (!banners.isEmpty || bannerStatus == .loading)
    }

    init(date: Date = Date()) {
        self.date = RecordMonth.calendar.startOfDay(for: date)
        self.week = Self.weekDates(date).map { HomeWeekDay(date: $0) }
    }

    static func weekDates(_ date: Date) -> [Date] {
        let calendar = RecordMonth.calendar
        let day = calendar.startOfDay(for: date)
        let sunday = calendar.date(byAdding: .day, value: 1 - calendar.component(.weekday, from: day), to: day)!
        return (0..<7).map { calendar.date(byAdding: .day, value: $0, to: sunday)! }
    }
}
