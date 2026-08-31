package com.stonefive.chalkak

import android.content.Context
import androidx.credentials.CredentialManager
import com.stonefive.chalkak.core.auth.GoogleIdTokenClient
import com.stonefive.chalkak.core.auth.KakaoIdTokenClient
import com.stonefive.chalkak.data.local.auth.UserSessionStore
import com.stonefive.chalkak.data.remote.NetworkModule
import com.stonefive.chalkak.data.remote.auth.AuthDataSourceImpl
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.signature.OkHttpSignatureUploader
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.UserDataSourceImpl
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.data.repository.PostRepositoryImpl
import com.stonefive.chalkak.data.repository.UserRepositoryImpl
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.UserRepository
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
    private val userDataSource: UserDataSource by lazy {
        UserDataSourceImpl(
            networkModule.userApi,
            networkModule.apiRequestExecutor,
        )
    }

    val googleIdTokenClient = GoogleIdTokenClient(
        credentialManager = CredentialManager.create(context),
        serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
    )

    val kakaoIdTokenClient = KakaoIdTokenClient()

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            authDataSource = AuthDataSourceImpl(
                networkModule.authApi,
                networkModule.apiRequestExecutor,
            ),
            signatureUploader = OkHttpSignatureUploader(networkModule.signatureUploadClient),
            sessionStore = sessionStore,
        )
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            userDataSource = userDataSource,
            signatureUploader = OkHttpSignatureUploader(networkModule.signatureUploadClient),
            sessionStore = sessionStore,
        )
    }

    val postRepository: PostRepository by lazy {
        PostRepositoryImpl(
            remoteDataSource = PostRemoteDataSourceImpl(
                topicApi = networkModule.topicApi,
                postApi = networkModule.postApi,
                requestExecutor = networkModule.apiRequestExecutor,
            ),
        )
    }
}
