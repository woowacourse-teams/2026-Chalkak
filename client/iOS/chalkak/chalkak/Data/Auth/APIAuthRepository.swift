import Foundation
import OSLog
import Security

@MainActor
final class APIAuthRepository: AuthRepository {
    private let baseURL: URL?
    private let session: URLSession
    private let decoder = JSONDecoder()
    private let retryDelay: AuthRetryDelay
    private var pendingLogin: PendingSocialLogin?
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "AuthAPI"
    )

    init(
        baseURL: URL?,
        session: URLSession? = nil,
        retryDelay: @escaping AuthRetryDelay = { milliseconds in
            try await Task.sleep(nanoseconds: milliseconds * 1_000_000)
        }
    ) {
        self.baseURL = baseURL
        self.retryDelay = retryDelay
        self.session = session ?? URLSession(
            configuration: .default,
            delegate: HTTPSRedirectDelegate(),
            delegateQueue: nil
        )
    }

    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult {
        let loginResponse = try await requestLogin(provider: provider, idToken: idToken)
        logger.debug(
            "Social login response result=\(loginResponse.status, privacy: .public)"
        )
        switch loginResponse.status {
        case "LOGIN_SUCCESS":
            let credentials = try loginResponse.validatedCredentials()
            try KeychainSessionStore.save(credentials: credentials)
            pendingLogin = nil
            return .authenticated(userID: credentials.userID)
        case "SIGN_UP_REQUIRED":
            pendingLogin = .social(provider: provider, idToken: idToken)
            return .signUpRequired
        default:
            throw AuthRepositoryError.invalidResponse
        }
    }

    func loginWithApple(credential: AppleLoginCredential) async throws -> SocialLoginResult {
        let endpoint = try apiURL(path: "auth/apple/social-login")
        let data = try await requestData(
            url: endpoint,
            body: try JSONEncoder().encode(AppleLoginRequest(credential: credential))
        )
        let response: SocialLoginResponse
        do {
            response = try decoder.decode(SocialLoginResponse.self, from: data)
        } catch {
            throw AuthRepositoryError.invalidResponse
        }

        switch response.status {
        case "LOGIN_SUCCESS":
            let credentials = try response.validatedCredentials()
            try KeychainSessionStore.save(credentials: credentials)
            pendingLogin = nil
            return .authenticated(userID: credentials.userID)
        case "SIGN_UP_REQUIRED":
            guard let signupToken = response.signupToken, !signupToken.isEmpty else {
                throw AuthRepositoryError.invalidResponse
            }
            pendingLogin = .apple(signupToken: signupToken)
            return .signUpRequired
        default:
            throw AuthRepositoryError.invalidResponse
        }
    }

    func completeSocialSignUp(signaturePNG: Data) async throws -> SocialSignUpResult {
        guard let pendingLogin else {
            return .failure(.missingLoginContext)
        }
        guard signaturePNG.count <= Constants.maxSignatureBytes else {
            return .failure(.signatureTooLarge)
        }

        do {
            let upload = try await createSignatureUpload(for: pendingLogin)
            try await uploadSignature(signaturePNG, to: upload.uploadURL)

            for attempt in 0..<Constants.signUpAttempts {
                do {
                    let credentials = try await completeSignUp(signupToken: upload.signupToken)
                    try KeychainSessionStore.save(credentials: credentials)
                    self.pendingLogin = nil
                    return .success(userID: credentials.userID)
                } catch let error as AuthRepositoryError {
                    guard error.isSignatureProcessingPending else {
                        return .failure(error.signUpFailure)
                    }
                    guard attempt < Constants.signUpAttempts - 1 else {
                        return .failure(.signatureProcessingTimeout)
                    }
                    try await retryDelay(Constants.signUpRetryDelayMilliseconds)
                }
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as SignatureUploadError {
            return .failure(error.signUpFailure)
        } catch let error as AuthRepositoryError {
            return .failure(error.signUpFailure)
        } catch {
            return .failure(.unknown)
        }

        return .failure(.unknown)
    }

    func continueAsGuest() async throws {
        pendingLogin = nil
        KeychainSessionStore.saveGuestAccess()
    }

    func logout() async {
        pendingLogin = nil
        defer { KeychainSessionStore.delete() }
        guard let refreshToken = KeychainSessionStore.refreshToken(),
              let endpoint = try? apiURL(path: "auth/logout"),
              let body = try? JSONEncoder().encode(RefreshTokenRequest(refreshToken: refreshToken))
        else { return }
        _ = try? await requestData(url: endpoint, body: body)
    }

    private func requestLogin(
        provider: SocialLoginProvider,
        idToken: String
    ) async throws -> SocialLoginResponse {
        let endpoint = try apiURL(path: "auth/social-login")
        logger.debug(
            "Social login request provider=\(provider.rawValue, privacy: .public), url=\(endpoint.absoluteString, privacy: .public), idTokenLength=\(idToken.count, privacy: .public)"
        )
        let data = try await requestData(
            url: endpoint,
            body: try JSONEncoder().encode(
                SocialLoginRequest(provider: provider.rawValue, idToken: idToken)
            )
        )
        do {
            return try decoder.decode(SocialLoginResponse.self, from: data)
        } catch {
            throw AuthRepositoryError.invalidResponse
        }
    }

    private func createSignatureUpload(
        for login: PendingSocialLogin
    ) async throws -> SignatureUploadResponse {
        let endpoint: URL
        let body: Data
        switch login {
        case let .social(provider, idToken):
            endpoint = try apiURL(path: "auth/social-signup/signature/uploads")
            body = try JSONEncoder().encode(
                SignatureUploadRequest(provider: provider.rawValue, idToken: idToken)
            )
        case let .apple(signupToken):
            endpoint = try apiURL(path: "auth/apple/social-signup/signature/uploads")
            body = try JSONEncoder().encode(AppleSignatureUploadRequest(signupToken: signupToken))
        }
        let data = try await requestData(
            url: endpoint,
            body: body
        )
        do {
            let response = try decoder.decode(SignatureUploadResponse.self, from: data)
            guard !response.uploadID.isEmpty,
                  let uploadURL = URL(string: response.uploadURL),
                  uploadURL.scheme?.lowercased() == "https",
                  uploadURL.host?.isEmpty == false,
                  response.expiresInSeconds > 0,
                  !response.signupToken.isEmpty,
                  response.signupTokenExpiresInSeconds == nil
                      || response.signupTokenExpiresInSeconds! > 0 else {
                throw AuthRepositoryError.invalidResponse
            }
            return response
        } catch let error as AuthRepositoryError {
            throw error
        } catch {
            throw AuthRepositoryError.invalidResponse
        }
    }

    private func uploadSignature(_ signaturePNG: Data, to uploadURL: String) async throws {
        guard let url = URL(string: uploadURL),
              url.scheme?.lowercased() == "https",
              url.host?.isEmpty == false else {
            throw SignatureUploadError.invalidUploadURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("image/png", forHTTPHeaderField: "Content-Type")
        request.httpBody = signaturePNG

        let response: URLResponse
        do {
            (_, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw SignatureUploadError.network
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SignatureUploadError.network
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            throw SignatureUploadError.rejected
        }
    }

    private func completeSignUp(signupToken: String) async throws -> LoginCredentials {
        let endpoint = try apiURL(path: "auth/social-signup")
        let data = try await requestData(
            url: endpoint,
            body: try JSONEncoder().encode(SocialSignUpRequest(signupToken: signupToken))
        )
        do {
            return try decoder.decode(SocialSignUpResponse.self, from: data)
                .validatedCredentials()
        } catch {
            throw AuthRepositoryError.invalidResponse
        }
    }

    private func apiURL(path: String) throws -> URL {
        guard let baseURL else {
            logger.error("Auth request skipped because API_BASE_URL is missing")
            throw AuthRepositoryError.configuration
        }
        let endpoint = baseURL.appendingPathComponent(path)
        guard endpoint.scheme?.lowercased() == "https", endpoint.host?.isEmpty == false else {
            throw AuthRepositoryError.configuration
        }
        return endpoint
    }

    private func requestData(
        url: URL,
        body: Data
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = body

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw AuthRepositoryError.requestFailed
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthRepositoryError.requestFailed
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            let serverError = try? decoder.decode(APIErrorResponse.self, from: data)
            logger.error(
                "Auth response status=\(httpResponse.statusCode, privacy: .public), errorCode=\(serverError?.errorCode ?? "unknown", privacy: .public), message=\(serverError?.message ?? "unknown", privacy: .public)"
            )
            throw AuthRepositoryError.server(
                statusCode: httpResponse.statusCode,
                errorCode: serverError?.errorCode,
                message: serverError?.message
            )
        }
        return data
    }
}

typealias AuthRetryDelay = @Sendable (UInt64) async throws -> Void

private enum PendingSocialLogin {
    case social(provider: SocialLoginProvider, idToken: String)
    case apple(signupToken: String)
}

private enum SignatureUploadError: Error {
    case network
    case invalidUploadURL
    case rejected
}

private final class HTTPSRedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard request.url?.scheme?.lowercased() == "https" else {
            completionHandler(nil)
            return
        }
        completionHandler(request)
    }
}

