import Foundation
import Testing
@testable import chalkak

struct FeedbackTimestampTests {
    @Test("고객센터 시각은 서울 기준 오늘·어제·그 전을 구분한다")
    func formatsSeoulDates() throws {
        // Given 서울의 오늘·어제·그 전과 자정 경계
        // When 고객센터 시각을 고정된 now로 표시한다
        // Then 서울 날짜에 맞는 시각을 표시한다
        let parse = ISO8601DateFormatter()
        let now = try #require(parse.date(from: "2026-11-05T15:01:00Z"))
        let dates = ["2026-11-05T15:00:00Z", "2026-11-05T14:59:00Z", "2026-11-04T14:59:00Z"]
        let daytime = try #require(parse.date(from: "2026-11-05T07:00:00Z"))
        let daytimeDates = ["2026-11-05T00:30:00Z", "2026-11-04T09:20:00Z", "2026-11-03T05:20:00Z"]
        let strings = try dates.map { FeedbackTimestampFormatter.string(from: try #require(parse.date(from: $0)), now: now) }
        let daytimeStrings = try daytimeDates.map { FeedbackTimestampFormatter.string(from: try #require(parse.date(from: $0)), now: daytime) }
        #expect(daytimeStrings == ["09:30", "어제 18:20", "11월 3일 14:20"])
        #expect(strings == ["00:00", "어제 23:59", "11월 4일 23:59"])
    }
}

struct FeedbackPushNavigationTests {
    @Test("사용 중 답변 푸시로 연 상세에서 고객센터 목록을 거쳐 이전 화면으로 돌아간다")
    func preservesRunningContext() {
        // Given 다른 상세와 피드 overlay가 열려 있다
        // When 답변 푸시 후 두 번 뒤로 간다(버튼·스와이프 공통 path)
        // Then 목록을 거쳐 원래 경로로 돌아가며 overlay 보존 상태를 복구한다
        var navigation = FeedbackPushNavigation()
        let original: [NotificationRoute] = [.inbox, .approved("prior-post")]
        var path = navigation.open(feedbackID: "F", path: original)
        #expect(path == original + [.customerSupport, .feedbackDetail("F")])
        #expect(navigation.hidesPreviousOverlays)
        path = Array(path.dropLast())
        navigation.pathChanged(path)
        #expect(path.last == .customerSupport)
        #expect(navigation.hidesPreviousOverlays)
        path = Array(path.dropLast())
        navigation.pathChanged(path)
        #expect(path == original)
        #expect(!navigation.hidesPreviousOverlays)
    }

    @Test("앱 종료 상태에서 답변 푸시로 열면 고객센터 목록을 거쳐 앱 시작 화면으로 돌아간다")
    func opensFromColdStart() {
        // Given 초기 화면이고 경로가 비어 있다
        // When 답변 푸시를 연 뒤 돌아간다
        // Then 목록·상세를 쌓고 초기 화면으로 돌아간다
        var navigation = FeedbackPushNavigation()
        var path = navigation.open(feedbackID: "F", path: [])
        #expect(path == [.customerSupport, .feedbackDetail("F")])
        path.removeAll()
        navigation.pathChanged(path)
        #expect(!navigation.hidesPreviousOverlays)
    }

    @Test("삭제된 문의의 옛 푸시는 상세 없이 고객센터 목록과 토스트를 보여준다")
    func missingOldPushReturnsToList() {
        // Given 옛 답변 푸시의 문의가 삭제됐다
        // When 상세가 404를 알린다
        // Then 상세를 빼고 목록·찾을 수 없음 안내를 보여준다
        var navigation = FeedbackPushNavigation()
        let path = navigation.open(feedbackID: "F", path: [.approved("prior")])
        let result = navigation.missingDetail(path: path, feedbackID: "F")
        #expect(result?.path == [.approved("prior"), .customerSupport])
        #expect(result?.message == "문의를 찾을 수 없어요")
        #expect(navigation.missingDetail(path: [.inbox, .feedbackDetail("F")], feedbackID: "F") == nil)
    }

    @Test("알림함에서 연 문의 상세의 뒤로가기는 알림함을 유지한다")
    func inboxKeepsInbox() {
        // Given 알림함에서 답변 알림을 누른다
        // When 상세에서 뒤로 간다
        // Then 알림함을 그대로 유지한다
        var path = FeedbackPushNavigation.inboxPath(feedbackID: "F")
        #expect(path == [.inbox, .feedbackDetail("F")])
        path = Array(path.dropLast())
        #expect(path == [.inbox])
    }

    @Test("답변 푸시 이외의 푸시·인증 실패·취소의 이동 동작은 바꾸지 않는다")
    func guardsNavigationAndResetsForOtherPushes() {
        // Given 다른 푸시 또는 취소·계정 교체·인증 실패
        // When 알림 이동을 판단한다
        // Then 기존 푸시 흐름은 유지하고 안전 조건 실패 시 이동하지 않는다
        #expect(!FeedbackPushNavigation.permitsNavigation(cancelled: true, sameAccount: true, authenticated: true, mainRoute: true, readAllowed: true))
        #expect(!FeedbackPushNavigation.permitsNavigation(cancelled: false, sameAccount: false, authenticated: true, mainRoute: true, readAllowed: true))
        #expect(!FeedbackPushNavigation.permitsNavigation(cancelled: false, sameAccount: true, authenticated: false, mainRoute: true, readAllowed: true))
        #expect(!FeedbackPushNavigation.permitsNavigation(cancelled: false, sameAccount: true, authenticated: true, mainRoute: true, readAllowed: false))
        let cases: [(PushNotificationTap, [NotificationRoute])] = [
            (.topic, []),
            (.postApproved(postID: "P", notificationID: "N"), [.approved("P")]),
            (.aiReview(postID: "P", notificationID: "N"), [.aiReview("P")]),
            (.postRejected(notificationID: "N"), [.rejected(NotificationDetailTarget(id: "N"))]),
            (.notice(notificationID: "N"), [.notice(NotificationDetailTarget(id: "N"))]),
        ]
        for (tap, expected) in cases {
            #expect(FeedbackPushNavigation.destinationPath(for: tap, fromInbox: false) == expected)
            #expect(FeedbackPushNavigation.destinationPath(for: tap, fromInbox: true) == (tap == .topic ? [] : [.inbox] + expected))
        }
        #expect(FeedbackPushNavigation.permitsNavigation(cancelled: false, sameAccount: true, authenticated: true, mainRoute: true, readAllowed: true))
        #expect(!FeedbackPushNavigation.permitsNavigation(cancelled: false, sameAccount: true, authenticated: true, mainRoute: false, readAllowed: true))
        var navigation = FeedbackPushNavigation()
        _ = navigation.open(feedbackID: "F", path: [])
        navigation.reset()
        #expect(!navigation.hidesPreviousOverlays)
    }
}
