import Foundation
import Testing
@testable import chalkak

@Suite(.serialized)
struct AuthenticatedHTTPClientTests {
    @Test("토큰 없이 보낸 게스트 요청의 401은 세션을 지우거나 refresh하지 않는다")
    func guestUnauthorizedResponseDoesNotInvalidateSession() async throws {
        for errorCode in ["UNAUTHORIZED", "REAUTHENTICATION_REQUIRED"] {
            let sessionBox = TestAuthSessionBox(accessToken: "", refreshToken: "")
            let recorder = AuthenticatedRequestRecorder()
            AuthenticatedMockURLProtocol.install { request in
                await recorder.append(request)
                return Self.response(
                    for: request,
                    statusCode: 401,
                    body: #"{"errorCode":"\#(errorCode)"}"#
                )
            }
            defer {
                AuthenticatedMockURLProtocol.uninstall()
            }

            let client = Self.client(sessionBox: sessionBox)
            let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

            let (_, response) = try await client.data(for: request)

            let requests = await recorder.requests
            #expect(response.statusCode == 401)
            #expect(requests.count == 1)
            #expect(requests.first?.value(forHTTPHeaderField: "Authorization") == nil)
            #expect(await sessionBox.invalidateCount == 0)
            #expect(await sessionBox.savedTokens.isEmpty)
        }
    }

