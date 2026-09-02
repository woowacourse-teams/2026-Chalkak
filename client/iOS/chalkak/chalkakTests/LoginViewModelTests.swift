import Testing
@testable import chalkak

@MainActor
struct LoginViewModelTests {
    @Test("Google ID 토큰을 백엔드 로그인 요청으로 전달한다")
    func loginForwardsProviderAndToken() async {
        let repository = MockAuthRepository(loginResult: .authenticated(userID: "user-1"))
        let viewModel = LoginViewModel(
            authRepository: repository,
            googleLoginClient: MockSocialLoginClient(token: "google-token"),
            kakaoLoginClient: MockSocialLoginClient(token: "kakao-token")
        )

        viewModel.login(provider: .google)
        await waitUntil { repository.loginProvider != nil }

        #expect(repository.loginProvider == .google)
        #expect(repository.loginToken == "google-token")
        #expect(viewModel.state.status == .authenticated)
    }

    @Test("신규 사용자는 회원가입 필요 상태가 된다")
    func signupRequiredIsPublished() async {
        let repository = MockAuthRepository(loginResult: .signUpRequired)
        let viewModel = LoginViewModel(
            authRepository: repository,
            googleLoginClient: MockSocialLoginClient(token: "google-token"),
            kakaoLoginClient: MockSocialLoginClient(token: "kakao-token")
        )

        viewModel.login(provider: .kakao)
        await waitUntil {
            if case .signUpRequired = viewModel.state.status { return true }
            return false
        }

        #expect(viewModel.state.status == .signUpRequired)
    }

    @Test("게스트 계속하기가 게스트 접근 허용 상태가 된다")
    func guestAccessIsPublished() async {
        let repository = MockAuthRepository(loginResult: .authenticated(userID: "unused"))
        let viewModel = LoginViewModel(
            authRepository: repository,
            googleLoginClient: MockSocialLoginClient(token: "google-token"),
            kakaoLoginClient: MockSocialLoginClient(token: "kakao-token")
        )

        viewModel.continueAsGuest()
        await waitUntil { repository.didContinueAsGuest }

        #expect(repository.didContinueAsGuest)
        #expect(viewModel.state.status == .guestAccessGranted)
    }

    private func waitUntil(
        _ condition: @escaping @MainActor () -> Bool
    ) async {
        for _ in 0..<100 {
            if condition() { return }
            await Task.yield()
        }
    }
}

@MainActor
private final class MockSocialLoginClient: SocialLoginClient {
    let token: String

    init(token: String) {
        self.token = token
    }

    func idToken() async throws -> String {
        token
    }
}

private final class MockAuthRepository: AuthRepository {
    let loginResult: SocialLoginResult
    private(set) var loginProvider: SocialLoginProvider?
    private(set) var loginToken: String?
    private(set) var didContinueAsGuest = false

    init(loginResult: SocialLoginResult) {
        self.loginResult = loginResult
    }

    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult {
        loginProvider = provider
        loginToken = idToken
        return loginResult
    }

    func continueAsGuest() async throws {
        didContinueAsGuest = true
    }
}
