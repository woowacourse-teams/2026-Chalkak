import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct APIAuthRepositoryTests {
    @Test("안드로이드와 같은 회원가입 API 순서와 요청 규격을 사용한다")
    func completesSocialSignupWithAndroidContract() async throws {
        let signaturePNG = Data([0, 1, 2, 3])
        var signupRequestCount = 0
        MockAuthURLProtocol.install { request in
            switch (request.httpMethod, request.url?.path) {
            case ("POST", "/api/v1/auth/social-login"):
                return .json("{\"status\":\"SIGN_UP_REQUIRED\"}")
            case ("POST", "/api/v1/auth/social-signup/signature/uploads"):
                return .json(
                    "{\"uploadId\":\"upload-id\",\"uploadUrl\":\"https://uploads.example.com/signature\",\"expiresInSeconds\":300,\"signupToken\":\"signup-token\",\"signupTokenExpiresInSeconds\":1800}"
                )
            case ("PUT", "/signature"):
                return .empty
            case ("POST", "/api/v1/auth/social-signup"):
                signupRequestCount += 1
                if signupRequestCount == 1 {
                    return .response(
                        statusCode: 400,
                        body: Data("{\"errorCode\":\"SIGNATURE_PROCESSING_PENDING\"}".utf8)
                    )
                }
                return .json(
                    "{\"userId\":\"user-1\",\"accessToken\":\"access-token\",\"expiresIn\":900,\"refreshToken\":\"refresh-token\",\"refreshTokenExpiresIn\":2592000}"
                )
            default:
                return .response(statusCode: 404, body: Data())
            }
        }
        defer {
            KeychainSessionStore.delete()
            MockAuthURLProtocol.uninstall()
        }

        let repository = APIAuthRepository(
            baseURL: URL(string: "https://api.example.com/api/v1/")!,
            session: MockAuthURLProtocol.session,
            retryDelay: { _ in }
        )

        #expect(try await repository.login(provider: .kakao, idToken: "kakao-token") == .signUpRequired)
        let result = try await repository.completeSocialSignUp(signaturePNG: signaturePNG)

        #expect(result == .success(userID: "user-1"))
        #expect(KeychainSessionStore.refreshToken() == "refresh-token")
        let requests = MockAuthURLProtocol.allRequests()
        #expect(requests.count == 5)
        #expect(requests[0].httpMethod == "POST")
        #expect(requests[0].url?.path == "/api/v1/auth/social-login")
        #expect(jsonBody(requests[0]) == ["provider": "KAKAO", "idToken": "kakao-token"])

        #expect(requests[1].httpMethod == "POST")
        #expect(requests[1].url?.path == "/api/v1/auth/social-signup/signature/uploads")
        #expect(jsonBody(requests[1]) == ["provider": "KAKAO", "idToken": "kakao-token"])

        #expect(requests[2].httpMethod == "PUT")
        #expect(requests[2].url?.path == "/signature")
        #expect(requests[2].value(forHTTPHeaderField: "Content-Type") == "image/png")
        #expect(requests[2].value(forHTTPHeaderField: "Authorization") == nil)
        #expect(requests[2].httpBody == signaturePNG)

        #expect(requests[3].httpMethod == "POST")
        #expect(requests[3].url?.path == "/api/v1/auth/social-signup")
        #expect(jsonBody(requests[3]) == ["signupToken": "signup-token"])
        #expect(requests[4].httpMethod == "POST")
        #expect(requests[4].url?.path == "/api/v1/auth/social-signup")
        #expect(jsonBody(requests[4]) == ["signupToken": "signup-token"])
    }

    @Test("로그아웃은 로컬 세션을 먼저 삭제하고 리프레시 토큰을 body로 보낸다")
    func logsOutWithRefreshToken() async throws {
        try KeychainSessionStore.save(
            userID: "user-1",
            accessToken: "access-token",
            expiresIn: 900,
            refreshToken: "refresh-token",
            refreshTokenExpiresIn: 2_592_000
        )
        MockAuthURLProtocol.install { request in
            #expect(!KeychainSessionStore.hasAuthenticatedSession())
            return .response(statusCode: 204, body: Data())
        }
        defer {
            KeychainSessionStore.delete()
            MockAuthURLProtocol.uninstall()
        }

        let repository = APIAuthRepository(
            baseURL: URL(string: "https://api.example.com/api/v1/")!,
            session: MockAuthURLProtocol.session
        )

        await repository.logout()

        let request = try #require(MockAuthURLProtocol.allRequests().first)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/auth/logout")
        #expect(jsonBody(request) == ["refreshToken": "refresh-token"])
        #expect(!KeychainSessionStore.hasAuthenticatedSession())
    }

    private func jsonBody(_ request: URLRequest) -> [String: String] {
        let object = try? JSONSerialization.jsonObject(with: request.httpBody ?? Data())
        return object as? [String: String] ?? [:]
    }
}

private final class MockAuthURLProtocol: URLProtocol {
    struct Response {
        let statusCode: Int
        let body: Data

        static var empty: Response { Response(statusCode: 200, body: Data()) }

        static func json(_ body: String) -> Response {
            Response(statusCode: 200, body: Data(body.utf8))
        }

        static func response(statusCode: Int, body: Data) -> Response {
            Response(statusCode: statusCode, body: body)
        }
    }

    private static var requestHandler: ((URLRequest) -> Response)?
    private static var capturedRequests: [URLRequest] = []
    private static let lock = NSLock()

    static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockAuthURLProtocol.self]
        return URLSession(configuration: configuration)
    }()

    static func install(handler: @escaping (URLRequest) -> Response) {
        lock.lock()
        defer { lock.unlock() }
        requestHandler = handler
        capturedRequests = []
    }

    static func uninstall() {
        lock.lock()
        defer { lock.unlock() }
        requestHandler = nil
        capturedRequests = []
    }

    static func allRequests() -> [URLRequest] {
        lock.lock()
        defer { lock.unlock() }
        return capturedRequests
    }

    static func requests(for path: String) -> [URLRequest] {
        allRequests().filter { $0.url?.path == path }
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let request = Self.requestWithReadableBody(request)
        Self.lock.lock()
        Self.capturedRequests.append(request)
        let handler = Self.requestHandler
        Self.lock.unlock()
        let response = handler?(request) ?? .response(statusCode: 500, body: Data())

        guard let url = request.url,
              let httpResponse = HTTPURLResponse(
                  url: url,
                  statusCode: response.statusCode,
                  httpVersion: "HTTP/1.1",
                  headerFields: ["Content-Type": "application/json"]
              ) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        client?.urlProtocol(self, didReceive: httpResponse, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: response.body)
        client?.urlProtocolDidFinishLoading(self)
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
