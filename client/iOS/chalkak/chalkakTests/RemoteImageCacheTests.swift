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
