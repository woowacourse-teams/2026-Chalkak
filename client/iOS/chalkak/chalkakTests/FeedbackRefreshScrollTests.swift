import SwiftUI
import Testing
@testable import chalkak

@MainActor
struct FeedbackRefreshScrollTests {
    private let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 600))

    @Test("짧은 문의 목록은 아래 빈 공간으로 스크롤하지 않는다")
    func shortContentCannotScrollPastBottom() {
        // Given 화면보다 짧은 목록
        let controller = makeController()
        controller.scrollView.contentSize = CGSize(width: 320, height: 200)
        // When 목록을 위로 밀어 아래 빈 공간으로 이동한다
        controller.scrollView.contentOffset.y = 40
        controller.scrollViewDidScroll(controller.scrollView)
        // Then 목록 시작 위치를 유지한다
        #expect(controller.scrollView.contentOffset.y == 0)
    }

    @Test("짧은 문의 목록도 아래로 당겨 새로고침할 수 있다")
    func shortContentRetainsPullDown() {
        // Given 화면보다 짧은 목록
        let controller = makeController()
        controller.scrollView.contentSize = CGSize(width: 320, height: 200)
        // When 아래로 당긴다
        controller.scrollView.contentOffset.y = -50
        controller.scrollViewDidScroll(controller.scrollView)
        // Then 위쪽 당기기와 바운스는 유지한다
        #expect(controller.scrollView.alwaysBounceVertical)
        #expect(controller.scrollView.contentOffset.y == -50)
    }

    @Test("긴 문의 목록은 정상적으로 스크롤한다")
    func longContentCanScroll() {
        // Given 화면보다 긴 목록
        let controller = makeController()
        controller.scrollView.contentSize = CGSize(width: 320, height: 1200)
        // When 아래 항목으로 스크롤한다
        controller.scrollView.contentOffset.y = 80
        controller.scrollViewDidScroll(controller.scrollView)
        // Then 스크롤 위치를 유지한다
        #expect(controller.scrollView.contentOffset.y == 80)
    }

    @Test("새로고침은 응답 전까지 인디케이터를 유지하고 중복 요청하지 않는다")
    func refreshingRemainsActiveUntilCompletion() async throws {
        // Given 응답을 보류하는 새로고침
        var requestCount = 0
        var completion: CheckedContinuation<Void, Never>?
        let controller = makeController {
            requestCount += 1
            await withCheckedContinuation { completion = $0 }
        }
        // When 새로고침을 시작한 뒤 다시 요청한다
        controller.refreshControl.beginRefreshing()
        controller.refreshRequested()
        try await waitUntil { completion != nil }
        controller.refreshRequested()
        // Then 요청 중에는 네이티브 인디케이터가 유지되고 완료 후 해제된다
        #expect(requestCount == 1)
        #expect(controller.refreshControl.isRefreshing)
        #expect(controller.refreshControl.bounds.height > 0)
        try await waitUntil {
            controller.scrollView.adjustedContentInset.top >= controller.refreshControl.bounds.height
        }
        // 손을 뗀 뒤 목록이 원점으로 돌아오려 해도 요청 중에는 인디케이터 공간을 남긴다.
        controller.scrollView.contentOffset.y = 0
        controller.scrollViewDidScroll(controller.scrollView)
        #expect(controller.scrollView.contentOffset.y == -controller.scrollView.adjustedContentInset.top)
        completion?.resume()
        try await waitUntil { !controller.refreshControl.isRefreshing }
        try await waitUntil { controller.scrollView.adjustedContentInset.top == 0 }
    }

    @Test("다음 문의 페이지는 목록 끝이 화면에 가까워질 때 요청한다")
    func paginationRequiresVisibleBottom() {
        // Given 화면보다 긴 목록
        var requestCount = 0
        let controller = FeedbackRefreshScrollController(content: Color.clear, tintColor: .black, onRefresh: {}, onApproachBottom: { requestCount += 1 })
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 320, height: 600)
        controller.view.layoutIfNeeded()
        controller.scrollView.contentSize = CGSize(width: 320, height: 1200)
        // When 처음 위치와 끝 근처로 스크롤한다
        controller.scrollViewDidScroll(controller.scrollView)
        #expect(requestCount == 0)
        controller.scrollView.contentOffset.y = 600
        controller.scrollViewDidScroll(controller.scrollView)
        // Then 끝 근처에서만 이어보기를 요청한다
        #expect(requestCount > 0)
    }

    private func makeController(onRefresh: @escaping () async -> Void = {}) -> FeedbackRefreshScrollController<Color> {
        let controller = FeedbackRefreshScrollController(content: Color.clear, tintColor: .black, onRefresh: onRefresh)
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 320, height: 600)
        controller.view.layoutIfNeeded()
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        return controller
    }
}
