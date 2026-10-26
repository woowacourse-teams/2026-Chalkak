import SwiftUI
import XCTest
@testable import chalkak

@MainActor
final class FeedZoomRenderingTests: XCTestCase {
    func testFeedKeepsSourcePhotoHiddenUntilItDisappears() async throws {
        let registry = FeedZoomRegistry()
        let source = FeedZoomSource.displayGrid(FeedPreviewData.content.post.id)
        registry.update(.init(frame: CGRect(x: 20, y: 200, width: 160, height: 210), cornerRadius: 12), for: source)
        let model = FeedViewModel(
            postID: FeedPreviewData.content.post.id,
            initialState: FeedPreviewData.loadedState,
            detailHandler: { _ in .success(FeedPreviewData.content) }
        )
        let host = UIHostingController(rootView: AnyView(
            FeedScreen(viewModel: model, zoomSource: source)
                .environment(\.feedZoomRegistry, registry)
                .chalkakTheme(.light)
        ))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 402, height: 874)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        host.view.layoutIfNeeded()
        let clock = ContinuousClock()
        let deadline = clock.now.advanced(by: .seconds(3))
        while registry.activeSource != source, clock.now < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(registry.activeSource, source, "The moving feed photo must replace its source card")
        try await Task.sleep(for: .seconds(1))
        XCTAssertEqual(registry.activeSource, source, "Keep the source hidden after opening so a return swipe cannot expose a duplicate")
        host.rootView = AnyView(Color.clear)
        host.view.layoutIfNeeded()
        let disappearanceDeadline = clock.now.advanced(by: .seconds(3))
        while registry.activeSource != nil, clock.now < disappearanceDeadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertNil(registry.activeSource, "Restore the source when the feed disappears")
    }

    func testOpeningWaitsForUncachedPhotoAfterLayoutIsMeasured() async throws {
        try await assertOpeningWaitsForPhoto(fails: false)
    }

    func testFailedOpeningPhotoDoesNotLeaveTransitionWaitingForever() async throws {
        try await assertOpeningWaitsForPhoto(fails: true)
    }

    func testOriginalPhotoReleasesOpeningWhileThumbnailIsStillLoading() async throws {
        try await assertOpeningWaitsForPhoto(fails: false, originalArrives: true)
    }

    private func assertOpeningWaitsForPhoto(fails: Bool, originalArrives: Bool = false) async throws {
        XCTAssertTrue(URLProtocol.registerClass(FeedOpeningImageProtocol.self))
        defer { URLProtocol.unregisterClass(FeedOpeningImageProtocol.self) }
        let url = try XCTUnwrap(URL(string: "https://feed-opening-test.invalid/\(UUID().uuidString).png"))
        let placeholder = FeedPhotoPlaceholder(imageSource: .remote(url), heightToWidthRatio: 1)
        XCTAssertNil(placeholder.preloadedImage)
        var measured = false
        var readyCount = 0
        func photo(post: FeedPost?) -> some View {
            FeedPhoto(
                post: post,
                placeholder: placeholder,
                zoom: FeedPhotoZoom(isSettled: false),
                onTransitionReady: { readyCount += 1 },
                onPhotoFrameChanged: { measured = $0.width > 0 && $0.height > 0 },
                onLike: {}
            )
            .frame(width: 300)
        }
        let host = UIHostingController(rootView: photo(post: nil))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 300, height: 500)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
            FeedOpeningImageProtocol.pending.remove(url)?.fail()
        }
        host.view.layoutIfNeeded()
        let clock = ContinuousClock()
        let layoutDeadline = clock.now.advanced(by: .seconds(3))
        while (!measured || !FeedOpeningImageProtocol.pending.contains(url)), clock.now < layoutDeadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(measured, "The layout must be ready before testing image readiness")
        let request = try XCTUnwrap(FeedOpeningImageProtocol.pending.remove(url))
        XCTAssertEqual(readyCount, 0, "A measured but empty photo must not expose the opaque feed screen")
        defer { if originalArrives { request.fail() } }
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 30, height: 30))
        let data = try XCTUnwrap(renderer.image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 30, height: 30))
        }.pngData())
        func resolve(_ request: FeedOpeningImageProtocol) {
            if fails { request.fail() } else { request.succeed(data) }
        }
        if originalArrives {
            // Detail data arrives after the uncached thumbnail request has started.
            // Keep that request blocked; the available original must release opening.
            host.rootView = photo(post: FeedPreviewData.content.post)
            host.view.layoutIfNeeded()
        } else {
            resolve(request)
        }
        let readyDeadline = clock.now.advanced(by: .seconds(3))
        while readyCount == 0, clock.now < readyDeadline {
            // The displayed image and the transition task load the same URL separately.
            // On a slow runner the second load can start after the first request resolved.
            if !originalArrives, let late = FeedOpeningImageProtocol.pending.remove(url) {
                resolve(late)
            }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertEqual(readyCount, 1, "A resolved photo must release opening exactly once, including failures")
    }

    func testAspectRatioTransitionKeepsPhotoLayoutSizeFixed() async throws {
        let destination = CGRect(x: 0, y: 0, width: 400, height: 400)
        let source = FeedZoomRegistry.Source(frame: CGRect(x: 20, y: 100, width: 150, height: 250), cornerRadius: 12)
        var sizes: [CGSize] = []
        func photo(progress: CGFloat) -> some View {
            Color.clear.frame(width: 400, height: 400)
                .overlay {
                    Color.red
                        .onGeometryChange(for: CGSize.self) { $0.size } action: { sizes.append($0) }
                        .feedZoomEffect(progress: progress, source: source, destinationFrame: destination, morphsAspectRatio: true)
                }
        }
        let host = UIHostingController(rootView: photo(progress: 1))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 400, height: 500)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        for progress: CGFloat in [1, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0] {
            host.rootView = photo(progress: progress)
            host.view.layoutIfNeeded()
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertFalse(sizes.isEmpty)
        XCTAssertTrue(sizes.allSatisfy { $0 == destination.size }, "Moving the crop must not relayout the image: \(sizes)")
    }

    func testPhotoAndSignatureStayStillWhenReturnSwipeProgressIsHeld() async throws {
        try await assertHeldSwipeGeometry(containerOffset: .zero)
    }

    func testHeldSwipeGeometryIsIndependentOfContainerOrigin() async throws {
        // Reproduce the CI origin shift without depending on a simulator's safe area.
        try await assertHeldSwipeGeometry(containerOffset: CGSize(width: 13, height: -2.5))
    }

    private func assertHeldSwipeGeometry(containerOffset: CGSize) async throws {
        // Exercise the production transform with synchronous fixtures. Image decoding,
        // original-image crossfades and text rasterization cannot change these frames.
        let state = HeldSwipeGeometry()
        let host = UIHostingController(rootView: AnyView(
            HeldSwipePhoto(state: state).offset(containerOffset)
        ))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 400, height: 400)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer {
            host.rootView = AnyView(EmptyView())
            host.view.layoutIfNeeded()
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }

        for progress: CGFloat in [1, 0.8, 0.5, 0.2, 0.5, 1] {
            state.progress = progress
            host.view.layoutIfNeeded()
            let expectedPhoto = interpolatedFrame(
                from: HeldSwipePhoto.photoSource.frame, to: HeldSwipePhoto.photoDestination, progress: progress
            )
            let expectedSignature = interpolatedFrame(
                from: HeldSwipePhoto.signatureSource.frame, to: HeldSwipePhoto.signatureDestination, progress: progress
            )
            // SwiftUI aligns the transformed frame to the display pixel grid.
            let pixelTolerance = 1 / scene.screen.scale
            // Wait for geometry readiness rather than assuming a CI render speed.
            let clock = ContinuousClock()
            let deadline = clock.now.advanced(by: .seconds(5))
            while (!framesMatch(state.photoFrame, expectedPhoto, tolerance: pixelTolerance) || !framesMatch(state.signatureFrame, expectedSignature, tolerance: pixelTolerance)), clock.now < deadline {
                try await Task.sleep(for: .milliseconds(10))
                host.view.layoutIfNeeded()
            }
            _ = try XCTUnwrap(
                framesMatch(state.photoFrame, expectedPhoto, tolerance: pixelTolerance) && framesMatch(state.signatureFrame, expectedSignature, tolerance: pixelTolerance) ? true : nil,
                "Photo and signature must reach swipe progress \(progress): photo=\(String(describing: state.photoFrame)) expected=\(expectedPhoto), signature=\(String(describing: state.signatureFrame)) expected=\(expectedSignature)"
            )
            let heldPhoto = try XCTUnwrap(state.photoFrame)
            let heldSignature = try XCTUnwrap(state.signatureFrame)
            // Sample geometry during the hold after readiness, not complete image bytes.
            for _ in 0..<10 {
                try await Task.sleep(for: .milliseconds(10))
                host.view.layoutIfNeeded()
                XCTAssertTrue(framesMatch(state.photoFrame, heldPhoto), "Photo moved while progress \(progress) was held: \(String(describing: state.photoFrame)), expected \(expectedPhoto)")
                XCTAssertTrue(framesMatch(state.signatureFrame, heldSignature), "Signature moved while progress \(progress) was held: \(String(describing: state.signatureFrame)), expected \(expectedSignature)")
            }
        }
    }

    private func framesMatch(_ actual: CGRect?, _ expected: CGRect, tolerance: CGFloat = 0.01) -> Bool {
        guard let actual else { return false }
        return abs(actual.minX - expected.minX) <= tolerance
            && abs(actual.minY - expected.minY) <= tolerance
            && abs(actual.width - expected.width) <= tolerance
            && abs(actual.height - expected.height) <= tolerance
    }

    private func interpolatedFrame(from source: CGRect, to destination: CGRect, progress: CGFloat) -> CGRect {
        CGRect(
            x: source.minX + (destination.minX - source.minX) * progress,
            y: source.minY + (destination.minY - source.minY) * progress,
            width: source.width + (destination.width - source.width) * progress,
            height: source.height + (destination.height - source.height) * progress
        )
    }

    func testFeedTitleRowMatchesTodayForShortLongAndBlankTitles() async throws {
        for width: CGFloat in [320, 402] {
            for title in ["  하늘을 담은 하루  ", String(repeating: "아주 긴 사진 제목 ", count: 10), " \n "] {
                var post = FeedPreviewData.content.post
                post.title = title
                let photo = TodayPhoto(
                    id: post.id,
                    imageSource: post.originalImageSource,
                    signatureSource: post.signatureImageSource,
                    contentDescription: post.contentDescription,
                    title: title,
                    likeCount: post.likeCount
                )
                let feed = FeedLikeRow(
                    title: post.title,
                    likeCount: post.likeCount,
                    isLiked: post.isLiked,
                    isEnabled: true,
                    onLike: {}
                )
                    .frame(width: width)
                    .background(ChalkakTheme.light.colors.surfaceElevated)
                    .chalkakTheme(.light)
                let today = TodayPhotoCard(
                    photo: photo,
                    isLiked: post.isLiked,
                    isLikeEnabled: true,
                    imageReloadGeneration: 0,
                    imageRatio: 1,
                    onImageRatioChanged: { _ in },
                    onLike: {}
                )
                .frame(width: width)
                .chalkakTheme(.light)
                let feedRenderer = ImageRenderer(content: feed)
                let todayRenderer = ImageRenderer(content: today)
                feedRenderer.scale = 1
                todayRenderer.scale = 1
                let feedImage = try XCTUnwrap(feedRenderer.uiImage)
                let todayImage = try XCTUnwrap(todayRenderer.uiImage)
                XCTAssertEqual(feedImage.size.height, 57)
                XCTAssertEqual(todayImage.size.height, width + 57)
                let row = CGRect(x: 0, y: width, width: width, height: 57)
                let feedRow = feedImage
                let todayRow = UIImage(cgImage: try XCTUnwrap(todayImage.cgImage?.cropping(to: row)))
                // Today 카드 그림자는 행 위쪽과 글자 가장자리에도 그려진다.
                // 중앙 터치 영역을 비교하고 그림자 수준의 색 차이만 허용한다.
                let contentRect = CGRect(x: 0, y: 7, width: width, height: 44)
                let actual = UIImage(cgImage: try XCTUnwrap(feedRow.cgImage?.cropping(to: contentRect)))
                let expected = UIImage(cgImage: try XCTUnwrap(todayRow.cgImage?.cropping(to: contentRect)))
                let maximumDifference = zip(try rgbaData(actual), try rgbaData(expected))
                    .map { abs(Int($0.0) - Int($0.1)) }.max() ?? 0
                XCTAssertLessThanOrEqual(maximumDifference, 8, "Feed title layout must match Today at width \(width)")
                let attachment = XCTAttachment(image: feedRow)
                attachment.name = "Feed title row \(Int(width)) \(title.prefix(10))"
                attachment.lifetime = .keepAlways
                add(attachment)
                let reference = XCTAttachment(image: todayRow)
                reference.name = "Today title row \(Int(width)) \(title.prefix(10))"
                reference.lifetime = .keepAlways
                add(reference)
            }
        }
    }

    private func rgbaData(_ image: UIImage) throws -> Data {
        let cgImage = try XCTUnwrap(image.cgImage)
        let context = try XCTUnwrap(CGContext(
            data: nil,
            width: cgImage.width,
            height: cgImage.height,
            bitsPerComponent: 8,
            bytesPerRow: cgImage.width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))
        return Data(bytes: try XCTUnwrap(context.data), count: context.bytesPerRow * cgImage.height)
    }

    func testFeaturedPhotoClipPreservesContainerCornersAcrossAspectRatios() {
        let card = CGRect(x: 50, y: 100, width: 300, height: 400)
        for ratio: CGFloat in [4.0 / 3.0, 1.32, 0.75, 2] {
            let fitted = FeedZoomCropGeometry.fittedFrame(in: card, heightToWidthRatio: ratio)
            let source = FeedZoomRegistry.Source(frame: fitted, cornerRadius: 24, clipFrame: card)
            let rect = CGRect(origin: .zero, size: fitted.size)
            let clip = FeedZoomPhotoClip(source: source, remaining: 1, scale: 1, cornerRadius: 24).path(in: rect)
            let expected = RoundedRectangle(cornerRadius: 24).path(in: card)
            for x in stride(from: CGFloat(1), to: fitted.width, by: 3) {
                for y in stride(from: CGFloat(1), to: fitted.height, by: 3) {
                    XCTAssertEqual(clip.contains(CGPoint(x: x, y: y)), expected.contains(CGPoint(x: fitted.minX + x, y: fitted.minY + y)))
                }
            }
            let arrived = FeedZoomPhotoClip(source: source, remaining: 0, scale: 1, cornerRadius: 0).path(in: rect)
            XCTAssertTrue(arrived.contains(CGPoint(x: 0.1, y: 0.1)))
            XCTAssertEqual(arrived.boundingRect, rect)
        }
    }

    func testFeaturedPhotoClipScalesAndReleasesCardMask() {
        let source = FeedZoomRegistry.Source(
            frame: CGRect(x: 50, y: 150, width: 300, height: 300),
            cornerRadius: 24,
            clipFrame: CGRect(x: 50, y: 100, width: 300, height: 400)
        )
        let rect = CGRect(x: 0, y: 0, width: 400, height: 400)
        let departure = FeedZoomPhotoClip(source: source, remaining: 1, scale: 0.75, cornerRadius: 32).path(in: rect)
        XCTAssertEqual(departure.boundingRect.minY, -50 / 0.75, accuracy: 0.001)
        let middle = FeedZoomPhotoClip(source: source, remaining: 0.5, scale: 0.875, cornerRadius: 12 / 0.875).path(in: rect)
        XCTAssertEqual(middle.boundingRect.minY, -25 / 0.875, accuracy: 0.001)
        let ordinary = FeedZoomPhotoClip(source: .init(frame: source.frame, cornerRadius: 24), remaining: 1, scale: 1, cornerRadius: 24).path(in: rect)
        XCTAssertEqual(ordinary.boundingRect, rect)
        XCTAssertFalse(ordinary.contains(CGPoint(x: 1, y: 1)))
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

@MainActor
@Observable
private final class HeldSwipeGeometry {
    var progress: CGFloat = 1
    var photoFrame: CGRect?
    var signatureFrame: CGRect?
}

private struct HeldSwipePhoto: View {
    let state: HeldSwipeGeometry
    private let coordinateSpaceName = "heldSwipeContainer"
    static let photoDestination = CGRect(x: 0, y: 0, width: 400, height: 400)
    static let signatureDestination = CGRect(x: 322, y: 340, width: 70, height: 52)
    static let photoSource = FeedZoomRegistry.Source(
        frame: CGRect(x: 20, y: 100, width: 160, height: 160), cornerRadius: 12
    )
    static let signatureSource = FeedZoomRegistry.Source(
        frame: CGRect(x: 137, y: 226, width: 35, height: 26), cornerRadius: 0
    )

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.red
                .frame(width: 400, height: 400)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(coordinateSpaceName)) } action: { state.photoFrame = $0 }
                .feedZoomEffect(progress: state.progress, source: Self.photoSource, destinationFrame: Self.photoDestination)
            Color.blue
                .frame(width: 70, height: 52)
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(coordinateSpaceName)) } action: { state.signatureFrame = $0 }
                .feedZoomEffect(progress: state.progress, source: Self.signatureSource, destinationFrame: Self.signatureDestination)
                .offset(x: Self.signatureDestination.minX, y: Self.signatureDestination.minY)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        // Fixtures and measurements use the same container-relative coordinates.
        .coordinateSpace(name: coordinateSpaceName)
        .ignoresSafeArea()
    }
}