private struct SocialLoginRequest: Encodable {
    let provider: String
    let idToken: String
}

private struct AppleLoginRequest: Encodable {
    let idToken: String
    let authorizationCode: String
    let rawNonce: String

    init(credential: AppleLoginCredential) {
        idToken = credential.idToken
        authorizationCode = credential.authorizationCode
        rawNonce = credential.rawNonce
    }
}

private struct SignatureUploadRequest: Encodable {
    let provider: String
    let idToken: String
}

private struct AppleSignatureUploadRequest: Encodable {
    let signupToken: String
}

private struct SocialSignUpRequest: Encodable {
    let signupToken: String
}

private struct RefreshTokenRequest: Encodable {
    let refreshToken: String
}

private struct SocialLoginResponse: Decodable {
    let status: String
    let userID: String?
    let accessToken: String?
    let expiresIn: Int?
    let refreshToken: String?
    let refreshTokenExpiresIn: Int?
    let signupToken: String?

    enum CodingKeys: String, CodingKey {
        case status
        case userID = "userId"
        case accessToken
        case expiresIn
        case refreshToken
        case refreshTokenExpiresIn
        case signupToken
    }

    func validatedCredentials() throws -> LoginCredentials {
        guard let userID,
              let accessToken,
              let expiresIn,
              let refreshToken,
              let refreshTokenExpiresIn,
              !userID.isEmpty,
              !accessToken.isEmpty,
              expiresIn > 0,
              !refreshToken.isEmpty,
              refreshTokenExpiresIn > 0 else {
            throw AuthRepositoryError.invalidResponse
        }
        return LoginCredentials(
            userID: userID,
            accessToken: accessToken,
            expiresIn: expiresIn,
            refreshToken: refreshToken,
            refreshTokenExpiresIn: refreshTokenExpiresIn
        )
    }
}

