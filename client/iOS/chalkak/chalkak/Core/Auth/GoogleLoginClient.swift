import CryptoKit
import Foundation
import GoogleSignIn
import OSLog
import Security
import UIKit

@MainActor
final class GoogleLoginClient: SocialLoginClient {
    private let clientID: String?
    private let serverClientID: String?
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "stonefive.chalkak",
        category: "GoogleSignIn"
    )

    init(clientID: String?, serverClientID: String?) {
        self.clientID = clientID
        self.serverClientID = serverClientID
    }

    func credential() async throws -> SocialLoginCredential {
        // Android requires the server client ID before requesting a credential.
        // Keep the same contract on iOS so the ID token audience is the backend's
        // OAuth client, not the iOS application client.
        guard let clientID, !clientID.isEmpty,
              let serverClientID, !serverClientID.isEmpty else {
            logger.error("Google sign-in configuration is incomplete")
            throw SocialLoginError.configuration
        }
        guard let presentingViewController = UIApplication.shared.chalkakPresentingViewController else {
            logger.error("Google sign-in presentation view controller is unavailable")
            throw SocialLoginError.configuration
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: clientID,
            serverClientID: serverClientID
        )
        let rawNonce = try Self.makeRawNonce()

        do {
            logger.debug("Google sign-in started")
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presentingViewController,
                hint: nil,
                additionalScopes: nil,
                nonce: Self.sha256(rawNonce)
            )

            guard let idToken = result.user.idToken?.tokenString, !idToken.isEmpty else {
                logger.error("Google sign-in returned an empty ID token")
                throw SocialLoginError.invalidToken
            }

            logger.debug("Google ID token received, length=\(idToken.count, privacy: .public)")
            return SocialLoginCredential(idToken: idToken, rawNonce: rawNonce)
        } catch let error as NSError where
            error.domain == kGIDSignInErrorDomain &&
            error.code == GIDSignInError.Code.canceled.rawValue {
            throw SocialLoginError.cancelled
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            logger.error("Google sign-in failed: \(String(describing: error), privacy: .public)")
            throw error
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

private extension UIApplication {
    var chalkakPresentingViewController: UIViewController? {
        let keyWindow = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })?
            .windows
            .first(where: \.isKeyWindow)

        var viewController = keyWindow?.rootViewController
        while let presentedViewController = viewController?.presentedViewController {
            viewController = presentedViewController
        }
        return viewController
    }
}
