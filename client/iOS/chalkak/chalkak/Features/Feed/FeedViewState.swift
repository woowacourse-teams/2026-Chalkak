import Foundation

enum FeedContentStatus: Equatable, Sendable {
    case loading
    case loaded
    case error(FeedError)
}

enum FeedError: Error, Equatable, Sendable {
    case notFound
    case network
    case invalidResponse
    case client
    case server
    case generic

    var message: String {
        switch self {
        case .notFound:
            "게시물을 찾을 수 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .invalidResponse:
            "게시물 정보를 불러오지 못했어요"
        case .client, .server, .generic:
            "게시물을 불러오지 못했어요"
        }
    }
}

struct FeedPost: Identifiable, Equatable, Sendable {
    let id: String
    let originalImageSource: ChalkakImageSource
    let signatureImageSource: ChalkakImageSource
    let contentDescription: String
    var title: String?
    var likeCount: Int
    var isLiked: Bool
}

struct FeedContent: Equatable, Sendable {
    var dateLabel: String
    var topic: String
    var post: FeedPost
}

struct FeedViewState: Equatable, Sendable {
    var contentStatus: FeedContentStatus = .loading
    var content: FeedContent?
    // 시드 좋아요 값이 신뢰 가능한지. 전시에서 온 시드는 좋아요 여부를 몰라
    // 상세 조회로 확정되기 전까지 좋아요 동작을 막는다.
    var isLikeEnabled = false
}

/// Home·Display에서 사진을 탭할 때 Feed로 전달하는 네비게이션 payload.
/// 상세를 다시 부르기 전 즉시 보여줄 시드 콘텐츠를 함께 담는다.
/// 동일성/해시는 게시물 id만으로 판단해 시드에 이미지 소스가 있어도 Hashable을 만족한다.
struct FeedTarget: Hashable, Identifiable, Sendable {
    let id: String
    let seed: FeedContent
    // 시드의 좋아요 값이 실제 값인지(홈)·미상인지(전시) 구분.
    let isLikeConfirmed: Bool

    init(seed: FeedContent, isLikeConfirmed: Bool) {
        self.id = seed.post.id
        self.seed = seed
        self.isLikeConfirmed = isLikeConfirmed
    }

    static func == (lhs: FeedTarget, rhs: FeedTarget) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

/// 주제 날짜를 Android FeedViewModel과 동일한 "M월 d일의 주제" 라벨로 변환한다.
enum FeedDateLabel {
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar
    }()

    static func make(from date: Date) -> String {
        let components = calendar.dateComponents([.month, .day], from: date)
        let month = components.month ?? 1
        let day = components.day ?? 1
        return "\(month)월 \(day)일의 주제"
    }
}
