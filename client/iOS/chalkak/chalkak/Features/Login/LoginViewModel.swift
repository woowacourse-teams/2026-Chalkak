import Foundation
import Combine

enum SocialLoginResult: Equatable {
    case authenticated(userID: String)
    case signUpRequired
}

enum SocialSignUpResult: Equatable {
    case success(userID: String)
    case failure(SocialSignUpFailure)
}

enum SocialSignUpFailure: Equatable {
    case missingLoginContext
    case signatureTooLarge
    case signatureProcessingTimeout
    case signatureNotFound
    case invalidSignature
    case reauthenticationRequired
    case networkUnavailable
    case unknown
}

@MainActor
protocol AuthRepository {
    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult
    func loginWithApple(credential: AppleLoginCredential) async throws -> SocialLoginResult
    func completeSocialSignUp(signaturePNG: Data) async throws -> SocialSignUpResult
    func continueAsGuest() async throws
    func logout() async
}

extension AuthRepository {
    func loginWithApple(credential: AppleLoginCredential) async throws -> SocialLoginResult {
        throw AuthRepositoryError.configuration
    }

    func logout() async {}
}

@MainActor
protocol SocialLoginClient {
    func idToken() async throws -> String
}

struct AppleLoginCredential: Equatable, Sendable {
    let idToken: String
    let authorizationCode: String
    let rawNonce: String
}

@MainActor
protocol AppleLoginCredentialClient {
    func credential() async throws -> AppleLoginCredential
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published private(set) var state = LoginViewState()

    private let authRepository: AuthRepository
    private let socialLoginClients: [SocialLoginProvider: any SocialLoginClient]
    private let appleLoginClient: (any AppleLoginCredentialClient)?
    private var loginTask: Task<Void, Never>?

    init(
        authRepository: AuthRepository,
        googleLoginClient: any SocialLoginClient,
        kakaoLoginClient: any SocialLoginClient,
        appleLoginClient: (any AppleLoginCredentialClient)? = nil
    ) {
        self.authRepository = authRepository
        let clients: [SocialLoginProvider: any SocialLoginClient] = [
            .google: googleLoginClient,
            .kakao: kakaoLoginClient,
        ]
        socialLoginClients = clients
        self.appleLoginClient = appleLoginClient
    }

    deinit {
        loginTask?.cancel()
    }

    func login(provider: SocialLoginProvider) {
        guard state.canSubmit else { return }
        guard provider == .apple ? appleLoginClient != nil : socialLoginClients[provider] != nil else {
            return
        }

        state = LoginViewState(status: .loading(provider))
        loginTask = Task { [weak self] in
            do {
                guard let self else { return }
                let result: SocialLoginResult
                if provider == .apple, let appleLoginClient {
                    let credential = try await appleLoginClient.credential()
                    result = try await authRepository.loginWithApple(credential: credential)
                } else if let client = socialLoginClients[provider] {
                    let idToken = try await client.idToken()
                    result = try await authRepository.login(provider: provider, idToken: idToken)
                } else {
                    return
                }
                switch result {
                case .authenticated:
                    state = LoginViewState(status: .authenticated)
                case .signUpRequired:
                    state = LoginViewState(status: .signUpRequired)
                }
            } catch let error as SocialLoginError where error == .cancelled {
                self?.state = LoginViewState()
            } catch is CancellationError {
                self?.state = LoginViewState()
            } catch {
                self?.state = LoginViewState(
                    errorMessage: error.localizedDescription.isEmpty
                        ? "로그인하지 못했어요. 다시 시도해 주세요."
                        : error.localizedDescription
                )
            }
        }
    }

    func continueAsGuest() {
        guard state.canSubmit else { return }

        state = LoginViewState(status: .loading(nil))
        loginTask = Task { [weak self] in
            do {
                try await self?.authRepository.continueAsGuest()
                self?.state = LoginViewState(status: .guestAccessGranted)
            } catch is CancellationError {
                self?.state = LoginViewState()
            } catch {
                self?.state = LoginViewState(
                    errorMessage: "화면을 불러오지 못했어요. 다시 시도해 주세요."
                )
            }
        }
    }

    func dismissError() {
        state.errorMessage = nil
    }
}
