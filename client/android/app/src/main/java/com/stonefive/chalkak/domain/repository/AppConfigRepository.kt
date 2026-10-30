package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AppConfig
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import kotlinx.coroutines.flow.StateFlow

interface AppConfigRepository {
    val config: StateFlow<AppConfig>
    val diagnostics: StateFlow<AppConfigDiagnostics>

    /** 갱신 주기를 무시하고 지금 설정을 다시 받아 적용한다. */
    suspend fun refresh()
}
