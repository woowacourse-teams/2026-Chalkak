import CoreGraphics
import Foundation
import Testing
@testable import chalkak

struct OnboardingTermsStateTests {
    @Test("전체 동의는 필수 약관을 모두 선택한다")
    func allConsentSelectsRequiredTerms() {
        var state = OnboardingTermsState()

        state.toggleAllConsent()

        #expect(state.isServiceTermsAgreed)
        #expect(state.isPrivacyPolicyAgreed)
        #expect(state.isAllAgreed)
    }

    @Test("전체 동의가 선택된 상태에서 다시 누르면 필수 약관을 모두 해제한다")
    func allConsentDeselectsRequiredTermsWhenAlreadySelected() {
        var state = OnboardingTermsState(
            isServiceTermsAgreed: true,
            isPrivacyPolicyAgreed: true
        )

        state.toggleAllConsent()

        #expect(!state.isServiceTermsAgreed)
        #expect(!state.isPrivacyPolicyAgreed)
        #expect(!state.isAllAgreed)
    }

    @Test("필수 약관 중 하나라도 해제되어 있으면 다음으로 진행할 수 없다")
    func doesNotAllowNextWhenAnyRequiredTermIsMissing() {
        var state = OnboardingTermsState()

        state.toggleServiceTerms()

        #expect(!state.isAllAgreed)
    }
}

struct OnboardingSignatureModelTests {
    @Test("사인 좌표는 캔버스 영역 안으로 보정된다")
    func clampsSignaturePointIntoCanvasBounds() {
        let point = OnboardingSignaturePoint(xRatio: -0.2, yRatio: 1.4)

        #expect(point.xRatio == 0)
        #expect(point.yRatio == 1)
    }

    @Test("비어 있는 획은 사인이 있는 상태로 취급하지 않는다")
    func emptyStrokeDoesNotCountAsSignature() {
        let stroke = OnboardingSignatureStroke()

        #expect(stroke.isEmpty)
    }

    @Test("포인트가 있는 획은 사인이 있는 상태로 취급한다")
    func strokeWithPointCountsAsSignature() {
        let stroke = OnboardingSignatureStroke(
            points: [OnboardingSignaturePoint(xRatio: 0.5, yRatio: 0.5)]
        )

        #expect(!stroke.isEmpty)
    }
}

@MainActor
struct SignUpViewModelTests {
    @Test("회원가입 중 화면이 사라지면 작업을 취소하고 뷰 모델을 해제한다")
    func cancelsSignUpWhenViewModelIsReleased() async {
        let repository = SuspendingSignUpAuthRepository()
        weak var releasedViewModel: SignUpViewModel?
        var viewModel: SignUpViewModel? = SignUpViewModel(
            authRepository: repository,
            pngEncoder: StubSignaturePngEncoder()
        )
        releasedViewModel = viewModel

        viewModel?.completeSignUp(strokes: [
            OnboardingSignatureStroke(
                points: [OnboardingSignaturePoint(xRatio: 0.5, yRatio: 0.5)]
            )
        ])
        await waitUntil { repository.didStartSignUp }

        viewModel = nil
        await waitUntil { releasedViewModel == nil && repository.wasCancelled }

        #expect(releasedViewModel == nil)
        #expect(repository.wasCancelled)
    }

    private func waitUntil(_ condition: @escaping @MainActor () -> Bool) async {
        for _ in 0..<100 {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
    }
}

private struct StubSignaturePngEncoder: OnboardingSignaturePngEncoder {
    func encode(_ strokes: [OnboardingSignatureStroke]) throws -> Data {
        Data([0x01])
    }
}

@MainActor
private final class SuspendingSignUpAuthRepository: AuthRepository {
    private(set) var didStartSignUp = false
    private(set) var wasCancelled = false

    func login(provider: SocialLoginProvider, idToken: String) async throws -> SocialLoginResult {
        .signUpRequired
    }

    func completeSocialSignUp(signaturePNG: Data) async throws -> SocialSignUpResult {
        didStartSignUp = true
        do {
            try await Task.sleep(nanoseconds: 60_000_000_000)
            return .failure(.unknown)
        } catch is CancellationError {
            wasCancelled = true
            throw CancellationError()
        }
    }

    func continueAsGuest() async throws {}
}
