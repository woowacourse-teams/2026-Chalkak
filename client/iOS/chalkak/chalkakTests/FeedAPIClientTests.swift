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
        #expect(content.post.authorID == nil)
        let request = try #require(await recorder.firstRequest)
        #expect(request.url?.path == "/api/v1/posts/post-1")
    }

    @Test("상세 응답의 authorId를 게시물 작성자 ID로 매핑한다")
    func mapsAuthorIDFromPostDetail() async throws {
        FeedAPIClientURLProtocol.handler = { request in
            Self.response(
                for: request,
                body: #"{"id":"post-1","authorId":"author-1","topic":{"title":"하늘","topicDate":"2026-09-01"},"originalImageUrl":"https://example.com/original.webp","signatureOriginalImageUrl":"https://example.com/signature.webp","title":"제목","likeCount":1,"isLiked":false,"isMine":false}"#
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

        #expect(content.post.authorID == "author-1")
    }

    @Test("상세 응답의 description을 작품 설명으로 매핑하고 비어 있으면 nil로 둔다", arguments: [
        (#""description":"첫 줄\n둘째 줄","#, Optional("첫 줄\n둘째 줄")),
        (#""description":"  ","#, nil),
        (#""description":null,"#, nil),
        ("", nil)
    ])
    func mapsDescriptionFromPostDetail(descriptionField: String, expected: String?) throws {
        let body = #"{"id":"post-1","topic":{"title":"하늘","topicDate":"2026-09-01"},"originalImageUrl":"https://example.com/original.webp","signatureOriginalImageUrl":"https://example.com/signature.webp","title":"제목","#
            + descriptionField
            + #""likeCount":1,"isLiked":false,"isMine":false}"#
        let response = try JSONDecoder().decode(FeedPostDetailResponse.self, from: Data(body.utf8))

        #expect(try response.toFeedContent().post.description == expected)
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

        let update = try await client.updatePost(
            postID: "post-1",
            title: "  수정한 제목  ",
            description: nil
        )

        #expect(update == FeedPostUpdate(postID: "post-1", title: "수정한 제목", description: nil))
        let request = try #require(await recorder.firstRequest)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/api/v1/posts/post-1")
        #expect(Self.bodyJSON(for: request) == ["title": "수정한 제목", "description": NSNull()] as NSDictionary)
    }

    @Test("저장하면 제목과 설명을 한 요청에 보낸다")
    func updatePostSendsTitleAndDescription() async throws {
        // Given 제목 "퇴근길 노을", 설명 없음인 내 게시물
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"퇴근길 노을","description":"회사 앞 육교에서 찍었어요"}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(baseURL: URL(string: "https://example.com/api/v1")!),
            session: URLSession(configuration: configuration)
        )

        // When  설명을 "회사 앞 육교에서 찍었어요"로 쓰고 저장한다
        _ = try await client.updatePost(postID: "post-1", title: "퇴근길 노을", description: "회사 앞 육교에서 찍었어요")

        // Then  요청이 title "퇴근길 노을", description "회사 앞 육교에서 찍었어요"
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyJSON(for: request) == ["title": "퇴근길 노을", "description": "회사 앞 육교에서 찍었어요"] as NSDictionary)
    }

    @Test("저장에 성공하면 상세의 제목·설명이 응답 값으로 바뀐다")
    func updatePostReturnsResponseDescription() async throws {
        // Given 저장 응답이 title "퇴근길", description "회사 앞 육교에서\n찍었어요"
        FeedAPIClientURLProtocol.handler = { request in
            Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"퇴근길","description":"회사 앞 육교에서\n찍었어요"}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(baseURL: URL(string: "https://example.com/api/v1")!),
            session: URLSession(configuration: configuration)
        )

        // When  저장이 끝난다
        let update = try await client.updatePost(postID: "post-1", title: "퇴근길", description: "회사 앞 육교에서\n찍었어요")

        // Then  상세 제목 "퇴근길", 설명 "회사 앞 육교에서\n찍었어요"
        #expect(update == FeedPostUpdate(postID: "post-1", title: "퇴근길", description: "회사 앞 육교에서\n찍었어요"))
    }

    @Test("설명을 공백만 남기고 저장하면 설명 없음으로 보낸다")
    func updatePostSendsNullForBlankDescription() async throws {
        // Given 설명 "회사 앞 육교에서 찍었어요"인 내 게시물
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(for: request, body: #"{"postId":"post-1","title":"퇴근길 노을","description":null}"#)
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        let client = FeedAPIClient(
            configuration: FeedAPIConfiguration(baseURL: URL(string: "https://example.com/api/v1")!),
            session: URLSession(configuration: configuration)
        )

        // When  설명을 "   \n"으로 바꾸고 저장한다
        let update = try await client.updatePost(postID: "post-1", title: "퇴근길 노을", description: "   \n")

        // Then  요청의 description null, 저장 뒤 상세 설명 없음(null)
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyJSON(for: request) == ["title": "퇴근길 노을", "description": NSNull()] as NSDictionary)
        #expect(update.description == nil)
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

        let update = try await client.updatePost(postID: "post-1", title: "   ", description: nil)

        #expect(update == FeedPostUpdate(postID: "post-1", title: nil, description: nil))
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyJSON(for: request) == ["title": NSNull(), "description": NSNull()] as NSDictionary)
    }

    /// 요청 본문 JSON. JSONEncoder의 키 순서는 실행마다 달라질 수 있어 문자열 대신 이것으로 비교한다.
    private static func bodyJSON(for request: URLRequest) -> NSDictionary? {
        guard let body = bodyString(for: request) else { return nil }
        return (try? JSONSerialization.jsonObject(with: Data(body.utf8))) as? NSDictionary
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
