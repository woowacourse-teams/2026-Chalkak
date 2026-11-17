import Testing
import UIKit
@testable import chalkak

@MainActor
struct RemoteImageCacheTests {
    @Test("최대 폭보다 넓은 이미지는 비율을 유지한 채 최대 폭으로 줄여서 디코딩한다")
    func downsamplesWideImageToMaxPixelWidth() throws {
        let data = try #require(Self.pngData(width: 1_200, height: 1_600))

        let image = try #require(RemoteImageCache.decodedImage(from: data, maxPixelWidth: 300))

        #expect(image.size.width == 300)
        #expect(image.size.height == 400)
    }

    @Test("최대 폭보다 좁은 이미지는 원래 크기로 디코딩한다")
    func keepsNarrowImageSize() throws {
        let data = try #require(Self.pngData(width: 200, height: 100))

        let image = try #require(RemoteImageCache.decodedImage(from: data, maxPixelWidth: 300))

        #expect(image.size.width == 200)
        #expect(image.size.height == 100)
    }

    @Test("같은 URL이라도 최대 폭이 다르면 따로 디코딩해 캐시한다")
    func cachesEachMaxPixelWidthSeparately() async throws {
        let data = try #require(Self.pngData(width: 1_200, height: 1_600))
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("remote-image-cache-\(UUID().uuidString).png")
        try data.write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        let cache = RemoteImageCache(maxPixelWidth: 600)

        let small = try await cache.load(url, maxPixelWidth: 240)

        #expect(small.size.width == 240)
        #expect(cache.image(for: url, maxPixelWidth: 240) === small)
        #expect(cache.image(for: url) == nil)

        let large = try await cache.load(url)

        #expect(large.size.width == 600)
        #expect(cache.image(for: url) === large)
        #expect(cache.image(for: url, maxPixelWidth: 600) === large)
        #expect(cache.image(for: url, maxPixelWidth: 240) === small)
    }

    @Test("썸네일과 원본은 같은 비율을 유지하며 따로 캐시한다")
    func cachesThumbnailAndOriginalSeparately() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let thumbnailURL = directory.appendingPathComponent("thumbnail.png")
        let originalURL = directory.appendingPathComponent("original.png")
        try #require(Self.pngData(width: 200, height: 300)).write(to: thumbnailURL)
        try #require(Self.pngData(width: 600, height: 900)).write(to: originalURL)
        let cache = RemoteImageCache(maxPixelWidth: 300)

        let thumbnail = try await cache.load(thumbnailURL)
        let original = try await cache.load(originalURL)
        // 재진입은 파일을 다시 읽지 않고 준비된 원본을 사용한다.
        try FileManager.default.removeItem(at: originalURL)
        let reopened = try await cache.load(originalURL)

