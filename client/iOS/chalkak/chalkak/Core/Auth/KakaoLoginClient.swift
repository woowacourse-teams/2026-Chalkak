import Foundation
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser

@MainActor
final class KakaoLoginClient: SocialLoginClient {
    private let nativeAppKey: String?

    init(nativeAppKey: String?) {
        self.nativeAppKey = nativeAppKey
    }

    func idToken() async throws -> String {
        guard nativeAppKey != nil else { throw SocialLoginError.configuration }

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
                continuation.resume(returning: idToken)
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(completion: completion)
            } else {
                UserApi.shared.loginWithKakaoAccount(completion: completion)
            }
        }
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
