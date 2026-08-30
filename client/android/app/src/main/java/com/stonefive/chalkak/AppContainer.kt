package com.stonefive.chalkak

import android.content.Context
import androidx.credentials.CredentialManager
import com.stonefive.chalkak.core.auth.GoogleIdTokenClient
import com.stonefive.chalkak.core.auth.KakaoIdTokenClient
import com.stonefive.chalkak.data.local.auth.UserSessionStore
import com.stonefive.chalkak.data.post.AndroidPostImageEncoder
import com.stonefive.chalkak.data.remote.NetworkModule
import com.stonefive.chalkak.data.remote.auth.AuthDataSourceImpl
import com.stonefive.chalkak.data.remote.display.MockDisplayRemoteDataSource
import com.stonefive.chalkak.data.remote.home.HomeRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.home.MockHomeRemoteDataSource
import com.stonefive.chalkak.data.remote.post.PostCreationRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.record.MockRecordRemoteDataSource
import com.stonefive.chalkak.data.remote.signature.OkHttpPresignedImageUploader
import com.stonefive.chalkak.data.remote.user.UserDataSourceImpl
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.data.repository.DisplayRepositoryImpl
import com.stonefive.chalkak.data.repository.HomeRepositoryImpl
import com.stonefive.chalkak.data.repository.PostCreationRepositoryImpl
import com.stonefive.chalkak.data.repository.RecordRepositoryImpl
import com.stonefive.chalkak.data.repository.UserRepositoryImpl
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.DisplayRepository
import com.stonefive.chalkak.domain.repository.HomeRepository
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import com.stonefive.chalkak.domain.repository.RecordRepository
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
    private val presignedImageUploader = OkHttpPresignedImageUploader(
        networkModule.presignedUploadClient,
    )

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
            signatureUploader = presignedImageUploader,
            sessionStore = sessionStore,
        )
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            userDataSource = UserDataSourceImpl(
                networkModule.userApi,
                networkModule.apiRequestExecutor,
            ),
            signatureUploader = presignedImageUploader,
        )
    }

    val postCreationRepository: PostCreationRepository by lazy {
        PostCreationRepositoryImpl(
            remoteDataSource = PostCreationRemoteDataSourceImpl(
                postApi = networkModule.postApi,
                topicApi = networkModule.topicApi,
                requestExecutor = networkModule.apiRequestExecutor,
            ),
            imageEncoder = AndroidPostImageEncoder(
                contentResolver = context.contentResolver,
                cacheDir = context.cacheDir,
            ),
            imageUploader = presignedImageUploader,
        )
    }

    val homeRepository: HomeRepository by lazy {
        HomeRepositoryImpl(
            remoteDataSource = HomeRemoteDataSourceImpl(
                topicApi = networkModule.topicApi,
                postApi = networkModule.postApi,
                json = networkModule.json,
            ),
        )
    }

    val feedRepository: HomeRepository by lazy {
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
}
