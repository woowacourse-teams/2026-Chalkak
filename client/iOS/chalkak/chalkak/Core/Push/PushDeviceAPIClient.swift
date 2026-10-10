import Foundation

enum PushDeviceAPIError: Error, Equatable, Sendable {
    case configuration
    case notAuthenticated
    case sessionUnavailable
    case network
    case http(Int)
}

struct PushDeviceAPIClient: Sendable {
    private let baseURL: URL?
    private let session: URLSession
    private let sessionStore: AuthSessionStore
    private let authenticatedClient: AuthenticatedHTTPClient?

    init(
        baseURL: URL?,
        session: URLSession = .shared,
        sessionStore: AuthSessionStore = .live(),
        refreshCoordinator: TokenRefreshCoordinator = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
        self.sessionStore = sessionStore
        self.authenticatedClient = baseURL.map {
            AuthenticatedHTTPClient(
                baseURL: $0,
                session: session,
                sessionStore: sessionStore,
                refreshCoordinator: refreshCoordinator
            )
        }
    }

    func register(fcmToken: String) async throws {
        guard let baseURL, let authenticatedClient else {
            throw PushDeviceAPIError.configuration
        }
        let url = baseURL.appendingPathComponent("push-devices/current")
        guard url.scheme?.lowercased() == "https" else {
            throw PushDeviceAPIError.configuration
        }
        guard var accessToken = await sessionStore.accessToken(), !accessToken.isEmpty else {
            throw PushDeviceAPIError.notAuthenticated
        }

        // 로그인 ID가 없는 이전 JWT는 서버가 등록을 거절하므로 먼저 갱신한다.
        if !AccessTokenClaims.containsSessionID(accessToken) {
            accessToken = try await authenticatedClient.refreshAccessToken(replacing: accessToken)
            guard AccessTokenClaims.containsSessionID(accessToken) else {
                throw PushDeviceAPIError.sessionUnavailable
            }
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(RegistrationRequest(fcmToken: fcmToken))

        // 등록 실패만으로 로그인을 해제하지 않도록 공통 401 처리 대신 갱신만 직접 요청한다.
        // 갱신 자체가 인증 오류로 거절되면 공통 갱신 처리가 재로그인으로 보낸다.
        var statusCode = try await send(request, accessToken: accessToken)
        if statusCode == 401 {
            accessToken = try await authenticatedClient.refreshAccessToken(replacing: accessToken)
            statusCode = try await send(request, accessToken: accessToken)
        }
        guard (200..<300).contains(statusCode) else {
            throw PushDeviceAPIError.http(statusCode)
        }
    }

    private func send(_ request: URLRequest, accessToken: String) async throws -> Int {
        var request = request
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        do {
            let (_, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else {
                throw PushDeviceAPIError.network
            }
            return response.statusCode
        } catch let error as PushDeviceAPIError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        } catch {
            throw PushDeviceAPIError.network
        }
    }
}

nonisolated enum AccessTokenClaims {
    static func containsSessionID(_ accessToken: String) -> Bool {
        let segments = accessToken.split(separator: ".", omittingEmptySubsequences: false)
        guard segments.count == 3,
              let payload = base64URLDecoded(String(segments[1])),
              let claims = try? JSONSerialization.jsonObject(with: payload) as? [String: Any],
              let sessionID = claims["session_id"] as? String else {
            return false
        }
        return !sessionID.isEmpty
    }

    private static func base64URLDecoded(_ value: String) -> Data? {
        var base64 = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: base64)
    }
}

private struct RegistrationRequest: Encodable {
    let fcmToken: String
}
