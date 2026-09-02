import Foundation

struct DisplayTopicResponse: Decodable {
    let title: String
    let topicDate: String
}

struct DisplayPostPageResponse: Decodable {
    let currentPage: Int
    let hasNext: Bool
    let randomSeed: String?
    let posts: [DisplayPostResponse]
}

struct DisplayPostResponse: Decodable {
    let id: String
    let originalImageURL: String
    let thumbnailImageURL: String
    let signatureOriginalImageURL: String
    let signatureThumbnailImageURL: String
    let title: String?
    let likeCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case originalImageURL = "originalImageUrl"
        case thumbnailImageURL = "thumbnailImageUrl"
        case signatureOriginalImageURL = "signatureOriginalImageUrl"
        case signatureThumbnailImageURL = "signatureThumbnailImageUrl"
        case title
        case likeCount
    }

    func toDisplayPhoto() throws -> DisplayPhoto {
        guard !id.isEmpty,
              likeCount >= 0,
              let originalImageURL = URL(string: originalImageURL),
              let thumbnailImageURL = URL(string: thumbnailImageURL),
              let signatureOriginalImageURL = URL(string: signatureOriginalImageURL),
              let signatureThumbnailImageURL = URL(string: signatureThumbnailImageURL)
        else {
            throw DisplayAPIError.invalidResponse
        }

        let normalizedTitle = title?.trimmingCharacters(in: .whitespacesAndNewlines)
        let contentDescription = normalizedTitle
            .flatMap { $0.isEmpty ? nil : $0 }
            .map { "작품 이미지: \($0)" }
            ?? "무제 작품 이미지"

        return DisplayPhoto(
            id: id,
            originalImageSource: .remote(originalImageURL),
            thumbnailImageSource: .remote(thumbnailImageURL),
            signatureOriginalImageSource: .remote(signatureOriginalImageURL),
            signatureThumbnailImageSource: .remote(signatureThumbnailImageURL),
            contentDescription: contentDescription,
            title: title,
            likeCount: likeCount
        )
    }
}
