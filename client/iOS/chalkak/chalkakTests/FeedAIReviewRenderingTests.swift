import SwiftUI
import XCTest
@testable import chalkak

@MainActor
final class FeedAIReviewRenderingTests: XCTestCase {
    func testFeedbackPresentationStates() async throws {
        for status in [AIReviewStatus.none, .processing, .completed] {
            let repository = FeedReviewRenderingRepository(status: status)
            let model = FeedViewModel(
                postID: FeedPreviewData.content.post.id,
                seed: FeedPreviewData.content,
                isLikeConfirmed: true,
                aiReviewRepository: repository
            )
            let aiModel = try XCTUnwrap(model.aiReview)
            let observation = Task { await aiModel.observe() }
            for _ in 0..<1000 {
                if !aiModel.isLoading { break }
                await Task.yield()
            }
            XCTAssertEqual(aiModel.review?.status, status)
            XCTAssertFalse(aiModel.isLoading)
            observation.cancel()
            await observation.value
            let screen = FeedScreen(viewModel: model, usesPhotoTransition: false).chalkakTheme(.light)
            try await capture(screen, name: "feed-\(status.rawValue.lowercased())", height: 852)
            XCTAssertEqual(aiModel.review?.status, status)
            XCTAssertFalse(aiModel.isLoading)
            try await capture(
                FeedAIReviewSheet(model: aiModel, onStart: {}).chalkakTheme(.light),
                name: "feed-sheet-\(status.rawValue.lowercased())",
                height: status == .none ? 380 : 510
            )
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

    private func capture(_ content: some View, name: String, height: CGFloat, expectsSheet: Bool = false) async throws {
        let host = UIHostingController(rootView: content)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 393, height: height)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(expectsSheet ? 700 : 200))
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
}

private struct FeedReviewRenderingRepository: AIReviewRepository {
    let status: AIReviewStatus
    func fetchReview(postID: String) async throws -> AIReview {
        AIReview(
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
