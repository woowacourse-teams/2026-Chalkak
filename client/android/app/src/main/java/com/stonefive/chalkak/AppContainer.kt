package com.stonefive.chalkak

import com.stonefive.chalkak.data.remote.auth.MockAuthRemoteDataSource
import com.stonefive.chalkak.data.remote.display.MockDisplayRemoteDataSource
import com.stonefive.chalkak.data.remote.home.MockHomeRemoteDataSource
import com.stonefive.chalkak.data.remote.record.MockRecordRemoteDataSource
import com.stonefive.chalkak.data.remote.signature.MockSignatureRemoteDataSource
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.data.repository.DisplayRepositoryImpl
import com.stonefive.chalkak.data.repository.HomeRepositoryImpl
import com.stonefive.chalkak.data.repository.RecordRepositoryImpl
import com.stonefive.chalkak.data.repository.SignatureRepositoryImpl
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.DisplayRepository
import com.stonefive.chalkak.domain.repository.HomeRepository
import com.stonefive.chalkak.domain.repository.RecordRepository
import com.stonefive.chalkak.domain.repository.SignatureRepository

class AppContainer {
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            remoteDataSource = MockAuthRemoteDataSource(),
        )
    }

    val homeRepository: HomeRepository by lazy {
        HomeRepositoryImpl(
            remoteDataSource = MockHomeRemoteDataSource(),
        )
    }

    val recordRepository: RecordRepository by lazy {
        RecordRepositoryImpl(
            remoteDataSource = MockRecordRemoteDataSource(),
        )
    }

    val displayRepository: DisplayRepository by lazy {
        DisplayRepositoryImpl(
            remoteDataSource = MockDisplayRemoteDataSource(),
        )
    }

    val signatureRepository: SignatureRepository by lazy {
        SignatureRepositoryImpl(
            remoteDataSource = MockSignatureRemoteDataSource(),
        )
    }
}
