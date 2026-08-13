package com.stonefive.chalkak

import com.stonefive.chalkak.data.remote.auth.MockAuthRemoteDataSource
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.domain.repository.AuthRepository

class AppContainer {
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            remoteDataSource = MockAuthRemoteDataSource(),
        )
    }
}
