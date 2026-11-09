package com.stonefive.chalkak.feature.notification

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.notification.component.NotificationTopBar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowBarGrowsForLargeMultilineTitle() {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                ChalkakTheme {
                    Box {
                        NotificationTopBar(
                            onBackClick = {},
                            title = "알림 상세",
                            modifier = Modifier.width(200.dp).testTag("bar"),
                        )
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("알림 상세").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertTrue("Expected wrapping at 200dp: ${layouts.single()}", layouts.single().lineCount > 1)
        assertFalse(layouts.single().hasVisualOverflow)
        assertTrue(
            composeRule
                .onNodeWithTag("bar")
                .getUnclippedBoundsInRoot()
                .height > 62.dp,
        )
    }
}
