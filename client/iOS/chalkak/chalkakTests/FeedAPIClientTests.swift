import Foundation
import Testing
@testable import chalkak

@Suite(.serialized)
struct FeedAPIClientTests {
    @Test("trailing slash가 없는 base URL에서도 상세 요청 경로를 유지한다")
    func preservesBasePathWhenFetchingPostDetail() async throws {
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"id":"post-1","topic":{"title":"하늘","topicDate":"2026-09-01"},"originalImageUrl":"https://example.com/original.webp","signatureOriginalImageUrl":"https://example.com/signature.webp","title":"제목","likeCount":1,"isLiked":false,"isMine":true}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1")!
            ),
            session: URLSession(configuration: configuration)
        )

        let content = try await client.fetchPostDetail(postID: "post-1")

        #expect(content.post.id == "post-1")
        let request = try #require(await recorder.firstRequest)
        #expect(request.url?.path == "/api/v1/posts/post-1")
    }

    @Test("게시물 제목 수정은 PUT과 정규화된 제목을 전송한다")
    func updatesPostTitleWithNormalizedBody() async throws {
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"수정한 제목"}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1")!
            ),
            session: URLSession(configuration: configuration)
        )

        let update = try await client.updatePostTitle(
            postID: "post-1",
            title: "  수정한 제목  "
        )

        #expect(update == FeedTitleUpdate(postID: "post-1", title: "수정한 제목"))
        let request = try #require(await recorder.firstRequest)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/api/v1/posts/post-1")
        #expect(Self.bodyString(for: request) == #"{"title":"수정한 제목"}"#)
    }

    @Test("빈 제목 수정은 null을 전송한다")
    func clearsPostTitleWithNullBody() async throws {
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","title":null}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1")!
            ),
            session: URLSession(configuration: configuration)
        )

        let update = try await client.updatePostTitle(postID: "post-1", title: "   ")

        #expect(update == FeedTitleUpdate(postID: "post-1", title: nil))
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyString(for: request) == #"{"title":null}"#)
    }

    private static func bodyString(for request: URLRequest) -> String? {
        if let httpBody = request.httpBody {
            return String(data: httpBody, encoding: .utf8)
        }
        guard let stream = request.httpBodyStream else { return nil }

        stream.open()
        defer { stream.close() }
        var data = Data()
        let bufferSize = 1024
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while stream.hasBytesAvailable {
            let count = buffer.withUnsafeMutableBytes { bufferPointer in
                stream.read(
                    bufferPointer.bindMemory(to: UInt8.self).baseAddress!,
                    maxLength: bufferSize
                )
            }
            guard count > 0 else { break }
            data.append(contentsOf: buffer.prefix(count))
        }
        return String(data: data, encoding: .utf8)
    }

    private static func response(
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

private actor FeedAPIRequestRecorder {
    private(set) var requests: [URLRequest] = []

    var firstRequest: URLRequest? { requests.first }

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class FeedAPIClientURLProtocol: URLProtocol, @unchecked Sendable {
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
