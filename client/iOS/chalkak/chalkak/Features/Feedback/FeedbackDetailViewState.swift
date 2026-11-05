import Foundation

/// `GET /feedbacks/{id}`로 받은 문의 한 건.
struct FeedbackDetail: Equatable, Sendable {
    let id: String
    let content: String
    /// UTC 접수 시각.
    let createdAt: Date
    /// 서버의 `answerContent`·`answeredAt`이 둘 다 있을 때만 값이 있다. 미답변이거나 답변이 지워졌으면 nil.
    let answer: FeedbackAnswer?
}

struct FeedbackAnswer: Equatable, Sendable {
    let content: String
    /// UTC 답변 등록 시각.
    let answeredAt: Date
}

enum FeedbackDetailStatus: Equatable, Sendable {
    case loading
    case content
    /// 404. 삭제됐거나 다른 계정의 문의라 볼 수 없다.
    case notFound
    case failed
}

struct FeedbackDetailViewState: Equatable, Sendable {
    var status: FeedbackDetailStatus = .loading
    var detail: FeedbackDetail?

    /// 찾을 수 없는 문의일 때 화면에 보여줄 안내.
    var message: String? {
        status == .notFound ? FeedbackMessages.notFound : nil
    }
}

enum FeedbackDetailEvent: Equatable, Sendable {
    /// 목록에 남아 있을 수 있는 없는 문의를 지우도록 목록을 1페이지부터 다시 불러 달라는 요청.
    case listRefreshRequested
}

enum FeedbackMessages {
    /// 상세·삭제 404 안내. 타인·삭제·없는 문의를 구분하지 않는다.
    static let notFound = "문의를 찾을 수 없어요"
}
