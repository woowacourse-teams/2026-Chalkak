import Foundation
import Testing
import UIKit
@testable import chalkak

@Suite(.serialized)
struct HomeImagePipelineTests {
    @Test("같은 홈 이미지를 동시에 요청하면 한 번만 다운로드하고 비율을 함께 반환한다")
    @MainActor
    func coalescesRequestsAndReturnsRatio() async throws {
        let data = try #require(makeImageData(width: 120, height: 80))
        HomeImageURLProtocol.install(data: data)
        defer { HomeImageURLProtocol.uninstall() }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [HomeImageURLProtocol.self]
        let pipeline = HomeImagePipeline(
            session: URLSession(configuration: configuration)
        )
        let url = try #require(URL(string: "https://example.com/home-image.png"))

        async let first = pipeline.image(for: url, targetPixelWidth: 60)
        async let second = pipeline.image(for: url, targetPixelWidth: 60)
        let (firstResult, secondResult) = try await (first, second)
        let cachedResult = try await pipeline.image(for: url, targetPixelWidth: 60)

        #expect(HomeImageURLProtocol.requestCount == 1)
        #expect(abs(firstResult.heightToWidthRatio - (2.0 / 3.0)) < 0.001)
        #expect(abs(secondResult.heightToWidthRatio - firstResult.heightToWidthRatio) < 0.001)
        #expect(abs(cachedResult.heightToWidthRatio - firstResult.heightToWidthRatio) < 0.001)
        #expect(firstResult.image.cgImage?.width == 60)
        #expect(firstResult.image.cgImage?.height == 40)
    }

    private func makeImageData(width: CGFloat, height: CGFloat) -> Data? {
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: height)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.pngData()
    }
}

private final class HomeImageURLProtocol: URLProtocol {
    private static let lock = NSLock()
    private static var responseData = Data()
    private static var requestCountStorage = 0

    static var requestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return requestCountStorage
    }

    static func install(data: Data) {
        lock.lock()
        responseData = data
        requestCountStorage = 0
        lock.unlock()
    }

    static func uninstall() {
        lock.lock()
        responseData = Data()
        requestCountStorage = 0
        lock.unlock()
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.lock.lock()
        Self.requestCountStorage += 1
        let data = Self.responseData
        Self.lock.unlock()

        guard let url = request.url,
              let response = HTTPURLResponse(
                url: url,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["Content-Type": "image/png"]
              )
        else {
            client?.urlProtocol(self, didFailWithError: HomeImagePipelineError.invalidResponse)
            return
        }

        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
