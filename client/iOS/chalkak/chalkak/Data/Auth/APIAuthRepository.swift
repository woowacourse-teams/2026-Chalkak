import Foundation
import OSLog
import Security

final class APIAuthRepository: AuthRepository {
    private let baseURL: URL?
    private let session: URLSession
    private let decoder = JSONDecoder()
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "AuthAPI"
    )

    init(baseURL: URL?, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult {
        guard let baseURL else {
            logger.error("Social login skipped because API_BASE_URL is missing")
            throw AuthRepositoryError.configuration
        }

        let endpoint = baseURL.appendingPathComponent("auth/social-login")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(
            SocialLoginRequest(provider: provider.rawValue, idToken: idToken)
        )

        logger.debug(
            "Social login request provider=\(provider.rawValue, privacy: .public), url=\(endpoint.absoluteString, privacy: .public), idTokenLength=\(idToken.count, privacy: .public)"
        )

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            logger.error(
                "Social login transport error: \(String(describing: error), privacy: .public)"
            )
            throw AuthRepositoryError.requestFailed
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            logger.error("Social login returned a non-HTTP response")
            throw AuthRepositoryError.requestFailed
        }

        guard 200..<300 ~= httpResponse.statusCode else {
            let serverError = try? decoder.decode(APIErrorResponse.self, from: data)
            logger.error(
                "Social login response status=\(httpResponse.statusCode, privacy: .public), errorCode=\(serverError?.errorCode ?? "unknown", privacy: .public), message=\(serverError?.message ?? "unknown", privacy: .public)"
            )
            throw AuthRepositoryError.server(
                statusCode: httpResponse.statusCode,
                errorCode: serverError?.errorCode,
                message: serverError?.message
            )
        }

        let loginResponse = try decoder.decode(SocialLoginResponse.self, from: data)
        logger.debug(
            "Social login response status=\(httpResponse.statusCode, privacy: .public), result=\(loginResponse.status, privacy: .public)"
        )
        switch loginResponse.status {
        case "LOGIN_SUCCESS":
            guard let userID = loginResponse.userID,
                  let accessToken = loginResponse.accessToken,
                  let expiresIn = loginResponse.expiresIn,
                  !userID.isEmpty,
                  !accessToken.isEmpty,
                  expiresIn > 0 else {
                throw AuthRepositoryError.invalidResponse
            }
            try KeychainSessionStore.save(
                userID: userID,
                accessToken: accessToken,
                expiresIn: expiresIn
            )
            return .authenticated(userID: userID)
        case "SIGN_UP_REQUIRED":
            return .signUpRequired
        default:
            throw AuthRepositoryError.invalidResponse
        }
    }

    func continueAsGuest() async throws {
        KeychainSessionStore.saveGuestAccess()
    }
}

private struct SocialLoginRequest: Encodable {
    let provider: String
    let idToken: String
}

private struct SocialLoginResponse: Decodable {
    let status: String
    let userID: String?
    let accessToken: String?
    let expiresIn: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case userID = "userId"
        case accessToken
        case expiresIn
    }
}

private struct APIErrorResponse: Decodable {
    let errorCode: String?
    let message: String?
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
    private static let guestAccessKey = "stonefive.chalkak.guest-access"

    static func save(userID: String, accessToken: String, expiresIn: Int) throws {
        guard !userID.isEmpty, !accessToken.isEmpty, expiresIn > 0 else {
            throw AuthRepositoryError.invalidResponse
        }

        let (expiresAt, overflow) = Int64(Date().timeIntervalSince1970)
            .addingReportingOverflow(Int64(expiresIn))
        guard !overflow else {
            throw AuthRepositoryError.invalidResponse
        }

        clearKeychainCredentials()
        try save(value: userID, account: userIDAccount)
        try save(value: accessToken, account: accessTokenAccount)
        try save(value: String(expiresAt), account: expiresAtAccount)
        UserDefaults.standard.removeObject(forKey: guestAccessKey)
    }

    static func saveGuestAccess() {
        clearKeychainCredentials()
        UserDefaults.standard.set(true, forKey: guestAccessKey)
    }

    static func accessToken() -> String? {
        validSession()?.accessToken
    }

    static func userID() -> String? {
        validSession()?.userID
    }

    static func hasActiveSession() -> Bool {
        validSession() != nil || UserDefaults.standard.bool(forKey: guestAccessKey)
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

    private static func validSession() -> StoredAuthSession? {
        guard let userID = read(account: userIDAccount),
              let accessToken = read(account: accessTokenAccount),
              let expiresAtString = read(account: expiresAtAccount),
              let expiresAt = Int64(expiresAtString),
              !userID.isEmpty,
              !accessToken.isEmpty,
              expiresAt > Int64(Date().timeIntervalSince1970) else {
            return nil
        }

        return StoredAuthSession(
            userID: userID,
            accessToken: accessToken,
            expiresAt: expiresAt
        )
    }

    private static func clearKeychainCredentials() {
        for account in [userIDAccount, accessTokenAccount, expiresAtAccount] {
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
    let expiresAt: Int64
}
