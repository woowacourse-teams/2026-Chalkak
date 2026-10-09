package com.stonefive.chalkak.feature.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionCoordinator
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionKey
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionProvider
import com.stonefive.chalkak.core.designsystem.component.image.SharedPhotoImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.home.component.HomeRankingRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class HomePhotoTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rankingPhotoExpandsIntoFeedAndReturnsToTheSelectedRow() {
        val coordinator = PhotoTransitionCoordinator()
        val today = LocalDate.of(2026, 10, 9)
        val image = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}"
        val post = Post(
            id = "same-post",
            originalImageUrl = image,
            thumbnailImageUrl = image,
            signatureOriginalImageUrl = "",
            contentDescription = "어제 사진",
            title = null,
            likeCount = 10,
        )
        val yesterdayKey = PhotoTransitionKey(post.id, "home-ranking:${today.minusDays(1)}:${post.id}")
        val todayKey = PhotoTransitionKey(post.id, "home-ranking:$today:${post.id}")
        lateinit var navController: NavHostController
        lateinit var sharedScope: SharedTransitionScope
        composeRule.setContent {
            navController = rememberNavController()
            ChalkakTheme {
                SharedTransitionLayout {
                    sharedScope = this
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            PhotoTransitionProvider(sharedScope, this, coordinator) {
                                Column {
                                    HomeRankingRow(
                                        title = "어제 랭킹",
                                        ranking = HomeRankingState(HomeSectionStatus.Ready, listOf(post)),
                                        displayDate = today.minusDays(1),
                                        onOpenDisplay = {},
                                        onOpenFeed = { navController.navigate("feed") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    HomeRankingRow(
                                        title = "오늘 인기있는 사진",
                                        ranking = HomeRankingState(
                                            HomeSectionStatus.Ready,
                                            listOf(post.copy(contentDescription = "오늘 사진")),
                                        ),
                                        displayDate = today,
                                        onOpenDisplay = {},
                                        onOpenFeed = { navController.navigate("feed") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        composable("feed") {
                            PhotoTransitionProvider(sharedScope, this, coordinator) {
                                SharedPhotoImage(
                                    key = coordinator.selectedSnapshot(post.id)!!.key,
                                    originalImageModel = image,
                                    thumbnailImageModel = image,
                                    signatureModel = null,
                                    thumbnailSignatureModel = null,
                                    contentDescription = "피드 사진",
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            coordinator.select(yesterdayKey)?.painter != null && coordinator.select(todayKey)?.painter != null
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("어제 사진").performClick()
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.runOnIdle {
            assertTrue("The Home photo should animate through the shared overlay", sharedScope.isTransitionActive)
            val snapshot = coordinator.selectedSnapshot(post.id)!!
            assertEquals(yesterdayKey, snapshot.key)
            assertNotNull(snapshot.painter)
            assertNotNull(snapshot.image)
            assertNotNull(snapshot.aspectRatio)
            assertNotNull(snapshot.sourceShape)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.runOnIdle {
            assertTrue("Returning should animate into the Home source", sharedScope.isTransitionActive)
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithContentDescription("어제 사진").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("오늘 사진").performClick()
        composeRule.runOnIdle {
            assertEquals(todayKey, coordinator.selectedSnapshot(post.id)?.key)
        }
    }
}
