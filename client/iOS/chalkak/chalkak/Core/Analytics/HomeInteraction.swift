/// 홈 A/B 테스트에서 비교하는 사용자 행동. position은 1부터 센다.
enum HomeInteraction: Equatable {
    enum RankingSection: String {
        case yesterday
        case trending
    }

    enum RankingAction: String {
        case photo
        case more
    }

    enum RecordAction: String {
        case more
        case date
    }

    enum UploadSource: String {
        case hero
        case weeklyRecord = "weekly_record"
    }

    case issue(bannerID: String, position: Int)
    case issueSwipe(position: Int)
    case ranking(section: RankingSection, action: RankingAction)
    case record(action: RecordAction)
    case upload(source: UploadSource)
    case feedback
    case notification
    case login

    var eventName: String {
        switch self {
        case .issue: "home_issue_clicked"
        case .issueSwipe: "home_issue_swiped"
        case .ranking: "home_ranking_clicked"
        case .record: "home_record_clicked"
        case .upload: "home_upload_clicked"
        case .feedback: "home_feedback_clicked"
        case .notification: "home_notification_clicked"
        case .login: "home_login_clicked"
        }
    }

    var parameters: [String: Any] {
        switch self {
        case let .issue(bannerID, position):
            ["banner_id": bannerID, "position": position]
        case let .issueSwipe(position):
            ["position": position]
        case let .ranking(section, action):
            ["section": section.rawValue, "action": action.rawValue]
        case let .record(action):
            ["action": action.rawValue]
        case let .upload(source):
            ["source": source.rawValue]
        case .feedback, .notification, .login:
            [:]
        }
    }
}
