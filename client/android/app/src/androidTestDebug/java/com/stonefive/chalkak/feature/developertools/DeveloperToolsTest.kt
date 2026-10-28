package com.stonefive.chalkak.feature.developertools

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.buildvariant.BuildVariantSettingsEntry
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeveloperToolsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsEntryOpensInternalDeveloperToolsActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(DeveloperToolsActivity::class.java.name, null, false)
        try {
            composeRule.setContent {
                ChalkakTheme {
                    BuildVariantSettingsEntry()
                }
            }
            composeRule.onNodeWithText("개발자 도구").performClick()
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
            assertNotNull("설정에서 개발자 도구 Activity를 열어야 합니다", activity)
            if (activity != null) {
                val activityInfo = activity.packageManager.getActivityInfo(activity.componentName, 0)
                assertFalse("개발자 도구는 외부 앱에서 열 수 없어야 합니다", activityInfo.exported)
                instrumentation.runOnMainSync { activity.finish() }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun screenShowsBuildAndServerInformationAndAllowsGoingBack() {
        var backClicked = false
        composeRule.setContent {
            ChalkakTheme {
                DeveloperToolsRoute(
                    onBackClick = { backClicked = true },
                )
            }
        }
        listOf(
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE.toString(),
            BuildConfig.BUILD_TYPE,
            BuildConfig.APPLICATION_ID,
            URI(BuildConfig.API_BASE_URL).host,
            BuildConfig.API_BASE_URL,
        ).forEach { value ->
            composeRule
                .onNodeWithText(value)
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule
            .onNodeWithText("뒤로")
            .performScrollTo()
            .performClick()
        assertTrue(backClicked)
    }
}
