package com.stonefive.chalkak.data.remote.auth

import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockAuthRemoteDataSourceTest {
    private val dataSource = MockAuthRemoteDataSource(responseDelayMillis = 0L)

    @Test
    fun `소셜 로그인 시 선택한 제공자의 응답을 반환한다`() = runBlocking {
        val result = dataSource.login(SocialLoginProvider.KAKAO)

        assertEquals(
            AuthResponse(provider = SocialLoginProvider.KAKAO.name, isGuest = false),
            result,
        )
    }

    @Test
    fun `비회원으로 계속하면 비회원 응답을 반환한다`() = runBlocking {
        val result = dataSource.continueAsGuest()

        assertEquals(AuthResponse(provider = null, isGuest = true), result)
    }
}