private struct LoginCredentials {
    let userID: String
    let accessToken: String
    let expiresIn: Int
    let refreshToken: String
    let refreshTokenExpiresIn: Int
}

private struct SignatureUploadResponse: Decodable {
    let uploadID: String
    let uploadURL: String
    let expiresInSeconds: Int
    let signupToken: String
    let signupTokenExpiresInSeconds: Int?

    enum CodingKeys: String, CodingKey {
        case uploadID = "uploadId"
        case uploadURL = "uploadUrl"
        case expiresInSeconds
        case signupToken
        case signupTokenExpiresInSeconds
    }
}

private struct SocialSignUpResponse: Decodable {
    let userID: String
    let accessToken: String
    let expiresIn: Int
    let refreshToken: String
    let refreshTokenExpiresIn: Int

    enum CodingKeys: String, CodingKey {
        case userID = "userId"
        case accessToken
        case expiresIn
        case refreshToken
        case refreshTokenExpiresIn
    }

    func validatedCredentials() throws -> LoginCredentials {
        guard !userID.isEmpty,
              !accessToken.isEmpty,
              expiresIn > 0,
              !refreshToken.isEmpty,
              refreshTokenExpiresIn > 0 else {
            throw AuthRepositoryError.invalidResponse
        }
        return LoginCredentials(
            userID: userID,
            accessToken: accessToken,
            expiresIn: expiresIn,
            refreshToken: refreshToken,
            refreshTokenExpiresIn: refreshTokenExpiresIn
        )
    }
}

