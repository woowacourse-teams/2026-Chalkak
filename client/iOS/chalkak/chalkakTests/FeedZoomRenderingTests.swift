import SwiftUI
import XCTest
@testable import chalkak

@MainActor
final class FeedZoomRenderingTests: XCTestCase {
    func testFirstPinchKeepsTouchSurfaceAliveWhenViewportClipIsRemoved() async throws {
        let frame = CGRect(x: 0, y: 0, width: 400, height: 400)
        let host = UIHostingController(rootView: ScrollLockPhotoHarness(frame: frame))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = frame
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))

        let surface = try XCTUnwrap(touchSurface(in: host.view))
        var ancestor = surface.superview
        while ancestor != nil && !(ancestor is UIScrollView) { ancestor = ancestor?.superview }
        let scrollView = try XCTUnwrap(ancestor as? UIScrollView)
        XCTAssertTrue(scrollView.isScrollEnabled)
        let first = NSObject()
        let second = NSObject()
        surface.tracker.onTouchesChanged?([
            FeedPhotoTouch(id: ObjectIdentifier(first), location: CGPoint(x: 100, y: 100)),
            FeedPhotoTouch(id: ObjectIdentifier(second), location: CGPoint(x: 200, y: 100))
        ], .zero)
        try await Task.sleep(for: .milliseconds(100))
        host.view.layoutIfNeeded()

        XCTAssertTrue(touchSurface(in: host.view) === surface, "Starting the first pinch must not replace its active touch recognizer")
        XCTAssertFalse(scrollView.isScrollEnabled)
        // One remaining finger continues panning the enlarged photo, not the feed.
        surface.tracker.onTouchesChanged?([
            FeedPhotoTouch(id: ObjectIdentifier(first), location: CGPoint(x: 120, y: 100))
        ], .zero)
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertFalse(scrollView.isScrollEnabled)
        surface.tracker.onTouchesChanged?([], .zero)
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertTrue(scrollView.isScrollEnabled)
        XCTAssertTrue(touchSurface(in: host.view) === surface)
    }

    private func touchSurface(in view: UIView) -> FeedPhotoTouchView? {
        if let surface = view as? FeedPhotoTouchView { return surface }
        return view.subviews.lazy.compactMap { self.touchSurface(in: $0) }.first
    }

    func testPinchKeepsTouchedPhotoPointUnderMovingFingers() {
        let offset = feedPhotoPinchOffset(
            offset: .zero,
            previousScale: 1,
            nextScale: 2,
            previousCenter: CGPoint(x: 100, y: 100),
            currentCenter: CGPoint(x: 120, y: 100),
            photoSize: CGSize(width: 400, height: 300)
        )
        XCTAssertEqual(offset, CGSize(width: 120, height: 50))
    }

    // Async XCTest methods provide a Swift task for MainActor-isolated teardown.
    func testReturningPhotoStaysBehindHeaderAndBottomBar() async throws {
        for sourceY in [CGFloat(100), CGFloat(700)] {
            let destination = CGRect(x: 0, y: 160, width: 402, height: 400)
            let source = FeedZoomRegistry.Source(
                frame: CGRect(x: 25, y: sourceY, width: 82, height: 250),
                cornerRadius: 0,
                visibleBounds: CGRect(x: 0, y: 220, width: 402, height: 560)
            )
            let scene = ZStack(alignment: .topLeading) {
                Color.white
                Color.clear.frame(width: 402, height: 400)
                    .overlay {
                        Color(.sRGB, red: 1, green: 0, blue: 0, opacity: 1)
                            .feedZoomEffect(progress: 0, source: source, destinationFrame: destination, morphsAspectRatio: true)
                    }
                    .feedZoomViewportClip(progress: 0, source: source, destination: CGRect(x: 0, y: 0, width: 402, height: 874), canvas: destination)
                    .offset(y: 160)
            }.frame(width: 402, height: 874)
            let renderer = ImageRenderer(content: scene)
            renderer.scale = 1
            let image = try XCTUnwrap(renderer.uiImage)
            let attachment = XCTAttachment(image: image)
            attachment.name = "Return photo at y=\(sourceY)"
            attachment.lifetime = .keepAlways
            add(attachment)
            let hiddenY = sourceY == 100 ? 180 : 800
            let visibleY = sourceY == 100 ? 230 : 740
            XCTAssertEqual(try pixel(image, x: 50, y: hiddenY), [255, 255, 255, 255])
            XCTAssertEqual(try pixel(image, x: 50, y: visibleY), [255, 0, 0, 255])
        }
    }

    func testSourceViewportExcludesBarsWithoutChangingPhotoBounds() async {
        let registry = FeedZoomRegistry()
        let photo = CGRect(x: 25, y: 100, width: 82, height: 250)
        registry.update(.init(frame: photo, cornerRadius: 8), for: .displayGrid("photo"))
        registry.updateViewport(CGRect(x: 0, y: 220, width: 402, height: 654), for: .display)
        registry.bottomBarFrame = CGRect(x: 20, y: 780, width: 362, height: 74)
        let source = registry.source(for: .displayGrid("photo"))
        XCTAssertEqual(source?.frame, photo)
        XCTAssertEqual(source?.visibleBounds, CGRect(x: 0, y: 220, width: 402, height: 560))
    }

    func testFeaturedPhotoUsesFittedImageBounds() {
        let card = CGRect(x: 20, y: 100, width: 300, height: 400)
        XCTAssertEqual(FeedZoomCropGeometry.fittedFrame(in: card, heightToWidthRatio: 0.5), CGRect(x: 20, y: 225, width: 300, height: 150))
        XCTAssertEqual(FeedZoomCropGeometry.fittedFrame(in: card, heightToWidthRatio: 2), CGRect(x: 70, y: 100, width: 200, height: 400))
        XCTAssertEqual(FeedZoomCropGeometry.fittedFrame(in: card, heightToWidthRatio: nil), card)
    }

    private func pixel(_ image: UIImage, x: Int, y: Int) throws -> [UInt8] {
        let cgImage = try XCTUnwrap(image.cgImage)
        // Keep bitmap storage owned by Core Graphics for the context's entire lifetime.
        let context = try XCTUnwrap(CGContext(
            data: nil,
            width: cgImage.width,
            height: cgImage.height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        let data = try XCTUnwrap(context.data)
        let index = y * context.bytesPerRow + x * 4
        return withExtendedLifetime(context) {
            Array(UnsafeBufferPointer(start: data.assumingMemoryBound(to: UInt8.self).advanced(by: index), count: 4))
        }
    }
}

private struct ScrollLockPhotoHarness: View {
    let frame: CGRect
    @State private var isPhotoInteracting = false

    var body: some View {
        ScrollView {
            FeedPhoto(
                post: nil,
                zoom: FeedPhotoZoom(source: .init(frame: frame, cornerRadius: 0, visibleBounds: frame), viewport: frame),
                onPhotoInteractionChanged: { isPhotoInteracting = $0 },
                onLike: {}
            )
            Color.clear.frame(height: 800)
        }
        .scrollClipDisabled(isPhotoInteracting)
        .scrollDisabled(isPhotoInteracting)
    }
}
