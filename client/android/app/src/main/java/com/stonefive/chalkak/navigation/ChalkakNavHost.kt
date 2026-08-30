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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login,
    signUpViewModel: SignUpViewModel? = null,
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

        composable<Today> { backStackEntry ->
            val homeSelectionSignal by backStackEntry.savedStateHandle
                .getStateFlow(HOME_SELECTION_REQUEST_KEY, 0)
                .collectAsStateWithLifecycle()

            HomeRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenFeed = { post, dateLabel, topic ->
                    navController.navigate(
                        post.toFeedRoute(
                            dateLabel = dateLabel,
                            topic = topic,
                        ),
                    )
                },
                selectionSignal = homeSelectionSignal,
            )
        }

        composable<Display> { backStackEntry ->
            val display = backStackEntry.toRoute<Display>()

            DisplayRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = navController::navigateToBottomBar,
                initialDate = display.date.toLocalDateOrNull(),
                onOpenFeed = { post, dateLabel, topic ->
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
                onDeleteClick = { navController.popBackStack() },
            )
        }

        composable<Record> {
            RecordRoute(
                onOpenPhotoUpload = navController::navigateToPhotoUpload,
                onNavigateToBottomBar = navController::navigateToBottomBar,
                onOpenFeed = { photo ->
                    navController.navigate(photo.toFeedRoute())
                },
                onOpenDisplay = { date ->
                    navController.navigateToDisplay(date)
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
                onNavigateToBottomBar = navController::navigateToBottomBar,
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
                            dateLabel = submission.content.dateLabel,
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
                    dateLabel = success.dateLabel,
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
private const val SETTINGS_SIGNATURE_UPDATED_KEY = "settings_signature_updated"

private fun Post.toFeedRoute(
    dateLabel: String,
    topic: String,
): Feed = Feed(
    postId = id,
    originalImageUrl = originalImageUrl,
    thumbnailImageUrl = thumbnailImageUrl,
    signatureOriginalImageUrl = signatureOriginalImageUrl,
    signatureThumbnailImageUrl = signatureThumbnailImageUrl,
    contentDescription = contentDescription,
    title = title,
    likeCount = likeCount,
    isLiked = isLiked,
    dateLabel = dateLabel,
    topic = topic,
    isOwnedByCurrentUser = isOwnedByCurrentUser,
)

private fun NavHostController.navigateToDisplay(date: LocalDate) {
    navigate(Display(date = date.toString()))
}

private fun NavHostController.navigateToPhotoUpload() {
    navigate(PhotoUpload(topicDate = LocalDate.now(KST).toString()))
}

private fun RecordPhoto.toFeedRoute(): Feed = Feed(
    postId = "record-$date",
    originalImageUrl = imageUrl,
    thumbnailImageUrl = imageUrl,
    signatureOriginalImageUrl = signatureUrl,
    signatureThumbnailImageUrl = signatureUrl,
    contentDescription = contentDescription,
    title = title,
    likeCount = 0,
    dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일의 주제",
    topic = title?.takeIf(String::isNotBlank) ?: "오늘의 기록",
    fetchDetail = false,
)

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(this)
}.getOrNull()

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
