import XCTest
@testable import chalkak

@MainActor
final class FeedZoomHitTestingTests: XCTestCase {
    func testDisplayPhotoRejectsTapsBehindBottomBar() throws {
        let registry = FeedZoomRegistry()
        let id = FeedZoomSource.displayGrid("partially-covered")
        let frame = CGRect(x: 20, y: 700, width: 160, height: 210)
        registry.updateViewport(CGRect(x: 0, y: 100, width: 402, height: 774), for: .display)
        registry.bottomBarFrame = CGRect(x: 20, y: 780, width: 362, height: 74)
        registry.update(.init(frame: frame, cornerRadius: 12), for: id)

        let source = try XCTUnwrap(registry.source(for: id))
        XCTAssertTrue(source.acceptsTap(at: CGPoint(x: 100, y: 779)))
        XCTAssertFalse(source.acceptsTap(at: CGPoint(x: 100, y: 780)))
        XCTAssertFalse(source.acceptsTap(at: CGPoint(x: 100, y: 820)))
        XCTAssertEqual(source.frame, frame)

        // A later bar position must be used even when the card has not moved.
        registry.bottomBarFrame = CGRect(x: 20, y: 740, width: 362, height: 74)
        XCTAssertFalse(try XCTUnwrap(registry.source(for: id)).acceptsTap(at: CGPoint(x: 100, y: 760)))
    }

    func testDisplayPhotoRejectsTapsAboveViewport() {
        let registry = FeedZoomRegistry()
        registry.updateViewport(CGRect(x: 0, y: 220, width: 402, height: 560), for: .display)
        let source = registry.update(
            .init(frame: CGRect(x: 20, y: 100, width: 160, height: 250), cornerRadius: 12),
            for: .displayGrid("top")
        )
        XCTAssertFalse(source.acceptsTap(at: CGPoint(x: 100, y: 200)))
        XCTAssertTrue(source.acceptsTap(at: CGPoint(x: 100, y: 240)))
    }

    func testFittedPhotoKeepsVisibleCardPaddingTappable() {
        let source = FeedZoomRegistry.Source(
            frame: CGRect(x: 20, y: 225, width: 300, height: 150),
            cornerRadius: 12,
            clipFrame: CGRect(x: 20, y: 100, width: 300, height: 400)
        )
        XCTAssertTrue(source.acceptsTap(at: CGPoint(x: 100, y: 150)))
        XCTAssertFalse(source.acceptsTap(at: CGPoint(x: 10, y: 150)))
    }
}
