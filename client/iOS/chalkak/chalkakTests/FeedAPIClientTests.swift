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

    @Test("수정을 시작하면 현재 동의와 ID가 채워진다")
    func mapsInstagramConsentFromPostDetail() throws {
        // Given 본인 게시물 상세의 동의 true, ID "chalkak.daily"
        let body = #"{"id":"post-1","topic":{"title":"하늘","topicDate":"2026-09-01"},"originalImageUrl":"https://example.com/original.webp","signatureOriginalImageUrl":"https://example.com/signature.webp","title":"제목","likeCount":1,"isLiked":false,"isMine":true,"instagramConsent":{"agreed":true,"instagramId":"chalkak.daily"}}"#

        // When  상세 응답을 읽는다
        let response = try JSONDecoder().decode(FeedPostDetailResponse.self, from: Data(body.utf8))

        // Then  게시물의 동의가 켜짐, ID가 "chalkak.daily"
        #expect(try response.toFeedContent().post.instagramConsent == InstagramConsent(isAgreed: true, instagramID: "chalkak.daily"))
    }

    @Test("수정에서 동의를 끄고 저장하면 제목·설명과 함께 철회를 보낸다")
    func updatePostSendsConsentWithdrawal() async throws {
        // Given 제목 "퇴근길 노을", 설명 "육교에서", 동의 true, ID "chalkak.daily"인 본인 게시물의 수정 상태
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"퇴근길 노을","description":"육교에서","instagramUploadAgreed":false,"instagramId":null}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let client = Self.makeClient()

        // When  스위치를 끄고 저장
        _ = try await client.updatePost(
            postID: "post-1",
            title: "퇴근길 노을",
            description: "육교에서",
            instagramConsent: InstagramConsent(isAgreed: false, instagramID: nil)
        )

        // Then  수정 요청이 title "퇴근길 노을", description "육교에서", instagramUploadAgreed false, instagramId null
        //       (서버는 빠진 동의를 철회로 저장한다, Android와 같음)
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyJSON(for: request) == ["title": "퇴근길 노을", "description": "육교에서"] as NSDictionary)
    }

    @Test("제목만 고쳐도 동의와 ID를 지우지 않는다")
    func updatePostKeepsInstagramConsent() async throws {
        // Given 동의 true, ID "chalkak.daily"인 본인 게시물의 수정 상태
        let recorder = FeedAPIRequestRecorder()
        FeedAPIClientURLProtocol.handler = { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"새 제목","description":null,"instagramUploadAgreed":true,"instagramId":"chalkak.daily"}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let client = Self.makeClient()

        // When  제목만 "새 제목"으로 바꾸고 저장
        _ = try await client.updatePost(
            postID: "post-1",
            title: "새 제목",
            description: nil,
            instagramConsent: InstagramConsent(isAgreed: true, instagramID: "chalkak.daily")
        )

        // Then  수정 요청의 instagramUploadAgreed가 true, instagramId가 "chalkak.daily"
        let request = try #require(await recorder.firstRequest)
        #expect(Self.bodyJSON(for: request) == [
            "title": "새 제목",
            "description": NSNull(),
            "instagramUploadAgreed": true,
            "instagramId": "chalkak.daily",
        ] as NSDictionary)
    }

    @Test("저장에 성공하면 응답의 동의와 ID를 돌려준다")
    func updatePostReturnsResponseInstagramConsent() async throws {
        // Given 저장 응답이 instagramUploadAgreed true, instagramId "chalkak.daily"
        FeedAPIClientURLProtocol.handler = { request in
            Self.response(
                for: request,
                body: #"{"postId":"post-1","title":"새 제목","description":null,"instagramUploadAgreed":true,"instagramId":"chalkak.daily"}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let client = Self.makeClient()

        // When  저장이 끝난다
        let update = try await client.updatePost(
            postID: "post-1",
            title: "새 제목",
            description: nil,
            instagramConsent: InstagramConsent(isAgreed: true, instagramID: "chalkak.daily")
        )

        // Then  결과의 동의가 켜짐, ID가 "chalkak.daily"
        #expect(update.instagramConsent == InstagramConsent(isAgreed: true, instagramID: "chalkak.daily"))
    }

    @Test("수정 저장에서 서버가 ID 형식을 거절하면 시트에 오류를 보여 준다")
    func updatePostMapsInstagramIDFormatRejection() async throws {
        // Given 수정 상태에서 동의 켜짐, ID "chalkak-daily", 수정 요청이 400 "인스타그램 ID 형식이 올바르지 않습니다."
        FeedAPIClientURLProtocol.handler = { request in
            Self.response(
                for: request,
                statusCode: 400,
                body: #"{"errorCode":"BUSINESS_ERROR","message":"인스타그램 ID 형식이 올바르지 않습니다."}"#
            )
        }
        defer { FeedAPIClientURLProtocol.handler = nil }
        let client = Self.makeClient()

        // When  저장
        do {
            _ = try await client.updatePost(
                postID: "post-1",
                title: "퇴근길 노을",
                description: nil,
                instagramConsent: InstagramConsent(isAgreed: true, instagramID: "chalkak-daily")
            )
            Issue.record("ID 형식 오류인데 저장이 성공하면 안 됩니다")
        } catch let error as FeedAPIError {
            // Then  ID 형식 오류로 돌려주어 시트가 ID 칸에 "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요"를 보여 준다
            #expect(error == .invalidInstagramID)
        }
    }

    private static func makeClient() -> FeedAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedAPIClientURLProtocol.self]
        return FeedAPIClient(
            configuration: FeedAPIConfiguration(baseURL: URL(string: "https://example.com/api/v1")!),
            session: URLSession(configuration: configuration)
        )
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
