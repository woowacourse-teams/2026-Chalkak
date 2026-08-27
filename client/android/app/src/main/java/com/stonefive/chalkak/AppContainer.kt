package com.stonefive.chalkak

import android.content.Context
import androidx.credentials.CredentialManager
import com.stonefive.chalkak.core.auth.GoogleIdTokenClient
import com.stonefive.chalkak.data.local.auth.UserSessionStore
import com.stonefive.chalkak.data.remote.NetworkModule
import com.stonefive.chalkak.data.remote.auth.AuthDataSourceImpl
import com.stonefive.chalkak.data.remote.display.MockDisplayRemoteDataSource
import com.stonefive.chalkak.data.remote.home.MockHomeRemoteDataSource
import com.stonefive.chalkak.data.remote.record.MockRecordRemoteDataSource
import com.stonefive.chalkak.data.remote.signature.MockSignatureRemoteDataSource
import com.stonefive.chalkak.data.remote.signature.OkHttpSignatureUploader
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = UserSessionStore(context, applicationScope)
    private val networkModule = NetworkModule(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionStore = sessionStore,
    )

    val googleIdTokenClient = GoogleIdTokenClient(
        credentialManager = CredentialManager.create(context),
        serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
    )

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            authDataSource = AuthDataSourceImpl(networkModule.authApi, networkModule.json),
            signatureUploader = OkHttpSignatureUploader(networkModule.signatureUploadClient),
            sessionStore = sessionStore,
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
