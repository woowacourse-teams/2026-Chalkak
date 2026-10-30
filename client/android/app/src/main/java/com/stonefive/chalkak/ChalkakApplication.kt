package com.stonefive.chalkak

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.stonefive.chalkak.data.local.reminder.LegacyReminderCleanup

class ChalkakApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        LegacyReminderCleanup.cancel(this)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        appContainer.initializeRemoteConfig()
        appContainer.initializePush()
    }
}
