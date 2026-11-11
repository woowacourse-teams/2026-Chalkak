package com.stonefive.chalkak.feature.developertools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.notification.LocalNotificationPublisher
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeveloperToolsUiState(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val applicationId: String,
    val serverEnvironment: String,
    val serverBaseUrl: String,
    val remoteConfig: AppConfigDiagnostics = AppConfigDiagnostics(),
    val isRefreshingRemoteConfig: Boolean = false,
)

class DeveloperToolsViewModel(
    private val appConfigRepository: AppConfigRepository,
    private val localNotificationPublisher: LocalNotificationPublisher,
) : ViewModel() {
    private val buildInfo = DeveloperToolsUiState(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        applicationId = BuildConfig.APPLICATION_ID,
        serverEnvironment = URI(BuildConfig.API_BASE_URL).host,
        serverBaseUrl = BuildConfig.API_BASE_URL,
    )
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DeveloperToolsUiState> =
        combine(appConfigRepository.diagnostics, isRefreshing) { diagnostics, refreshing ->
            buildInfo.copy(remoteConfig = diagnostics, isRefreshingRemoteConfig = refreshing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = buildInfo.copy(remoteConfig = appConfigRepository.diagnostics.value),
        )

    fun refreshRemoteConfig() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                appConfigRepository.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    /** [notification]을 [title] 제목의 로컬 알림으로 띄운다. 알림이 꺼져 있어 띄우지 못하면 false다. */
    fun sendTestNotification(
        notification: DeveloperToolsTestNotification,
        title: String,
    ): Boolean = localNotificationPublisher.publish(
        payload = NotificationPushData(
            eventId = "developer-tools-${System.currentTimeMillis()}",
            notificationId = null,
            type = null,
            sourceType = null,
            sourceId = null,
            screen = notification.uri,
        ),
        title = title,
        body = notification.uri,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                DeveloperToolsViewModel(
                    appConfigRepository = application.appContainer.appConfigRepository,
                    localNotificationPublisher = application.appContainer.localNotificationPublisher,
                )
            }
        }
    }
}
