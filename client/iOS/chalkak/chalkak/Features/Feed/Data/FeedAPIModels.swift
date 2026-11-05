import Foundation

struct FeedTopicResponse: Decodable {
    let title: String
    let topicDate: String
}

struct FeedPostDetailResponse: Decodable {
    let id: String
    let topic: FeedTopicResponse
    let originalImageURL: String
    let signatureOriginalImageURL: String
    let title: String?
    let description: String?
    let likeCount: Int
    let isLiked: Bool
    let isMine: Bool?
    // 사인을 눌러 작가 사진 목록으로 갈 때 쓴다. 서버에 아직 없으면 nil이고 사인 탭만 동작하지 않는다.
    let authorID: String?

    enum CodingKeys: String, CodingKey {
        case id
        case topic
        case originalImageURL = "originalImageUrl"
        case signatureOriginalImageURL = "signatureOriginalImageUrl"
        case title
        case description
        case likeCount
        case isLiked
        case isMine
        case authorID = "authorId"
    }

    func toFeedContent() throws -> FeedContent {
        guard !id.isEmpty,
              likeCount >= 0,
              let originalImageURL = URL(string: originalImageURL),
              let signatureImageURL = URL(string: signatureOriginalImageURL),
              let topicDate = Self.apiDateFormatter.date(from: topic.topicDate)
        else {
            throw FeedAPIError.invalidResponse
        }

        let normalizedTitle = title?.trimmingCharacters(in: .whitespacesAndNewlines)
        let contentDescription = normalizedTitle
            .flatMap { $0.isEmpty ? nil : $0 }
            .map { "작품 이미지: \($0)" }
            ?? "무제 작품 이미지"

        return FeedContent(
            dateLabel: FeedDateLabel.make(from: topicDate),
            topicDate: topicDate,
            topic: topic.title,
            post: FeedPost(
                id: id,
                originalImageSource: .remote(originalImageURL),
                signatureImageSource: .remote(signatureImageURL),
                contentDescription: contentDescription,
                title: title,
                likeCount: likeCount,
                isLiked: isLiked,
                isOwnedByCurrentUser: isMine ?? false,
                authorID: authorID,
                description: description.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
            )
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

struct FeedLikeResponse: Decodable {
    let postID: String
    let likeCount: Int
    let isLiked: Bool

    enum CodingKeys: String, CodingKey {
        case postID = "postId"
        case likeCount
        case isLiked
    }
}

struct FeedLikeUpdate: Equatable, Sendable {
    let postID: String
    let isLiked: Bool
    let likeCount: Int
}

/// 게시물 수정 요청. 서버는 제목·설명·인스타그램 동의를 한 번에 통째로 교체한다(POST-010).
/// 빠진 필드는 "없음"으로 덮어쓰므로, 고치지 않은 값도 현재 값을 다시 보내야 한다.
/// 주의: 인스타그램 동의(instagramUploadAgreed, instagramId)는 앱에 입력 UI가 없어 아직 보내지 않고, 서버는 이를 "동의 안 함"으로 저장한다.
/// 동의 UI를 만들 때 이 요청에도 상세 응답의 instagramConsent 값을 함께 실어야 수정할 때마다 동의가 꺼지지 않는다.
/// 근거: 이슈 #938
struct FeedPostUpdateRequest: Encodable {
    let title: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case title
        case description
    }

    // 빈 값도 null로 꼭 보낸다. 설명을 빼고 보내면 서버가 설명을 지운다(POST-010).
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(title, forKey: .title)
        try container.encode(description, forKey: .description)
    }
}

struct FeedPostUpdateResponse: Decodable, Equatable, Sendable {
    let postID: String
    let title: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case postID = "postId"
        case title
        case description
    }
}

struct FeedPostUpdate: Equatable, Sendable {
    let postID: String
    let title: String?
    let description: String?
}
