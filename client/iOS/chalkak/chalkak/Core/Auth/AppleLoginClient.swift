import AuthenticationServices
import UIKit

@MainActor
final class AppleLoginClient: NSObject, SocialLoginClient {
    private var authorizationController: ASAuthorizationController?
    private var continuation: CheckedContinuation<String, Error>?

    func idToken() async throws -> String {
        guard UIApplication.shared.chalkakKeyWindow != nil else {
            throw SocialLoginError.configuration
        }

        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation

            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            authorizationController = controller
            controller.performRequests()
        }
    }

    private func finish(_ result: Result<String, Error>) {
        authorizationController = nil
        let continuation = continuation
        self.continuation = nil

        switch result {
        case let .success(token):
            continuation?.resume(returning: token)
        case let .failure(error):
            continuation?.resume(throwing: error)
        }
    }
}

extension AppleLoginClient: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityToken = credential.identityToken,
              let token = String(data: identityToken, encoding: .utf8),
              !token.isEmpty else {
            finish(.failure(SocialLoginError.invalidToken))
            return
        }

        finish(.success(token))
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