        #expect(thumbnail.size == CGSize(width: 200, height: 300))
        #expect(original.size == CGSize(width: 300, height: 450))
        #expect(cache.image(for: thumbnailURL) === thumbnail)
        #expect(reopened === original)
    }

    @Test("피드는 카드의 1:1 크롭 대신 캐시된 썸네일의 전체 비율로 열린다")
    func placeholderPreservesUncroppedThumbnail() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("feed-thumbnail-\(UUID().uuidString).png")
        try #require(Self.pngData(width: 200, height: 300)).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        let thumbnail = try await RemoteImageCache.shared.load(url)

        let placeholder = FeedPhotoPlaceholder(
            imageSource: .remote(url),
            heightToWidthRatio: 1
        )

        #expect(placeholder.preloadedImage === thumbnail)
        #expect(placeholder.heightToWidthRatio == 1.5)
        #expect(thumbnail.size == CGSize(width: 200, height: 300))
    }

    @Test("홈 랭킹 칸은 썸네일을 칸 폭에 맞춰 줄여 디코딩한다")
    func rankingCellDecodesThumbnailToCellWidth() async throws {
        // Given 810×1080 썸네일, 칸 폭 82pt, 화면 배율 3
        // When  홈 랭킹 칸이 썸네일을 받는다
        // Then  캐시에 올라간 이미지가 246×328px
        let url = try Self.writeTemporaryPNG(width: 810, height: 1_080)
        defer { try? FileManager.default.removeItem(at: url) }
        let cache = RemoteImageCache()
        let fillPixelSize = HomeRankingRow.thumbnailFillPixelSize(displayScale: 3)

        let image = try await cache.load(url, fillPixelSize: fillPixelSize)

        #expect(image.pixelSize == CGSize(width: 246, height: 328))
    }

    @Test("전시 그리드 칸은 썸네일을 칸 폭 × 화면 배율로 줄여 디코딩한다")
    func displayGridCellDecodesThumbnailToCellWidthTimesScale() async throws {
        // Given 810×1080 썸네일, 전시 그리드 칸 폭 189pt(iPhone 17 Pro Max: (440 - 25×2 - 12) / 2), 화면 배율 3
        // When  전시 그리드 칸이 썸네일을 받는다
        // Then  캐시에 올라간 이미지 폭이 567px (810px보다 작음)
        let url = try Self.writeTemporaryPNG(width: 810, height: 1_080)
        defer { try? FileManager.default.removeItem(at: url) }
        let cache = RemoteImageCache()
        let maxPixelWidth = ChalkakImage.pixelWidth(forPointWidth: 189, displayScale: 3)

        let image = try await cache.load(url, maxPixelWidth: maxPixelWidth)

        #expect(image.pixelSize.width == 567)
        #expect(cache.image(for: url, maxPixelWidth: maxPixelWidth) === image)
    }

    @Test("칸 크기로 받아 둔 썸네일로 피드가 첫 프레임부터 열린다")
    func placeholderUsesThumbnailCachedAtCellWidth() async throws {
        // Given 전시 그리드가 썸네일을 칸 폭으로 받아 캐시에 둠
        // When  그 사진을 눌러 피드 자리표시(placeholder)를 만든다
        // Then  미리 받은 이미지(preloadedImage)가 비어 있지 않고, 그 칸 크기 이미지다
        let url = try Self.writeTemporaryPNG(width: 810, height: 1_080)
        defer { try? FileManager.default.removeItem(at: url) }
        let cellImage = try await RemoteImageCache.shared.load(url, maxPixelWidth: 567)

        let placeholder = FeedPhotoPlaceholder(imageSource: .remote(url), heightToWidthRatio: 1)

        let preloaded = try #require(placeholder.preloadedImage)
        #expect(preloaded === cellImage)
        #expect(preloaded.pixelSize.width == 567)
        let ratio = try #require(placeholder.heightToWidthRatio)
        #expect(abs(ratio - 1_080.0 / 810.0) < 0.001)
    }

    @Test("썸네일이 칸보다 작으면 늘리지 않는다")
    func rankingCellKeepsSmallerThumbnailSize() async throws {
        // Given 200×300 썸네일, 칸 폭 82pt, 화면 배율 3 (246px)
        // When  홈 랭킹 칸이 썸네일을 받는다
        // Then  캐시에 올라간 이미지가 200×300px 그대로
        let url = try Self.writeTemporaryPNG(width: 200, height: 300)
        defer { try? FileManager.default.removeItem(at: url) }
        let cache = RemoteImageCache()
        let fillPixelSize = HomeRankingRow.thumbnailFillPixelSize(displayScale: 3)

        let image = try await cache.load(url, fillPixelSize: fillPixelSize)

        #expect(image.pixelSize == CGSize(width: 200, height: 300))
    }

    @Test("칸 크기를 아직 모르면 기본 폭으로 디코딩한다")
    func unknownCellWidthDecodesWithDefaultWidth() async throws {
        // Given 칸 폭이 0으로 측정된 첫 그리기
        // When  썸네일을 받는다
        // Then  기본 폭(1320px 이하) 규칙으로 디코딩되고 실패하지 않는다
        let url = try Self.writeTemporaryPNG(width: 1_500, height: 2_000)
        defer { try? FileManager.default.removeItem(at: url) }
        let cache = RemoteImageCache()
        let maxPixelWidth = ChalkakImage.pixelWidth(forPointWidth: 0, displayScale: 3)

        let image = try await cache.load(url, maxPixelWidth: maxPixelWidth)

        #expect(maxPixelWidth == nil)
        #expect(image.pixelSize == CGSize(width: 1_320, height: 1_760))
        #expect(cache.image(for: url) === image)
    }

    private static func writeTemporaryPNG(width: CGFloat, height: CGFloat) throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("remote-image-cell-\(UUID().uuidString).png")
        try #require(pngData(width: width, height: height)).write(to: url)
        return url
    }

    private static func pngData(width: CGFloat, height: CGFloat) -> Data? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
            .image { context in
                UIColor.systemTeal.setFill()
                context.fill(CGRect(x: 0, y: 0, width: width, height: height))
            }
            .pngData()
    }
}

@MainActor
struct FeedPhotoImageSwapTests {
    @Test("원본이 이동 중 도착해도 썸네일은 전환 종료까지 유지된다")
    func retainsThumbnailDuringTransition() {
        let thumbnail = UIImage()
        let original = UIImage()
        var state = FeedPhotoImageSwap(displayedImage: thumbnail)

        state.receiveOriginal(original)
        #expect(state.displayedImage === thumbnail)
        #expect(!state.hasOriginal)

        state.finishTransition()
        #expect(state.displayedImage === original)
        #expect(state.hasOriginal)
        state.finishTransition()
        #expect(state.displayedImage === original)
    }

    @Test("원본 서명은 원본 사진과 같은 시점에 교체한다")
    func swapsSignatureWithOriginal() {
        let original = UIImage()
        let signature = UIImage()
        var state = FeedPhotoImageSwap(displayedImage: UIImage())

        state.receiveOriginal(original, signature: signature)
        #expect(state.originalSignature == nil)

        state.finishTransition()
        #expect(state.displayedImage === original)
        #expect(state.originalSignature === signature)
    }

    @Test("전환이 끝난 뒤 받은 원본은 즉시 교체한다")
    func replacesAfterTransition() {
        let original = UIImage()
        var state = FeedPhotoImageSwap(displayedImage: UIImage())
        state.finishTransition()
        state.receiveOriginal(original)
        #expect(state.displayedImage === original)
        #expect(state.hasOriginal)
    }

    @Test("출발 이미지가 없는 진입은 원본 표시를 미루지 않는다")
    func showsOriginalWithoutThumbnail() {
        let original = UIImage()
        var state = FeedPhotoImageSwap(displayedImage: nil)
        state.receiveOriginal(original)
        #expect(state.displayedImage === original)
    }
}

private extension UIImage {
    var pixelSize: CGSize {
        CGSize(width: size.width * scale, height: size.height * scale)
    }
}
