package com.stonefive.chalkak.feature.terms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsReducerTest {
    @Test
    fun `전체 동의를 누르면 필수 약관을 모두 동의한다`() {
        val state = TermsUiState().reduce(TermsUiAction.AllConsentClicked)

        assertTrue(state.serviceTermsAgreed)
        assertTrue(state.privacyPolicyAgreed)
        assertTrue(state.isAllAgreed)
    }

    @Test
    fun `전체 동의 상태에서 다시 누르면 필수 약관을 모두 해제한다`() {
        val state = TermsUiState(
            serviceTermsAgreed = true,
            privacyPolicyAgreed = true,
        ).reduce(TermsUiAction.AllConsentClicked)

        assertFalse(state.serviceTermsAgreed)
        assertFalse(state.privacyPolicyAgreed)
        assertFalse(state.isAllAgreed)
    }

    @Test
    fun `필수 약관을 하나라도 동의하지 않으면 전체 동의가 아니다`() {
        val state = TermsUiState().reduce(TermsUiAction.ServiceTermsClicked)

        assertTrue(state.serviceTermsAgreed)
        assertFalse(state.privacyPolicyAgreed)
        assertFalse(state.isAllAgreed)
    }
}
