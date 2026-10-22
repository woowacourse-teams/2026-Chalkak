import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct AIReviewAPIClientTests {
    private let postID = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"

    @Test("조회와 요청은 같은 게시물 경로와 인증을 사용하며 본문을 보내지 않는다", arguments: ["GET", "POST"])
    func usesAPIContract(method: String) async throws {
        let recorder = AIReviewRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: method == "POST" ? 202 : 200,
                                 body: Self.body(status: "PROCESSING"))
        }

        let review = try await perform(method, client: client)

        #expect(review.status == .processing)
        let request = try #require(await recorder.requests.first)
        #expect(await recorder.requests.count == 1)
        #expect(request.httpMethod == method)
        #expect(request.url?.path == "/api/v1/posts/\(postID)/ai-review")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
        #expect(request.httpBody == nil)
        #expect(request.httpBodyStream == nil)
    }

    @Test("200 응답의 본문 상태와 재요청 여부를 그대로 읽는다", arguments: ["NONE", "PROCESSING", "FAILED"])
    func decodesStatusFromBody(status: String) async throws {
        let client = makeClient { request in
            Self.response(for: request, body: Self.body(status: status, canRequestAgain: status == "FAILED"))
        }

        let review = try await client.fetchReview(postID: postID)

        #expect(review.status.rawValue == status)
        #expect(review.canRequestAgain == (status == "FAILED"))
        #expect(review.requestedAt == nil)
        #expect(review.completedAt == nil)
    }

    @Test("완료된 응답의 점수와 피드백 및 시각을 디코딩한다")
    func decodesCompletedResponse() async throws {
        let client = makeClient { request in
            Self.response(for: request, body: #"{"status":"COMPLETED","requestedAt":"2026-10-21T12:24:32.600Z","completedAt":"2026-10-21T12:25:00.000Z","result":{"overallScore":8.7,"criteria":[{"key":"composition","label":"구도","score":9.0}],"distinctivePerspective":true,"feedback":{"summary":"균형 잡힌 사진입니다.","strength":"구도가 좋아요.","weakness":"배경이 복잡해요.","suggestion":"배경을 정리해 보세요."}},"canRequestAgain":false}"#)
        }

        let review = try await client.requestReview(postID: postID)

        #expect(review.status == .completed)
        #expect(review.requestedAt == "2026-10-21T12:24:32.600Z")
        #expect(review.completedAt == "2026-10-21T12:25:00.000Z")
        #expect(!review.canRequestAgain)
        let result = try #require(review.result)
        #expect(result.overallScore == 8.7)
        #expect(result.criteria == [AIReviewCriterion(key: "composition", label: "구도", score: 9.0)])
        #expect(result.distinctivePerspective)
        #expect(result.feedback.summary == "균형 잡힌 사진입니다.")
        #expect(result.feedback.strength == "구도가 좋아요.")
        #expect(result.feedback.weakness == "배경이 복잡해요.")
        #expect(result.feedback.suggestion == "배경을 정리해 보세요.")
    }

    @Test("완료 상태에 결과가 없으면 유효하지 않은 응답이다")
    func rejectsCompletedWithoutResult() async {
        let client = makeClient { request in
            Self.response(for: request, body: Self.body(status: "COMPLETED"))
        }

        await #expect(throws: AIReviewError.invalidResponse) {
            try await client.fetchReview(postID: postID)
        }
    }

    @Test("범위를 벗어난 점수는 유효하지 않은 응답이다", arguments: [-0.1, 10.1])
    func rejectsOutOfRangeScore(score: Double) async {
        let client = makeClient { request in
            let body = "{\"status\":\"COMPLETED\",\"requestedAt\":null,\"completedAt\":null,\"canRequestAgain\":false,\"result\":{\"overallScore\":\(score),\"criteria\":[],\"distinctivePerspective\":false,\"feedback\":{\"summary\":\"a\",\"strength\":\"b\",\"weakness\":\"c\",\"suggestion\":\"d\"}}}"
            return Self.response(for: request, body: body)
        }

        await #expect(throws: AIReviewError.invalidResponse) {
            try await client.fetchReview(postID: postID)
        }
    }

    @Test("실패한 응답은 재요청 가능 여부를 서버 값으로 읽는다", arguments: [true, false])
    func preservesRetryEligibility(canRequestAgain: Bool) async throws {
        let client = makeClient { request in
            Self.response(for: request, body: Self.body(status: "FAILED", canRequestAgain: canRequestAgain))
        }

        let review = try await client.fetchReview(postID: postID)

        #expect(review.status == .failed)
        #expect(review.canRequestAgain == canRequestAgain)
    }

    @Test("400 응답의 사용자 안내를 변경하지 않는다", arguments: ["지금은 AI 리뷰를 요청할 수 없습니다.", "더 이상 AI 리뷰를 다시 요청할 수 없습니다."])
    func preservesBadRequestMessage(message: String) async throws {
        let data = try JSONSerialization.data(withJSONObject: ["errorCode": "BUSINESS_ERROR", "message": message])
        let body = try #require(String(data: data, encoding: .utf8))
        let client = makeClient { request in
            Self.response(for: request, statusCode: 400, body: body)
        }

        await #expect(throws: AIReviewError.http(statusCode: 400, message: message)) {
            try await client.requestReview(postID: postID)
        }
    }

    @Test("접근 제한 HTTP 상태와 메시지를 보존한다", arguments: [403, 404])
    func preservesAccessErrors(statusCode: Int) async {
        let client = makeClient { request in
            Self.response(for: request, statusCode: statusCode,
                          body: #"{"errorCode":"BUSINESS_ERROR","message":"접근할 수 없습니다."}"#)
        }

        await #expect(throws: AIReviewError.http(statusCode: statusCode, message: "접근할 수 없습니다.")) {
            try await client.fetchReview(postID: postID)
        }
    }

    @Test("알 수 없는 서버 상태는 유효하지 않은 응답이다")
    func rejectsUnknownStatus() async {
        let client = makeClient { request in
            Self.response(for: request, body: Self.body(status: "UNRECOGNIZED"))
        }

        await #expect(throws: AIReviewError.invalidResponse) {
            try await client.fetchReview(postID: postID)
        }
    }

    @Test("취소는 통신 오류로 바꾸지 않는다")
    func propagatesCancellation() async {
        let client = makeClient { _ in throw URLError(.cancelled) }

        await #expect(throws: CancellationError.self) {
            try await client.fetchReview(postID: postID)
        }
    }

    @Test("Repository는 조회와 요청을 해당 클라이언트 메서드로 전달한다")
    func repositoryDelegatesBothOperations() async throws {
        let recorder = AIReviewRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: request.httpMethod == "POST" ? 202 : 200,
                                 body: Self.body(status: request.httpMethod == "POST" ? "PROCESSING" : "NONE"))
        }
        let repository: any AIReviewRepository = APIAIReviewRepository(client: client)

        let initial = try await repository.fetchReview(postID: postID)
        let requested = try await repository.requestReview(postID: postID)

        #expect(initial.status == .none)
        #expect(requested.status == .processing)
        #expect(await recorder.requests.map(\.httpMethod) == ["GET", "POST"])
        #expect(await recorder.requests.allSatisfy { $0.url?.path == "/api/v1/posts/\(postID)/ai-review" })
    }

    private func perform(_ method: String, client: AIReviewAPIClient) async throws -> AIReview {
        if method == "POST" { return try await client.requestReview(postID: postID) }
        return try await client.fetchReview(postID: postID)
    }

    private func makeClient(handler: @escaping AIReviewMockURLProtocol.Handler) -> AIReviewAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AIReviewMockURLProtocol.self]
        AIReviewMockURLProtocol.handler = handler
        return AIReviewAPIClient(
            baseURL: URL(string: "https://example.com/api/v1/")!,
            session: URLSession(configuration: configuration),
            accessTokenProvider: { "access-token" }
        )
    }

    nonisolated private static func body(status: String, canRequestAgain: Bool = false) -> String {
        "{\"status\":\"\(status)\",\"requestedAt\":null,\"completedAt\":null,\"result\":null,\"canRequestAgain\":\(canRequestAgain)}"
    }

    nonisolated private static func response(for request: URLRequest, statusCode: Int = 200, body: String) -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(url: request.url!, statusCode: statusCode, httpVersion: nil,
                                       headerFields: ["Content-Type": "application/json"])!
        return (response, Data(body.utf8))
    }
}

private actor AIReviewRequestRecorder {
    private(set) var requests: [URLRequest] = []
    func append(_ request: URLRequest) { requests.append(request) }
}

private final class AIReviewMockURLProtocol: URLProtocol, @unchecked Sendable {
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