private struct APIErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
}

private extension AuthRepositoryError {
    var signUpFailure: SocialSignUpFailure {
        switch self {
        case .requestFailed:
            .networkUnavailable
        case .invalidResponse, .configuration:
            .unknown
        case let .server(statusCode, _, _):
            switch statusCode {
            case 401:
                .reauthenticationRequired
            case 404:
                .signatureNotFound
            case 400:
                .invalidSignature
            default:
                .unknown
            }
        }
    }

    var isSignatureProcessingPending: Bool {
        if case let .server(_, errorCode, _) = self {
            return errorCode == Constants.signatureProcessingPending
        }
        return false
    }
}

private extension SignatureUploadError {
    var signUpFailure: SocialSignUpFailure {
        switch self {
        case .network:
            .networkUnavailable
        case .invalidUploadURL:
            .unknown
        case .rejected:
            .invalidSignature
        }
    }
}

private enum Constants {
    static let maxSignatureBytes = 1024 * 1024
    static let signUpAttempts = 10
    static let signUpRetryDelayMilliseconds: UInt64 = 1_000
    static let signatureProcessingPending = "SIGNATURE_PROCESSING_PENDING"
}

enum AuthRepositoryError: LocalizedError {
    case configuration
    case invalidResponse
    case requestFailed
    case server(statusCode: Int, errorCode: String?, message: String?)

    var errorDescription: String? {
        switch self {
        case .configuration:
            "API 설정을 확인해 주세요."
        case .invalidResponse:
            "로그인 응답을 확인할 수 없어요. 다시 시도해 주세요."
        case .requestFailed:
            "네트워크 연결을 확인해 주세요."
        case let .server(_, _, message):
            message?.isEmpty == false ? message : "로그인 요청에 실패했어요. 다시 시도해 주세요."
        }
    }
}

enum KeychainSessionStore {
    private static let service = "stonefive.chalkak"
    private static let userIDAccount = "user-id"
    private static let accessTokenAccount = "access-token"
    private static let expiresAtAccount = "expires-at-epoch-seconds"
    private static let refreshTokenAccount = "refresh-token"
    private static let refreshTokenExpiresAtAccount = "refresh-token-expires-at-epoch-seconds"
    private static let guestAccessKey = "stonefive.chalkak.guest-access"

    static func save(
        userID: String,
        accessToken: String,
        expiresIn: Int,
        refreshToken: String,
        refreshTokenExpiresIn: Int
    ) throws {
        guard !userID.isEmpty,
              !accessToken.isEmpty,
              expiresIn > 0,
              !refreshToken.isEmpty,
              refreshTokenExpiresIn > 0 else {
            throw AuthRepositoryError.invalidResponse
        }

        let now = Int64(Date().timeIntervalSince1970)
        let (expiresAt, accessTokenExpiryOverflow) = now.addingReportingOverflow(Int64(expiresIn))
        let (refreshTokenExpiresAt, refreshTokenExpiryOverflow) = now.addingReportingOverflow(
            Int64(refreshTokenExpiresIn)
        )
        guard !accessTokenExpiryOverflow, !refreshTokenExpiryOverflow else {
            throw AuthRepositoryError.invalidResponse
        }

        clearKeychainCredentials()
        try save(value: userID, account: userIDAccount)
        try save(value: accessToken, account: accessTokenAccount)
        try save(value: String(expiresAt), account: expiresAtAccount)
        try save(value: refreshToken, account: refreshTokenAccount)
        try save(value: String(refreshTokenExpiresAt), account: refreshTokenExpiresAtAccount)
        UserDefaults.standard.removeObject(forKey: guestAccessKey)
    }

