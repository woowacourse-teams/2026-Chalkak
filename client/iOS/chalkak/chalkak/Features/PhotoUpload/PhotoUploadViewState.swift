import Foundation
import UIKit

struct PhotoUploadTopic: Equatable, Sendable {
    let id: String
    let title: String
    let date: Date
}

enum PhotoUploadModerationStatus: String, Equatable, Sendable {
    case validating = "VALIDATING"
    case pending = "PENDING"

    var successMessage: String {
        switch self {
        case .pending:
            "검수를 기다리고 있어요. 피드 표시까지 시간이 조금 걸릴 수도 있어요!"
        case .validating:
            "사진을 확인하고 있어요. 피드 표시까지 시간이 조금 걸릴 수도 있어요!"
        }
    }
}

struct PhotoUploadSuccessContent: Equatable, Sendable {
    let date: Date
    let topic: String
    let moderationStatus: PhotoUploadModerationStatus
}

struct PhotoUploadPreparation: Sendable {
    let id: UUID
    let sourceData: Data
    let encodedData: Data
    let upload: PhotoUploadUploadPolicy
    let uploadURLExpiresAt: Date
}

struct PhotoUploadUploadPolicy: Equatable, Sendable {
    let uploadID: String
    let uploadURL: URL
    let expiresInSeconds: Int64
    let contentType: String
    let maxBytes: Int64
}

struct PhotoUploadCreation: Equatable, Sendable {
    let postID: String
    let topic: PhotoUploadTopic
    let moderationStatus: PhotoUploadModerationStatus
}

struct PhotoUploadSubmission {
    let id: UUID
    let image: UIImage
    let caption: String
    let content: PhotoUploadSuccessContent

    init(
        id: UUID = UUID(),
        image: UIImage,
        caption: String,
        content: PhotoUploadSuccessContent
    ) {
        self.id = id
        self.image = image
        self.caption = caption
        self.content = content
    }
}

enum PhotoUploadImagePreparationStatus: Equatable, Sendable {
    case idle
    case preparing
    case ready
    case failed
}

enum PhotoUploadFailure: Error, Equatable, Sendable {
    case reauthenticationRequired
    case networkUnavailable
    case imagePreparationFailed
    case uploadRejected
    case postCreationRejected
    case alreadySubmitted
    case topicNotOpen
    case topicLoadFailed
    case invalidResponse

    var message: String {
        switch self {
        case .reauthenticationRequired:
            "로그인이 필요해요."
        case .networkUnavailable:
            "네트워크 연결을 확인해 주세요."
        case .imagePreparationFailed:
            "사진을 준비하지 못했어요. 다시 시도해 주세요."
        case .uploadRejected:
            "사진을 업로드하지 못했어요. 다시 시도해 주세요."
        case .postCreationRejected:
            "전시를 완료하지 못했어요. 다시 시도해 주세요."
        case .alreadySubmitted:
            "이미 이 주제에 전시한 사진이 있어요."
        case .topicNotOpen:
            "주제가 변경되어 전시할 수 없어요."
        case .topicLoadFailed:
            "주제를 불러오지 못했어요. 다시 시도해 주세요."
        case .invalidResponse:
            "전시를 완료하지 못했어요. 다시 시도해 주세요."
        }
    }
}

struct PhotoUploadMessage: Equatable, Sendable {
    let id: Int
    let text: String
}

struct PhotoUploadViewState {
    var selectedImage: UIImage? = nil
    var selectedImageData: Data? = nil
    var imagePreparationStatus: PhotoUploadImagePreparationStatus = .idle
    var caption = ""
    var topicTitle: String?
    var isTopicLoading = false
    var isSubmitting = false
    var topicErrorMessage: String?
    var completedSubmission: PhotoUploadSubmission?
    var pendingMessage: PhotoUploadMessage?

    var canSubmit: Bool {
        selectedImage != nil
            && !isTopicLoading
            && !isSubmitting
            && topicErrorMessage == nil
            && completedSubmission == nil
    }
}

enum PhotoUploadEvent: Equatable, Sendable {
    case navigateBack
    case openGallery
    case openCamera
    case reauthenticationRequired
}

enum PhotoUploadDate {
    static let timeZone = TimeZone(identifier: "Asia/Seoul")!

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    static func startOfDay(_ date: Date) -> Date {
        calendar.startOfDay(for: date)
    }

    static func today() -> Date {
        startOfDay(Date())
    }

    static func apiString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func date(fromAPIString string: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: string) else { return nil }
        return calendar.startOfDay(for: date)
    }

    static func displayString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy. MM. dd"
        return formatter.string(from: date)
    }
}
