package com.stonefive.chalkak.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.analytics.AnalyticsTracker
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.legal.LegalDocument
import com.stonefive.chalkak.core.legal.LegalDocumentDialog
import com.stonefive.chalkak.core.legal.LegalDocumentLauncher
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.feed.FeedContentState
import com.stonefive.chalkak.feature.feed.FeedRoute
import com.stonefive.chalkak.feature.home.HomeRoute
import com.stonefive.chalkak.feature.login.LoginRoute
import com.stonefive.chalkak.feature.record.RecordRoute
import com.stonefive.chalkak.feature.settings.SettingsRoute
import com.stonefive.chalkak.feature.signature.ChangeSignaturePreviewRoute
import com.stonefive.chalkak.feature.signature.ChangeSignatureRoute
import com.stonefive.chalkak.feature.signature.OnboardingSignaturePreviewRoute
import com.stonefive.chalkak.feature.signature.OnboardingSignatureRoute
import com.stonefive.chalkak.feature.signature.SignUpViewModel
import com.stonefive.chalkak.feature.terms.TermsRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessContent
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessScreen
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ChalkakNavHost(
    analyticsTracker: AnalyticsTracker,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login,
    signUpViewModel: SignUpViewModel? = null,
) {
    val application = LocalContext.current.applicationContext as ChalkakApplication
    val sessionState by application.appContainer.authRepository.sessionState
        .collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var signaturePreviewPng by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var pendingMessage by remember { mutableStateOf<UiMessage?>(null) }
    var nextMessageId by remember { mutableLongStateOf(0L) }

    UiMessageEffect(
        message = pendingMessage,
        onMessageShown = { messageId ->
            if (pendingMessage?.id == messageId) pendingMessage = null
        },
    )

    val showToast: (String) -> Unit = { text ->
        pendingMessage = UiMessage.Toast(id = nextMessageId++, text = text)
    }

    val legalDocumentLauncher = remember {
        LegalDocumentLauncher(
            showLegalDocument = { selectedLegalDocument = it },
            onOpenFailed = {
                showToast("문서를 열 수 없어요")
            },
        )
    }

    LaunchedEffect(currentBackStackEntry) {
        currentBackStackEntry
            ?.destination
            ?.analyticsScreen()
            ?.let { screen ->
                analyticsTracker.trackScreenView(
                    screenName = screen.name,
                    screenClass = screen.screenClass,
                )
            }
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
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<Login> {
            LoginRoute(
                onSignUpRequired = {
                    navController.navigate(Terms)
                },
            )
        }

        composable<Terms> {
            TermsRoute(
                onNextClick = {
                    navController.navigate(OnboardingSignature)
                },
                onServiceTermsViewClick = {
                    legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                },
                onPrivacyPolicyViewClick = {
                    legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                },
            )
        }

        composable<OnboardingSignature> {
            OnboardingSignatureRoute(
                onPreviewRequested = { signaturePng ->
                    signaturePreviewPng = signaturePng
                    navController.navigate(OnboardingSignaturePreview)
                },
            )
        }

        composable<ChangeSignature> {
            ChangeSignatureRoute(
                onPreviewRequested = { signaturePng ->
                    signaturePreviewPng = signaturePng
                    navController.navigate(ChangeSignaturePreview)
                },
            )
        }

        composable<OnboardingSignaturePreview> {
            signaturePreviewPng?.let { signaturePng ->
                val previewSignUpViewModel = signUpViewModel
                    ?: viewModel(factory = SignUpViewModel.Factory)

                OnboardingSignaturePreviewRoute(
                    imageModel = R.drawable.preview_photo,
                    signaturePng = signaturePng,
                    onRedrawClick = {
                        navController.popBackStack()
                        signaturePreviewPng = null
                    },
                    onSignUpSuccess = {
                        navController.navigate(Today) {
                            popUpTo<Terms> { inclusive = true }
                            launchSingleTop = true
                        }
                        signaturePreviewPng = null
                    },
                    onReauthenticationRequired = {
                        navController.navigate(Login) {
                            popUpTo<Login> { inclusive = true }
                        }
                        signaturePreviewPng = null
                    },
                    viewModel = previewSignUpViewModel,
                )
            }
        }

        composable<ChangeSignaturePreview> {
            signaturePreviewPng?.let { signaturePng ->
                ChangeSignaturePreviewRoute(
                    signaturePng = signaturePng,
                    onRedrawClick = {
                        navController.popBackStack()
                        signaturePreviewPng = null
                    },
                    onSignatureChanged = { profile ->
                        navController.getBackStackEntry<Settings>().savedStateHandle[
                            SETTINGS_SIGNATURE_UPDATED_KEY,
                        ] = profile.signatureUrl
                        signaturePreviewPng = null
                        navController.popBackStack(Settings, inclusive = false)
                    },
                )
            }
        }

        composable<Today> {
            HomeRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = { item ->
                    navController.navigateToBottomBar(item, analyticsTracker)
                },
            )
        }

        composable<Display> { backStackEntry ->
            val display = backStackEntry.toRoute<Display>()

            DisplayRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = { item ->
                    navController.navigateToBottomBar(item, analyticsTracker)
                },
                initialDate = display.date.toLocalDateOrNull(),
                onOpenFeed = { post, dateLabel, topic ->
                    if (sessionState is UserSessionState.Authenticated) {
                        navController.navigate(
                            Feed(
                                postId = post.id,
                                originalImageUrl = post.originalImageUrl,
                                thumbnailImageUrl = post.thumbnailImageUrl,
                                signatureOriginalImageUrl = post.signatureOriginalImageUrl,
                                signatureThumbnailImageUrl = post.signatureThumbnailImageUrl,
                                contentDescription = post.contentDescription,
                                title = post.title,
                                likeCount = post.likeCount,
                                isLiked = post.isLiked,
                                dateLabel = dateLabel,
                                topic = topic,
                                isOwnedByCurrentUser = post.isOwnedByCurrentUser,
                            ),
                        )
                    } else {
                        showToast(DISPLAY_FEED_LOGIN_REQUIRED_MESSAGE)
                    }
                },
            )
        }

        composable<Feed> { backStackEntry ->
            val feed = backStackEntry.toRoute<Feed>()

            FeedRoute(
                postId = feed.postId.takeIf { feed.fetchDetail },
                initialContent = FeedContentState.Success(
                    dateLabel = feed.dateLabel,
                    topic = feed.topic,
                    post = Post(
                        id = feed.postId,
                        originalImageUrl = feed.originalImageUrl,
                        thumbnailImageUrl = feed.thumbnailImageUrl,
                        signatureOriginalImageUrl = feed.signatureOriginalImageUrl,
                        signatureThumbnailImageUrl = feed.signatureThumbnailImageUrl,
                        contentDescription = feed.contentDescription,
                        title = feed.title,
                        likeCount = feed.likeCount,
                        isLiked = feed.isLiked,
                        isOwnedByCurrentUser = feed.isOwnedByCurrentUser,
                    ),
                    isLiked = false,
                ),
                onNavigateBack = { navController.popBackStack() },
                onPostDeleted = { postId ->
                    showToast(POST_DELETED_MESSAGE)
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        POST_DELETED_KEY,
                        postId,
                    )
                    navController.popBackStack()
                },
            )
        }

        composable<FeedById> { backStackEntry ->
            val feed = backStackEntry.toRoute<FeedById>()

            FeedRoute(
                postId = feed.postId,
                isOwnedByCurrentUser = feed.isOwnedByCurrentUser,
                onNavigateBack = { navController.popBackStack() },
                onPostDeleted = { postId ->
                    showToast(POST_DELETED_MESSAGE)
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        POST_DELETED_KEY,
                        postId,
                    )
                    navController.popBackStack()
                },
            )
        }

        composable<Record> { backStackEntry ->
            val deletedPostId by backStackEntry.savedStateHandle
                .getStateFlow<String?>(POST_DELETED_KEY, null)
                .collectAsStateWithLifecycle()

            RecordRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = { item ->
                    navController.navigateToBottomBar(item, analyticsTracker)
                },
                onOpenFeed = { postId ->
                    navController.navigate(
                        FeedById(
                            postId = postId,
                            isOwnedByCurrentUser = true,
                        ),
                    )
                },
                onOpenDisplay = { date ->
                    navController.navigateToDisplay(date)
                },
                deletedPostId = deletedPostId,
                onDeletedPostConsumed = {
                    backStackEntry.savedStateHandle[POST_DELETED_KEY] = null
                },
            )
        }

        composable<Settings> { backStackEntry ->
            val updatedSignatureUrl by backStackEntry.savedStateHandle
                .getStateFlow<String?>(SETTINGS_SIGNATURE_UPDATED_KEY, null)
                .collectAsStateWithLifecycle()

            SettingsRoute(
                signatureUpdateUrl = updatedSignatureUrl,
                onNavigateToLogin = {
                    navController.navigate(Login) {
                        popUpTo<Today> { inclusive = true }
                    }
                },
                onNavigateToSignature = {
                    navController.navigate(ChangeSignature)
                },
                onOpenPrivacyPolicy = {
                    legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                },
                onOpenTerms = {
                    legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                },
                onNavigateToBottomBar = { item ->
                    navController.navigateToBottomBar(item, analyticsTracker)
                },
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
            )
        }

        composable<PhotoUpload> { backStackEntry ->
            val upload = backStackEntry.toRoute<PhotoUpload>()
            PhotoUploadRoute(
                topicDate = LocalDate.parse(upload.topicDate),
                onBack = { navController.popBackStack() },
                onReauthenticationRequired = {
                    navController.navigate(Login) {
                        popUpTo<Today> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSubmitted = { submission ->
                    navController.navigate(
                        PhotoUploadSuccess(
                            imageModel = submission.imageModel,
                            caption = submission.caption,
                            date = submission.content.date
                                .toString(),
                            topic = submission.content.topic,
                            moderationStatus = submission.content.moderationStatus,
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
                    date = LocalDate.parse(success.date),
                    topic = success.topic,
                    moderationStatus = success.moderationStatus,
                ),
                onConfirmClick = {
                    navController.popBackStack<PhotoUpload>(inclusive = true)
                },
            )
        }
    }
}

private fun NavHostController.navigateToBottomBar(
    item: ChalkakBottomBarItem,
    analyticsTracker: AnalyticsTracker,
) {
    analyticsTracker.trackBottomNavigationSelection(item.analyticsName)

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

private val ChalkakBottomBarItem.analyticsName: String
    get() = when (this) {
        ChalkakBottomBarItem.TODAY -> "today"
        ChalkakBottomBarItem.DISPLAY -> "display"
        ChalkakBottomBarItem.RECORD -> "record"
        ChalkakBottomBarItem.SETTINGS -> "settings"
    }

private fun NavDestination.analyticsScreen(): AnalyticsScreen? = when {
    hasRoute<Today>() -> AnalyticsScreen(name = "today", screenClass = "Today")
    hasRoute<Display>() -> AnalyticsScreen(name = "display", screenClass = "Display")
    hasRoute<Feed>() || hasRoute<FeedById>() -> AnalyticsScreen(name = "feed", screenClass = "Feed")
    hasRoute<Record>() -> AnalyticsScreen(name = "record", screenClass = "Record")
    hasRoute<Settings>() -> AnalyticsScreen(name = "settings", screenClass = "Settings")
    else -> null
}

private data class AnalyticsScreen(
    val name: String,
    val screenClass: String,
)

private const val SETTINGS_SIGNATURE_UPDATED_KEY = "settings_signature_updated"
private const val POST_DELETED_KEY = "post_deleted"
private const val POST_DELETED_MESSAGE = "게시물을 삭제했어요"
private const val DISPLAY_FEED_LOGIN_REQUIRED_MESSAGE = "게시물 피드를 보려면 로그인이 필요해요"

private fun NavHostController.navigateToDisplay(date: LocalDate) {
    navigate(Display(date = date.toString()))
}

private fun NavHostController.navigateToPhotoUpload() {
    navigate(PhotoUpload(topicDate = LocalDate.now(KST).toString()))
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(this)
}.getOrNull()

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
