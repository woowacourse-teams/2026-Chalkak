import SwiftUI

struct LoginView: View {
    @StateObject private var viewModel: LoginViewModel

    let onAuthenticated: () -> Void
    let onGuestAccessGranted: () -> Void
    let onSignUpRequired: () -> Void

    init(
        configuration: AppConfiguration = AppConfiguration(),
        authRepository: AuthRepository? = nil,
        onAuthenticated: @escaping () -> Void = {},
        onGuestAccessGranted: @escaping () -> Void = {},
        onSignUpRequired: @escaping () -> Void = {}
    ) {
        _viewModel = StateObject(
            wrappedValue: LoginViewModel(
                authRepository: authRepository
                    ?? APIAuthRepository(baseURL: configuration.apiBaseURL),
                googleLoginClient: GoogleLoginClient(
                    clientID: configuration.googleClientID,
                    serverClientID: configuration.googleServerClientID
                ),
                kakaoLoginClient: KakaoLoginClient(
                    nativeAppKey: configuration.kakaoNativeAppKey
                ),
                appleLoginClient: AppleLoginClient()
            )
        )
        self.onAuthenticated = onAuthenticated
        self.onGuestAccessGranted = onGuestAccessGranted
        self.onSignUpRequired = onSignUpRequired
    }

    var body: some View {
        LoginScreen(
            onSocialLogin: viewModel.login,
            onContinueAsGuest: viewModel.continueAsGuest,
            isEnabled: viewModel.state.canSubmit
        )
        .alert(
            "로그인",
            isPresented: Binding(
                get: { viewModel.state.errorMessage != nil },
                set: { if !$0 { viewModel.dismissError() } }
            ),
            presenting: viewModel.state.errorMessage
        ) { _ in
            Button("확인", action: viewModel.dismissError)
        } message: { message in
            Text(message)
        }
        .onChange(of: viewModel.state.status) { _, status in
            switch status {
            case .authenticated:
                onAuthenticated()
            case .guestAccessGranted:
                onGuestAccessGranted()
            case .signUpRequired:
                onSignUpRequired()
            case .idle, .loading:
                break
            }
        }
    }
}

#Preview("Login route") {
    LoginView()
        .chalkakTheme(.light)
}
