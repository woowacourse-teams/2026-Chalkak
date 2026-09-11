package com.stonefive.chalkak.core.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoIdTokenClientTest {
    @Test
    fun `returns Talk id token when KakaoTalk login succeeds`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { true },
            loginWithKakaoTalk = { KakaoCredentialResult.Success("talk-id-token") },
            loginWithKakaoAccount = { error("Account fallback should not run") },
        )

        assertEquals(KakaoCredentialResult.Success("talk-id-token"), result)
    }

    @Test
    fun `does not fall back to account login when KakaoTalk login is cancelled`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { true },
            loginWithKakaoTalk = { KakaoCredentialResult.Cancelled },
            loginWithKakaoAccount = { error("Account fallback should not run") },
        )

        assertEquals(KakaoCredentialResult.Cancelled, result)
    }

    @Test
    fun `falls back to account login when KakaoTalk login fails`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { true },
            loginWithKakaoTalk = {
                KakaoCredentialResult.Failure(KakaoCredentialFailure.LOGIN_FAILED)
            },
            loginWithKakaoAccount = { KakaoCredentialResult.Success("account-id-token") },
        )

        assertEquals(KakaoCredentialResult.Success("account-id-token"), result)
    }

    @Test
    fun `uses account login when KakaoTalk is not available`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { false },
            loginWithKakaoTalk = { error("Talk login should not run") },
            loginWithKakaoAccount = { KakaoCredentialResult.Success("account-id-token") },
        )

        assertEquals(KakaoCredentialResult.Success("account-id-token"), result)
    }

    @Test
    fun `returns unknown failure when KakaoTalk availability check fails`() = runTest {
        var talkLoginRequests = 0
        var accountLoginRequests = 0

        val result = getIdToken(
            isKakaoTalkLoginAvailable = { error("Availability check failed") },
            loginWithKakaoTalk = {
                talkLoginRequests += 1
                KakaoCredentialResult.Success("talk-id-token")
            },
            loginWithKakaoAccount = {
                accountLoginRequests += 1
                KakaoCredentialResult.Success("account-id-token")
            },
        )

        assertEquals(KakaoCredentialResult.Failure(KakaoCredentialFailure.UNKNOWN), result)
        assertEquals(0, talkLoginRequests)
        assertEquals(0, accountLoginRequests)
    }

    @Test
    fun `returns configuration failure when account login has no id token`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { false },
            loginWithKakaoTalk = { error("Talk login should not run") },
            loginWithKakaoAccount = {
                KakaoCredentialResult.Failure(KakaoCredentialFailure.CONFIGURATION)
            },
        )

        assertEquals(
            KakaoCredentialResult.Failure(KakaoCredentialFailure.CONFIGURATION),
            result,
        )
    }
}
