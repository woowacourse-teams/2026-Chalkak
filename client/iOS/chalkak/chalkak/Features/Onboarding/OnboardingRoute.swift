import SwiftUI

struct OnboardingRoute: View {
    @State private var step: OnboardingStep
    @State private var signatureStrokes: [OnboardingSignatureStroke]
    @StateObject private var signUpViewModel: SignUpViewModel

    let onFinish: () -> Void
    let onReauthenticationRequired: () -> Void
    let onServiceTermsView: () -> Void
    let onPrivacyPolicyView: () -> Void

    init(
        authRepository: AuthRepository,
        onFinish: @escaping () -> Void,
        onReauthenticationRequired: @escaping () -> Void = {},
        onServiceTermsView: @escaping () -> Void = {},
        onPrivacyPolicyView: @escaping () -> Void = {}
    ) {
        _signUpViewModel = StateObject(
            wrappedValue: SignUpViewModel(authRepository: authRepository)
        )
        self.onFinish = onFinish
        self.onReauthenticationRequired = onReauthenticationRequired
        self.onServiceTermsView = onServiceTermsView
        self.onPrivacyPolicyView = onPrivacyPolicyView

        var initialStep = OnboardingStep.terms
        var initialStrokes: [OnboardingSignatureStroke] = []
#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-show-onboarding-signature") {
            initialStep = .signature
        } else if arguments.contains("-show-onboarding-preview") {
            initialStep = .preview
            initialStrokes = Self.previewStrokes
        }
#endif
        _step = State(initialValue: initialStep)
        _signatureStrokes = State(initialValue: initialStrokes)
    }

    var body: some View {
        Group {
            switch step {
            case .terms:
                OnboardingTermsScreen(
                    onNext: { step = .signature },
                    onServiceTermsView: onServiceTermsView,
                    onPrivacyPolicyView: onPrivacyPolicyView
                )
            case .signature:
                OnboardingSignatureScreen(
                    strokes: $signatureStrokes,
                    onSubmit: { step = .preview }
                )
            case .preview:
                OnboardingSignaturePreviewScreen(
                    strokes: signatureStrokes,
                    onRedraw: { step = .signature },
                    onStart: {
                        signUpViewModel.completeSignUp(strokes: signatureStrokes)
                    },
                    isSubmitting: signUpViewModel.state.isSubmitting
                )
            }
        }
        .animation(.default, value: step)
        .alert(
            "회원가입",
            isPresented: Binding(
                get: { signUpViewModel.state.errorMessage != nil },
                set: { if !$0 { signUpViewModel.dismissError() } }
            ),
            presenting: signUpViewModel.state.errorMessage
        ) { _ in
            Button("확인", action: signUpViewModel.dismissError)
        } message: { message in
            Text(message)
        }
        .onChange(of: signUpViewModel.state.status) { _, status in
            switch status {
            case .completed:
                onFinish()
            case .reauthenticationRequired:
                onReauthenticationRequired()
            case .idle, .submitting:
                break
            }
        }
    }

    private static let previewStrokes = [
        OnboardingSignatureStroke(points: [
            OnboardingSignaturePoint(xRatio: 0.12, yRatio: 0.64),
            OnboardingSignaturePoint(xRatio: 0.24, yRatio: 0.52),
            OnboardingSignaturePoint(xRatio: 0.38, yRatio: 0.68),
            OnboardingSignaturePoint(xRatio: 0.55, yRatio: 0.48),
            OnboardingSignaturePoint(xRatio: 0.78, yRatio: 0.56),
        ]),
    ]
}

private enum OnboardingStep: Equatable {
    case terms
    case signature
    case preview
}

#Preview("Onboarding route") {
    OnboardingRoute(
        authRepository: APIAuthRepository(baseURL: AppConfiguration().apiBaseURL),
        onFinish: {}
    )
        .chalkakTheme(.light)
}
