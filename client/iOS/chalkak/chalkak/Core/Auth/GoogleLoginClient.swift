import GoogleSignIn
import OSLog
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

    func idToken() async throws -> String {
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

        do {
            logger.debug("Google sign-in started")
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presentingViewController
            )

            guard let idToken = result.user.idToken?.tokenString, !idToken.isEmpty else {
                logger.error("Google sign-in returned an empty ID token")
                throw SocialLoginError.invalidToken
            }

            logger.debug("Google ID token received, length=\(idToken.count, privacy: .public)")
            return idToken
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            logger.error("Google sign-in failed: \(String(describing: error), privacy: .public)")
            throw error
        }
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
