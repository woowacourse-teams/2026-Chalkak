package com.stonefive.chalkak.feature.terms

data class TermsUiState(
    val serviceTermsAgreed: Boolean = false,
    val privacyPolicyAgreed: Boolean = false,
) {
    val isAllAgreed: Boolean
        get() = serviceTermsAgreed && privacyPolicyAgreed
}
