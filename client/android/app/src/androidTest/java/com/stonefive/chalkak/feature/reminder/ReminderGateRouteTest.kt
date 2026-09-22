package com.stonefive.chalkak.feature.reminder

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class ReminderGateRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unconfiguredUserSeesReminderSetupUntilPreferenceIsSaved() {
        val repository = FakeReminderGateRepository(ReminderPreference.Unconfigured)
        val viewModel = ReminderGateViewModel(repository)

        composeRule.setContent {
            ChalkakTheme {
                ReminderGateRoute(
                    reminderRequiredContent = { Text("알림 설정 화면") },
                    configuredContent = { Text("홈 화면") },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithText("알림 설정 화면").assertIsDisplayed()
        composeRule.onAllNodesWithText("홈 화면").assertCountEquals(0)

        composeRule.runOnIdle {
            repository.preferenceState.value = ReminderPreference.Disabled
        }

        composeRule.onNodeWithText("홈 화면").assertIsDisplayed()
        composeRule.onAllNodesWithText("알림 설정 화면").assertCountEquals(0)
    }
}

private class FakeReminderGateRepository(initialPreference: ReminderPreference) : ReminderPreferenceRepository {
    val preferenceState = MutableStateFlow(initialPreference)
    override val preference: StateFlow<ReminderPreference> = preferenceState

    override suspend fun enable(
        hour: Int,
        minute: Int,
    ) {
        preferenceState.value = ReminderPreference.Enabled(hour, minute)
    }

    override suspend fun disable() {
        preferenceState.value = ReminderPreference.Disabled
    }
}
