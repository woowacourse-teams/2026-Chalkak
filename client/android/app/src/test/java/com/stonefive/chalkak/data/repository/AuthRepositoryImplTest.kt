package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.auth.AuthRemoteDataSource
import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryImplTest {
    private val dataSource = RecordingAuthRemoteDataSource()
    private val repository = AuthRepositoryImpl(dataSource)

    @Test
    fun `소셜 로그인 시 선택한 제공자를 원격 데이터 소스에 전달한다`() = runBlocking {
        val result = repository.login(SocialLoginProvider.GOOGLE)

        assertEquals(SocialLoginProvider.GOOGLE, dataSource.requestedProvider)
        assertEquals(AuthSession.Authenticated(SocialLoginProvider.GOOGLE), result)
    }

    @Test
    fun `비회원으로 계속하면 원격 데이터 소스에 비회원 진입을 요청한다`() = runBlocking {
        val result = repository.continueAsGuest()

        assertEquals(1, dataSource.guestRequestCount)
        assertEquals(AuthSession.Guest, result)
    }
}

private class RecordingAuthRemoteDataSource : AuthRemoteDataSource {
    var requestedProvider: SocialLoginProvider? = null
    var guestRequestCount: Int = 0

    override suspend fun login(provider: SocialLoginProvider): AuthResponse {
        requestedProvider = provider
        return AuthResponse(
            provider = provider.name,
            isGuest = false,
        )
    }

    override suspend fun continueAsGuest(): AuthResponse {
        guestRequestCount += 1
        return AuthResponse(
            provider = null,
            isGuest = true,
        )
    }
}
