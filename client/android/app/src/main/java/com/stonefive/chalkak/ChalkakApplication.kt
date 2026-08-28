package com.stonefive.chalkak

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class ChalkakApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
