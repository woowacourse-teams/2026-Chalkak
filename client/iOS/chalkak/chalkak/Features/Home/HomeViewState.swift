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
    case loading, empty, noOpenTopic, processing, error
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
    var isRefreshing = false
    var isAuthenticated = false

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
