package com.stonefive.chalkak.feature.developertools

import androidx.lifecycle.ViewModel
import com.stonefive.chalkak.BuildConfig
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeveloperToolsUiState(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val applicationId: String,
    val serverEnvironment: String,
    val serverBaseUrl: String,
)

class DeveloperToolsViewModel : ViewModel() {
    val uiState: StateFlow<DeveloperToolsUiState> = MutableStateFlow(
        DeveloperToolsUiState(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            applicationId = BuildConfig.APPLICATION_ID,
            serverEnvironment = URI(BuildConfig.API_BASE_URL).host,
            serverBaseUrl = BuildConfig.API_BASE_URL,
        ),
    ).asStateFlow()
}
