import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct BannerAPIClientTests {
    @Test("공개 배너는 기본 페이지를 인증 없이 요청하고 응답 순서와 메타데이터를 유지한다")
    func fetchesPublicBannerPage() async throws {
        let recorder = BannerAPIRequestRecorder()
        BannerAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(for: request, body: Self.pageBody)
        }
        defer { BannerAPIClientURLProtocol.handler = nil }

        let page = try await Self.makeClient().fetchPage()

        #expect(page.currentPage == 1)
        #expect(page.pageSize == 10)
        #expect(page.hasNext)
        #expect(page.banners == [Banner(
            id: "banner-1",
            title: "제목",
            subtitle: "설명",
            webViewURL: URL(string: "https://example.com/issue/1")!,
            thumbnailURL: URL(string: "https://example.com/image.webp")!,
            createdAt: "2026-10-20T08:08:11.001Z"
        )])
        let requests = await recorder.requests
        let request = try #require(requests.first)
        #expect(requests.count == 1)
        #expect(request.url?.path == "/api/v1/banners")
        #expect(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems == [
            URLQueryItem(name: "page", value: "1"),
            URLQueryItem(name: "pageSize", value: "10")
        ])
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
    }

    @Test("빈 페이지와 지정한 페이지 인자를 그대로 처리한다")
    func fetchesEmptyPage() async throws {
        let recorder = BannerAPIRequestRecorder()
        BannerAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"currentPage":3,"pageSize":20,"hasNext":false,"banners":[]}"#
            )
        }
        defer { BannerAPIClientURLProtocol.handler = nil }

        let page = try await Self.makeClient().fetchPage(page: 3, pageSize: 20)

        #expect(page == BannerPage(currentPage: 3, pageSize: 20, hasNext: false, banners: []))
        let requests = await recorder.requests
        let request = try #require(requests.first)
        #expect(request.url?.query == "page=3&pageSize=20")
    }

    @Test("hasNext 필드가 없으면 마지막 페이지로 처리한다")
    func missingHasNextStopsPagination() async throws {
        BannerAPIClientURLProtocol.handler = { request in
            Self.response(for: request, body: Self.pageBody.replacingOccurrences(of: "\"hasNext\":true,", with: ""))
        }
        defer { BannerAPIClientURLProtocol.handler = nil }
        let page = try await Self.makeClient().fetchPage()
        #expect(!page.hasNext)
        #expect(page.banners.count == 1)
    }

    @Test("HTTP 오류는 인증 갱신이나 재요청 없이 반환한다", arguments: [400, 401, 403, 500])
    func returnsHTTPErrorWithoutRetry(statusCode: Int) async throws {
        let recorder = BannerAPIRequestRecorder()
        BannerAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: statusCode, body: "{}")
        }
        defer { BannerAPIClientURLProtocol.handler = nil }

        await #expect(throws: BannerAPIError.http(statusCode)) {
            try await Self.makeClient().fetchPage()
        }
        #expect(await recorder.requests.count == 1)
    }

    @Test("잘못된 JSON과 안전하지 않은 배너 URL은 잘못된 응답으로 처리한다", arguments: [
        "", "{}",
        pageBody.replacingOccurrences(of: "https://example.com/issue/1", with: "/issue/1")
    ])
    func rejectsInvalidResponse(body: String) async {
        BannerAPIClientURLProtocol.handler = { request in
            Self.response(for: request, body: body)
        }
        defer { BannerAPIClientURLProtocol.handler = nil }

        await #expect(throws: BannerAPIError.invalidResponse) {
            try await Self.makeClient().fetchPage()
        }
    }

    @Test("잘못된 썸네일 URL은 배너를 유지하고 이미지 대체 표시를 사용한다", arguments: [
        "", "/image.webp", "http://example.com/image.webp", "https://"
    ])
    func preservesBannerWithInvalidThumbnail(thumbnailURL: String) async throws {
        BannerAPIClientURLProtocol.handler = { request in
            Self.response(
                for: request,
                body: Self.pageBody.replacingOccurrences(of: "https://example.com/image.webp", with: thumbnailURL)
            )
        }
        defer { BannerAPIClientURLProtocol.handler = nil }

        let page = try await Self.makeClient().fetchPage()

        #expect(page.banners.count == 1)
        #expect(page.banners.first?.id == "banner-1")
        #expect(page.banners.first?.thumbnailURL == nil)
    }

    @Test("연결 실패는 네트워크 오류로 전달한다")
    func mapsNetworkError() async {
        BannerAPIClientURLProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }
        defer { BannerAPIClientURLProtocol.handler = nil }

        await #expect(throws: BannerAPIError.network) {
            try await Self.makeClient().fetchPage()
        }
    }

    @Test("취소된 요청을 네트워크 오류로 바꾸지 않는다")
    func preservesCancellation() async {
        BannerAPIClientURLProtocol.handler = { _ in throw URLError(.cancelled) }
        defer { BannerAPIClientURLProtocol.handler = nil }

        await #expect(throws: CancellationError.self) {
            try await Self.makeClient().fetchPage()
        }
    }

    nonisolated private static let pageBody = #"{"currentPage":1,"pageSize":10,"hasNext":true,"banners":[{"id":"banner-1","title":"제목","subtitle":"설명","webViewUrl":"https://example.com/issue/1","thumbnailUrl":"https://example.com/image.webp","createdAt":"2026-10-20T08:08:11.001Z"}]}"#

    private static func makeClient() -> BannerAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [BannerAPIClientURLProtocol.self]
        return BannerAPIClient(
            configuration: BannerAPIConfiguration(baseURL: URL(string: "https://example.com/api/v1")!),
            session: URLSession(configuration: configuration)
        )
    }

    nonisolated private static func response(
        for request: URLRequest,
        statusCode: Int = 200,
        body: String
    ) -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }
}

private actor BannerAPIRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class BannerAPIClientURLProtocol: URLProtocol {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)

    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        Task {
            do {
                let (response, data) = try await handler(request)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
    }

    override func stopLoading() {}
}
