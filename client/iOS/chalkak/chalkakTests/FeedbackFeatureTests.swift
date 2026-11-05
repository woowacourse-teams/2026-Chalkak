import Foundation
import SwiftUI
import Testing
import UIKit
@testable import chalkak

@MainActor
struct FeedbackViewStateTests {
    @Test("앞뒤 공백을 제외한 내용이 있을 때만 제출할 수 있다")
    func validatesContentBeforeSubmission() {
        let emptyState = FeedbackViewState(content: " \n\t")
        let validState = FeedbackViewState(content: "  사용하기 편해요.  ")

        #expect(!emptyState.canSubmit)
        #expect(validState.canSubmit)
        #expect(validState.normalizedContent == "사용하기 편해요.")
    }

    @Test("내용 길이는 백엔드와 같이 Unicode code point 기준으로 계산한다")
    func countsUnicodeCodePoints() {
        let maximum = FeedbackViewState(
            content: String(repeating: "😀", count: FeedbackLimits.maximumContentLength)
        )
        let overMaximum = FeedbackViewState(
            content: String(repeating: "😀", count: FeedbackLimits.maximumContentLength + 1)
        )

        #expect(maximum.contentLength == FeedbackLimits.maximumContentLength)
        #expect(maximum.canSubmit)
        #expect(!overMaximum.canSubmit)
    }
}

@MainActor
struct FeedbackViewModelTests {
    @Test("제출 시 앞뒤 공백을 제거한 내용만 핸들러에 전달한다")
    func submitsNormalizedContent() async {
        var submittedContent: String?
        let viewModel = FeedbackViewModel(
            submitFeedback: { content in
                submittedContent = content
            }
        )

        viewModel.updateContent("  사진 업로드 후 화면이 멈춰요.  \n")
        await viewModel.submit()

        #expect(submittedContent == "사진 업로드 후 화면이 멈춰요.")
        #expect(!viewModel.viewState.isSubmitting)
        #expect(viewModel.event == .submitted)
    }

    @Test("공백만 있는 내용은 제출 핸들러를 호출하지 않는다")
    func skipsBlankContent() async {
        var submitCount = 0
        let viewModel = FeedbackViewModel(
            submitFeedback: { _ in
                submitCount += 1
            }
        )

        viewModel.updateContent(" \n\t")
        await viewModel.submit()

        #expect(submitCount == 0)
        #expect(viewModel.event == nil)
    }

    @Test("인증 만료 오류는 재인증 이벤트로 변환한다")
    func publishesReauthenticationEvent() async {
        let viewModel = FeedbackViewModel(
            submitFeedback: { _ in
                throw FeedbackAPIError.unauthorized
            }
        )

        viewModel.updateContent("로그인이 필요한 피드백")
        await viewModel.submit()

        #expect(viewModel.event == .reauthenticationRequired)
        #expect(!viewModel.viewState.isSubmitting)
    }

    @Test("400 오류는 서버 메시지를 사용자 메시지 이벤트로 전달한다")
    func publishesServerValidationMessage() async {
        let viewModel = FeedbackViewModel(
            submitFeedback: { _ in
                throw FeedbackAPIError.http(
                    statusCode: 400,
                    message: "피드백 내용이 올바르지 않습니다."
                )
            }
        )

        viewModel.updateContent("잘못된 요청")
        await viewModel.submit()

        #expect(
            viewModel.event
                == .showMessage("피드백 내용이 올바르지 않습니다.")
        )
    }

    @Test("제출 중 보내기를 다시 눌러도 제출은 한 번이다")
    func submitWhileSubmittingSendsOnce() async throws {
        // Given 첫 제출이 아직 응답하지 않음
        // When  보내기를 한 번 더 누른다
        // Then  제출 핸들러 호출 1회
        let gate = FeedbackSubmitGate()
        let viewModel = FeedbackViewModel(
            submitFeedback: { _ in try await gate.submit() }
        )
        viewModel.updateContent("두 번 눌러도 한 번만")

        let firstSubmit = Task { await viewModel.submit() }
        try await waitUntil { gate.callCount == 1 }
        await viewModel.submit()
        gate.resume()
        await firstSubmit.value

        #expect(gate.callCount == 1)
        #expect(viewModel.event == .submitted)
    }

    @Test("제출이 네트워크 오류면 다시 보내지 않고 목록 확인을 안내한다",
          arguments: [FeedbackSubmitFailure.network, .timedOut])
    func networkFailureAsksToCheckList(failure: FeedbackSubmitFailure) async {
        // Given 제출 핸들러가 네트워크 오류(타임아웃 포함)를 던짐
        // When  보낸다
        // Then  핸들러 호출 1회, 메시지 "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요."
        var submitCount = 0
        let viewModel = FeedbackViewModel(
            submitFeedback: { _ in
                submitCount += 1
                throw failure.error
            }
        )
        viewModel.updateContent("저장됐는지 모르는 문의")

        await viewModel.submit()

        #expect(submitCount == 1)
        #expect(
            viewModel.event
                == .showMessage("보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요.")
        )
        #expect(!viewModel.viewState.isSubmitting)
    }
}

