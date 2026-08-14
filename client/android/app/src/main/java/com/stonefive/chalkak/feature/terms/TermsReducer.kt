package com.stonefive.chalkak.feature.terms

internal fun TermsUiState.reduce(action: TermsUiAction): TermsUiState = when (action) {
    TermsUiAction.AllConsentClicked -> copy(
        serviceTermsAgreed = !isAllAgreed,
        privacyPolicyAgreed = !isAllAgreed,
    )

    TermsUiAction.ServiceTermsClicked -> copy(
        serviceTermsAgreed = !serviceTermsAgreed,
    )

    TermsUiAction.PrivacyPolicyClicked -> copy(
        privacyPolicyAgreed = !privacyPolicyAgreed,
    )
}
