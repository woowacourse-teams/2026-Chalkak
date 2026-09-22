package com.stonefive.chalkak

import android.content.Context
import androidx.credentials.CredentialManager
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.analytics.FirebaseAnalyticsTracker
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import com.stonefive.chalkak.core.appupdate.DisabledAppUpdateGateway
import com.stonefive.chalkak.core.appupdate.PlayAppUpdateGateway
import com.stonefive.chalkak.core.auth.GoogleIdTokenClient
import com.stonefive.chalkak.core.auth.KakaoIdTokenClient
import com.stonefive.chalkak.core.network.AndroidConnectivityObserver
import com.stonefive.chalkak.core.network.ConnectivityObserver
import com.stonefive.chalkak.data.local.auth.UserSessionStore
import com.stonefive.chalkak.data.local.reminder.AndroidReminderAlarmScheduler
import com.stonefive.chalkak.data.local.reminder.ReminderNotificationManager
import com.stonefive.chalkak.data.local.reminder.ReminderPreferenceStore
import com.stonefive.chalkak.data.post.AndroidPostImageEncoder
import com.stonefive.chalkak.data.remote.NetworkModule
import com.stonefive.chalkak.data.remote.auth.AuthDataSourceImpl
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSourceImpl
import com.stonefive.chalkak.data.remote.post.OkHttpPostImageUploader
import com.stonefive.chalkak.data.remote.post.PostCreationRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.post.PostRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.signature.OkHttpSignatureUploader
import com.stonefive.chalkak.data.remote.topic.TopicRemoteDataSourceImpl
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.data.remote.user.UserDataSourceImpl
import com.stonefive.chalkak.data.repository.AuthRepositoryImpl
import com.stonefive.chalkak.data.repository.FeedbackRepositoryImpl
import com.stonefive.chalkak.data.repository.PostCreationRepositoryImpl
import com.stonefive.chalkak.data.repository.PostRepositoryImpl
import com.stonefive.chalkak.data.repository.UserRepositoryImpl
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.AuthRepository
import com.stonefive.chalkak.domain.repository.FeedbackRepository
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import com.stonefive.chalkak.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val analyticsTracker: AnalyticsTracker by lazy {
        FirebaseAnalyticsTracker(context)
    }

    val appUpdateGateway: AppUpdateGateway by lazy {
        if (BuildConfig.DEBUG) {
            DisabledAppUpdateGateway()
        } else {
            PlayAppUpdateGateway(context)
        }
    }

    val connectivityObserver: ConnectivityObserver by lazy {
        AndroidConnectivityObserver(context)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepositoryImpl(
            feedbackDataSource = feedbackDataSource,
        )
    }

    private val topicRemoteDataSource by lazy {
        TopicRemoteDataSourceImpl(
            topicApi = networkModule.topicApi,
            requestExecutor = networkModule.apiRequestExecutor,
        )
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

    val reminderAlarmScheduler by lazy {
        AndroidReminderAlarmScheduler(applicationContext)
    }

    val reminderPreferenceRepository: ReminderPreferenceRepository by lazy {
        ReminderPreferenceStore(
            context = applicationContext,
            scope = applicationScope,
            alarmScheduler = reminderAlarmScheduler,
        )
    }

    fun initializeReminder() {
        ReminderNotificationManager.createChannel(applicationContext)
        applicationScope.launch {
            reconcileReminderAlarm()
        }
    }

    suspend fun reconcileReminderAlarm() {
        val preference = reminderPreferenceRepository.preference.first { preference ->
            preference !is ReminderPreference.Loading
        }
        when (preference) {
            is ReminderPreference.Enabled ->
                reminderAlarmScheduler.schedule(preference.hour, preference.minute)

            ReminderPreference.Disabled,
            ReminderPreference.Unconfigured,
            ReminderPreference.Loading,
            -> reminderAlarmScheduler.cancel()
        }
    }
}
