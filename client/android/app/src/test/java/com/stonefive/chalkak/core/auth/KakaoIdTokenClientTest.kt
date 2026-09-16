package com.stonefive.chalkak.core.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoIdTokenClientTest {
    private companion object {
        const val RAW_NONCE = "raw-kakao-nonce"
    }

    @Test
    fun `returns Talk id token when KakaoTalk login succeeds`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { true },
            loginWithKakaoTalk = { rawNonce -> KakaoCredentialResult.Success("talk-id-token", rawNonce) },
            loginWithKakaoAccount = { error("Account fallback should not run") },
            rawNonce = RAW_NONCE,
        )

        assertEquals(KakaoCredentialResult.Success("talk-id-token", RAW_NONCE), result)
    }

    @Test
    fun `does not fall back to account login when KakaoTalk login is cancelled`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { true },
            loginWithKakaoTalk = { KakaoCredentialResult.Cancelled },
            loginWithKakaoAccount = { error("Account fallback should not run") },
            rawNonce = RAW_NONCE,
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
            loginWithKakaoAccount = { rawNonce -> KakaoCredentialResult.Success("account-id-token", rawNonce) },
            rawNonce = RAW_NONCE,
        )

        assertEquals(KakaoCredentialResult.Success("account-id-token", RAW_NONCE), result)
    }

    @Test
    fun `uses account login when KakaoTalk is not available`() = runTest {
        val result = getIdToken(
            isKakaoTalkLoginAvailable = { false },
            loginWithKakaoTalk = { error("Talk login should not run") },
            loginWithKakaoAccount = { rawNonce -> KakaoCredentialResult.Success("account-id-token", rawNonce) },
            rawNonce = RAW_NONCE,
        )

        assertEquals(KakaoCredentialResult.Success("account-id-token", RAW_NONCE), result)
    }

    @Test
    fun `returns unknown failure when KakaoTalk availability check fails`() = runTest {
        var talkLoginRequests = 0
        var accountLoginRequests = 0

        val result = getIdToken(
            isKakaoTalkLoginAvailable = { error("Availability check failed") },
            loginWithKakaoTalk = {
                talkLoginRequests += 1
                KakaoCredentialResult.Success("talk-id-token", RAW_NONCE)
            },
            loginWithKakaoAccount = {
                accountLoginRequests += 1
                KakaoCredentialResult.Success("account-id-token", RAW_NONCE)
            },
            rawNonce = RAW_NONCE,
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
            rawNonce = RAW_NONCE,
        )

        assertEquals(
            KakaoCredentialResult.Failure(KakaoCredentialFailure.CONFIGURATION),
            result,
        )
    }
}
