package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.auth.AuthApi
import com.stonefive.chalkak.data.remote.post.PostApi
import com.stonefive.chalkak.data.remote.topic.TopicApi
import com.stonefive.chalkak.data.remote.user.UserApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkModule(
    baseUrl: String,
    sessionStore: SessionStore,
) {
    val json = Json {
        ignoreUnknownKeys = true
    }

    private val backendClient = OkHttpClient
        .Builder()
        .addInterceptor(UserIdHeaderInterceptor(sessionStore))
        .addInterceptor(createLoggingInterceptor())
        .build()

    private val retrofit = Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(backendClient)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val topicApi: TopicApi = retrofit.create(TopicApi::class.java)
    val postApi: PostApi = retrofit.create(PostApi::class.java)
    val userApi: UserApi = retrofit.create(UserApi::class.java)
    val apiRequestExecutor = ApiRequestExecutor(
        json = json,
        onUnauthorized = sessionStore::clear,
    )

    val signatureUploadClient = OkHttpClient
        .Builder()
        .addInterceptor(createLoggingInterceptor())
        .build()

    private fun createLoggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
