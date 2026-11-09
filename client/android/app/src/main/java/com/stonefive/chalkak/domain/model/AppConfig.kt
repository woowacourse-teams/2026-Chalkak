package com.stonefive.chalkak.domain.model

/**
 * 스토어 배포 없이 바꿀 수 있는 원격 설정.
 * 기본값은 설정을 한 번도 받지 못했을 때(첫 실행, 오프라인, 서버 장애) 앱이 그대로 쓰는 값이다.
 */
data class AppConfig(
    val isAiReviewEnabled: Boolean = true,
    val isPhotoEditEnabled: Boolean = true,
    val isHomeIssueTopEnabled: Boolean = false,
)

/**
 * 이번 앱 실행 동안 고정되는 홈 ISSUE 배치 실험 값.
 * [isAssigned]는 원격 값을 받았는지를 뜻하며, 기본값만 쓰는 사용자는 실험 집계에서 뺀다.
 */
data class HomeIssueExperiment(
    val isIssueOnTop: Boolean = false,
    val isAssigned: Boolean = false,
)

/** 개발자 도구에서 지금 적용된 설정을 확인하기 위한 상태. */
data class AppConfigDiagnostics(
    val entries: List<AppConfigEntry> = emptyList(),
    val lastFetchStatus: AppConfigFetchStatus = AppConfigFetchStatus.NotFetchedYet,
    val lastFetchedAtMillis: Long? = null,
)

data class AppConfigEntry(
    val key: String,
    val value: String,
    val source: AppConfigSource,
)

enum class AppConfigSource {
    Default,
    Remote,
}

enum class AppConfigFetchStatus {
    NotFetchedYet,
    Success,
    Failure,
    Throttled,
}
