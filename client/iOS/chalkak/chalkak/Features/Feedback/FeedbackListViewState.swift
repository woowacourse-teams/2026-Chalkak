import Foundation

/// 내 문의 목록의 한 줄. 서버는 문의 원문을 주고, 목록은 화면에서 2줄로 줄여 보여준다.
struct FeedbackSummary: Identifiable, Equatable, Sendable {
    /// 문의 UUID(`feedbackId`). 상세·삭제 경로에 그대로 쓴다.
    let id: String
    let content: String
    /// 서버가 UTC로 준 접수 시각.
    let createdAt: Date
}

/// `GET /feedbacks?page=&pageSize=` 한 페이지.
struct FeedbackPage: Equatable, Sendable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool
    let items: [FeedbackSummary]
}

enum FeedbackListStatus: Equatable, Sendable {
    /// 첫 페이지를 불러오는 중이고 보여줄 목록이 아직 없다.
    case loading
    case content
    /// 첫 페이지가 `feedbacks: []`라서 빈 화면 안내를 보여준다.
    case empty
    /// 첫 페이지를 불러오지 못해 다시 시도 버튼을 보여준다.
    case failed
}

struct FeedbackListViewState: Equatable, Sendable {
    var status: FeedbackListStatus = .loading
    var items: [FeedbackSummary] = []
    var hasNext = false
    var isLoadingMore = false
    /// 다음 페이지를 불러오지 못하면 목록 끝에 다시 시도 줄을 보여준다.
    var loadMoreFailed = false
    /// 삭제 요청이 응답을 기다리는 문의 ID. 같은 문의의 삭제를 다시 보내지 않는다.
    var deletingIDs: Set<String> = []
}

/// 상세 화면의 삭제 버튼을 누른 결과. 화면은 이 값으로 안내 문구와 닫기 여부를 정한다.
enum FeedbackDeleteResult: Equatable, Sendable {
    /// 204를 받아 목록에서 뺐다. 상세를 닫는다.
    case deleted
    /// 404라 더 볼 수 없는 문의다. 안내 후 상세를 닫고 목록은 1페이지부터 다시 불러온다.
    case notFound(message: String)
    /// 그 밖의 실패. 상세에 남아 다시 누를 수 있다.
    case failed(message: String)
    /// 같은 문의를 이미 삭제하는 중이라 요청을 보내지 않았다.
    case ignored
}
