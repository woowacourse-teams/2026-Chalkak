package com.stonefive.chalkak.feature.settings.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    private val aiReviewEnabled: Boolean,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchesReflectIndependentNotificationSettings() {
        setCardContent()
        assertSwitch("주제 알림", topicEnabled)
        assertSwitch("승인·반려 알림", moderationEnabled)
        assertSwitch("AI 피드백 알림", aiReviewEnabled)
        composeRule.onAllNodesWithText("기기 알림 설정").assertCountEquals(0)
    }

    @Test
    fun topicRowOnlyChangesTopicSetting() {
        val topicChanges = mutableListOf<Boolean>()
        val moderationChanges = mutableListOf<Boolean>()
        setCardContent(topicChanges::add, moderationChanges::add)
        composeRule.onNodeWithText("주제 알림").performClick()
        assertEquals(listOf(!topicEnabled), topicChanges)
        assertEquals(emptyList<Boolean>(), moderationChanges)
    }

    @Test
    fun moderationRowOnlyChangesModerationSetting() {
        val topicChanges = mutableListOf<Boolean>()
        val moderationChanges = mutableListOf<Boolean>()
        setCardContent(topicChanges::add, moderationChanges::add)
        composeRule.onNodeWithText("승인·반려 알림").performClick()
        assertEquals(emptyList<Boolean>(), topicChanges)
        assertEquals(listOf(!moderationEnabled), moderationChanges)
    }

    @Test
    fun aiReviewRowOnlyChangesAiReviewSetting() {
        val aiReviewChanges = mutableListOf<Boolean>()
        setCardContent(onAiReviewPushChanged = aiReviewChanges::add)
        composeRule.onNodeWithText("AI 피드백 알림").performClick()
        assertEquals(listOf(!aiReviewEnabled), aiReviewChanges)
    }

    @Test
    fun switchesOnlyChangeTheirOwnSetting() {
        val topicChanges = mutableListOf<Boolean>()
        val moderationChanges = mutableListOf<Boolean>()
        setCardContent(topicChanges::add, moderationChanges::add)
        switchFor("주제 알림").performClick()
        assertEquals(listOf(!topicEnabled), topicChanges)
        assertEquals(emptyList<Boolean>(), moderationChanges)
        switchFor("승인·반려 알림").performClick()
        assertEquals(listOf(!moderationEnabled), moderationChanges)
    }

    @Test
    fun topicSavingOnlyDisablesTopicSwitch() {
        val topicChanges = mutableListOf<Boolean>()
        val moderationChanges = mutableListOf<Boolean>()
        setCardContent(
            onTopicPushChanged = topicChanges::add,
            onModerationPushChanged = moderationChanges::add,
            isTopicSaving = true,
            isModerationSaving = false,
        )

        switchFor("주제 알림").assertIsNotEnabled()
        switchFor("승인·반려 알림").assertIsEnabled().performClick()

        assertEquals(emptyList<Boolean>(), topicChanges)
        assertEquals(listOf(!moderationEnabled), moderationChanges)
    }

    @Test
    fun moderationSavingOnlyDisablesModerationSwitch() {
        val topicChanges = mutableListOf<Boolean>()
        val moderationChanges = mutableListOf<Boolean>()
        setCardContent(
            onTopicPushChanged = topicChanges::add,
            onModerationPushChanged = moderationChanges::add,
            isTopicSaving = false,
            isModerationSaving = true,
        )

        switchFor("주제 알림").assertIsEnabled().performClick()
        switchFor("승인·반려 알림").assertIsNotEnabled()

        assertEquals(listOf(!topicEnabled), topicChanges)
        assertEquals(emptyList<Boolean>(), moderationChanges)
    }

    private fun switchFor(label: String) = composeRule.onAllNodes(isToggleable())[
        when (label) {
            "주제 알림" -> 0
            "승인·반려 알림" -> 1
            else -> 2
        },
    ]

    private fun assertSwitch(
        label: String,
        enabled: Boolean,
    ) {
        if (enabled) switchFor(label).assertIsOn() else switchFor(label).assertIsOff()
    }

    private fun setCardContent(
        onTopicPushChanged: (Boolean) -> Unit = {},
        onModerationPushChanged: (Boolean) -> Unit = {},
        onAiReviewPushChanged: (Boolean) -> Unit = {},
        isTopicSaving: Boolean = false,
        isModerationSaving: Boolean = false,
    ) {
        composeRule.setContent {
            ChalkakTheme {
                SettingsPushNotificationCard(
                    settings = NotificationPushSettings(topicEnabled, moderationEnabled, aiReviewEnabled),
                    isTopicSaving = isTopicSaving,
                    isModerationSaving = isModerationSaving,
                    onTopicPushChanged = onTopicPushChanged,
                    onModerationPushChanged = onModerationPushChanged,
                    onAiReviewPushChanged = onAiReviewPushChanged,
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "topic={0}, moderation={1}, aiReview={2}")
        fun settings(): List<Array<Boolean>> = listOf(
            arrayOf(false, false, false),
            arrayOf(true, false, true),
            arrayOf(false, true, true),
            arrayOf(true, true, false),
        )
    }
}
