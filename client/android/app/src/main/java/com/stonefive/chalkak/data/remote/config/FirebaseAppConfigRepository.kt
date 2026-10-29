package com.stonefive.chalkak.data.remote.config

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.stonefive.chalkak.domain.model.AppConfig
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.AppConfigEntry
import com.stonefive.chalkak.domain.model.AppConfigFetchStatus
import com.stonefive.chalkak.domain.model.AppConfigSource
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase Remote Config로 [AppConfig]를 받아 온다.
 *
 * - 시작하면 마지막으로 적용했던 값(기기에 저장됨)을 바로 쓰고, 백그라운드에서 새 값을 받아 즉시 적용한다.
 * - 실행 중에는 실시간 업데이트로 바뀐 값을 받자마자 적용한다. 화면은 진입할 때 값을 확인한다.
 * - 원격에서 받은 적 없는 키는 [AppConfig]의 기본값을 쓴다.
 */
class FirebaseAppConfigRepository(
    private val scope: CoroutineScope,
    private val minimumFetchIntervalSeconds: Long,
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : AppConfigRepository {
    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _diagnostics = MutableStateFlow(AppConfigDiagnostics())
    override val diagnostics: StateFlow<AppConfigDiagnostics> = _diagnostics.asStateFlow()

    fun start() {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings
                .Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds)
                .build(),
        )
        publish()
        scope.launch { fetchAndActivate(minimumFetchIntervalSeconds) }
        remoteConfig.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    remoteConfig.activate().addOnCompleteListener { publish() }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    publish()
                }
            },
        )
    }

    override suspend fun refresh() {
        fetchAndActivate(minimumFetchIntervalSeconds = 0)
    }

    private suspend fun fetchAndActivate(minimumFetchIntervalSeconds: Long) {
        try {
            remoteConfig.fetch(minimumFetchIntervalSeconds).await()
            remoteConfig.activate().await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 받지 못하면 마지막으로 적용한 값이나 기본값을 그대로 쓴다.
        }
        publish()
    }

    private fun publish() {
        val defaults = AppConfig()
        _config.value = AppConfig(
            isAiReviewEnabled = remoteBoolean(KEY_AI_REVIEW_ENABLED) ?: defaults.isAiReviewEnabled,
            isPhotoEditEnabled = remoteBoolean(KEY_PHOTO_EDIT_ENABLED) ?: defaults.isPhotoEditEnabled,
        )
        val info = remoteConfig.info
        _diagnostics.value = AppConfigDiagnostics(
            entries = listOf(
                entry(KEY_AI_REVIEW_ENABLED, _config.value.isAiReviewEnabled),
                entry(KEY_PHOTO_EDIT_ENABLED, _config.value.isPhotoEditEnabled),
            ),
            lastFetchStatus = when (info.lastFetchStatus) {
                FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS -> AppConfigFetchStatus.Success
                FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE -> AppConfigFetchStatus.Failure
                FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED -> AppConfigFetchStatus.Throttled
                else -> AppConfigFetchStatus.NotFetchedYet
            },
            lastFetchedAtMillis = info.fetchTimeMillis.takeIf { it > 0 },
        )
    }

    /** 원격 값이 없거나 true/false로 읽히지 않으면 null을 돌려 기본값을 쓰게 한다. */
    private fun remoteBoolean(key: String): Boolean? {
        val value = remoteConfig.getValue(key)
        if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) return null
        return value
            .asString()
            .lowercase()
            .toBooleanStrictOrNull()
    }

    private fun entry(
        key: String,
        appliedValue: Boolean,
    ) = AppConfigEntry(
        key = key,
        value = appliedValue.toString(),
        source = if (remoteBoolean(key) != null) AppConfigSource.Remote else AppConfigSource.Default,
    )

    companion object {
        const val KEY_AI_REVIEW_ENABLED = "ai_review_enabled"
        const val KEY_PHOTO_EDIT_ENABLED = "photo_edit_enabled"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val exception = task.exception
        when {
            exception != null -> continuation.resumeWithException(exception)
            task.isCanceled -> continuation.resumeWithException(IllegalStateException("Task canceled"))
            else -> continuation.resume(task.result)
        }
    }
}