/// Holds only this test host's requests, so unrelated app traffic remains untouched.
private final class FeedOpeningImageProtocol: URLProtocol, @unchecked Sendable {
    static let pending = PendingRequests()

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "feed-opening-test.invalid"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        if let url = request.url { Self.pending.insert(self, for: url) }
    }

    override func stopLoading() {
        if let url = request.url { _ = Self.pending.remove(url) }
    }

    func succeed(_ data: Data) {
        guard let url = request.url else { return }
        let response = URLResponse(url: url, mimeType: "image/png", expectedContentLength: data.count, textEncodingName: nil)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    func fail() {
        client?.urlProtocol(self, didFailWithError: URLError(.cannotLoadFromNetwork))
    }

    final class PendingRequests: @unchecked Sendable {
        private let lock = NSLock()
        private var requests: [URL: FeedOpeningImageProtocol] = [:]

        func insert(_ request: FeedOpeningImageProtocol, for url: URL) {
            lock.lock()
            defer { lock.unlock() }
            requests[url] = request
        }

        func contains(_ url: URL) -> Bool {
            lock.lock()
            defer { lock.unlock() }
            return requests[url] != nil
        }

        func remove(_ url: URL) -> FeedOpeningImageProtocol? {
            lock.lock()
            defer { lock.unlock() }
            return requests.removeValue(forKey: url)
        }
    }
}
