package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.auth.AuthApi
import com.stonefive.chalkak.data.remote.auth.RefreshApi
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

    private val backendClient = OkHttpClient
        .Builder()
        .addInterceptor(AuthorizationHeaderInterceptor(sessionStore))
        .authenticator(TokenAuthenticator(sessionStore, tokenRefresher, json))
        .build()

    private val retrofit = Retrofit
        .Builder()
        .baseUrl(httpsBaseUrl)
        .client(backendClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val topicApi: TopicApi = retrofit.create(TopicApi::class.java)
    val postApi: PostApi = retrofit.create(PostApi::class.java)
    val userApi: UserApi = retrofit.create(UserApi::class.java)
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