@MainActor
@Suite(.serialized)
struct FeedbackAPIClientTests {
    @Test("피드백 제출은 POST, JSON body, 인증 헤더를 사용한다")
    func submitsFeedbackUsingAPIContract() async throws {
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                statusCode: 201,
                body: #"{"feedbackId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","createdAt":"2026-09-16T13:15:43.652Z"}"#
            )
        }

        let result = try await client.submitFeedback(content: "  사진 업로드 후 화면이 멈춰요.  ")

        #expect(result.feedbackID == "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        #expect(result.createdAt == "2026-09-16T13:15:43.652Z")

        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/feedbacks")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")

        let body = try #require(request.httpBody)
        let requestBody = try JSONDecoder().decode(FeedbackRequestBody.self, from: body)
        #expect(requestBody.content == "사진 업로드 후 화면이 멈춰요.")
    }

    @Test("400 응답은 서버 오류 메시지를 포함한 HTTP 오류로 변환한다")
    func mapsBadRequest() async {
        let client = makeClient(accessToken: nil) { request in
            Self.response(
                for: request,
                statusCode: 400,
                body: #"{"errorCode":"BUSINESS_ERROR","message":"피드백 내용이 올바르지 않습니다."}"#
            )
        }

        await #expect(
            throws: FeedbackAPIError.http(
                statusCode: 400,
                message: "피드백 내용이 올바르지 않습니다."
            )
        ) {
            try await client.submitFeedback(content: "잘못된 요청")
        }
    }

    @Test("401 응답은 인증 오류로 변환한다")
    func mapsUnauthorized() async {
        let client = makeClient(accessToken: nil) { request in
            Self.response(
                for: request,
                statusCode: 401,
                body: #"{"errorCode":"UNAUTHORIZED","message":"인증이 필요합니다."}"#
            )
        }

        await #expect(throws: FeedbackAPIError.unauthorized) {
            try await client.submitFeedback(content: "인증 오류")
        }
    }

    @Test("잘못된 내용은 네트워크 요청 전에 거부한다")
    func rejectsInvalidContentBeforeRequest() async {
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: 201, body: "{}")
        }

        await #expect(throws: FeedbackAPIError.invalidContent) {
            try await client.submitFeedback(content: " \n\t")
        }
        await #expect(throws: FeedbackAPIError.invalidContent) {
            try await client.submitFeedback(
                content: String(repeating: "😀", count: FeedbackLimits.maximumContentLength + 1)
            )
        }
        #expect(await recorder.requests.isEmpty)
    }

    @Test("목록의 hasAnswer를 현재 답변 상태로 담고 누락·null은 상태 없음으로 둔다")
    func decodesAnswerStateWithoutDetailRequests() async throws {
        // Given 현재 API의 true·false·누락·null 목록 항목
        // When 목록 API를 한 번 불러온다
        // Then 네 상태를 보존하며 항목별 상세 요청을 하지 않는다
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, body: """
            {"currentPage":1,"pageSize":20,"hasNext":false,"feedbacks":[
              {"feedbackId":"\(Self.feedbackA)","content":"완료","createdAt":"2026-11-03T06:00:00Z","hasAnswer":true},
              {"feedbackId":"\(Self.feedbackB)","content":"대기","createdAt":"2026-11-03T06:00:00Z","hasAnswer":false},
              {"feedbackId":"\(Self.feedbackA)","content":"누락","createdAt":"2026-11-03T06:00:00Z"},
              {"feedbackId":"\(Self.feedbackB)","content":"null","createdAt":"2026-11-03T06:00:00Z","hasAnswer":null}
            ]}
            """)
        }
        let page = try await client.fetchFeedbacks(page: 1)
        #expect(page.items.map(\.hasAnswer) == [true, false, nil, nil])
        #expect(await recorder.requests.count == 1)
        #expect(await recorder.requests.first?.url?.path == "/api/v1/feedbacks")
    }

    @Test("내 문의 목록은 첫 페이지를 20건 단위로 인증해 요청한다")
    func fetchesFirstFeedbackPage() async throws {
        // Given 로그인한 회원
        // When  목록 첫 페이지를 불러온다
        // Then  `GET feedbacks?page=1&pageSize=20`, `Authorization: Bearer` 헤더, 응답의 문의 ID·내용·접수 시각이 담긴다
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: """
                {"currentPage":1,"pageSize":20,"hasNext":true,"feedbacks":[
                  {"feedbackId":"\(Self.feedbackA)","content":"사진 업로드 후 화면이 멈춰요.","createdAt":"2026-11-03T06:00:00Z"},
                  {"feedbackId":"\(Self.feedbackB)","content":"알림이 늦게 와요.","createdAt":"2026-11-02T23:30:15.250Z"}
                ]}
                """
            )
        }

        let page = try await client.fetchFeedbacks(page: 1)

        let request = await recorder.requests.first
        let components = request?.url.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }
        #expect(request?.httpMethod == "GET")
        #expect(components?.path == "/api/v1/feedbacks")
        #expect(components?.queryItems?.sorted { $0.name < $1.name } == [
            URLQueryItem(name: "page", value: "1"),
            URLQueryItem(name: "pageSize", value: "20"),
        ])
        #expect(request?.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(page.currentPage == 1)
        #expect(page.hasNext)
        #expect(page.items == [
            FeedbackSummary(
                id: Self.feedbackA,
                content: "사진 업로드 후 화면이 멈춰요.",
                createdAt: Self.utc(year: 2026, month: 11, day: 3, hour: 6)
            ),
            FeedbackSummary(
                id: Self.feedbackB,
                content: "알림이 늦게 와요.",
                createdAt: Self.utc(year: 2026, month: 11, day: 2, hour: 23, minute: 30, second: 15)
                    .addingTimeInterval(0.25)
            ),
        ])
    }

    @Test("답변이 있으면 상세 상태에 답변 내용과 시각을 담는다")
    func decodesAnsweredFeedback() async throws {
        // Given `answerContent`="확인했어요", `answeredAt`="2026-11-04T01:00:00Z"
        // When  상세를 불러온다
        // Then  답변 내용 "확인했어요", 답변 시각 2026-11-04T01:00:00Z
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: """
                {"feedbackId":"\(Self.feedbackA)","content":"사진 업로드 후 화면이 멈춰요.","createdAt":"2026-11-03T06:00:00Z",
                 "answerContent":"확인했어요","answeredAt":"2026-11-04T01:00:00Z"}
                """
            )
        }

        let detail = try await client.fetchFeedback(id: Self.feedbackA)

        let request = await recorder.requests.first
        #expect(request?.httpMethod == "GET")
        #expect(request?.url?.path == "/api/v1/feedbacks/\(Self.feedbackA)")
        #expect(request?.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(detail.answer == FeedbackAnswer(
            content: "확인했어요",
            answeredAt: Self.utc(year: 2026, month: 11, day: 4, hour: 1)
        ))
    }

    @Test("답변 필드가 둘 다 null이면 미답변 상태가 된다")
    func decodesUnansweredFeedback() async throws {
        // Given `answerContent`=null, `answeredAt`=null
        // When  상세를 불러온다
        // Then  답변 없음(nil)
        let client = makeClient { request in
            Self.response(
                for: request,
                body: """
                {"feedbackId":"\(Self.feedbackA)","content":"사진 업로드 후 화면이 멈춰요.","createdAt":"2026-11-03T06:00:00Z",
                 "answerContent":null,"answeredAt":null}
                """
            )
        }

        let detail = try await client.fetchFeedback(id: Self.feedbackA)

        #expect(detail == FeedbackDetail(
            id: Self.feedbackA,
            content: "사진 업로드 후 화면이 멈춰요.",
            createdAt: Self.utc(year: 2026, month: 11, day: 3, hour: 6),
            answer: nil
        ))
    }

    @Test("상세에서 404를 받으면 찾을 수 없음 안내와 목록 갱신을 요청한다")
    func mapsDetailNotFound() async {
        // Given 상세 요청이 404 / BUSINESS_ERROR
        // When  상세를 불러온다
        // Then  "문의를 찾을 수 없어요" 메시지, 목록 갱신 이벤트 (API는 notFound로 바꿔 ViewModel에 넘긴다)
        let client = makeClient { request in
            Self.response(for: request, statusCode: 404, body: Self.notFoundBody)
        }

        await #expect(throws: FeedbackAPIError.notFound) {
            _ = try await client.fetchFeedback(id: Self.feedbackA)
        }
    }

    @Test("삭제 204 빈 본문을 성공으로 처리하고 목록에서 뺀다")
    func deletesWithEmptyNoContentResponse() async throws {
        // Given 목록 A, B, C, B 삭제 요청이 본문 없는 204
        // When  B를 삭제한다
        // Then  `DELETE feedbacks/{B}`, 목록이 A, C (목록 처리는 FeedbackListViewModelTests)
        let recorder = FeedbackRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            let response = HTTPURLResponse(
                url: request.url!, statusCode: 204, httpVersion: nil, headerFields: [:]
            )!
            return (response, Data())
        }

        try await client.deleteFeedback(id: Self.feedbackB)

        let request = await recorder.requests.first
        #expect(request?.httpMethod == "DELETE")
        #expect(request?.url?.path == "/api/v1/feedbacks/\(Self.feedbackB)")
        #expect(request?.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    @Test("삭제에서 404를 받으면 찾을 수 없음 안내 후 목록을 다시 불러온다")
    func mapsDeleteNotFound() async {
        // Given 삭제 요청이 404
        // When  삭제한다
        // Then  "문의를 찾을 수 없어요" 메시지, 목록 1페이지 재조회 (API는 notFound로 바꿔 ViewModel에 넘긴다)
        let client = makeClient { request in
            Self.response(for: request, statusCode: 404, body: Self.notFoundBody)
        }

        await #expect(throws: FeedbackAPIError.notFound) {
            try await client.deleteFeedback(id: Self.feedbackB)
        }
    }

    private static let feedbackA = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
    private static let feedbackB = "0198f6c1-62ba-7d30-8b12-0f733b6570d5"
    private static let notFoundBody = #"{"errorCode":"BUSINESS_ERROR","message":"문의를 찾을 수 없습니다."}"#

    private static func utc(
        year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0
    ) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar.date(from: DateComponents(
            year: year, month: month, day: day, hour: hour, minute: minute, second: second
        ))!
    }

    private func makeClient(
        accessToken: String? = "access-token",
        handler: @escaping FeedbackMockURLProtocol.Handler
    ) -> FeedbackAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FeedbackMockURLProtocol.self]
        FeedbackMockURLProtocol.handler = handler
        return FeedbackAPIClient(
            configuration: FeedbackAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1/")!
            ),
            session: URLSession(configuration: configuration),
            accessTokenProvider: { accessToken }
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

