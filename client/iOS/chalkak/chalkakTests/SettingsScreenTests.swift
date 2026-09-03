import CoreGraphics
import SwiftUI
import Testing
import UIKit
@testable import chalkak

struct SettingsAccountDialogTests {
    @Test("로그아웃과 회원탈퇴 다이얼로그 문구 및 강조 방식을 구분한다")
    func exposesAccountDialogContent() {
        #expect(SettingsAccountDialog.logout.title == "로그아웃")
        #expect(SettingsAccountDialog.logout.message == "정말 로그아웃 하시겠습니까?")
        #expect(SettingsAccountDialog.withdraw.title == "회원탈퇴")
        #expect(SettingsAccountDialog.withdraw.message == "정말 회원탈퇴 하시겠습니까?")
    }
}

@MainActor
struct SettingsScreenLayoutTests {
    @Test("회원과 게스트 설정 화면은 iPhone 크기로 렌더링된다", arguments: [true, false])
    func rendersAtIPhoneSize(isLoggedIn: Bool) {
        let viewModel = SettingsViewModel(
            initialState: SettingsViewState(
                isLoading: false,
                isLoggedIn: isLoggedIn,
                signatureURL: nil,
                version: "1.0"
            ),
            isAuthenticated: { isLoggedIn }
        )
        let view = SettingsScreen(viewModel: viewModel)
            .chalkakTheme(.light)
        let controller = UIHostingController(
            rootView: view.frame(width: 402, height: 874)
        )

        let size = controller.sizeThatFits(in: CGSize(width: 402, height: 874))

        #expect(size == CGSize(width: 402, height: 874))
    }
}

@MainActor
struct SettingsViewModelTests {
    @Test("로그인 사용자는 서버에서 서명 썸네일을 불러온다")
    func loadsAuthenticatedSignature() async {
        let expectedURL = URL(string: "https://cdn.example.com/signature.png")!
        let viewModel = SettingsViewModel(
            isAuthenticated: { true },
            loadSignature: { expectedURL }
        )

        await viewModel.load()

        #expect(viewModel.viewState.isLoggedIn)
        #expect(!viewModel.viewState.isLoading)
        #expect(viewModel.viewState.signatureURL == expectedURL)
    }

    @Test("비회원은 서명 API를 호출하지 않는다")
    func skipsSignatureForGuest() async {
        var loadCount = 0
        let viewModel = SettingsViewModel(
            isAuthenticated: { false },
            loadSignature: {
                loadCount += 1
                return nil
            }
        )

        await viewModel.load()

        #expect(!viewModel.viewState.isLoggedIn)
        #expect(loadCount == 0)
    }

    @Test("회원탈퇴가 성공하면 로그아웃 이벤트를 보낸다")
    func signsOutAfterWithdrawal() async {
        var isAuthenticated = true
        var withdrawCount = 0
        let viewModel = SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, isLoggedIn: true),
            isAuthenticated: { isAuthenticated },
            logout: { isAuthenticated = false },
            withdraw: {
                withdrawCount += 1
                isAuthenticated = false
            }
        )

        viewModel.showWithdrawDialog()
        await viewModel.confirmAccountAction()

        #expect(withdrawCount == 1)
        #expect(!viewModel.viewState.isLoggedIn)
        #expect(viewModel.event == .signedOut)
    }

    @Test("계정 작업 실패는 로그인 상태를 유지한다")
    func preservesSessionAfterAccountFailure() async {
        let viewModel = SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, isLoggedIn: true),
            isAuthenticated: { true },
            withdraw: { throw SettingsAPIError.network }
        )

        viewModel.showWithdrawDialog()
        await viewModel.confirmAccountAction()

        #expect(viewModel.viewState.isLoggedIn)
        #expect(!viewModel.viewState.isAccountActionInProgress)
        #expect(
            viewModel.event == .showMessage("요청을 처리하지 못했어요. 다시 시도해 주세요.")
        )
    }

    @Test("서명 변경 성공 시 설정 카드 이미지를 즉시 갱신한다")
    func updatesSignatureURLAfterChange() async throws {
        let expectedURL = URL(string: "https://cdn.example.com/signature-updated.png")!
        let viewModel = SettingsViewModel(
            initialState: SettingsViewState(isLoading: false, isLoggedIn: true),
            isAuthenticated: { true },
            updateSignature: { data in
                #expect(data == Data([0x89, 0x50, 0x4E, 0x47]))
                return expectedURL
            }
        )

        try await viewModel.updateSignature(Data([0x89, 0x50, 0x4E, 0x47]))

        #expect(viewModel.viewState.signatureURL == expectedURL)
    }
}

