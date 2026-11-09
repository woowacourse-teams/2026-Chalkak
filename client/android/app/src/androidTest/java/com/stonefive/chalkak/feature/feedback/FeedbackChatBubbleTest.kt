package com.stonefive.chalkak.feature.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feedback.component.FeedbackChatBubble
import com.stonefive.chalkak.feature.feedback.component.FeedbackChatBubbleSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedbackChatBubbleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inquiryIsRightAndAnswerIsLeftWithMatchingTimestampsAndOriginalTextColors() {
        var mineColor = Color.Unspecified
        var answerColor = Color.Unspecified
        composeRule.setContent {
            ChalkakTheme {
                mineColor = ChalkakTheme.colors.onActionPrimary
                answerColor = ChalkakTheme.colors.textPrimary
                Box {
                    Column(modifier = Modifier.width(320.dp).testTag("conversation")) {
                        FeedbackChatBubble(
                            text = "내 문의",
                            timeText = "09:30",
                            side = FeedbackChatBubbleSide.Mine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FeedbackChatBubble(
                            text = "운영 답변",
                            timeText = "16:12",
                            side = FeedbackChatBubbleSide.Answer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        val host = composeRule.onNodeWithTag("conversation").getUnclippedBoundsInRoot()
        val mine = composeRule.onNodeWithText("내 문의").getUnclippedBoundsInRoot()
        val answer = composeRule.onNodeWithText("운영 답변").getUnclippedBoundsInRoot()
        val mineTime = composeRule.onNodeWithText("09:30").getUnclippedBoundsInRoot()
        val answerTime = composeRule.onNodeWithText("16:12").getUnclippedBoundsInRoot()
        val midpoint = (host.left.value + host.right.value) / 2
        assertTrue(mine.left.value > midpoint)
        assertTrue(answer.right.value < midpoint)
        assertEquals(host.right.value, mineTime.right.value, 0.5f)
        assertEquals(host.left.value, answerTime.left.value, 0.5f)
        listOf("내 문의" to mineColor, "운영 답변" to answerColor).forEach { (text, color) ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertEquals(
                color,
                layouts
                    .single()
                    .layoutInput.style.color,
            )
        }
    }
}
