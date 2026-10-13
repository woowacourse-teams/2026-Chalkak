package com.stonefive.chalkak.feature.settings.component

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SettingsPushNotificationCardTest(
    private val topicEnabled: Boolean,
    private val moderationEnabled: Boolean,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchReflectsWhetherAnyNotificationIsEnabled() {
        setCardContent()

        if (topicEnabled || moderationEnabled) {
            composeRule.onNode(isToggleable()).assertIsOn()
        } else {
            composeRule.onNode(isToggleable()).assertIsOff()
        }
    }

    @Test
    fun rowRequestsDisablingWhenAnyNotificationIsEnabled() {
        val changes = mutableListOf<Boolean>()
        setCardContent(changes::add)

        composeRule.onNodeWithText("알림 설정").performClick()

        assertEquals(listOf(!(topicEnabled || moderationEnabled)), changes)
    }

    @Test
    fun switchRequestsDisablingWhenAnyNotificationIsEnabled() {
        val changes = mutableListOf<Boolean>()
        setCardContent(changes::add)

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(!(topicEnabled || moderationEnabled)), changes)
    }

    private fun setCardContent(onPushChanged: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            ChalkakTheme {
                SettingsPushNotificationCard(
                    settings = NotificationPushSettings(topicEnabled, moderationEnabled),
                    isSaving = false,
                    onPushChanged = onPushChanged,
                    onDeviceNotificationSettingsClick = {},
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "topic={0}, moderation={1}")
        fun settings(): List<Array<Boolean>> = listOf(
            arrayOf(false, false),
            arrayOf(true, false),
            arrayOf(false, true),
            arrayOf(true, true),
        )
    }
}
