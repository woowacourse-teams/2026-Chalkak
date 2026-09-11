package com.stonefive.chalkak.feature.login

import com.stonefive.chalkak.core.auth.GoogleCredentialFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleLoginActionTest {
    @Test
    fun `Google credential failure is converted to a user message`() {
        val expectedMessages = mapOf(
            GoogleCredentialFailure.NO_CREDENTIAL to "사용 가능한 Google 계정이 없어요.",
            GoogleCredentialFailure.INTERRUPTED to "Google 로그인이 중단됐어요. 다시 시도해 주세요.",
            GoogleCredentialFailure.CONFIGURATION to "Google 로그인 설정을 확인해 주세요.",
            GoogleCredentialFailure.UNSUPPORTED to "이 기기에서는 Google 로그인을 사용할 수 없어요.",
            GoogleCredentialFailure.UNEXPECTED_CREDENTIAL to "Google 로그인에 실패했어요. 다시 시도해 주세요.",
            GoogleCredentialFailure.INVALID_CREDENTIAL to "Google 로그인에 실패했어요. 다시 시도해 주세요.",
            GoogleCredentialFailure.UNKNOWN to "Google 로그인에 실패했어요. 다시 시도해 주세요.",
        )

        expectedMessages.forEach { (failure, expectedMessage) ->
            assertEquals(expectedMessage, failure.toUserMessage())
        }
    }
}
