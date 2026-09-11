package com.stonefive.chalkak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test that runs on an Android device.
 *
 * See the [testing documentation](http://d.android.com/tools/testing) for more information.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun appContextHasCorrectPackageName() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
        assertEquals("com.stonefive.chalkak", appContext.packageName)
    }
}
