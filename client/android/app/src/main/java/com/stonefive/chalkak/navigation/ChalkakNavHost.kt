package com.stonefive.chalkak.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.legal.LegalDocument
import com.stonefive.chalkak.core.legal.LegalDocumentDialog
import com.stonefive.chalkak.core.legal.LegalDocumentLauncher
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.RecordPhoto
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.feed.FeedContentState
import com.stonefive.chalkak.feature.feed.FeedRoute
import com.stonefive.chalkak.feature.home.HomeRoute
import com.stonefive.chalkak.feature.login.LoginRoute
import com.stonefive.chalkak.feature.record.RecordRoute
import com.stonefive.chalkak.feature.settings.SettingsRoute
import com.stonefive.chalkak.feature.signature.SignaturePreviewRoute
import com.stonefive.chalkak.feature.signature.SignatureRoute
import com.stonefive.chalkak.feature.terms.TermsRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessContent
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessScreen
import java.time.LocalDate

@Composable
fun ChalkakNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var signaturePreviewPng by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val legalDocumentLauncher = remember {
        LegalDocumentLauncher(
            showLegalDocument = { selectedLegalDocument = it },
            onOpenFailed = {
                Toast
                    .makeText(
                        context,
                        "문서를 열 수 없어요",
                        Toast.LENGTH_SHORT,
                    ).show()
            },
        )
    }

    selectedLegalDocument?.let { document ->
        LegalDocumentDialog(
            document = document,
            closeContentDescription = "닫기",
            loadFailedText = "문서를 불러오지 못했어요",
            retryText = "다시 시도",
            onDismiss = { selectedLegalDocument = null },
        )
    }

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
                onServiceTermsViewClick = {
                    legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                },
                onPrivacyPolicyViewClick = {
                    legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                },
            )
        }

        composable<Signature> { backStackEntry ->
            val signature = backStackEntry.toRoute<Signature>()

            SignatureRoute(
                onSignatureSaved = { signaturePng ->
                    when (signature.origin) {
                        SignatureOrigin.ONBOARDING -> {
                            signaturePreviewPng = signaturePng
                            navController.navigate(SignaturePreview)
                        }

                        SignatureOrigin.SETTINGS -> navController.popBackStack()
                    }
                },
            )
        }

        composable<SignaturePreview> {
            SignaturePreviewRoute(
                imageModel = R.drawable.preview_photo,
                signatureModel = signaturePreviewPng
                    ?: R.drawable.preview_signature,
                onRedrawClick = {
                    navController.popBackStack()
                    signaturePreviewPng = null
                },
                onStartClick = {
                    navController.navigate(Today) {
                        popUpTo<Login> { inclusive = true }
                    }
                    signaturePreviewPng = null
                },
            )
        }

        composable<Today> { backStackEntry ->
            val homeSelectionSignal by backStackEntry.savedStateHandle
                .getStateFlow(HOME_SELECTION_REQUEST_KEY, 0)
                .collectAsStateWithLifecycle()

            HomeRoute(
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
                onNavigateToBottomBar = navController::navigateToBottomBar,
                selectionSignal = homeSelectionSignal,
            )
        }

        composable<Display> { backStackEntry ->
            val display = backStackEntry.toRoute<Display>()

            DisplayRoute(
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
                onNavigateToBottomBar = navController::navigateToBottomBar,
                initialDate = display.date.toLocalDateOrNull(),
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
                            isOwnedByCurrentUser = post.isOwnedByCurrentUser,
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
                        isOwnedByCurrentUser = feed.isOwnedByCurrentUser,
                    ),
                    isLiked = false,
                ),
                onNavigateBack = { navController.popBackStack() },
                onDeleteClick = { navController.popBackStack() },
            )
        }

        composable<Record> {
            RecordRoute(
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenFeed = { photo ->
                    navController.navigate(photo.toFeedRoute())
                },
                onOpenDisplay = { date ->
                    navController.navigateToDisplay(date)
                },
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
                onOpenPrivacyPolicy = {
                    legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                },
                onOpenTerms = {
                    legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                },
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenPhotoUpload = { navController.navigate(PhotoUpload) },
            )
        }

        composable<PhotoUpload> {
            PhotoUploadRoute(
                onBack = { navController.popBackStack() },
                onSubmitted = { submission ->
                    navController.navigate(
                        PhotoUploadSuccess(
                            imageModel = submission.imageModel,
                            caption = submission.caption,
                            dateLabel = submission.content.dateLabel,
                            topic = submission.content.topic,
                            nickname = submission.content.nickname,
                            exhibitionCount = submission.content.exhibitionCount,
                        ),
                    )
                },
            )
        }

        composable<PhotoUploadSuccess> { backStackEntry ->
            val success = backStackEntry.toRoute<PhotoUploadSuccess>()

            PhotoUploadSuccessScreen(
                imageModel = success.imageModel,
                caption = success.caption,
                content = PhotoUploadSuccessContent(
                    dateLabel = success.dateLabel,
                    topic = success.topic,
                    nickname = success.nickname,
                    exhibitionCount = success.exhibitionCount,
                ),
                onConfirmClick = {
                    navController.navigate(Display(date = "")) {
                        popUpTo<PhotoUpload> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

private fun NavHostController.navigateToBottomBar(item: ChalkakBottomBarItem) {
    if (item == ChalkakBottomBarItem.TODAY) {
        val todayEntry = getBackStackEntry<Today>()
        val currentSelectionSignal = todayEntry.savedStateHandle[HOME_SELECTION_REQUEST_KEY] ?: 0
        todayEntry.savedStateHandle[HOME_SELECTION_REQUEST_KEY] = currentSelectionSignal + 1
    }

    val destination = when (item) {
        ChalkakBottomBarItem.TODAY -> Today
        ChalkakBottomBarItem.DISPLAY -> Display(date = "")
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

private const val HOME_SELECTION_REQUEST_KEY = "home_selection_request"

private fun NavHostController.navigateToDisplay(date: LocalDate) {
    navigate(Display(date = date.toString()))
}

private fun RecordPhoto.toFeedRoute(): Feed = Feed(
    postId = "record-$date",
    imageUrl = imageUrl,
    signatureUrl = signatureUrl,
    contentDescription = contentDescription,
    title = title,
    likeCount = 0,
    dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일의 주제",
    topic = title?.takeIf(String::isNotBlank) ?: "오늘의 기록",
)

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(this)
}.getOrNull()
