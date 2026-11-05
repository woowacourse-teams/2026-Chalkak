package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.AppConfig
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.HomeIssueExperiment
import kotlinx.coroutines.flow.StateFlow

interface AppConfigRepository {
    val config: StateFlow<AppConfig>
    val diagnostics: StateFlow<AppConfigDiagnostics>

    /** 갱신 주기를 무시하고 지금 설정을 다시 받아 적용한다. */
    suspend fun refresh()

    /** 앱 실행 시 기기에 저장돼 있던 값으로 고정한 실험 값을 돌려준다. 실행 중에 새 값이 와도 바뀌지 않는다. */
    suspend fun homeIssueExperiment(): HomeIssueExperiment
}