    fileprivate static func save(credentials: LoginCredentials) throws {
        try save(
            userID: credentials.userID,
            accessToken: credentials.accessToken,
            expiresIn: credentials.expiresIn,
            refreshToken: credentials.refreshToken,
            refreshTokenExpiresIn: credentials.refreshTokenExpiresIn
        )
    }

    static func saveGuestAccess() {
        clearKeychainCredentials()
        UserDefaults.standard.set(true, forKey: guestAccessKey)
    }

    static func accessToken() -> String? {
        authenticatedSession()?.accessToken
    }

    static func userID() -> String? {
        authenticatedSession()?.userID
    }

    static func refreshToken() -> String? {
        guard let refreshToken = read(account: refreshTokenAccount),
              let expiresAtString = read(account: refreshTokenExpiresAtAccount),
              let expiresAt = Int64(expiresAtString),
              !refreshToken.isEmpty,
              expiresAt > Int64(Date().timeIntervalSince1970) else {
            return nil
        }
        return refreshToken
    }

    static func hasActiveSession() -> Bool {
        hasAuthenticatedSession() || UserDefaults.standard.bool(forKey: guestAccessKey)
    }

    static func hasAuthenticatedSession() -> Bool {
        guard let userID = read(account: userIDAccount),
              !userID.isEmpty else {
            return false
        }
        return refreshToken() != nil
    }

    static func delete() {
        clearKeychainCredentials()
        UserDefaults.standard.removeObject(forKey: guestAccessKey)
    }

    private static func save(value: String, account: String) throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
        ]

        SecItemDelete(query as CFDictionary)
        var item = query
        item[kSecValueData] = Data(value.utf8)
        guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else {
            throw AuthRepositoryError.requestFailed
        }
    }

    private static func read(account: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne,
        ]

        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    private static func authenticatedSession() -> StoredAuthSession? {
        guard let userID = read(account: userIDAccount),
              let accessToken = read(account: accessTokenAccount),
              let refreshToken = read(account: refreshTokenAccount),
              let refreshTokenExpiresAtString = read(account: refreshTokenExpiresAtAccount),
              let refreshTokenExpiresAt = Int64(refreshTokenExpiresAtString),
              !userID.isEmpty,
              !accessToken.isEmpty,
              !refreshToken.isEmpty,
              refreshTokenExpiresAt > Int64(Date().timeIntervalSince1970) else {
            return nil
        }

        return StoredAuthSession(
            userID: userID,
            accessToken: accessToken,
            refreshTokenExpiresAt: refreshTokenExpiresAt
        )
    }

    static func rotateTokens(_ tokens: RefreshedTokens) throws {
        guard let userID = read(account: userIDAccount), !userID.isEmpty else {
            throw AuthRepositoryError.invalidResponse
        }
        try save(
            userID: userID,
            accessToken: tokens.accessToken,
            expiresIn: tokens.expiresIn,
            refreshToken: tokens.refreshToken,
            refreshTokenExpiresIn: tokens.refreshTokenExpiresIn
        )
    }

    private static func clearKeychainCredentials() {
        for account in [
            userIDAccount,
            accessTokenAccount,
            expiresAtAccount,
            refreshTokenAccount,
            refreshTokenExpiresAtAccount,
        ] {
            let query: [CFString: Any] = [
                kSecClass: kSecClassGenericPassword,
                kSecAttrService: service,
                kSecAttrAccount: account,
            ]
            SecItemDelete(query as CFDictionary)
        }
    }
}

private struct StoredAuthSession {
    let userID: String
    let accessToken: String
    let refreshTokenExpiresAt: Int64
}
