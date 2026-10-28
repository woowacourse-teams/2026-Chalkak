import Foundation

struct TodayViewState: Equatable, Sendable {
    var contentStatus: TodayContentStatus = .loading
    var topicDate: Date?
    var topic = ""
    var photos: [TodayPhoto] = []
    var selectedSort: TodayPostSort = .latest
    var likedPhotoIDs: Set<TodayPhoto.ID> = []
    var currentPage = 0
    var hasNext = false
    var randomSeed: String?
    var isLoadingNext = false
    var isRefreshing = false
    var areLikesEnabled = true
}

enum TodayContentStatus: Equatable, Sendable {
    case loading
    case error(TodayInitialError)
    case content
}

enum TodayInitialError: Error, Equatable, Sendable {
    case topicNotFound
    case unauthorized
    case network
    case invalidResponse
    case client
    case server
    case generic
    case cancelled

    var message: String {
        switch self {
        case .topicNotFound:
            "오늘의 주제가 아직 준비되지 않았어요"
        case .unauthorized:
            "로그인 정보를 확인할 수 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .invalidResponse:
            "홈 정보를 불러오지 못했어요"
        case .client:
            "요청을 처리하지 못했어요"
        case .server:
            "서버에 잠시 문제가 생겼어요"
        case .generic:
            "홈을 불러오지 못했어요"
        case .cancelled:
            ""
        }
    }
}

enum TodayPostSort: Equatable, Sendable {
    case latest
    case popular
    case random
}

struct TodayPhoto: Identifiable, Equatable, Sendable {
    let id: String
    var imageSource: ChalkakImageSource
    var signatureSource: ChalkakImageSource
    var contentDescription: String
    var title: String?
    var likeCount: Int
    var isOwnedByCurrentUser: Bool

    init(
        id: String,
        imageSource: ChalkakImageSource,
        signatureSource: ChalkakImageSource,
        contentDescription: String,
        title: String?,
        likeCount: Int,
        isOwnedByCurrentUser: Bool = false
    ) {
        self.id = id
        self.imageSource = imageSource
        self.signatureSource = signatureSource
        self.contentDescription = contentDescription
        self.title = title
        self.likeCount = likeCount
        self.isOwnedByCurrentUser = isOwnedByCurrentUser
    }
}

struct TodayPage: Equatable, Sendable {
    var photos: [TodayPhoto]
    var likedPhotoIDs: Set<TodayPhoto.ID>
    var currentPage: Int
    var hasNext: Bool
    var randomSeed: String?
}

struct TodayLikeUpdate: Equatable, Sendable {
    var photoID: TodayPhoto.ID
    var isLiked: Bool
    var likeCount: Int
}

enum TodayEvent: Equatable, Sendable {
    case openPhotoUpload
    case showGuestLikeMessage
    case showRefreshFailure(TodayInitialError)
    case navigateToBottomBar(ChalkakBottomBarItem)
}
