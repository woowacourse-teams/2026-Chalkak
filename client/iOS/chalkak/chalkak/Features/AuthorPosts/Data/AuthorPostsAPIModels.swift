import Foundation

// GET /users/{userId}/posts 응답. 목록에는 제목·사인이 없다(POST-012).
struct AuthorPostPageResponse: Decodable {
    let currentPage: Int
    let pageSize: Int?
    let hasNext: Bool
    let posts: [AuthorPostResponse]
}

struct AuthorPostTopicResponse: Decodable {
    let title: String
    let topicDate: String
}

struct AuthorPostResponse: Decodable {
    let id: String
    let originalImageURL: String
    // 썸네일이 없는 기존 사진은 null이다.
    let thumbnailImageURL: String?
    let topic: AuthorPostTopicResponse
    // 전체 좋아요 수와 로그인한 조회자 본인의 좋아요 여부(#819). 운영 서버에 아직 없으면 nil이고 카드에 좋아요를 그리지 않는다.
    let likeCount: Int?
    let isLiked: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case originalImageURL = "originalImageUrl"
        case thumbnailImageURL = "thumbnailImageUrl"
        case topic
        case likeCount
        case isLiked
    }

    func toAuthorPost() throws -> AuthorPost {
        guard !id.isEmpty,
              likeCount.map({ $0 >= 0 }) ?? true,
              let originalImageURL = URL(string: originalImageURL),
              let topicDate = Self.apiDateFormatter.date(from: topic.topicDate)
        else {
            throw AuthorPostsAPIError.invalidResponse
        }
        // 썸네일이 없거나 잘못된 주소면 원본을 그리드에 그린다.
        let thumbnailImageURL = thumbnailImageURL.flatMap(URL.init(string:)) ?? originalImageURL

        return AuthorPost(
            id: id,
            originalImageSource: .remote(originalImageURL),
            thumbnailImageSource: .remote(thumbnailImageURL),
            topic: topic.title,
            topicDate: topicDate,
            contentDescription: "\(FeedDateLabel.make(from: topicDate)) \(topic.title) 작품 이미지",
            likeCount: likeCount,
            isLiked: isLiked
        )
    }

    private static let apiDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Seoul")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
