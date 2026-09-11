package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.core.auth.KakaoCredentialFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoLoginActionTest {
    @Test
    fun `configuration failure is converted to Kakao setup message`() {
        assertEquals(
            "카카오 로그인 설정을 확인해 주세요.",
            KakaoCredentialFailure.CONFIGURATION.toUserMessage(),
        )
    }

    @Test
    fun `login failure is converted to retryable Kakao message`() {
        assertEquals(
            "카카오 로그인에 실패했어요. 다시 시도해 주세요.",
            KakaoCredentialFailure.LOGIN_FAILED.toUserMessage(),
        )
    }

    @Test
    fun `unknown failure is converted to retryable Kakao message`() {
        assertEquals(
            "카카오 로그인에 실패했어요. 다시 시도해 주세요.",
            KakaoCredentialFailure.UNKNOWN.toUserMessage(),
        )
    }
}
