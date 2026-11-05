import Foundation
import UIKit

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
    case invalidInstagramID

    var message: String {
        switch self {
        case .notFound:
            "게시물을 찾을 수 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .invalidResponse:
            "게시물 정보를 불러오지 못했어요"
        case .client, .server, .generic, .invalidInstagramID:
            "게시물을 불러오지 못했어요"
        }
    }

    var deleteMessage: String {
        switch self {
        case .notFound:
            "게시물을 찾을 수 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .invalidResponse, .client, .server, .generic, .invalidInstagramID:
            "게시물을 삭제하지 못했어요"
        }
    }

    var postUpdateMessage: String {
        switch self {
        case .notFound:
            "게시물을 찾을 수 없어요"
        case .network:
            "네트워크 연결을 확인해 주세요"
        case .client:
            "수정할 수 없는 상태이거나 주제 참여 기간이 끝났어요"
        case .invalidResponse, .server, .generic:
            "게시물을 수정하지 못했어요"
        case .invalidInstagramID:
            InstagramConsentInput.invalidInstagramIDMessage
        }
    }
}

enum FeedEvent: Equatable, Sendable {
    case showDeleteFailure(FeedError)
    case showPostUpdateSuccess
    case showPostUpdateFailure(FeedError)
    case showPostEditUnavailable
    case showLikeFailure

    /// 본인 동의 정보를 못 받아 수정을 열 수 없을 때의 안내. 그대로 열면 저장할 때 동의가 철회된다(POST-010).
    static let postEditUnavailableMessage = "게시물을 불러오지 못해 수정할 수 없어요"
}

struct FeedPost: Identifiable, Equatable, Sendable {
    let id: String
    let originalImageSource: ChalkakImageSource
    let signatureImageSource: ChalkakImageSource
    let contentDescription: String
    var title: String?
    var likeCount: Int
    var isLiked: Bool
    var isOwnedByCurrentUser: Bool
    // 사진 작성자의 회원 ID. 사인을 눌러 작가 사진 목록을 열 때 쓰며, 응답에 없으면 nil이다.
    var authorID: String? = nil
    // 작품 설명. 줄바꿈을 포함할 수 있고, 없으면 nil이라 Feed에 표시하지 않는다.
    var description: String? = nil
    // 공식 인스타그램 게시 동의. 서버가 작성자 본인의 상세 조회에만 내려 주며, 받기 전이거나 다른 사람 게시물이면 nil이다.
    var instagramConsent: InstagramConsent? = nil
}

enum FeedSignatureTapAction: Equatable, Sendable {
    case openAuthorPosts(AuthorPostsTarget)
    // 이 피드를 연 작가 사진 목록으로 돌아간다.
    case returnToAuthorPosts
}

struct FeedContent: Equatable, Sendable {
    var dateLabel: String
    var topicDate: Date
    var topic: String
    var post: FeedPost
}

struct FeedViewState: Equatable, Sendable {
    var contentStatus: FeedContentStatus = .loading
    var content: FeedContent?
    // 시드 좋아요 값이 신뢰 가능한지. 전시에서 온 시드는 좋아요 여부를 몰라
    // 상세 조회로 확정되기 전까지 좋아요 동작을 막는다.
    var isLikeEnabled = false
    var isDeleting = false
    var isSavingPostEdit = false
    var deletedPostID: FeedPost.ID?
    // 수정 저장이 성공할 때마다 1씩 오른다. 진행 중이던 상세 조회가 수정 결과를 덮지 않게 비교하는 데 쓴다.
    var postUpdateVersion = 0
    // 수정 화면이 열려 있는 동안의 입력값. nil이면 수정 화면이 닫혀 있다.
    var postEdit: FeedPostEditState?
}

struct FeedPostEditState: Equatable, Sendable {
    var title: String
    var description: String
    // 인스타그램 게시 동의 스위치와 ID 칸. 수정은 동의도 통째로 교체하므로 현재 값으로 채워 연다.
    var instagramConsent = InstagramConsentInput()
}

/// 원본 이미지가 로드되기 전까지 Feed 사진 자리에 보여줄, 출발 화면에 떠 있던 이미지.
struct FeedPhotoPlaceholder: Equatable, Sendable {
    let imageSource: ChalkakImageSource
    let signatureImageSource: ChalkakImageSource?
    // 세로/가로 비율(height / width). 모르면 Feed가 이미지에서 다시 구한다.
    let heightToWidthRatio: CGFloat?
    // 출발 화면이 이미 받아 둔 이미지. 있으면 전환 첫 프레임부터 바로 그린다.
    let preloadedImage: UIImage?
    let preloadedSignatureImage: UIImage?

