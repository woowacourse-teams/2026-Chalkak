import Foundation

/// 작가 사진 목록을 여는 데 필요한 값. 피드에서 사인을 눌렀을 때 만든다.
/// 목록 응답에 사인이 없어서, 피드가 이미 가진 사인 주소를 넘겨 탑바에 그린다.
struct AuthorPostsTarget: Hashable, Identifiable, Sendable {
    let authorID: String
    let signatureImageSource: ChalkakImageSource
    // 내 사진 목록이면 그리드 위 설명과 사인 접근성 이름이 달라진다.
    let isMine: Bool
    // 누른 피드 사인의 화면상(global) 위치. 작가 사진 목록이 이 자리에서 펼쳐지고, 닫을 때 다시 접힌다.
    // 없으면 펼치지 않고 페이드한다.
    var signatureTransitionSource: CGRect?

    var id: String { authorID }

    // 전환 출발 위치는 같은 작가 목록인지와 관계없어 비교에서 뺀다.
    static func == (lhs: AuthorPostsTarget, rhs: AuthorPostsTarget) -> Bool {
        lhs.authorID == rhs.authorID
            && lhs.signatureImageSource == rhs.signatureImageSource
            && lhs.isMine == rhs.isMine
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(authorID)
        hasher.combine(signatureImageSource)
        hasher.combine(isMine)
    }
}

struct AuthorPost: Identifiable, Equatable, Sendable {
    let id: String
    let originalImageSource: ChalkakImageSource
    // 응답에 썸네일이 없으면 원본과 같다.
    let thumbnailImageSource: ChalkakImageSource
    let topic: String
    let topicDate: Date
    let contentDescription: String
    // 응답에 좋아요 필드가 없으면(운영 서버 반영 전) nil이다.
    var likeCount: Int? = nil
    var isLiked: Bool? = nil
}

struct AuthorPostsPage: Equatable, Sendable {
    let posts: [AuthorPost]
    let currentPage: Int
    let hasNext: Bool
}

enum AuthorPostsContentStatus: Equatable, Sendable {
    case loading
    // 빈 목록도 loaded이며 posts가 비어 있다.
    case loaded
    case error(AuthorPostsError)
}

enum AuthorPostsError: Error, Equatable, Sendable {
    // 없거나 탈퇴한 작가(404). 탈퇴 여부는 드러내지 않는다.
    case authorNotFound
    // 토큰 갱신까지 실패한 경우(401). 앱이 로그인 화면으로 보낸다.
    case unauthorized
    case network
    case generic

    var message: String {
        switch self {
        case .authorNotFound:
            "더 이상 사진을 볼 수 없어요"
        case .unauthorized, .network, .generic:
            "사진을 불러오지 못했어요"
        }
    }
}

struct AuthorPostsViewState: Equatable, Sendable {
    var contentStatus: AuthorPostsContentStatus = .loading
    var posts: [AuthorPost] = []
    var currentPage = 0
    var hasNext = false
    var isLoadingNext = false

    var isEmpty: Bool {
        contentStatus == .loaded && posts.isEmpty
    }

    // 모든 사진이 좋아요 정보를 받았을 때만 카드에 좋아요를 그린다.
    var showsLikes: Bool {
        posts.allSatisfy { $0.likeCount != nil && $0.isLiked != nil }
    }

    // 404면 작가를 특정할 수 없으므로 탑바 사인과 그리드 위 설명을 숨긴다.
    var showsAuthorIdentity: Bool {
        contentStatus != .error(.authorNotFound)
    }
}

enum AuthorPostsEvent: Equatable, Sendable {
    // 다음 페이지를 받지 못했을 때. 이미 받은 사진은 그대로 둔다.
    case nextPageFailed
    // 카드의 좋아요를 반영하지 못했을 때. 카드는 이전 상태를 유지한다.
    case likeFailed
}
