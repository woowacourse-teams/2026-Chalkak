import Foundation

nonisolated enum AuthenticatedHTTPClientError: Error, Equatable, Sendable {
    case invalidResponse
    case refreshFailed
    case reauthenticationRequired
}

extension Notification.Name {
    static let authSessionDidRequireReauthentication = Notification.Name(
        "stonefive.chalkak.auth-session-did-require-reauthentication"
    )
}

struct AuthSessionStore: Sendable {
    typealias TokenProvider = @Sendable () async -> String?
    typealias TokenUpdater = @Sendable (RefreshedTokens) async throws -> Void
    typealias SessionInvalidator = @Sendable () async -> Void

    let accessToken: TokenProvider
    let refreshToken: TokenProvider
    let updateTokens: TokenUpdater
    let invalidate: SessionInvalidator

    static func live(
        accessTokenProvider: @escaping TokenProvider = {
            await MainActor.run { KeychainSessionStore.accessToken() }
        }
    ) -> AuthSessionStore {
        AuthSessionStore(
            accessToken: accessTokenProvider,
            refreshToken: {
                await MainActor.run { KeychainSessionStore.refreshToken() }
            },
            updateTokens: { tokens in
                try await MainActor.run {
                    try KeychainSessionStore.rotateTokens(tokens)
                }
            },
            invalidate: {
                await MainActor.run {
                    KeychainSessionStore.delete()
                    NotificationCenter.default.post(
                        name: .authSessionDidRequireReauthentication,
                        object: nil
                    )
                }
            }
        )
    }
}

nonisolated struct RefreshedTokens: Decodable, Equatable, Sendable {
    let accessToken: String
    let expiresIn: Int
    let refreshToken: String
    let refreshTokenExpiresIn: Int

    var isValid: Bool {
        !accessToken.isEmpty
            && expiresIn > 0
            && !refreshToken.isEmpty
            && refreshTokenExpiresIn > 0
    }
}

struct AuthenticatedHTTPClient: Sendable {
    private let baseURL: URL
    private let session: URLSession
    private let sessionStore: AuthSessionStore
    private let refreshCoordinator: TokenRefreshCoordinator
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init(
        baseURL: URL,
        session: URLSession = .shared,
        sessionStore: AuthSessionStore = .live(),
        refreshCoordinator: TokenRefreshCoordinator = .shared
    ) {
        self.baseURL = baseURL
        self.session = session
        self.sessionStore = sessionStore
        self.refreshCoordinator = refreshCoordinator
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let sentAccessToken = await sessionStore.accessToken()
        let initialRequest = authorized(request, accessToken: sentAccessToken)
        let initial = try await response(for: initialRequest)

        guard initial.response.statusCode == 401 else {
            return initial
        }

        if let replacementAccessToken = await replacementAccessToken(afterAuthFailureFor: sentAccessToken) {
            let retried = try await response(for: authorized(request, accessToken: replacementAccessToken))
            if retried.response.statusCode == 401,
               errorCode(in: retried.data) == ErrorCode.reauthenticationRequired {
                guard await authFailureBelongsToCurrentSession(failedAccessToken: replacementAccessToken) else {
                    return retried
                }
                await invalidateSession()
                throw AuthenticatedHTTPClientError.reauthenticationRequired
            }
            return retried
        }

        switch errorCode(in: initial.data) {
        case ErrorCode.reauthenticationRequired:
            guard await authFailureBelongsToCurrentSession(failedAccessToken: sentAccessToken) else {
                return initial
            }
            await invalidateSession()
            throw AuthenticatedHTTPClientError.reauthenticationRequired
        case ErrorCode.unauthorized:
            guard hasUsableToken(sentAccessToken) else {
                return initial
            }

            let accessToken: String
            do {
                accessToken = try await refreshCoordinator.accessToken(
                    afterUnauthorizedToken: sentAccessToken,
                    currentAccessToken: sessionStore.accessToken,
                    refresh: refreshTokens
                )
            } catch let error as AuthenticatedHTTPClientError {
                throw error
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw AuthenticatedHTTPClientError.refreshFailed
            }

            let retried = try await response(for: authorized(request, accessToken: accessToken))
            if retried.response.statusCode == 401,
               errorCode(in: retried.data) == ErrorCode.reauthenticationRequired {
                guard await authFailureBelongsToCurrentSession(failedAccessToken: accessToken) else {
                    return retried
                }
                await invalidateSession()
                throw AuthenticatedHTTPClientError.reauthenticationRequired
            }
            return retried
        default:
            return initial
        }
    }

