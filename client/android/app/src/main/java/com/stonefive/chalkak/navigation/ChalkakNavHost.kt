package com.stonefive.chalkak.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.feed.FeedContentState
import com.stonefive.chalkak.feature.feed.FeedRoute
import com.stonefive.chalkak.feature.home.HomeRoute
import com.stonefive.chalkak.feature.login.LoginRoute
import com.stonefive.chalkak.feature.settings.SettingsRoute
import com.stonefive.chalkak.feature.signature.SignatureRoute
import com.stonefive.chalkak.feature.terms.TermsRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadRoute

@Composable
fun ChalkakNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Login,
        modifier = modifier,
    ) {
        composable<Login> {
            LoginRoute(
                onLoginSuccess = {
                    navController.navigate(Terms)
                },
            )
        }

        composable<Terms> {
            TermsRoute(
                onNextClick = {
                    navController.navigate(Signature(SignatureOrigin.ONBOARDING))
                },
            )
        }

        composable<Signature> { backStackEntry ->
            val signature = backStackEntry.toRoute<Signature>()

            SignatureRoute(
                onSignatureSaved = {
                    when (signature.origin) {
                        SignatureOrigin.ONBOARDING -> navController.navigate(Today) {
                            popUpTo<Login> { inclusive = true }
                        }

                        SignatureOrigin.SETTINGS -> navController.popBackStack()
                    }
                },
            )
        }

        composable<Today> {
            HomeRoute(
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
                onNavigateToBottomBar = navController::navigateToBottomBar,
            )
        }

        composable<Display> {
            DisplayRoute(
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenFeed = { post, dateLabel, topic ->
                    navController.navigate(
                        Feed(
                            postId = post.id,
                            imageUrl = post.imageUrl,
                            signatureUrl = post.signatureUrl,
                            contentDescription = post.contentDescription,
                            title = post.title,
                            likeCount = post.likeCount,
                            dateLabel = dateLabel,
                            topic = topic,
                        ),
                    )
                },
            )
        }

        composable<Feed> { backStackEntry ->
            val feed = backStackEntry.toRoute<Feed>()

            FeedRoute(
                initialContent = FeedContentState.Success(
                    dateLabel = feed.dateLabel,
                    topic = feed.topic,
                    post = Post(
                        id = feed.postId,
                        imageUrl = feed.imageUrl,
                        signatureUrl = feed.signatureUrl,
                        contentDescription = feed.contentDescription,
                        title = feed.title,
                        likeCount = feed.likeCount,
                    ),
                    isLiked = false,
                ),
                onNavigateBack = { navController.popBackStack() },
                onDeleteClick = { navController.popBackStack() },
            )
        }

        composable<Record> {
            PlaceholderTabRoute(
                title = "기록",
                selectedItem = ChalkakBottomBarItem.RECORD,
                navController = navController,
            )
        }

        composable<Settings> {
            SettingsRoute(
                onNavigateToLogin = {
                    navController.navigate(Login) {
                        popUpTo<Today> { inclusive = true }
                    }
                },
                onNavigateToSignature = {
                    navController.navigate(Signature(SignatureOrigin.SETTINGS))
                },
                onOpenPrivacyPolicy = {},
                onOpenTerms = {},
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
            )
        }

        composable<PhotoUpload> {
            PhotoUploadRoute(
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun PlaceholderTabRoute(
    title: String,
    selectedItem: ChalkakBottomBarItem,
    navController: NavHostController,
) {
    Scaffold(
        bottomBar = {
            ChalkakBottomBar(
                selectedItem = selectedItem,
                onItemSelected = navController::navigateToBottomBar,
                onAddClick = { navController.navigate(PhotoUpload) },
            )
        },
    ) { innerPadding ->
        PlaceholderScreen(
            title = title,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$title 화면 준비 중",
            color = ChalkakTheme.colors.textPrimary,
        )
    }
}

private fun NavHostController.navigateToBottomBar(item: ChalkakBottomBarItem) {
    val destination = when (item) {
        ChalkakBottomBarItem.TODAY -> Today
        ChalkakBottomBarItem.DISPLAY -> Display
        ChalkakBottomBarItem.RECORD -> Record
        ChalkakBottomBarItem.SETTINGS -> Settings
    }

    navigate(destination) {
        launchSingleTop = true
        restoreState = true
        popUpTo(Today) {
            saveState = true
        }
    }
}
