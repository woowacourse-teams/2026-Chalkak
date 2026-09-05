import AuthenticationServices
import CryptoKit
import Security
import UIKit

@MainActor
final class AppleLoginClient: NSObject, AppleLoginCredentialClient {
    private var authorizationController: ASAuthorizationController?
    private var continuation: CheckedContinuation<AppleLoginCredential, Error>?

    func credential() async throws -> AppleLoginCredential {
        guard continuation == nil else {
            throw SocialLoginError.failed
        }
        guard UIApplication.shared.chalkakKeyWindow != nil else {
            throw SocialLoginError.configuration
        }
        let rawNonce = try Self.makeRawNonce()

        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                guard !Task.isCancelled else {
                    continuation.resume(throwing: CancellationError())
                    return
                }
                self.continuation = continuation

                let request = ASAuthorizationAppleIDProvider().createRequest()
                request.requestedScopes = [.fullName, .email]
                request.nonce = Self.sha256(rawNonce)

                let controller = ASAuthorizationController(authorizationRequests: [request])
                controller.delegate = self
                controller.presentationContextProvider = self
                authorizationController = controller
                pendingRawNonce = rawNonce
                controller.performRequests()
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                guard let self, continuation != nil else { return }
                authorizationController?.cancel()
                finish(.failure(CancellationError()))
            }
        }
    }

    private var pendingRawNonce: String?

    private func finish(_ result: Result<AppleLoginCredential, Error>) {
        authorizationController = nil
        pendingRawNonce = nil
        let continuation = continuation
        self.continuation = nil

        switch result {
        case let .success(token):
            continuation?.resume(returning: token)
        case let .failure(error):
            continuation?.resume(throwing: error)
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

extension AppleLoginClient: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityToken = credential.identityToken,
              let idToken = String(data: identityToken, encoding: .utf8),
              !idToken.isEmpty,
              let authorizationCodeData = credential.authorizationCode,
              let authorizationCode = String(data: authorizationCodeData, encoding: .utf8),
              !authorizationCode.isEmpty,
              let rawNonce = pendingRawNonce else {
            finish(.failure(SocialLoginError.invalidToken))
            return
        }

        finish(
            .success(
                AppleLoginCredential(
                    idToken: idToken,
                    authorizationCode: authorizationCode,
                    rawNonce: rawNonce
                )
            )
        )
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            finish(.failure(SocialLoginError.cancelled))
        } else {
            finish(.failure(SocialLoginError.failed))
        }
    }
}

extension AppleLoginClient: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let window = UIApplication.shared.chalkakKeyWindow else {
            preconditionFailure("Unable to find a presentation window for Sign in with Apple")
        }
        return window
    }
}

private extension UIApplication {
    var chalkakKeyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })?
            .windows
            .first(where: \.isKeyWindow)
    }
}
