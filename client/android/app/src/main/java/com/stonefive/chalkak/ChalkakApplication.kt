package com.stonefive.chalkak

import android.app.Application

class ChalkakApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
}
