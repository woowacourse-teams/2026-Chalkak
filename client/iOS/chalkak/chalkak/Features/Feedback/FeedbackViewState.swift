import Foundation

struct FeedbackViewState: Equatable, Sendable {
    var content = ""
    var isSubmitting = false

    var normalizedContent: String {
        content.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var contentLength: Int {
        normalizedContent.unicodeScalars.count
    }

    var canSubmit: Bool {
        !isSubmitting
            && !normalizedContent.isEmpty
            && contentLength <= FeedbackLimits.maximumContentLength
    }
}

enum FeedbackEvent: Equatable, Sendable {
    case submitted
    case reauthenticationRequired
    case showMessage(String)
}
