import CryptoKit
import Foundation
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser
import Security

@MainActor
final class KakaoLoginClient: SocialLoginClient {
    private let nativeAppKey: String?

    init(nativeAppKey: String?) {
        self.nativeAppKey = nativeAppKey
    }

    func credential() async throws -> SocialLoginCredential {
        guard nativeAppKey?.isEmpty == false else {
            throw SocialLoginError.configuration
        }
        let rawNonce = try Self.makeRawNonce()
        let hashedNonce = Self.sha256(rawNonce)

        return try await withCheckedThrowingContinuation { continuation in
            let completion: (OAuthToken?, Error?) -> Void = { token, error in
                if let sdkError = error as? SdkError,
                   case .ClientFailed(.Cancelled, _) = sdkError {
                    continuation.resume(throwing: SocialLoginError.cancelled)
                    return
                }

                if error != nil {
                    continuation.resume(throwing: SocialLoginError.failed)
                    return
                }

                guard let idToken = token?.idToken, !idToken.isEmpty else {
                    continuation.resume(throwing: SocialLoginError.invalidToken)
                    return
                }
                continuation.resume(
                    returning: SocialLoginCredential(idToken: idToken, rawNonce: rawNonce)
                )
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(nonce: hashedNonce, completion: completion)
            } else {
                UserApi.shared.loginWithKakaoAccount(nonce: hashedNonce, completion: completion)
            }
        }
    }

    private static func makeRawNonce(byteCount: Int = 32) throws -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, byteCount, buffer.baseAddress!)
        }
        guard status == errSecSuccess else {
            throw SocialLoginError.failed
        }
        return Data(bytes)
            .base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }
}

enum SocialLoginError: LocalizedError {
    case cancelled
    case configuration
    case failed
    case invalidToken

    var errorDescription: String? {
        switch self {
        case .cancelled:
            ""
        case .configuration:
            "로그인 설정을 확인해 주세요."
        case .failed, .invalidToken:
            "로그인하지 못했어요. 다시 시도해 주세요."
        }
    }
}
