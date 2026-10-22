package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.aireview.AiReviewQueryApi
import com.stonefive.chalkak.data.remote.aireview.AiReviewRequestApi
import com.stonefive.chalkak.data.remote.auth.AuthApi
import com.stonefive.chalkak.data.remote.auth.RefreshApi
import com.stonefive.chalkak.data.remote.banner.BannerApi
import com.stonefive.chalkak.data.remote.feedback.FeedbackApi
import com.stonefive.chalkak.data.remote.notification.NotificationApi
import com.stonefive.chalkak.data.remote.post.PostApi
import com.stonefive.chalkak.data.remote.topic.TopicApi
import com.stonefive.chalkak.data.remote.user.UserApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkModule(
    baseUrl: String,
    sessionStore: SessionStore,
) {
    val json = Json {
        ignoreUnknownKeys = true
    }

    private val httpsBaseUrl = baseUrl.toHttpsBaseUrl()

    private val refreshClient = OkHttpClient
        .Builder()
        .build()
    private val refreshApi: RefreshApi = Retrofit
        .Builder()
        .baseUrl(httpsBaseUrl)
        .client(refreshClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(RefreshApi::class.java)
    private val tokenRefresher = AuthTokenRefresher(refreshApi, json)
    val sessionRefreshCoordinator = SessionRefreshCoordinator(sessionStore, tokenRefresher)

    private val backendClient = OkHttpClient
        .Builder()
        .addInterceptor(AuthorizationHeaderInterceptor(sessionStore))
        .authenticator(TokenAuthenticator(sessionStore, sessionRefreshCoordinator, json))
        .build()

    private val retrofit = Retrofit
        .Builder()
        .baseUrl(httpsBaseUrl)
        .client(backendClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    // AI review POSTs are not transparently retried: a retry could consume a
    // server-side request attempt after the first request was already accepted.
    // GETs stay on backendClient so a stale pooled connection is retried.
    private val aiReviewClient = backendClient
        .newBuilder()
        .retryOnConnectionFailure(false)
        .build()

    private val aiReviewRetrofit = Retrofit
        .Builder()
        .baseUrl(httpsBaseUrl)
        .client(aiReviewClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    // Public banners must not attach session credentials or trigger token refresh.
    internal val publicClient = OkHttpClient.Builder().build()

    private val publicRetrofit = Retrofit
        .Builder()
        .baseUrl(httpsBaseUrl)
        .client(publicClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    val bannerApi: BannerApi = publicRetrofit.create(BannerApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val topicApi: TopicApi = retrofit.create(TopicApi::class.java)
    val postApi: PostApi = retrofit.create(PostApi::class.java)
    val userApi: UserApi = retrofit.create(UserApi::class.java)
    val feedbackApi: FeedbackApi = retrofit.create(FeedbackApi::class.java)
    val notificationApi: NotificationApi = retrofit.create(NotificationApi::class.java)
    val aiReviewQueryApi: AiReviewQueryApi = retrofit.create(AiReviewQueryApi::class.java)
    val aiReviewRequestApi: AiReviewRequestApi = aiReviewRetrofit.create(AiReviewRequestApi::class.java)
    val apiRequestExecutor = ApiRequestExecutor(json = json)

    val presignedUploadClient = OkHttpClient
        .Builder()
        .build()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun String.toHttpsBaseUrl(): HttpUrl = toHttpUrl().also { url ->
    require(url.isHttps) { "API base URL must use HTTPS: $url" }
}