    private func refreshTokens() async throws -> String {
        guard let refreshToken = await sessionStore.refreshToken(), !refreshToken.isEmpty else {
            await invalidateSession()
            throw AuthenticatedHTTPClientError.reauthenticationRequired
        }

        let url = baseURL.appendingPathComponent("auth/refresh")
        guard url.scheme?.lowercased() == "https", url.host?.isEmpty == false else {
            throw AuthenticatedHTTPClientError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(RefreshTokenRequest(refreshToken: refreshToken))

        let result = try await response(for: request)
        guard 200..<300 ~= result.response.statusCode else {
            if result.response.statusCode == 401,
               errorCode(in: result.data) == ErrorCode.reauthenticationRequired {
                await invalidateSession()
                throw AuthenticatedHTTPClientError.reauthenticationRequired
            }
            throw AuthenticatedHTTPClientError.refreshFailed
        }

        guard let tokens = try? decoder.decode(RefreshedTokens.self, from: result.data),
              tokens.isValid else {
            throw AuthenticatedHTTPClientError.invalidResponse
        }
        try await sessionStore.updateTokens(tokens)
        return tokens.accessToken
    }

    private func authorized(_ request: URLRequest, accessToken: String?) -> URLRequest {
        var request = request
        if let accessToken, !accessToken.isEmpty {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        } else {
            request.setValue(nil, forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func replacementAccessToken(afterAuthFailureFor failedAccessToken: String?) async -> String? {
        guard let currentAccessToken = usableToken(await sessionStore.accessToken()),
              currentAccessToken != usableToken(failedAccessToken) else {
            return nil
        }
        return currentAccessToken
    }

    private func authFailureBelongsToCurrentSession(failedAccessToken: String?) async -> Bool {
        guard let failedAccessToken = usableToken(failedAccessToken) else {
            return false
        }
        return usableToken(await sessionStore.accessToken()) == failedAccessToken
    }

    private func hasUsableToken(_ token: String?) -> Bool {
        usableToken(token) != nil
    }

    private func usableToken(_ token: String?) -> String? {
        guard let token, !token.isEmpty else {
            return nil
        }
        return token
    }

    private func response(for request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse) {
        do {
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else {
                throw AuthenticatedHTTPClientError.invalidResponse
            }
            return (data, response)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw CancellationError()
        }
    }

    private func errorCode(in data: Data) -> String? {
        (try? decoder.decode(AuthenticationErrorResponse.self, from: data))?.errorCode
    }

    private func invalidateSession() async {
        await sessionStore.invalidate()
    }
}

actor TokenRefreshCoordinator {
    static let shared = TokenRefreshCoordinator()

    private var refreshTask: Task<String, Error>?

    func accessToken(
        afterUnauthorizedToken failedAccessToken: String?,
        currentAccessToken: @escaping AuthSessionStore.TokenProvider,
        refresh: @escaping @Sendable () async throws -> String
    ) async throws -> String {
        if let currentAccessToken = await currentAccessToken(),
           !currentAccessToken.isEmpty,
           currentAccessToken != failedAccessToken {
            return currentAccessToken
        }

        if let refreshTask {
            return try await refreshTask.value
        }

        let task = Task { try await refresh() }
        refreshTask = task
        defer { refreshTask = nil }
        return try await task.value
    }
}

private struct RefreshTokenRequest: Encodable {
    let refreshToken: String
}

private struct AuthenticationErrorResponse: Decodable {
    let errorCode: String?
}

private enum ErrorCode {
    static let unauthorized = "UNAUTHORIZED"
    static let reauthenticationRequired = "REAUTHENTICATION_REQUIRED"
}