    init(
        imageSource: ChalkakImageSource,
        signatureImageSource: ChalkakImageSource? = nil,
        heightToWidthRatio: CGFloat? = nil
    ) {
        let preloadedImage = imageSource.cachedImage()
        self.imageSource = imageSource
        self.signatureImageSource = signatureImageSource
        self.preloadedImage = preloadedImage
        self.preloadedSignatureImage = signatureImageSource?.cachedImage()
        // 카드의 크롭 비율보다 캐시된 이미지 자체의 비율을 우선한다.
        self.heightToWidthRatio = preloadedImage.flatMap { image in
            image.size.width > 0 ? image.size.height / image.size.width : nil
        } ?? heightToWidthRatio
    }

    /// 원본을 띄우는 화면에서 출발할 때 쓴다. Android 랭킹 카드처럼 원본이 이미 받아져 있으면 원본,
    /// 아직 없으면(또는 캐시에서 밀려났으면) 썸네일로 출발해 피드가 이미지를 다시 받는 동안 비지 않게 한다.
    static func preferringOriginal(
        original: ChalkakImageSource,
        thumbnail: ChalkakImageSource,
        signatureOriginal: ChalkakImageSource,
        signatureThumbnail: ChalkakImageSource
    ) -> FeedPhotoPlaceholder {
        FeedPhotoPlaceholder(
            imageSource: original.cachedImage() != nil ? original : thumbnail,
            signatureImageSource: signatureOriginal.cachedImage() != nil ? signatureOriginal : signatureThumbnail
        )
    }
}

/// Home·Display에서 사진을 탭할 때 Feed로 전달하는 네비게이션 payload.
/// 상세를 다시 부르기 전 즉시 보여줄 시드 콘텐츠를 함께 담는다.
/// 동일성/해시는 게시물 id만으로 판단해 시드에 이미지 소스가 있어도 Hashable을 만족한다.
struct FeedTarget: Hashable, Identifiable, Sendable {
    let id: String
    // 시드가 없으면(기록에서 진입) Feed가 상세 조회로 콘텐츠를 채운다.
    let seed: FeedContent?
    // 시드의 좋아요 값이 실제 값인지(홈)·미상인지(전시) 구분.
    let isLikeConfirmed: Bool
    // 기록에서 진입한 경우처럼, 상세 응답과 별개로 내 게시물임이 보장된 경로인지 여부.
    let isOwnedByCurrentUser: Bool
    // 줌 전환의 출발 뷰. 없으면 사진 이동 없이 페이드로만 연다.
    let zoomSource: FeedZoomSource?
    // 원본 이미지가 로드되기 전까지 보여줄, 출발 화면에 이미 표시된 이미지.
    let placeholder: FeedPhotoPlaceholder?
    // 탭 순간의 출발 좌표. LazyVStack 셀의 수명과 독립적으로 열림 전환 동안 보존한다.
    var transitionSource: FeedZoomRegistry.Source?
    // 작가 사진 목록에서 연 피드면 그 작가의 ID. 같은 작가의 사인을 누르면 목록을 새로 쌓지 않고 돌아간다.
    var sourceAuthorID: String?

    init(
        seed: FeedContent,
        isLikeConfirmed: Bool,
        zoomSource: FeedZoomSource? = nil,
        placeholder: FeedPhotoPlaceholder? = nil
    ) {
        self.id = seed.post.id
        self.seed = seed
        self.isLikeConfirmed = isLikeConfirmed
        self.isOwnedByCurrentUser = seed.post.isOwnedByCurrentUser
        self.zoomSource = zoomSource
        self.placeholder = placeholder
    }

    /// 게시물 id만으로 진입한다(기록 화면). 시드가 없어 Feed가 상세를 로드한다.
    init(
        postID: String,
        isOwnedByCurrentUser: Bool = false,
        zoomSource: FeedZoomSource? = nil,
        placeholder: FeedPhotoPlaceholder? = nil
    ) {
        self.id = postID
        self.seed = nil
        self.isLikeConfirmed = false
        self.isOwnedByCurrentUser = isOwnedByCurrentUser
        self.zoomSource = zoomSource
        self.placeholder = placeholder
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

    static func isToday(_ date: Date, now: Date = Date()) -> Bool {
        calendar.dateComponents([.year, .month, .day], from: date) ==
            calendar.dateComponents([.year, .month, .day], from: now)
    }
}
