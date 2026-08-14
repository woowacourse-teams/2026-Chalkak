package com.stonefive.chalkak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 안드로이드 기기에서 실행하는 계측 테스트입니다.
 *
 * 자세한 내용은 [테스트 문서](http://d.android.com/tools/testing)를 참고하세요.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun `앱 컨텍스트의 패키지 이름이 올바르다`() {
        // 테스트 대상 앱의 컨텍스트
        val appContext = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
        assertEquals("com.stonefive.chalkak", appContext.packageName)
    }
}
