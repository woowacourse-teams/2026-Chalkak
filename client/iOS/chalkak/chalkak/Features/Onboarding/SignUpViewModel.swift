import Combine
import Foundation

enum SignUpStatus: Equatable {
    case idle
    case submitting
    case completed
    case reauthenticationRequired
}

struct SignUpViewState: Equatable {
    var status: SignUpStatus = .idle
    var errorMessage: String?

    var isSubmitting: Bool {
        status == .submitting
    }
}

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published private(set) var state = SignUpViewState()

    private let authRepository: AuthRepository
    private let pngEncoder: any OnboardingSignaturePngEncoder
    private var signUpTask: Task<Void, Never>?

    init(
        authRepository: AuthRepository,
        pngEncoder: (any OnboardingSignaturePngEncoder)? = nil
    ) {
        self.authRepository = authRepository
        self.pngEncoder = pngEncoder ?? DefaultOnboardingSignaturePngEncoder()
    }

    deinit {
        signUpTask?.cancel()
    }

    func completeSignUp(strokes: [OnboardingSignatureStroke]) {
        guard !state.isSubmitting,
              strokes.contains(where: { !$0.isEmpty }) else { return }

        state = SignUpViewState(status: .submitting)
        signUpTask = Task { [weak self] in
            guard let self else { return }

            do {
                let signaturePNG = try pngEncoder.encode(strokes)
                let result = try await authRepository.completeSocialSignUp(
                    signaturePNG: signaturePNG
                )
                switch result {
                case .success:
                    state = SignUpViewState(status: .completed)
                case let .failure(failure):
                    handle(failure)
                }
            } catch is CancellationError {
                state = SignUpViewState()
            } catch {
                state = SignUpViewState(
                    errorMessage: "회원가입을 완료하지 못했어요. 다시 시도해 주세요."
                )
            }
        }
    }

    func dismissError() {
        state.errorMessage = nil
    }

    private func handle(_ failure: SocialSignUpFailure) {
        switch failure {
        case .reauthenticationRequired, .missingLoginContext:
            state = SignUpViewState(status: .reauthenticationRequired)
        default:
            state = SignUpViewState(errorMessage: failure.message)
        }
    }
}

private extension SocialSignUpFailure {
    var message: String {
        switch self {
        case .signatureTooLarge:
            "사인 이미지가 1MB를 초과했어요."
        case .signatureProcessingTimeout:
            "사인 처리에 시간이 걸리고 있어요. 다시 시도해 주세요."
        case .signatureNotFound:
            "사인을 다시 업로드해 주세요."
        case .invalidSignature:
            "사용할 수 없는 사인이에요. 다시 그려주세요."
        case .networkUnavailable:
            "네트워크 연결을 확인해 주세요."
        case .unknown:
            "회원가입을 완료하지 못했어요. 다시 시도해 주세요."
        case .missingLoginContext, .reauthenticationRequired:
            "로그인 정보가 만료되었어요. 다시 로그인해 주세요."
        }
    }
}
