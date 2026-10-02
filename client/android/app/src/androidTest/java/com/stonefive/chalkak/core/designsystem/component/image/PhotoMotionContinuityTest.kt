package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class PhotoMotionContinuityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedPhotoMovesThroughIntermediatePixelBoundsDuringNavHostTransition() {
        val coordinator = PhotoTransitionCoordinator()
        val key = PhotoTransitionKey(postId = "post", sourceId = "display:post")
        var wasTransitionActive = false

        composeRule.setContent {
            val navController = rememberNavController()
            SharedTransitionLayout {
                val sharedTransitionScope = this
                androidx.compose.runtime.SideEffect {
                    wasTransitionActive = wasTransitionActive || isTransitionActive
                }
                NavHost(
                    navController = navController,
                    startDestination = SOURCE_ROUTE,
                    modifier = Modifier
                        .size(width = ROOT_WIDTH_DP.dp, height = ROOT_HEIGHT_DP.dp)
                        .background(Color.White)
                        .testTag(ROOT_TAG),
                ) {
                    composable(SOURCE_ROUTE) {
                        PhotoTransitionProvider(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this,
                            coordinator = coordinator,
                        ) {
                            androidx.compose.runtime.LaunchedEffect(coordinator, key) {
                                coordinator.register(
                                    SharedPhotoSnapshot(
                                        key = key,
                                        imageModel = null,
                                        signatureModel = null,
                                        painter = ColorPainter(Color.Red),
                                        image = null,
                                        memoryCacheKey = null,
                                        aspectRatio = 1f,
                                        contentScale = ContentScale.FillWidth,
                                    ),
                                )
                            }
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .offset(x = SOURCE_X_DP.dp, y = SOURCE_Y_DP.dp)
                                        .sharedPhotoElement(key)
                                        .size(SOURCE_SIZE_DP.dp)
                                        .background(Color.Red)
                                        .clickable {
                                            coordinator.select(key)
                                            navController.navigate(DESTINATION_ROUTE)
                                        }.testTag(SOURCE_TAG),
                                )
                            }
                        }
                    }

                    composable(DESTINATION_ROUTE) {
                        PhotoTransitionProvider(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = this,
                            coordinator = coordinator,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .offset(x = DESTINATION_X_DP.dp, y = DESTINATION_Y_DP.dp)
                                        .width(DESTINATION_SIZE_DP.dp),
                                ) {
                                    SharedFeedImage(
                                        key = key,
                                        originalImageModel = null,
                                        thumbnailImageModel = null,
                                        signatureModel = null,
                                        thumbnailSignatureModel = null,
                                        contentDescription = "photo",
                                        modifier = Modifier.testTag(DESTINATION_TAG),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val startBounds = captureRedPhotoBounds()

        composeRule.onNodeWithTag(SOURCE_TAG).performClick()
        val sampledBounds = buildList {
            repeat(INTERMEDIATE_FRAME_COUNT) {
                composeRule.mainClock.advanceTimeBy(FRAME_STEP_MILLIS)
                composeRule.waitForIdle()
                add(captureRedPhotoBounds())
            }
        }
        composeRule.mainClock.advanceTimeBy(PHOTO_TRANSITION_DURATION_MILLIS)
        composeRule.waitForIdle()
        val finalBounds = captureRedPhotoBounds()

        assertNear("source x", sourcePx(SOURCE_X_DP), startBounds.left)
        assertNear("source y", sourcePx(SOURCE_Y_DP), startBounds.top)
        assertNear("source width", sourcePx(SOURCE_SIZE_DP), startBounds.width)
        assertNear("destination x", sourcePx(DESTINATION_X_DP), finalBounds.left)
        assertNear("destination y", sourcePx(DESTINATION_Y_DP), finalBounds.top)
        assertNear("destination width", sourcePx(DESTINATION_SIZE_DP), finalBounds.width)

        val intermediateBounds = sampledBounds.filter { bounds ->
            bounds.width > startBounds.width + POSITION_TOLERANCE_PX &&
                bounds.width < finalBounds.width - POSITION_TOLERANCE_PX
        }
        assertTrue(
            "Expected multiple in-flight photo sizes, but captured ${sampledBounds.describe()}",
            intermediateBounds.size >= 3,
        )
        assertTrue(
            "Expected photo width to grow monotonically, but captured ${sampledBounds.describe()}",
            sampledBounds.isMonotonicBy { it.width },
        )
        assertTrue(
            "Expected photo x to move monotonically, but captured ${sampledBounds.describe()}",
            sampledBounds.isMonotonicBy { it.left },
        )
        assertTrue(
            "Expected photo y to move monotonically upward, but captured ${sampledBounds.describe()}",
            sampledBounds.isMonotonicBy(descending = true) { it.top },
        )
        assertTrue(
            "Expected distinct intermediate widths, but captured ${sampledBounds.describe()}",
            sampledBounds
                .map { it.width }
                .distinctByTolerance()
                .size >= 4,
        )
        assertTrue(
            "Expected shared overlay geometry to activate during NavHost transition; " +
                "isTransitionActive=$wasTransitionActive, frames=${sampledBounds.describe()}",
            intermediateBounds.isNotEmpty(),
        )
    }

    private fun captureRedPhotoBounds(): PixelBounds {
        val pixelMap = composeRule
            .onNodeWithTag(ROOT_TAG)
            .captureToImage()
            .toPixelMap()
        val visited = BooleanArray(pixelMap.width * pixelMap.height)
        var largest: PixelBounds? = null

        for (y in 0 until pixelMap.height) {
            for (x in 0 until pixelMap.width) {
                val index = y * pixelMap.width + x
                if (visited[index] || !pixelMap[x, y].isPhotoRed()) continue

                val component = ArrayDeque<Int>()
                var left = x
                var top = y
                var right = x
                var bottom = y
                var area = 0

                visited[index] = true
                component.add(index)
                while (component.isNotEmpty()) {
                    val current = component.removeFirst()
                    val currentX = current % pixelMap.width
                    val currentY = current / pixelMap.width
                    area += 1
                    left = minOf(left, currentX)
                    top = minOf(top, currentY)
                    right = maxOf(right, currentX)
                    bottom = maxOf(bottom, currentY)

                    listOf(
                        currentX - 1 to currentY,
                        currentX + 1 to currentY,
                        currentX to currentY - 1,
                        currentX to currentY + 1,
                    ).forEach { (nextX, nextY) ->
                        if (nextX !in 0 until pixelMap.width || nextY !in 0 until pixelMap.height) {
                            return@forEach
                        }
                        val nextIndex = nextY * pixelMap.width + nextX
                        if (!visited[nextIndex] && pixelMap[nextX, nextY].isPhotoRed()) {
                            visited[nextIndex] = true
                            component.add(nextIndex)
                        }
                    }
                }

                val bounds = PixelBounds(
                    left = left,
                    top = top,
                    right = right + 1,
                    bottom = bottom + 1,
                    area = area,
                )
                if (largest == null || bounds.area > largest.area) {
                    largest = bounds
                }
            }
        }

        return largest ?: throw AssertionError("No red photo pixels were captured")
    }

    private fun Color.isPhotoRed(): Boolean = red > 0.9f && green < 0.1f && blue < 0.1f && alpha > 0.9f

    private fun sourcePx(dp: Int): Int = (dp * composeRule.density.density).roundToInt()

    private fun assertNear(
        label: String,
        expected: Int,
        actual: Int,
    ) {
        assertTrue(
            "$label expected $expected px +/- $POSITION_TOLERANCE_PX px, got $actual px",
            abs(expected - actual) <= POSITION_TOLERANCE_PX,
        )
    }

    private fun List<PixelBounds>.isMonotonicBy(
        descending: Boolean = false,
        selector: (PixelBounds) -> Int,
    ): Boolean = zipWithNext().all { (previous, next) ->
        if (descending) {
            selector(next) <= selector(previous) + POSITION_TOLERANCE_PX
        } else {
            selector(next) + POSITION_TOLERANCE_PX >= selector(previous)
        }
    }

    private fun List<Int>.distinctByTolerance(): List<Int> = fold(emptyList()) { distinct, value ->
        if (distinct.any { abs(it - value) <= POSITION_TOLERANCE_PX }) {
            distinct
        } else {
            distinct + value
        }
    }

    private fun List<PixelBounds>.describe(): String = joinToString(
        prefix = "[",
        postfix = "]",
    ) { bounds ->
        "(x=${bounds.left}, y=${bounds.top}, w=${bounds.width}, h=${bounds.height})"
    }

    private data class PixelBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int,
    ) {
        val width: Int = right - left
        val height: Int = bottom - top
    }

    private companion object {
        private const val ROOT_TAG = "motion-root"
        private const val SOURCE_TAG = "motion-source"
        private const val DESTINATION_TAG = "motion-destination"
        private const val SOURCE_ROUTE = "source"
        private const val DESTINATION_ROUTE = "destination"
        private const val ROOT_WIDTH_DP = 400
        private const val ROOT_HEIGHT_DP = 520
        private const val SOURCE_X_DP = 20
        private const val SOURCE_Y_DP = 300
        private const val SOURCE_SIZE_DP = 100
        private const val DESTINATION_X_DP = 80
        private const val DESTINATION_Y_DP = 40
        private const val DESTINATION_SIZE_DP = 260
        private const val FRAME_STEP_MILLIS = 96L
        private const val INTERMEDIATE_FRAME_COUNT = 7
        private const val PHOTO_TRANSITION_DURATION_MILLIS = 700L
        private const val POSITION_TOLERANCE_PX = 3
    }
}
