import Foundation

struct OnboardingTermsState: Equatable {
    var isServiceTermsAgreed = false
    var isPrivacyPolicyAgreed = false

    var isAllAgreed: Bool {
        isServiceTermsAgreed && isPrivacyPolicyAgreed
    }

    mutating func toggleAllConsent() {
        let nextValue = !isAllAgreed
        isServiceTermsAgreed = nextValue
        isPrivacyPolicyAgreed = nextValue
    }

    mutating func toggleServiceTerms() {
        isServiceTermsAgreed.toggle()
    }

    mutating func togglePrivacyPolicy() {
        isPrivacyPolicyAgreed.toggle()
    }
}
