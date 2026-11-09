import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.google.services)
    alias(libs.plugins.roborazzi)
}

fun Properties.requiredProperty(name: String): String = getProperty(name)
    ?.takeIf { it.isNotBlank() }
    ?: error("$name is missing from local.properties")

fun String.toBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { input -> load(input) }
    }
}
val googleServerClientId = localProperties.requiredProperty("GOOGLE_SERVER_CLIENT_ID")

ktlint {
    version.set("1.8.0")
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

android {
    namespace = "com.stonefive.chalkak"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.stonefive.chalkak"
        minSdk = 26
        targetSdk = 37
        versionCode = 29
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ENABLE_PLAY_APP_UPDATES", "true")
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            googleServerClientId.toBuildConfigString(),
        )
    }

    val releaseSigningProperties = listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD",
    )
    val hasReleaseSigning = releaseSigningProperties.any { localProperties.containsKey(it) }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(localProperties.requiredProperty("RELEASE_STORE_FILE"))
                storePassword = localProperties.requiredProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.requiredProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.requiredProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_PLAY_APP_UPDATES", "false")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val apiBaseUrl = localProperties.requiredProperty("API_BASE_URL_DEV")
            val kakaoNativeAppKey = localProperties.requiredProperty("KAKAO_DEBUG_NATIVE_APP_KEY")
            buildConfigField("String", "API_BASE_URL", apiBaseUrl.toBuildConfigString())
            buildConfigField("String", "KAKAO_NATIVE_APP_KEY", kakaoNativeAppKey.toBuildConfigString())
            manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            val apiBaseUrl = localProperties.requiredProperty("API_BASE_URL")
            val kakaoNativeAppKey = localProperties.requiredProperty("KAKAO_NATIVE_APP_KEY")
            buildConfigField("String", "API_BASE_URL", apiBaseUrl.toBuildConfigString())
            buildConfigField("String", "KAKAO_NATIVE_APP_KEY", kakaoNativeAppKey.toBuildConfigString())
            manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
            optimization {
                enable = false
            }
        }
        create("localRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            buildConfigField("boolean", "ENABLE_PLAY_APP_UPDATES", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    // Preview 스냅샷(Roborazzi): Robolectric이 리소스를 읽고 화면을 실제 그래픽으로 그리게 한다
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
}

// Preview 스냅샷: @Preview를 찾아 스크린샷 테스트를 만든다.
// 평소 testDebugUnitTest에는 넣지 않고 -Pchalkak.previewSnapshots를 줄 때만 만든다(전체 검사 시간·실패에 영향 없게).
// 찍을 패키지는 -Pchalkak.previewPackages=a.b,c.d (기본: 모든 feature). 사용법은 하네스의 화면 자동 확인 문서.
roborazzi {
    generateComposePreviewRobolectricTests {
        enable = providers
            .gradleProperty("chalkak.previewSnapshots")
            .isPresent
        packages = providers
            .gradleProperty("chalkak.previewPackages")
            .map { value ->
                value
                    .split(",")
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }.getOrElse(listOf("com.stonefive.chalkak.feature"))
        includePrivatePreviews = true
        robolectricConfig = mapOf("sdk" to "[36]", "qualifiers" to "RobolectricDeviceQualifiers.Pixel7")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.googleid)
    implementation(libs.kakao.user)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
