package com.stonefive.chalkak

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.messaging.FirebaseMessaging
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.analytics.FirebaseAnalyticsTracker
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.appupdate.DisabledAppUpdateGateway
import com.stonefive.chalkak.core.appupdate.PlayAppUpdateGateway
import com.stonefive.chalkak.core.auth.GoogleIdTokenClient
import com.stonefive.chalkak.core.auth.KakaoIdTokenClient
import com.stonefive.chalkak.core.network.AndroidConnectivityObserver
import com.stonefive.chalkak.core.network.ConnectivityObserver
import com.stonefive.chalkak.core.notification.PendingNotificationNavigationStore
import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.UserSessionStore
import com.stonefive.chalkak.data.local.notification.PushNotificationManager
import com.stonefive.chalkak.data.post.AndroidPhotoEditImageRepository
import com.stonefive.chalkak.data.post.AndroidPostImageEncoder
import com.stonefive.chalkak.data.remote.NetworkModule
import com.stonefive.chalkak.data.remote.aireview.AiReviewDataSourceImpl
import com.stonefive.chalkak.data.remote.auth.AuthDataSourceImpl
import com.stonefive.chalkak.data.remote.config.FirebaseAppConfigRepository
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSourceImpl
import com.stonefive.chalkak.data.remote.notification.NotificationRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.notification.NotificationRepositoryImpl
import com.stonefive.chalkak.data.remote.notification.PushDeviceRegistrationCoordinator
import com.stonefive.chalkak.data.remote.post.OkHttpPostImageUploader
import com.stonefive.chalkak.data.remote.post.PostCreationRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.signature.OkHttpSignatureUploader
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.UserDataSourceImpl
import com.stonefive.chalkak.data.repository.AiReviewRepositoryImpl
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.data.repository.BannerRepositoryImpl
import com.stonefive.chalkak.data.repository.FeedbackRepositoryImpl
import com.stonefive.chalkak.data.repository.PostCreationRepositoryImpl
import com.stonefive.chalkak.data.repository.PostRepositoryImpl
import com.stonefive.chalkak.data.repository.UserRepositoryImpl
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.BannerRepository
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import com.stonefive.chalkak.domain.repository.NotificationRepository
import com.stonefive.chalkak.domain.repository.PhotoEditImageRepository
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val signatureHintRepository: com.stonefive.chalkak.domain.repository.SignatureHintRepository by lazy {
        com.stonefive.chalkak.data.local.feed
            .SignatureHintStore(applicationContext)
    }

    val analyticsTracker: AnalyticsTracker by lazy {
        FirebaseAnalyticsTracker(context)
    }

    val appUpdateGateway: AppUpdateGateway by lazy {
        com.stonefive.chalkak.data.remote.appversion.ServerAppUpdateGateway(
            api = networkModule.appVersionApi,
            currentVersion = BuildConfig.VERSION_NAME.substringBefore("-"),
            playGateway = if (BuildConfig.ENABLE_PLAY_APP_UPDATES) {
                PlayAppUpdateGateway(
                    context,
                )
            } else {
                DisabledAppUpdateGateway()
            },
        )
    }

    val connectivityObserver: ConnectivityObserver by lazy {
        AndroidConnectivityObserver(context)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val firebaseAppConfigRepository by lazy {
        FirebaseAppConfigRepository(
            scope = applicationScope,
            minimumFetchIntervalSeconds = if (BuildConfig.DEBUG) 0L else REMOTE_CONFIG_FETCH_INTERVAL_SECONDS,
        )
    }

    val appConfigRepository: AppConfigRepository by lazy { firebaseAppConfigRepository }
    private val sessionStore = UserSessionStore(context, applicationScope)
    private val networkModule = NetworkModule(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionStore = sessionStore,
    )
    private val signatureUploader = OkHttpSignatureUploader(
        networkModule.presignedUploadClient,
    )
    private val postImageUploader = OkHttpPostImageUploader(
        networkModule.presignedUploadClient,
    )
    private val userDataSource: UserDataSource by lazy {
        UserDataSourceImpl(
            networkModule.userApi,
            networkModule.apiRequestExecutor,
        )
    }

    private val feedbackDataSource: FeedbackDataSource by lazy {
        FeedbackDataSourceImpl(
            networkModule.feedbackApi,
            networkModule.apiRequestExecutor,
        )
    }

    private val notificationRemoteDataSource by lazy {
        NotificationRemoteDataSourceImpl(
            api = networkModule.notificationApi,
            requestExecutor = networkModule.apiRequestExecutor,
        )
    }

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepositoryImpl(notificationRemoteDataSource)
    }

    private val pushDeviceRegistrationCoordinator by lazy {
        PushDeviceRegistrationCoordinator(
            sessionStore = sessionStore,
            sessionRefreshCoordinator = networkModule.sessionRefreshCoordinator,
            notificationRepository = notificationRepository,
        )
    }

    val pendingNotificationNavigation = PendingNotificationNavigationStore()

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
            signatureUploader = signatureUploader,
            sessionStore = sessionStore,
        )
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            userDataSource = userDataSource,
            signatureUploader = signatureUploader,
            sessionStore = sessionStore,
        )
    }

    val recordPreferenceRepository: com.stonefive.chalkak.domain.repository.RecordPreferenceRepository by lazy {
        com.stonefive.chalkak.data.local.record
            .RecordPreferenceStore(applicationContext)
    }

    val photoColorRepository: com.stonefive.chalkak.domain.repository.PhotoColorRepository by lazy {
        com.stonefive.chalkak.data.local.record
            .PhotoColorStore(applicationContext)
    }

    val bannerRepository: BannerRepository by lazy {
        BannerRepositoryImpl(networkModule.bannerApi, networkModule.apiRequestExecutor)
    }

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepositoryImpl(
            feedbackDataSource = feedbackDataSource,
        )
    }

    val aiReviewRepository: AiReviewRepository by lazy {
        AiReviewRepositoryImpl(
            AiReviewDataSourceImpl(
                queryApi = networkModule.aiReviewQueryApi,
                requestApi = networkModule.aiReviewRequestApi,
                requestExecutor = networkModule.apiRequestExecutor,
            ),
        )
    }

    private val topicRemoteDataSource by lazy {
        TopicRemoteDataSourceImpl(
            topicApi = networkModule.topicApi,
            requestExecutor = networkModule.apiRequestExecutor,
        )
    }

    val photoEditImageRepository: PhotoEditImageRepository by lazy {
        AndroidPhotoEditImageRepository(contentResolver = context.contentResolver)
    }

    val postCreationRepository: PostCreationRepository by lazy {
        PostCreationRepositoryImpl(
            remoteDataSource = PostCreationRemoteDataSourceImpl(
                postApi = networkModule.postApi,
                requestExecutor = networkModule.apiRequestExecutor,
            ),
            topicRemoteDataSource = topicRemoteDataSource,
            imageEncoder = AndroidPostImageEncoder(
                contentResolver = context.contentResolver,
                cacheDir = context.cacheDir,
            ),
            imageUploader = postImageUploader,
        )
    }

    private val postRepositoryImpl: PostRepositoryImpl by lazy {
        PostRepositoryImpl(
            remoteDataSource = PostRemoteDataSourceImpl(
                postApi = networkModule.postApi,
                requestExecutor = networkModule.apiRequestExecutor,
            ),
            topicRemoteDataSource = topicRemoteDataSource,
        )
    }

    val postRepository: PostRepository by lazy {
        postRepositoryImpl
    }

    val photoUploadEntryRepository: PhotoUploadEntryRepository by lazy {
        postRepositoryImpl
    }

    fun initializeRemoteConfig() {
        firebaseAppConfigRepository.start()
    }

    fun initializePush() {
        PushNotificationManager.createChannel(applicationContext)
        applicationScope.launch {
            sessionStore.session
                .map { session ->
                    (session as? LocalSession.Authenticated)?.credentials?.userId
                }.distinctUntilChanged()
                .collectLatest { userId ->
                    pushDeviceRegistrationCoordinator.onSessionChanged(userId)
                    if (userId != null) syncPushDevice(userId)
                }
        }
    }

    fun registerPushDevice(token: String) {
        applicationScope.launch {
            pushDeviceRegistrationCoordinator.register(token)
        }
    }

    fun syncPushDevice() {
        applicationScope.launch {
            val userId = (sessionStore.session.value as? LocalSession.Authenticated)
                ?.credentials
                ?.userId
                ?: return@launch
            syncPushDevice(userId)
        }
    }

    suspend fun markNotificationRead(notificationId: String) = notificationRepository.markAsRead(notificationId)

    private suspend fun syncPushDevice(expectedUserId: String) {
        val token = try {
            FirebaseMessaging
                .getInstance()
                .token
                .awaitResult()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }?.takeIf(String::isNotBlank)
        if (token == null) {
            return
        }
        val currentUserId = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?.userId
        if (currentUserId == expectedUserId) {
            pushDeviceRegistrationCoordinator.register(token)
        }
    }
}

// 운영 앱이 원격 설정을 다시 받는 최소 간격. 근거는 docs/architecture/remote-config.md 참고.
private const val REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 60L * 60L

private suspend fun com.google.android.gms.tasks.Task<String>.awaitResult(): String? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resume(null)
            }
        }
    }
