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
    let likeCount: Int
    let isLiked: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case topic
        case originalImageURL = "originalImageUrl"
        case signatureOriginalImageURL = "signatureOriginalImageUrl"
        case title
        case likeCount
        case isLiked
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
            topic: topic.title,
            post: FeedPost(
                id: id,
                originalImageSource: .remote(originalImageURL),
                signatureImageSource: .remote(signatureImageURL),
                contentDescription: contentDescription,
                title: title,
                likeCount: likeCount,
                isLiked: isLiked
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
