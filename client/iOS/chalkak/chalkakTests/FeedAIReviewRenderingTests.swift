import SwiftUI
import XCTest
@testable import chalkak

@MainActor
final class FeedAIReviewRenderingTests: XCTestCase {
    func testFeedModelsDeallocateSynchronouslyWithTaskLocalStorage() {
        // Keep this synchronous: older runtimes crash in isolated deinit when
        // task-local storage is present outside an asynchronous Swift task.
        FeedDeinitTestContext.$marker.withValue(1) {
            weak var releasedFeed: FeedViewModel?
            weak var releasedReview: FeedAIReviewViewModel?
            autoreleasepool {
                let model = FeedViewModel(
                    postID: "deinit-regression",
                    aiReviewRepository: FeedReviewRenderingRepository(status: .completed)
                )
                releasedFeed = model
                releasedReview = model.aiReview
                XCTAssertNotNil(releasedFeed)
                XCTAssertNotNil(releasedReview)
            }
            XCTAssertNil(releasedFeed)
            XCTAssertNil(releasedReview)
        }
    }

    func testNotificationEntryAutomaticallyPresentsFeedbackSheet() async throws {
        let model = FeedViewModel(
            postID: FeedPreviewData.content.post.id,
            seed: FeedPreviewData.content,
            isLikeConfirmed: true,
            aiReviewRepository: FeedReviewRenderingRepository(status: .completed)
        )
        try await capture(
            FeedScreen(viewModel: model, usesPhotoTransition: false, opensAIReviewOnEntry: true)
                .chalkakTheme(.light),
            name: "feed-notification-entry", height: 852, expectsSheet: true
        )
    }

    private func capture(
        _ content: some View,
        name: String,
        height: CGFloat,
        expectsSheet: Bool = false,
        isContentReady: () -> Bool = { true }
    ) async throws {
        let host = FeedReviewCaptureHostingController(rootView: AnyView(content))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 393, height: height)
        window.rootViewController = host
        window.makeKeyAndVisible()
        addTeardownBlock { @MainActor in
            // Keep the window alive until UIKit finishes dismissing its presented sheet.
            if host.presentedViewController != nil {
                await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                    host.dismiss(animated: false) { continuation.resume() }
                }
            }
            // Remove the SwiftUI content and its tasks before releasing the hosting window.
            host.rootView = AnyView(EmptyView())
            host.view.layoutIfNeeded()
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        host.view.layoutIfNeeded()
        try await waitFor("\(name): 화면과 콘텐츠가 표시되어야 합니다") {
            host.hasAppeared && isContentReady()
        }
        if expectsSheet {
            try await waitFor("\(name): 피드백 시트 표시가 완료되어야 합니다") {
                guard let sheet = host.presentedViewController else { return false }
                return sheet.viewIfLoaded?.window != nil && !sheet.isBeingPresented
            }
        }
        host.view.layoutIfNeeded()
        XCTAssertEqual(host.presentedViewController != nil, expectsSheet)
        let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        XCTAssertEqual(image.size.width, 393)
        XCTAssertEqual(image.size.height, height)
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func waitFor(
        _ message: String,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ condition: () -> Bool
    ) async throws {
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: .seconds(5))
        while !condition() && clock.now < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        // XCTest에 실패를 기록하고, 준비되지 않은 화면의 캡처는 중단한다.
        _ = try XCTUnwrap(condition() ? true : nil, message, file: file, line: line)
    }
}

private enum FeedDeinitTestContext {
    @TaskLocal static var marker = 0
}

@MainActor
private final class FeedReviewCaptureHostingController<Content: View>: UIHostingController<Content> {
    private(set) var hasAppeared = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        hasAppeared = true
    }
}

private struct FeedReviewRenderingRepository: AIReviewRepository {
    let status: AIReviewStatus
    var fetchDelay: Duration = .zero

    func fetchReview(postID: String) async throws -> AIReview {
        try await Task.sleep(for: fetchDelay)
        return AIReview(
            status: status, requestedAt: nil, completedAt: nil, canRequestAgain: status != .completed,
            result: status == .completed ? Self.result : nil
        )
    }
    func requestReview(postID: String) async throws -> AIReview { try await fetchReview(postID: postID) }

    private static let result = AIReviewResult(
        overallScore: 8.7,
        criteria: [.init(key: "composition", label: "구도", score: 8.7)],
        distinctivePerspective: true,
        feedback: .init(
            summary: "건물의 크기와 높이 차이가 잘 비교되는 구도예요",
            strength: "아래쪽 계단과 겹겹이 쌓인 집들, 위쪽 아파트가 한 화면에 같이 담겼어요.",
            weakness: "맨 아래 담장과 검은 옥상이 골목의 흐름을 끊어요.",
            suggestion: "왼쪽 계단 입구와 밝은 도로는 남기면서 맨 아래 담장이 덜 나오게 담아보세요."
        )
    )
}
