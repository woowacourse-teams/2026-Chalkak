package com.stonefive.chalkak.feature.terms

sealed interface TermsUiAction {
    data object AllConsentClicked : TermsUiAction

    data object ServiceTermsClicked : TermsUiAction

    data object PrivacyPolicyClicked : TermsUiAction
}
