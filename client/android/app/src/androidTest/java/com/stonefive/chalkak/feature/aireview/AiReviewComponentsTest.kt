package com.stonefive.chalkak.feature.aireview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.AiReviewFeedback
import com.stonefive.chalkak.feature.aireview.component.AiReviewAudioWaveIcon
import com.stonefive.chalkak.feature.aireview.component.AiReviewResultContent
import com.stonefive.chalkak.feature.aireview.component.AiReviewSheetDragHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class AiReviewComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun processingWaveChangesAcrossFrames() {
        showWave(isAnimating = true)
        val wave = composeRule.onNodeWithTag("ai-review-wave", useUnmergedTree = true)
        composeRule.mainClock.advanceTimeByFrame()
        val before = wave.captureToImage()
        composeRule.mainClock.advanceTimeBy(225)
        assertTrue(changedPixels(before, wave.captureToImage()).isNotEmpty())
    }

    @Test
    fun inactiveWaveRemainsStill() {
        showWave(isAnimating = false)
        val wave = composeRule.onNodeWithTag("ai-review-wave", useUnmergedTree = true)
        val before = wave.captureToImage()
        composeRule.mainClock.advanceTimeBy(450)
        assertTrue(changedPixels(before, wave.captureToImage()).isEmpty())
    }

    @Test
    fun handleRippleDoesNotPaintOutsideVisibleHandle() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ChalkakTheme {
                Box(modifier = Modifier.background(ChalkakTheme.colors.surfaceElevated)) {
                    AiReviewSheetDragHandle(sheetState = rememberModalBottomSheetState(), onDismiss = {})
                }
            }
        }
        val target = composeRule.onNodeWithTag("ai-review-drag-handle-touch")
        val before = target.captureToImage()
        target.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(200)
        val changed = changedPixels(before, target.captureToImage())
        target.performTouchInput { up() }
        assertTrue("Press should produce a visible ripple", changed.isNotEmpty())
        val handleLeft = before.width * (48f - 32f) / (2 * 48f)
        val handleTop = before.height * (48f - 4f) / (2 * 48f)
        changed.forEach { (x, y) ->
            assertTrue("Ripple escaped handle at $x,$y", x >= handleLeft - 1 && x < before.width - handleLeft + 1)
            assertTrue("Ripple escaped handle at $x,$y", y >= handleTop - 1 && y < before.height - handleTop + 1)
        }
    }

    @Test
    fun scoringCriteriaLinkOpensAndClosesDialog() {
        composeRule.setContent {
            ChalkakTheme {
                AiReviewResultContent(
                    result = AiReviewContent(
                        overallScore = 8.7,
                        criteria = emptyList(),
                        distinctivePerspective = false,
                        feedback = AiReviewFeedback("요약", "장점", "아쉬움", "제안"),
                    ),
                )
            }
        }
        val description = "Nikon, PPA(Professional Photographers of America), Canon"
        composeRule.onNodeWithText(description, substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("찰칵AI는 어떤 기준으로 점수를 내나요?").performClick()
        composeRule.onNodeWithText(description, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("확인").performClick()
        composeRule.onNodeWithText(description, substring = true).assertDoesNotExist()
    }

    private fun showWave(isAnimating: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ChalkakTheme {
                Box(modifier = Modifier.background(ChalkakTheme.colors.surfaceElevated)) {
                    AiReviewAudioWaveIcon(isAnimating = isAnimating)
                }
            }
        }
    }

    private fun changedPixels(
        before: ImageBitmap,
        after: ImageBitmap,
    ): List<Pair<Int, Int>> {
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        val first = before.toPixelMap()
        val second = after.toPixelMap()
        return buildList {
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    if (first[x, y] != second[x, y]) add(x to y)
                }
            }
        }
    }
}