    @Test("동시 401 응답은 refresh API 하나를 공유하고 새 토큰으로 원 요청을 재시도한다")
    func sharesSingleRefreshAcrossConcurrentUnauthorizedResponses() async throws {
        let sessionBox = TestAuthSessionBox()
        let recorder = AuthenticatedRequestRecorder()
        AuthenticatedMockURLProtocol.install { request in
            await recorder.append(request)

            switch (request.httpMethod, request.url?.path) {
            case ("GET", "/api/v1/protected"):
                if request.value(forHTTPHeaderField: "Authorization") == "Bearer access-2" {
                    return Self.response(for: request, body: #"{"ok":true}"#)
                }
                return Self.response(
                    for: request,
                    statusCode: 401,
                    body: #"{"errorCode":"UNAUTHORIZED"}"#
                )
            case ("POST", "/api/v1/auth/refresh"):
                try? await Task.sleep(for: .milliseconds(100))
                return Self.response(
                    for: request,
                    body: #"{"accessToken":"access-2","expiresIn":900,"refreshToken":"refresh-2","refreshTokenExpiresIn":2592000}"#
                )
            default:
                return Self.response(for: request, statusCode: 404, body: "{}")
            }
        }
        defer {
            AuthenticatedMockURLProtocol.uninstall()
        }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

        async let first = client.data(for: request)
        async let second = client.data(for: request)
        let results = try await [first.0, second.0]

        #expect(results.allSatisfy { String(data: $0, encoding: .utf8) == #"{"ok":true}"# })
        let requests = await recorder.requests
        let refreshRequests = requests.filter { $0.url?.path == "/api/v1/auth/refresh" }
        let protectedRequests = requests.filter { $0.url?.path == "/api/v1/protected" }

        #expect(refreshRequests.count == 1)
        #expect(Self.jsonBody(try #require(refreshRequests.first)) == ["refreshToken": "refresh-1"])
        #expect(refreshRequests.first?.value(forHTTPHeaderField: "Authorization") == nil)
        #expect(protectedRequests.filter {
            $0.value(forHTTPHeaderField: "Authorization") == "Bearer access-1"
        }.count == 2)
        #expect(protectedRequests.filter {
            $0.value(forHTTPHeaderField: "Authorization") == "Bearer access-2"
        }.count == 2)
        #expect(await sessionBox.savedTokens == [
            RefreshedTokens(
                accessToken: "access-2",
                expiresIn: 900,
                refreshToken: "refresh-2",
                refreshTokenExpiresIn: 2_592_000
            )
        ])
        #expect(await sessionBox.invalidateCount == 0)
    }

    @Test("늦게 도착한 재인증 응답은 새 토큰으로 한 번 재시도하고 세션을 지우지 않는다")
    func retriesLateReauthenticationResponseWithNewSessionToken() async throws {
        let sessionBox = TestAuthSessionBox()
        let recorder = AuthenticatedRequestRecorder()
        AuthenticatedMockURLProtocol.install { request in
            await recorder.append(request)
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer access-1" {
                await sessionBox.save(
                    RefreshedTokens(
                        accessToken: "access-2",
                        expiresIn: 900,
                        refreshToken: "refresh-2",
                        refreshTokenExpiresIn: 2_592_000
                    )
                )
                return Self.response(
                    for: request,
                    statusCode: 401,
                    body: #"{"errorCode":"REAUTHENTICATION_REQUIRED"}"#
                )
            }
            return Self.response(for: request, body: #"{"ok":true}"#)
        }
        defer { AuthenticatedMockURLProtocol.uninstall() }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

        let (data, response) = try await client.data(for: request)

        let requests = await recorder.requests
        #expect(response.statusCode == 200)
        #expect(String(data: data, encoding: .utf8) == #"{"ok":true}"#)
        #expect(requests.count == 2)
        #expect(requests.map { $0.value(forHTTPHeaderField: "Authorization") } == [
            "Bearer access-1",
            "Bearer access-2"
        ])
        #expect(await sessionBox.invalidateCount == 0)
    }

    @Test("새 토큰 재시도도 재인증 필요면 현재 세션을 지운다")
    func invalidatesWhenReplacementTokenRetryRequiresReauthentication() async {
        let sessionBox = TestAuthSessionBox()
        let recorder = AuthenticatedRequestRecorder()
        AuthenticatedMockURLProtocol.install { request in
            await recorder.append(request)
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer access-1" {
                await sessionBox.save(
                    RefreshedTokens(
                        accessToken: "access-2",
                        expiresIn: 900,
                        refreshToken: "refresh-2",
                        refreshTokenExpiresIn: 2_592_000
                    )
                )
            }
            return Self.response(
                for: request,
                statusCode: 401,
                body: #"{"errorCode":"REAUTHENTICATION_REQUIRED"}"#
            )
        }
        defer { AuthenticatedMockURLProtocol.uninstall() }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

        await #expect(throws: AuthenticatedHTTPClientError.reauthenticationRequired) {
            try await client.data(for: request)
        }

        let requests = await recorder.requests
        #expect(requests.map { $0.value(forHTTPHeaderField: "Authorization") } == [
            "Bearer access-1",
            "Bearer access-2"
        ])
        #expect(await sessionBox.invalidateCount == 1)
    }

    @Test("REAUTHENTICATION_REQUIRED 응답은 세션을 지우고 refresh를 호출하지 않는다")
    func invalidatesSessionWhenReauthenticationIsRequired() async {
        let sessionBox = TestAuthSessionBox()
        let recorder = AuthenticatedRequestRecorder()
        AuthenticatedMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                statusCode: 401,
                body: #"{"errorCode":"REAUTHENTICATION_REQUIRED"}"#
            )
        }
        defer {
            AuthenticatedMockURLProtocol.uninstall()
        }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

        await #expect(throws: AuthenticatedHTTPClientError.reauthenticationRequired) {
            _ = try await client.data(for: request)
        }

        let requests = await recorder.requests
        #expect(requests.count == 1)
        #expect(requests.first?.url?.path == "/api/v1/protected")
        #expect(await sessionBox.invalidateCount == 1)
    }

    @Test("늦게 도착한 401은 갱신된 토큰으로 재시도하고 refresh를 반복하지 않는다")
    func retriesLateUnauthorizedResponseWithRotatedToken() async throws {
        let sessionBox = TestAuthSessionBox()
        let recorder = AuthenticatedRequestRecorder()
        let requestOrder = ProtectedRequestOrder()
        AuthenticatedMockURLProtocol.install { request in
            await recorder.append(request)
            if request.url?.path == "/api/v1/auth/refresh" {
                return Self.response(
                    for: request,
                    body: #"{"accessToken":"access-2","expiresIn":900,"refreshToken":"refresh-2","refreshTokenExpiresIn":2592000}"#
                )
            }
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer access-1" {
                if await requestOrder.isSecond() {
                    await sessionBox.waitForAccessToken("access-2")
                }
                return Self.response(
                    for: request,
                    statusCode: 401,
                    body: #"{"errorCode":"UNAUTHORIZED"}"#
                )
            }
            return Self.response(for: request, body: #"{"ok":true}"#)
        }
        defer { AuthenticatedMockURLProtocol.uninstall() }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)
        async let first = client.data(for: request)
        async let second = client.data(for: request)

        _ = try await [first.0, second.0]

        let requests = await recorder.requests
        #expect(requests.filter { $0.url?.path == "/api/v1/auth/refresh" }.count == 1)
    }

    @Test("refresh API가 재인증 필요를 응답하면 세션을 삭제한다")
    func invalidatesSessionWhenRefreshTokenIsRejected() async {
        let sessionBox = TestAuthSessionBox()
        AuthenticatedMockURLProtocol.install { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                return Self.response(
                    for: request,
                    statusCode: 401,
                    body: #"{"errorCode":"REAUTHENTICATION_REQUIRED"}"#
                )
            }
            return Self.response(
                for: request,
                statusCode: 401,
                body: #"{"errorCode":"UNAUTHORIZED"}"#
            )
        }
        defer { AuthenticatedMockURLProtocol.uninstall() }

        let client = Self.client(sessionBox: sessionBox)
        let request = URLRequest(url: URL(string: "https://example.com/api/v1/protected")!)

        await #expect(throws: AuthenticatedHTTPClientError.reauthenticationRequired) {
            try await client.data(for: request)
        }
        #expect(await sessionBox.invalidateCount == 1)
    }

    private static func client(sessionBox: TestAuthSessionBox) -> AuthenticatedHTTPClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AuthenticatedMockURLProtocol.self]
        return AuthenticatedHTTPClient(
            baseURL: URL(string: "https://example.com/api/v1/")!,
            session: URLSession(configuration: configuration),
            sessionStore: AuthSessionStore(
                accessToken: { await sessionBox.accessToken },
                refreshToken: { await sessionBox.refreshToken },
                updateTokens: { await sessionBox.save($0) },
                invalidate: { await sessionBox.invalidate() }
            ),
            refreshCoordinator: TokenRefreshCoordinator()
        )
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

    private static func jsonBody(_ request: URLRequest) -> [String: String] {
        let object = try? JSONSerialization.jsonObject(with: request.httpBody ?? Data())
        return object as? [String: String] ?? [:]
    }
}

