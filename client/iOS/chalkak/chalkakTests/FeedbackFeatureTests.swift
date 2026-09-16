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
            onBack: {},
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
