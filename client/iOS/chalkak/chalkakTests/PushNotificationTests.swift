import Foundation
import Testing
@testable import chalkak

@Suite(.serialized)
struct PushDeviceAPIClientTests {
    @Test("로그인 ID가 있는 JWT는 갱신 없이 현재 기기를 등록한다")
    func registersWithSessionToken() async throws {
        let accessToken = Self.jwt(sessionID: "session-1")
        let sessionBox = PushSessionBox(accessToken: accessToken)
        let recorder = PushRequestRecorder()
        PushMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: 204)
        }
        defer { PushMockURLProtocol.uninstall() }

        try await Self.client(sessionBox: sessionBox).register(fcmToken: "fcm-token")

        let requests = await recorder.requests
        #expect(requests.count == 1)
        #expect(requests.first?.httpMethod == "PUT")
        #expect(requests.first?.url?.path == "/api/v1/push-devices/current")
        #expect(requests.first?.value(forHTTPHeaderField: "Authorization") == "Bearer \(accessToken)")
        #expect(Self.fcmToken(in: requests.first) == "fcm-token")
    }

    @Test("로그인 ID가 없는 이전 JWT는 먼저 갱신한 뒤 새 토큰으로 등록한다")
    func refreshesLegacyTokenBeforeRegistering() async throws {
        let refreshedToken = Self.jwt(sessionID: "session-1")
        let sessionBox = PushSessionBox(accessToken: Self.jwt(sessionID: nil))
        let recorder = PushRequestRecorder()
        PushMockURLProtocol.install { request in
            await recorder.append(request)
            if request.url?.path == "/api/v1/auth/refresh" {
                return Self.refreshResponse(for: request, accessToken: refreshedToken)
            }
            return Self.response(for: request, statusCode: 204)
        }
        defer { PushMockURLProtocol.uninstall() }

        try await Self.client(sessionBox: sessionBox).register(fcmToken: "fcm-token")

        let requests = await recorder.requests
        #expect(requests.map { $0.url?.path } == ["/api/v1/auth/refresh", "/api/v1/push-devices/current"])
        #expect(requests.last?.value(forHTTPHeaderField: "Authorization") == "Bearer \(refreshedToken)")
        #expect(await sessionBox.savedTokens.count == 1)
        #expect(await sessionBox.invalidateCount == 0)
    }

    @Test("갱신한 JWT에도 로그인 ID가 없으면 등록을 보내지 않고 로그인도 유지한다")
    func skipsRegistrationWhenSessionIsStillMissing() async {
        let sessionBox = PushSessionBox(accessToken: Self.jwt(sessionID: nil))
        let recorder = PushRequestRecorder()
        PushMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.refreshResponse(for: request, accessToken: Self.jwt(sessionID: nil, subject: "rotated"))
        }
        defer { PushMockURLProtocol.uninstall() }

        await #expect(throws: PushDeviceAPIError.sessionUnavailable) {
            try await Self.client(sessionBox: sessionBox).register(fcmToken: "fcm-token")
        }

        #expect(await recorder.requests.map { $0.url?.path } == ["/api/v1/auth/refresh"])
        #expect(await sessionBox.invalidateCount == 0)
    }

    @Test("만료된 액세스 토큰의 401은 갱신 후 한 번 다시 등록한다")
    func retriesOnceAfterUnauthorized() async throws {
        let expiredToken = Self.jwt(sessionID: "session-1")
        let refreshedToken = Self.jwt(sessionID: "session-1", subject: "rotated")
        let sessionBox = PushSessionBox(accessToken: expiredToken)
        let recorder = PushRequestRecorder()
        PushMockURLProtocol.install { request in
            await recorder.append(request)
            if request.url?.path == "/api/v1/auth/refresh" {
                return Self.refreshResponse(for: request, accessToken: refreshedToken)
            }
            let isExpired = request.value(forHTTPHeaderField: "Authorization") == "Bearer \(expiredToken)"
            return Self.response(for: request, statusCode: isExpired ? 401 : 204)
        }
        defer { PushMockURLProtocol.uninstall() }

        try await Self.client(sessionBox: sessionBox).register(fcmToken: "fcm-token")

        #expect(await recorder.requests.map { $0.url?.path } == [
            "/api/v1/push-devices/current",
            "/api/v1/auth/refresh",
            "/api/v1/push-devices/current",
        ])
    }

    @Test("등록이 계속 거절돼도 기기 등록 실패만으로 로그인을 해제하지 않는다")
    func keepsSessionWhenRegistrationKeepsFailing() async {
        let sessionBox = PushSessionBox(accessToken: Self.jwt(sessionID: "session-1"))
        PushMockURLProtocol.install { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                return Self.refreshResponse(
                    for: request,
                    accessToken: Self.jwt(sessionID: "session-1", subject: "rotated")
                )
            }
            return Self.response(
                for: request,
                statusCode: 401,
                body: #"{"errorCode":"REAUTHENTICATION_REQUIRED"}"#
            )
        }
        defer { PushMockURLProtocol.uninstall() }

        await #expect(throws: PushDeviceAPIError.http(401)) {
            try await Self.client(sessionBox: sessionBox).register(fcmToken: "fcm-token")
        }

        #expect(await sessionBox.invalidateCount == 0)
    }

    @Test("JWT의 session_id 유무를 구분한다")
    func detectsSessionIDClaim() {
        #expect(AccessTokenClaims.containsSessionID(Self.jwt(sessionID: "session-1")))
        #expect(!AccessTokenClaims.containsSessionID(Self.jwt(sessionID: nil)))
        #expect(!AccessTokenClaims.containsSessionID(Self.jwt(sessionID: "")))
        #expect(!AccessTokenClaims.containsSessionID("not-a-jwt"))
    }

    private static func client(sessionBox: PushSessionBox) -> PushDeviceAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PushMockURLProtocol.self]
        return PushDeviceAPIClient(
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

    private static func jwt(sessionID: String?, subject: String = "user-1") -> String {
        var claims: [String: Any] = ["sub": subject]
        if let sessionID {
            claims["session_id"] = sessionID
        }
        let payload = try! JSONSerialization.data(withJSONObject: claims, options: [.sortedKeys])
        return "header.\(base64URL(payload)).signature"
    }

    private static func base64URL(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func fcmToken(in request: URLRequest?) -> String? {
        guard let body = request?.httpBody,
              let json = try? JSONSerialization.jsonObject(with: body) as? [String: String] else {
            return nil
        }
        return json["fcmToken"]
    }

    private static func refreshResponse(
        for request: URLRequest,
        accessToken: String
    ) -> (HTTPURLResponse, Data) {
        response(
            for: request,
            statusCode: 200,
            body: """
            {"accessToken":"\(accessToken)","expiresIn":900,\
            "refreshToken":"refresh-2","refreshTokenExpiresIn":2592000}
            """
        )
    }

    private static func response(
        for request: URLRequest,
        statusCode: Int,
        body: String = ""
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
struct PushDeviceRegistrarTests {
    @Test("로그인하지 않은 상태에서는 FCM 토큰이 있어도 등록하지 않는다")
    func skipsRegistrationWithoutAuthentication() async {
        let recorder = RegisteredTokenRecorder()
        let registrar = PushDeviceRegistrar(
            isAuthenticated: { false },
            currentFCMToken: { "fcm-token" },
            register: { recorder.tokens.append($0) }
        )

        registrar.updateFCMToken("fcm-token")
        await registrar.registerCurrentDevice().value

        #expect(recorder.tokens.isEmpty)
    }

    @Test("토큰이 바뀌면 마지막 토큰으로 다시 등록한다")
    func registersLatestToken() async {
        let recorder = RegisteredTokenRecorder()
        let registrar = PushDeviceRegistrar(
            isAuthenticated: { true },
            currentFCMToken: { nil },
            register: { recorder.tokens.append($0) }
        )

        registrar.updateFCMToken("fcm-token-1")
        registrar.updateFCMToken("fcm-token-2")
        await registrar.registerCurrentDevice().value

        #expect(recorder.tokens.last == "fcm-token-2")
    }

    @Test("전달받은 토큰이 없으면 현재 FCM 토큰을 조회해 등록한다")
    func fetchesCurrentTokenAfterLogin() async {
        let recorder = RegisteredTokenRecorder()
        let registrar = PushDeviceRegistrar(
            isAuthenticated: { true },
            currentFCMToken: { "fetched-token" },
            register: { recorder.tokens.append($0) }
        )

        await registrar.registerCurrentDevice().value

        #expect(recorder.tokens == ["fetched-token"])
    }

    @Test("FCM 토큰이 준비되지 않았으면 등록을 건너뛴다")
    func skipsRegistrationWithoutToken() async {
        let recorder = RegisteredTokenRecorder()
        let registrar = PushDeviceRegistrar(
            isAuthenticated: { true },
            currentFCMToken: { nil },
            register: { recorder.tokens.append($0) }
        )

        await registrar.registerCurrentDevice().value

        #expect(recorder.tokens.isEmpty)
    }
}

@MainActor
private final class RegisteredTokenRecorder {
    var tokens: [String] = []
}

private actor PushSessionBox {
    private(set) var accessToken: String
    private(set) var refreshToken = "refresh-1"
    private(set) var savedTokens: [RefreshedTokens] = []
    private(set) var invalidateCount = 0

    init(accessToken: String) {
        self.accessToken = accessToken
    }

    func save(_ tokens: RefreshedTokens) {
        savedTokens.append(tokens)
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    func invalidate() {
        accessToken = ""
        refreshToken = ""
        invalidateCount += 1
    }
}

private actor PushRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class PushMockURLProtocol: URLProtocol, @unchecked Sendable {
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