private actor TestAuthSessionBox {
    private(set) var accessToken = "access-1"
    private(set) var refreshToken = "refresh-1"
    private(set) var savedTokens: [RefreshedTokens] = []
    private(set) var invalidateCount = 0
    private var accessTokenWaiters: [(String, CheckedContinuation<Void, Never>)] = []

    init(accessToken: String = "access-1", refreshToken: String = "refresh-1") {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }

    func save(_ tokens: RefreshedTokens) {
        savedTokens.append(tokens)
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        let readyWaiters = accessTokenWaiters.filter { $0.0 == accessToken }
        accessTokenWaiters.removeAll { $0.0 == accessToken }
        readyWaiters.forEach { $0.1.resume() }
    }

    func invalidate() {
        accessToken = ""
        refreshToken = ""
        invalidateCount += 1
    }

    func waitForAccessToken(_ expectedToken: String) async {
        guard accessToken != expectedToken else { return }
        await withCheckedContinuation { continuation in
            accessTokenWaiters.append((expectedToken, continuation))
        }
    }
}

private actor ProtectedRequestOrder {
    private var count = 0

    func isSecond() -> Bool {
        count += 1
        return count == 2
    }
}

private actor AuthenticatedRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class AuthenticatedMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async -> (HTTPURLResponse, Data)

    nonisolated(unsafe) private static var handler: Handler?

    static func install(handler: @escaping Handler) {
        self.handler = handler
    }

    static func uninstall() {
        handler = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        let request = Self.requestWithReadableBody(request)
        Task {
            let (response, data) = await handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
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
