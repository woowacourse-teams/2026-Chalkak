// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // 루트 프로젝트에도 적용해야 Git pre-commit 훅 생성 태스크가 등록됩니다.
    alias(libs.plugins.ktlint)
}
