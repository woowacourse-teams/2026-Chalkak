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
    // 공식 인스타그램 게시 동의. 조회자가 작성자 본인일 때만 오고, 아니면 null이다.
    let instagramConsent: FeedInstagramConsentResponse?

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
        case instagramConsent
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
                description: description.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 },
                instagramConsent: instagramConsent.map {
                    InstagramConsent(isAgreed: $0.agreed, instagramID: $0.agreed ? $0.instagramID : nil)
                }
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

/// 상세 응답의 `instagramConsent`. 예: `{"agreed":true,"instagramId":"chalkak.daily"}`
struct FeedInstagramConsentResponse: Decodable {
    let agreed: Bool
    let instagramID: String?

    enum CodingKeys: String, CodingKey {
        case agreed
        case instagramID = "instagramId"
    }
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
/// 동의는 동의했을 때만 `instagramUploadAgreed: true`와 ID를 싣는다. 빠지면 서버가 동의를 철회하고 ID를 지운다(Android와 같음).
/// 예: 제목만 고치고 동의 유지 → `{"title":"새 제목","description":null,"instagramUploadAgreed":true,"instagramId":"chalkak.daily"}`
/// 근거: 이슈 #938, #939
struct FeedPostUpdateRequest: Encodable {
    let title: String?
    let description: String?
    var instagramConsent: InstagramConsent = .notAgreed

    enum CodingKeys: String, CodingKey {
        case title
        case description
        case instagramUploadAgreed
        case instagramID = "instagramId"
    }

    // 제목·설명은 빈 값도 null로 꼭 보낸다. 설명을 빼고 보내면 서버가 설명을 지운다(POST-010).
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(title, forKey: .title)
        try container.encode(description, forKey: .description)
        if instagramConsent.isAgreed {
            try container.encode(true, forKey: .instagramUploadAgreed)
            try container.encodeIfPresent(instagramConsent.instagramID, forKey: .instagramID)
        }
    }
}

struct FeedPostUpdateResponse: Decodable, Equatable, Sendable {
    let postID: String
    let title: String?
    let description: String?
    // 저장된 동의. 서버 반영 전이면 없다.
    let instagramUploadAgreed: Bool?
    let instagramID: String?

    enum CodingKeys: String, CodingKey {
        case postID = "postId"
        case title
        case description
        case instagramUploadAgreed
        case instagramID = "instagramId"
    }
}

struct FeedPostUpdate: Equatable, Sendable {
    let postID: String
    let title: String?
    let description: String?
    // 저장된 인스타그램 동의. 응답에 없으면 nil이고, 뷰모델이 보낸 값으로 대신한다.
    var instagramConsent: InstagramConsent? = nil
}
