import Foundation

/// 달력 사진의 심사 상태. Android `PostStatus`와 의미를 맞춘다.
enum RecordPostStatus: Equatable, Sendable {
    case approved
    case pending
    case rejected
    case unknown

    init(rawValue: String) {
        switch rawValue.uppercased() {
        case "APPROVED":
            self = .approved
        case "PENDING":
            self = .pending
        case "REJECTED":
            self = .rejected
        default:
            self = .unknown
        }
    }
}

/// 달력의 하루에 대응하는 게시물. Android `PostCalendarItem`과 대응한다.
struct RecordPost: Identifiable, Equatable, Sendable {
    let postId: String
    let topicDate: Date
    let thumbnailImageSource: ChalkakImageSource
    let status: RecordPostStatus

    var id: String { postId }
}

/// 특정 연월의 달력 조회 결과. Android `PostCalendar`와 대응한다.
struct RecordCalendar: Equatable, Sendable {
    let month: RecordMonth
    let posts: [RecordPost]
}

enum RecordError: Error, Equatable, Sendable {
    case invalidMonth
    case unauthorized
    case network
    case invalidResponse
    case generic

    /// Android `HomeFailure.toRecordMessage()`와 문구를 맞춘다.
    var message: String {
        switch self {
        case .unauthorized:
            "로그인이 필요해요"
        case .invalidMonth:
            "조회할 수 없는 연월이에요"
        case .network, .invalidResponse, .generic:
            "기록을 불러오지 못했어요"
        }
    }

    var isLoginRequired: Bool {
        self == .unauthorized
    }
}

enum RecordContentStatus: Equatable, Sendable {
    case loading
    case loaded
    case error(RecordError)
}

struct RecordViewState: Equatable, Sendable {
    var contentStatus: RecordContentStatus = .loading
    var month: RecordMonth
    var latestMonth: RecordMonth
    var availableMonths: [RecordMonth]
    var posts: [RecordPost] = []
    var selectedDate: Date?
    /// 주간 보기에 표시할 날짜별 주제 제목. 날짜가 바뀌지 않는 값이라 월을 옮겨도 유지한다.
    var topicTitles: [Date: String] = [:]
    /// 연간 달력에 쓰는 월별 게시물. 한 번 불러온 달은 다시 부르지 않는다.
    var yearPosts: [RecordMonth: [RecordPost]] = [:]
    /// 연간 달력 칸에 칠할 사진별 대표색.
    var photoColors: [RecordPost.ID: RecordPhotoColor] = [:]
    /// 월간 책자 속표지에 넣을 내 사인. 아직 받지 못했거나 사인이 없으면 nil.
    var signatureURL: URL?

    init(
        contentStatus: RecordContentStatus = .loading,
        month: RecordMonth = .current(),
        latestMonth: RecordMonth = .current(),
        availableMonths: [RecordMonth] = [],
        posts: [RecordPost] = [],
        selectedDate: Date? = nil,
        topicTitles: [Date: String] = [:],
        yearPosts: [RecordMonth: [RecordPost]] = [:],
        photoColors: [RecordPost.ID: RecordPhotoColor] = [:],
        signatureURL: URL? = nil
    ) {
        self.contentStatus = contentStatus
        self.month = month
        self.latestMonth = latestMonth
        self.availableMonths = availableMonths
        self.posts = posts
        self.selectedDate = selectedDate
        self.topicTitles = topicTitles
        self.yearPosts = yearPosts
        self.photoColors = photoColors
        self.signatureURL = signatureURL
    }

    /// 연간 달력에서 이동할 수 있는 가장 이른 해. 기록이 없으면 최신 연도다.
    var earliestYear: Int {
        min(availableMonths.map(\.year).min() ?? latestMonth.year, latestMonth.year)
    }

    var selectedPost: RecordPost? {
        guard let selectedDate else { return nil }
        return posts.first { $0.topicDate == selectedDate }
    }

    var errorMessage: String? {
        if case let .error(error) = contentStatus { return error.message }
        return nil
    }

    var isLoginRequired: Bool {
        if case let .error(error) = contentStatus { return error.isLoginRequired }
        return false
    }

    var canGoPrevious: Bool {
        contentStatus != .loading && availableMonths.contains { $0 < month }
    }

    var canGoNext: Bool {
        contentStatus != .loading && availableMonths.contains { $0 > month }
    }

    /// 년/월 휠을 열 수 있는지. 휠에는 `availableMonths`만 보인다.
    var canPickMonth: Bool {
        contentStatus != .loading && !availableMonths.isEmpty
    }
}

enum RecordEvent: Equatable, Sendable {
    case showToast(String)
}