@Suite(.serialized)
struct SettingsAPIClientTests {
    @Test("서명 조회는 안드와 같은 경로와 인증 헤더를 사용한다")
    func fetchesSignatureUsingAuthenticatedEndpoint() async throws {
        let recorder = SettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"signatureOriginalImageUrl":"https://cdn.example.com/original.png","signatureThumbnailImageUrl":"https://cdn.example.com/thumbnail.png"}"#
            )
        }

        let url = try await client.fetchSignature()

        #expect(url == URL(string: "https://cdn.example.com/thumbnail.png"))
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "GET")
        #expect(request.url?.path == "/api/v1/users/me/signature")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    @Test("회원탈퇴는 body 없이 DELETE하고 204를 성공 처리한다")
    func withdrawsWithoutRequestBody() async throws {
        let recorder = SettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: 204, body: "")
        }

        try await client.withdraw()

        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "DELETE")
        #expect(request.url?.path == "/api/v1/users/me")
        #expect(request.httpBody == nil)
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    @Test("401 응답을 인증 만료로 구분한다")
    func mapsUnauthorizedResponse() async {
        let client = makeClient { request in
            Self.response(for: request, statusCode: 401, body: "{}")
        }

        await #expect(throws: SettingsAPIError.unauthorized) {
            try await client.fetchSignature()
        }
    }

    @Test("URLSession 취소 오류를 CancellationError로 전달한다")
    func mapsURLSessionCancellationError() async {
        let client = makeClient { _ in
            throw URLError(.cancelled)
        }

        await #expect(throws: CancellationError.self) {
            try await client.fetchSignature()
        }
    }

    @Test("서명 변경은 발급, PNG 업로드, 적용 요청을 순서대로 수행한다")
    func updatesSignatureUsingPresignedUpload() async throws {
        let recorder = SettingsRequestRecorder()
        let pngData = Data([0x89, 0x50, 0x4E, 0x47])
        let client = makeClient { request in
            await recorder.append(request)
            switch (request.httpMethod, request.url?.host, request.url?.path) {
            case ("POST", "example.com", "/api/v1/users/me/signature/uploads"):
                return Self.response(
                    for: request,
                    body: #"{"uploadId":"upload-id","uploadUrl":"https://uploads.example.com/signature.png","expiresInSeconds":300}"#
                )
            case ("PUT", "uploads.example.com", "/signature.png"):
                return Self.response(for: request, statusCode: 200, body: "")
            case ("PUT", "example.com", "/api/v1/users/me/signature"):
                return Self.response(
                    for: request,
                    body: #"{"signatureOriginalImageUrl":"https://cdn.example.com/signature-updated.png"}"#
                )
            default:
                return Self.response(for: request, statusCode: 404, body: "{}")
            }
        }

        let result = try await client.updateSignature(pngData: pngData)

        #expect(result == URL(string: "https://cdn.example.com/signature-updated.png"))
        let requests = await recorder.requests
        #expect(requests.count == 3)

        let createUploadRequest = requests[0]
        #expect(createUploadRequest.httpMethod == "POST")
        #expect(createUploadRequest.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")

        let uploadRequest = requests[1]
        #expect(uploadRequest.httpMethod == "PUT")
        #expect(uploadRequest.value(forHTTPHeaderField: "Content-Type") == "image/png")
        #expect(uploadRequest.value(forHTTPHeaderField: "Authorization") == nil)

        let updateRequest = requests[2]
        #expect(updateRequest.httpMethod == "PUT")
        #expect(updateRequest.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
        #expect(updateRequest.value(forHTTPHeaderField: "Content-Type") == "application/json")
    }

    @Test("1MB를 넘는 서명 이미지는 네트워크 요청 전에 거부한다")
    func rejectsOversizedSignature() async {
        let recorder = SettingsRequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            return Self.response(for: request, body: "{}")
        }

        await #expect(throws: SettingsAPIError.signatureTooLarge) {
            try await client.updateSignature(pngData: Data(repeating: 0, count: 1_048_577))
        }
        #expect(await recorder.requests.isEmpty)
    }

    private func makeClient(
        handler: @escaping SettingsMockURLProtocol.Handler
    ) -> SettingsAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [SettingsMockURLProtocol.self]
        SettingsMockURLProtocol.handler = handler
        return SettingsAPIClient(
            baseURL: URL(string: "https://example.com/api/v1/"),
            session: URLSession(configuration: configuration),
            accessTokenProvider: { "access-token" }
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

struct SignaturePngEncoderTests {
    @Test("설정에서 만든 사인은 투명 배경과 흰색 선으로 저장된다")
    func encodesWhiteSignatureOnTransparentBackground() throws {
        let strokes = [
            SignatureStroke(points: [
                SignaturePoint(xRatio: 0.2, yRatio: 0.3),
                SignaturePoint(xRatio: 0.8, yRatio: 0.7)
            ])
        ]

        let data = try SignaturePngEncoder().encode(strokes)
        let image = try #require(UIImage(data: data)?.cgImage)
        let pixels = try #require(rgbaPixels(from: image))
        var hasTransparentPixel = false
        var hasWhiteStrokePixel = false

        for index in stride(from: 0, to: pixels.count, by: 4) {
            let red = pixels[index]
            let green = pixels[index + 1]
            let blue = pixels[index + 2]
            let alpha = pixels[index + 3]
            hasTransparentPixel = hasTransparentPixel || alpha == 0
            hasWhiteStrokePixel = hasWhiteStrokePixel
                || (alpha > 200 && red > 240 && green > 240 && blue > 240)
        }

        #expect(hasTransparentPixel)
        #expect(hasWhiteStrokePixel)
    }

    private func rgbaPixels(from image: CGImage) -> [UInt8]? {
        let bytesPerPixel = 4
        var pixels = [UInt8](repeating: 0, count: image.width * image.height * bytesPerPixel)
        guard let context = CGContext(
            data: &pixels,
            width: image.width,
            height: image.height,
            bitsPerComponent: 8,
            bytesPerRow: image.width * bytesPerPixel,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return pixels
    }
}

struct LegalDocumentTests {
    @Test("설정 약관은 회원가입과 같은 Notion 문서를 사용한다")
    func usesSignupDocumentLinks() {
        #expect(
            LegalDocument.privacyPolicy.url.absoluteString
                == "https://app.notion.com/p/3b56b8e8e36780af8ec8ea0bf92b97a9?source=copy_link"
        )
        #expect(
            LegalDocument.termsOfService.url.absoluteString
                == "https://app.notion.com/p/3c66b8e8e3678064b543c26b5c0f932d?source=copy_link"
        )
        #expect(LegalDocumentNavigationPolicy.allows(LegalDocument.privacyPolicy.url))
        #expect(!LegalDocumentNavigationPolicy.allows(URL(string: "https://example.com")!))
    }
}

private actor SettingsRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class SettingsMockURLProtocol: URLProtocol, @unchecked Sendable {
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
