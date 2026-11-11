package com.stonefive.chalkak.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabPushNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun settingsPushFromOtherTabDropsSavedSettingsStack() {
        // Given  설정 → 서명 변경을 열어 둔 채 홈 탭으로 나갔다
        setContent()
        step { selectTab(Settings) }
        step { navController.navigate(ChangeSignature) }
        step { selectTab(Home) }

        // When   설정 탭 푸시를 누르고, 홈 탭을 거쳐 설정 탭을 다시 누른다
        step { navController.openPushNotificationTarget(NotificationTarget.SettingsTab) }
        step { selectTab(Home) }
        step { selectTab(Settings) }

        // Then   서명 변경이 아니라 설정 첫 화면이다
        step { assertTrue(navController.currentDestination!!.hasRoute<Settings>()) }
    }

    @Test
    fun settingsPushInsideSettingsStackDropsThatStack() {
        // Given  설정 → 서명 변경을 보고 있다
        setContent()
        step { selectTab(Settings) }
        step { navController.navigate(ChangeSignature) }

        // When   설정 탭 푸시를 누르고, 홈 탭을 거쳐 설정 탭을 다시 누른다
        step { navController.openPushNotificationTarget(NotificationTarget.SettingsTab) }
        step { assertTrue(navController.currentDestination!!.hasRoute<Settings>()) }
        step { selectTab(Home) }
        step { selectTab(Settings) }

        // Then   서명 변경이 아니라 설정 첫 화면이다
        step { assertTrue(navController.currentDestination!!.hasRoute<Settings>()) }
    }

    @Test
    fun otherTabPushKeepsSavedSettingsStack() {
        // Given  설정 → 서명 변경을 열어 둔 채 홈 탭으로 나갔다
        setContent()
        step { selectTab(Settings) }
        step { navController.navigate(ChangeSignature) }
        step { selectTab(Home) }

        // When   기록 탭 푸시를 누른 뒤 설정 탭을 누른다
        step { navController.openPushNotificationTarget(NotificationTarget.RecordTab) }
        step { selectTab(Settings) }

        // Then   설정 탭에 열어 둔 서명 변경이 그대로 돌아온다
        step { assertTrue(navController.currentDestination!!.hasRoute<ChangeSignature>()) }
    }

    private fun setContent() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> {}
                composable<Record> {}
                composable<Settings> {}
                composable<ChangeSignature> {}
            }
        }
    }

    private fun step(action: () -> Unit) {
        composeRule.runOnIdle(action)
        composeRule.waitForIdle()
    }

    /** 하단 탭을 누를 때(`navigateToBottomBar`)와 같은 옵션으로 탭을 연다. */
    private fun selectTab(tab: Any) {
        navController.navigate(tab) {
            launchSingleTop = true
            restoreState = tab != Home
            popUpTo(Home) {
                saveState = true
            }
        }
    }
}
