package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.auth.AuthRemoteDataSource
import com.stonefive.chalkak.data.remote.auth.model.AuthResponse
import com.stonefive.chalkak.domain.model.AuthSession
import com.stonefive.chalkak.domain.model.SocialLoginProvider
import com.stonefive.chalkak.domain.repository.AuthRepository

class AuthRepositoryImpl(private val remoteDataSource: AuthRemoteDataSource) : AuthRepository {
    override suspend fun login(provider: SocialLoginProvider): AuthSession.Authenticated =
        remoteDataSource.login(provider).toAuthenticatedSession()

    override suspend fun continueAsGuest(): AuthSession.Guest {
        val response = remoteDataSource.continueAsGuest()
        require(response.isGuest) { "비회원 인증 응답이 아닙니다." }
        return AuthSession.Guest
    }

    private fun AuthResponse.toAuthenticatedSession(): AuthSession.Authenticated {
        require(!isGuest) { "회원 인증 응답이 아닙니다." }
        val provider = requireNotNull(provider) { "로그인 제공자가 없습니다." }
        return AuthSession.Authenticated(SocialLoginProvider.valueOf(provider))
    }
}