@MainActor
struct FeedbackScreenLayoutTests {
    @Test("피드백 화면은 iPhone 크기로 렌더링된다")
    func rendersAtIPhoneSize() {
        let view = FeedbackScreen(
            viewModel: FeedbackViewModel(
                initialState: FeedbackViewState(content: "사용하기 편해요.")
            ),
            onDismiss: {},
            onSubmitted: {},
            onReauthenticationRequired: {}
        )
        .chalkakTheme(.light)
        let controller = UIHostingController(
            rootView: view.frame(width: 402, height: 874)
        )

        let size = controller.sizeThatFits(in: CGSize(width: 402, height: 874))

        #expect(size == CGSize(width: 402, height: 874))
    }
}

private struct FeedbackRequestBody: Decodable {
    let content: String
}

private actor FeedbackRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class FeedbackMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)

    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        let request = Self.requestWithReadableBody(request)
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

    private static func requestWithReadableBody(_ request: URLRequest) -> URLRequest {
        guard request.httpBody == nil, let stream = request.httpBodyStream else {
            return request
        }

        var request = request
        var body = Data()
        stream.open()
        defer { stream.close() }

        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            body.append(buffer, count: count)
        }
        request.httpBody = body
        return request
    }
}

/// 제출 테스트에서 고르는 네트워크 오류. API 클라이언트의 `.network`와 URLSession 타임아웃을 함께 본다.
enum FeedbackSubmitFailure: Sendable, CustomTestStringConvertible {
    case network
    case timedOut

    var error: Error {
        switch self {
        case .network: FeedbackAPIError.network
        case .timedOut: URLError(.timedOut)
        }
    }

    var testDescription: String {
        switch self {
        case .network: "FeedbackAPIError.network"
        case .timedOut: "URLError.timedOut"
        }
    }
}

/// 첫 제출을 `resume()`할 때까지 붙잡아 둔다.
@MainActor
private final class FeedbackSubmitGate {
    private(set) var callCount = 0
    private var continuation: CheckedContinuation<Void, Never>?

    func submit() async throws {
        callCount += 1
        await withCheckedContinuation { continuation = $0 }
    }

    func resume() {
        continuation?.resume()
        continuation = nil
    }
}
