import Foundation
import Combine

enum SocialLoginResult: Equatable {
    case authenticated(userID: String)
    case signUpRequired
}

protocol AuthRepository {
    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult
    func continueAsGuest() async throws
}

@MainActor
protocol SocialLoginClient {
    func idToken() async throws -> String
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published private(set) var state = LoginViewState()

    private let authRepository: AuthRepository
    private let socialLoginClients: [SocialLoginProvider: any SocialLoginClient]
    private var loginTask: Task<Void, Never>?

    init(
        authRepository: AuthRepository,
        googleLoginClient: any SocialLoginClient,
        kakaoLoginClient: any SocialLoginClient,
        appleLoginClient: (any SocialLoginClient)? = nil
    ) {
        self.authRepository = authRepository
        var clients: [SocialLoginProvider: any SocialLoginClient] = [
            .google: googleLoginClient,
            .kakao: kakaoLoginClient,
        ]
        if let appleLoginClient {
            clients[.apple] = appleLoginClient
        }
        socialLoginClients = clients
    }

    deinit {
        loginTask?.cancel()
    }

    func login(provider: SocialLoginProvider) {
        guard state.canSubmit, let client = socialLoginClients[provider] else { return }

        state = LoginViewState(status: .loading(provider))
        loginTask = Task { [weak self] in
            do {
                let idToken = try await client.idToken()
                let result = try await self?.authRepository.login(provider: provider, idToken: idToken)
                guard let result else { return }
                guard let self else { return }
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
