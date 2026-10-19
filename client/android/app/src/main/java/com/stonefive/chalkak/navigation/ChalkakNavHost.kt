package com.stonefive.chalkak.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.stonefive.chalkak.core.designsystem.component.image.PHOTO_CONTENT_FADE_DURATION_MILLIS
import com.stonefive.chalkak.core.designsystem.component.image.PhotoTransitionProvider
import com.stonefive.chalkak.core.designsystem.component.image.rememberPhotoTransitionCoordinator
import com.stonefive.chalkak.core.legal.LegalDocument
import com.stonefive.chalkak.core.legal.LegalDocumentDialog
import com.stonefive.chalkak.core.legal.LegalDocumentLauncher
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.core.ui.UiMessageEffect
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.feed.FeedContentState
import com.stonefive.chalkak.feature.feed.FeedRoute
import com.stonefive.chalkak.feature.feedback.FeedbackRoute
import com.stonefive.chalkak.feature.home.HomeRoute
import com.stonefive.chalkak.feature.home.HomeViewModel
import com.stonefive.chalkak.feature.login.LoginRoute
import com.stonefive.chalkak.feature.notification.NotificationDetailRoute
import com.stonefive.chalkak.feature.notification.NotificationItemUiState
import com.stonefive.chalkak.feature.notification.NotificationRoute
import com.stonefive.chalkak.feature.record.RecordRoute
import com.stonefive.chalkak.feature.settings.SettingsRoute
import com.stonefive.chalkak.feature.signature.ChangeSignaturePreviewRoute
import com.stonefive.chalkak.feature.signature.ChangeSignatureRoute
import com.stonefive.chalkak.feature.signature.OnboardingSignaturePreviewRoute
import com.stonefive.chalkak.feature.signature.OnboardingSignatureRoute
import com.stonefive.chalkak.feature.signature.SignUpViewModel
import com.stonefive.chalkak.feature.terms.TermsRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadEntryGateUiEvent
import com.stonefive.chalkak.feature.upload.PhotoUploadEntryGateViewModel
import com.stonefive.chalkak.feature.upload.PhotoUploadRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessContent
import com.stonefive.chalkak.feature.upload.PhotoUploadSuccessScreen
import com.stonefive.chalkak.feature.web.WebPageRoute
import java.time.LocalDate

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChalkakNavHost(
    analyticsTracker: AnalyticsTracker,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login,
    signUpViewModel: SignUpViewModel? = null,
) {
    val photoTransitionCoordinator = rememberPhotoTransitionCoordinator()
    val context = LocalContext.current
    val application = context.applicationContext as ChalkakApplication
    val sessionState by application.appContainer.authRepository.sessionState
        .collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val pendingNotificationPush by application.appContainer.pendingNotificationNavigation.pending
        .collectAsStateWithLifecycle()
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var signaturePreviewPng by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var pendingMessage by remember { mutableStateOf<UiMessage?>(null) }
    var nextMessageId by remember { mutableLongStateOf(0L) }
    val photoUploadEntryGateViewModel: PhotoUploadEntryGateViewModel =
        viewModel(factory = PhotoUploadEntryGateViewModel.Factory)
    val photoUploadEntryGateUiState by photoUploadEntryGateViewModel.uiState
        .collectAsStateWithLifecycle()
    val pushNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {}

    UiMessageEffect(
        message = pendingMessage,
        onMessageShown = { messageId ->
            if (pendingMessage?.id == messageId) pendingMessage = null
        },
    )
    UiMessageEffect(
        message = photoUploadEntryGateUiState.pendingMessage,
        onMessageShown = photoUploadEntryGateViewModel::onMessageShown,
    )

    val showToast: (String) -> Unit = { text ->
        pendingMessage = UiMessage.Toast(id = nextMessageId++, text = text)
    }

    val navigateToLogin: () -> Unit = {
        navController.navigate(Login) {
            popUpTo<Home> { inclusive = true }
            launchSingleTop = true
        }
    }

    val openPhotoUpload: () -> Unit = {
        photoUploadEntryGateViewModel.openPhotoUpload()
    }

    val navigateToBottomBar: (NavBackStackEntry, ChalkakBottomBarItem) -> Unit = { entry, item ->
        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
            if (item == ChalkakBottomBarItem.RECORD &&
                sessionState !is UserSessionState.Authenticated
            ) {
                showToast(RECORD_LOGIN_REQUIRED_MESSAGE)
            } else {
                navController.navigateToBottomBar(item, analyticsTracker)
            }
        }
    }

    val legalDocumentLauncher = remember {
        LegalDocumentLauncher(
            showLegalDocument = { selectedLegalDocument = it },
            onOpenFailed = {
                showToast("문서를 열 수 없어요")
            },
        )
    }

    LaunchedEffect(currentBackStackEntry, selectedLegalDocument) {
        val screen = selectedLegalDocument?.analyticsScreen()
            ?: currentBackStackEntry?.destination?.analyticsScreen()
        screen?.let {
            analyticsTracker.trackScreenView(
                screenName = it.name,
                screenClass = it.screenClass,
            )
        }
    }

    LaunchedEffect(photoUploadEntryGateViewModel) {
        photoUploadEntryGateViewModel.uiEvent.collect { event ->
            when (event) {
                is PhotoUploadEntryGateUiEvent.OpenPhotoUpload ->
                    navController.navigateToPhotoUpload(event.topicDate)

                PhotoUploadEntryGateUiEvent.NavigateToLogin -> navigateToLogin()
            }
        }
    }

    LaunchedEffect(sessionState) {
        if (sessionState is UserSessionState.Authenticated &&
            context.shouldRequestPushNotificationPermissionOnce()
        ) {
            context.markPushNotificationPermissionRequested()
            pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(
        pendingNotificationPush,
        sessionState,
        currentBackStackEntry?.destination,
    ) {
        val push = pendingNotificationPush ?: return@LaunchedEffect
        if (sessionState !is UserSessionState.Authenticated) return@LaunchedEffect
        if (currentBackStackEntry == null) return@LaunchedEffect

        if (push.type == TOPIC) {
            application.appContainer.pendingNotificationNavigation
                .consume(push)
            navController.openNotificationTarget(
                type = push.type,
                sourceType = push.sourceType,
                sourceId = push.sourceId,
                notificationId = push.notificationId,
            )
            return@LaunchedEffect
        }

        if (currentBackStackEntry?.destination?.hasRoute<Home>() != true) return@LaunchedEffect

        val notificationId = push.notificationId?.takeIf(String::isNotBlank)
        if (notificationId == null) {
            application.appContainer.pendingNotificationNavigation
                .consume(push)
            showToast("알림 정보를 확인할 수 없어요.")
            return@LaunchedEffect
        }

        when (val result = application.appContainer.markNotificationRead(notificationId)) {
            is NotificationResult.Success -> {
                application.appContainer.pendingNotificationNavigation
                    .consume(push)
                navController.openNotificationTarget(
                    type = push.type,
                    sourceType = push.sourceType,
                    sourceId = push.sourceId,
                    notificationId = notificationId,
                )
            }

            is NotificationResult.Failure -> {
                application.appContainer.pendingNotificationNavigation
                    .consume(push)
                if (result.reason == NotificationFailure.Network) {
                    showToast("읽음 상태를 저장하지 못했어요. 네트워크 연결을 확인해 주세요.")
                    navController.openNotificationTarget(
                        type = push.type,
                        sourceType = push.sourceType,
                        sourceId = push.sourceId,
                        notificationId = notificationId,
                    )
                } else {
                    showToast("알림이 만료되었거나 현재 계정에서 확인할 수 없어요.")
                    if ((result.reason as? NotificationFailure.Http)?.statusCode == 404) {
                        navController.navigate(Notifications) { launchSingleTop = true }
                    }
                }
            }
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

    SharedTransitionLayout {
        val sharedTransitionScope = this
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
        ) {
            composable<Login> {
                LoginRoute(
                    onLoginSuccess = {
                        navController.navigate(Home) {
                            popUpTo<Login> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGuestAccessGranted = {
                        navController.navigate(Home) {
                            popUpTo<Login> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSignUpRequired = {
                        navController.navigate(Terms)
                    },
                )
            }

            composable<Terms> { backStackEntry ->
                TermsRoute(
                    onNextClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.navigate(OnboardingSignature)
                    },
                    onServiceTermsViewClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                    },
                    onPrivacyPolicyViewClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                    },
                )
            }

            composable<OnboardingSignature> { backStackEntry ->
                OnboardingSignatureRoute(
                    onPreviewRequested = { signaturePng ->
                        if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                            signaturePreviewPng = signaturePng
                            navController.navigate(OnboardingSignaturePreview)
                        }
                    },
                )
            }

            composable<ChangeSignature> { backStackEntry ->
                ChangeSignatureRoute(
                    onBackClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                    onPreviewRequested = { signaturePng ->
                        if (navController.currentBackStackEntry?.id == backStackEntry.id) {
                            signaturePreviewPng = signaturePng
                            navController.navigate(ChangeSignaturePreview)
                        }
                    },
                )
            }

            composable<OnboardingSignaturePreview> { backStackEntry ->
                signaturePreviewPng?.let { signaturePng ->
                    val previewSignUpViewModel = signUpViewModel
                        ?: viewModel(factory = SignUpViewModel.Factory)

                    OnboardingSignaturePreviewRoute(
                        imageModel = R.drawable.preview_photo,
                        signaturePng = signaturePng,
                        onRedrawClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                            navController.popBackStack()
                            signaturePreviewPng = null
                        },
                        onSignUpSuccess = {
                            navController.navigate(Home) {
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

            composable<ChangeSignaturePreview> { backStackEntry ->
                signaturePreviewPng?.let { signaturePng ->
                    ChangeSignaturePreviewRoute(
                        signaturePng = signaturePng,
                        onRedrawClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
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

            composable<Home> { backStackEntry ->
                PhotoTransitionProvider(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    coordinator = photoTransitionCoordinator,
                ) {
                    HomeRoute(
                        onOpenPhotoUpload = dropUnlessResumed(lifecycleOwner = backStackEntry, block = openPhotoUpload),
                        onNavigateToBottomBar = { item -> navigateToBottomBar(backStackEntry, item) },
                        onOpenRecord = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                            navigateToBottomBar(backStackEntry, ChalkakBottomBarItem.RECORD)
                        },
                        onOpenDisplay = { date ->
                            if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                navController.navigate(
                                    Display(
                                        date = date.toString(),
                                        sort = PostSort.POPULAR.name,
                                    ),
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenFeed = { post ->
                            if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                if (sessionState is UserSessionState.Authenticated) {
                                    navController.navigate(
                                        FeedById(
                                            postId = post.id,
                                            isOwnedByCurrentUser = post.isOwnedByCurrentUser,
                                            thumbnailImageUrl = post.thumbnailImageUrl,
                                        ),
                                    )
                                } else {
                                    showToast(DISPLAY_FEED_LOGIN_REQUIRED_MESSAGE)
                                }
                            }
                        },
                        onNotificationClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                            navController.navigate(Notifications) { launchSingleTop = true }
                        },
                        onIssueClick = { title, url ->
                            if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                navController.navigate(WebPage(title = title, url = url)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        viewModel = homeViewModel,
                    )
                }
            }

            composable<WebPage> { backStackEntry ->
                val page = backStackEntry.toRoute<WebPage>()
                WebPageRoute(
                    title = page.title,
                    url = page.url,
                    onBackClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                )
            }

            composable<Notifications> { backStackEntry ->
                NotificationRoute(
                    onBackClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                    onNotificationClick = { item ->
                        if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.openNotificationTarget(item)
                        }
                    },
                    onReadFailureMessage = showToast,
                )
            }

            composable<NotificationDetail> { backStackEntry ->
                val detail = backStackEntry.toRoute<NotificationDetail>()
                NotificationDetailRoute(
                    notificationId = detail.notificationId,
                    onBackClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                    onOpenNotificationList = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        if (!navController.popBackStack<Notifications>(inclusive = false)) {
                            navController.navigate(Notifications) { launchSingleTop = true }
                        }
                    },
                )
            }

            composable<Display> { backStackEntry ->
                PhotoTransitionProvider(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    coordinator = photoTransitionCoordinator,
                ) {
                    val display = backStackEntry.toRoute<Display>()

                    DisplayRoute(
                        onOpenPhotoUpload = dropUnlessResumed(lifecycleOwner = backStackEntry, block = openPhotoUpload),
                        onNavigateToBottomBar = { item -> navigateToBottomBar(backStackEntry, item) },
                        initialDate = display.date.toLocalDateOrNull(),
                        initialSort = PostSort.entries.firstOrNull {
                            it.name == display.sort
                        } ?: PostSort.LATEST,
                        onOpenFeed = { post, dateLabel, topic, topicDate ->
                            if (backStackEntry.lifecycle.currentState != Lifecycle.State.RESUMED) {
                                false
                            } else if (sessionState is UserSessionState.Authenticated) {
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
                                        topicDate = topicDate?.toString(),
                                        isOwnedByCurrentUser = post.isOwnedByCurrentUser,
                                    ),
                                )
                                true
                            } else {
                                showToast(DISPLAY_FEED_LOGIN_REQUIRED_MESSAGE)
                                false
                            }
                        },
                    )
                }
            }

            composable<Feed>(
                enterTransition = { fadeIn(tween(PHOTO_CONTENT_FADE_DURATION_MILLIS)) },
            ) { backStackEntry ->
                PhotoTransitionProvider(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    coordinator = photoTransitionCoordinator,
                ) {
                    val feed = backStackEntry.toRoute<Feed>()
                    DisposableEffect(backStackEntry) {
                        onDispose { photoTransitionCoordinator.clear(feed.postId) }
                    }

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
                            topicDate = feed.topicDate?.toLocalDateOrNull(),
                        ),
                        onNavigateBack = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                            navController.popBackStack()
                        },
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
            }

            composable<FeedById>(
                enterTransition = { fadeIn(tween(PHOTO_CONTENT_FADE_DURATION_MILLIS)) },
            ) { backStackEntry ->
                PhotoTransitionProvider(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    coordinator = photoTransitionCoordinator,
                ) {
                    val feed = backStackEntry.toRoute<FeedById>()
                    DisposableEffect(backStackEntry) {
                        onDispose { photoTransitionCoordinator.clear(feed.postId) }
                    }

                    FeedRoute(
                        postId = feed.postId,
                        entryThumbnailImageUrl = feed.thumbnailImageUrl,
                        isOwnedByCurrentUser = feed.isOwnedByCurrentUser,
                        onNavigateBack = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                            navController.popBackStack()
                        },
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
            }

            composable<Record> { backStackEntry ->
                PhotoTransitionProvider(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    coordinator = photoTransitionCoordinator,
                ) {
                    val deletedPostId by backStackEntry.savedStateHandle
                        .getStateFlow<String?>(POST_DELETED_KEY, null)
                        .collectAsStateWithLifecycle()

                    RecordRoute(
                        onOpenPhotoUpload = dropUnlessResumed(lifecycleOwner = backStackEntry, block = openPhotoUpload),
                        onNavigateToBottomBar = { item -> navigateToBottomBar(backStackEntry, item) },
                        onOpenFeed = { postId, thumbnailImageUrl ->
                            if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                navController.navigate(
                                    FeedById(
                                        postId = postId,
                                        isOwnedByCurrentUser = true,
                                        thumbnailImageUrl = thumbnailImageUrl,
                                    ),
                                )
                            }
                        },
                        onOpenDisplay = { date ->
                            if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                                navController.navigateToDisplay(date)
                            }
                        },
                        onNavigateToLogin = dropUnlessResumed(lifecycleOwner = backStackEntry, block = navigateToLogin),
                        deletedPostId = deletedPostId,
                        onDeletedPostConsumed = {
                            backStackEntry.savedStateHandle[POST_DELETED_KEY] = null
                        },
                    )
                }
            }

            composable<Settings> { backStackEntry ->
                val updatedSignatureUrl by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(SETTINGS_SIGNATURE_UPDATED_KEY, null)
                    .collectAsStateWithLifecycle()

                SettingsRoute(
                    signatureUpdateUrl = updatedSignatureUrl,
                    onNavigateToSignature = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.navigate(ChangeSignature)
                    },
                    onOpenPrivacyPolicy = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        legalDocumentLauncher.open(LegalDocument.PRIVACY_POLICY)
                    },
                    onOpenTerms = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        legalDocumentLauncher.open(LegalDocument.TERMS_OF_SERVICE)
                    },
                    onOpenFeedback = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        if (sessionState is UserSessionState.Authenticated) {
                            navController.navigate(Feedback)
                        } else {
                            showToast(FEEDBACK_LOGIN_REQUIRED_MESSAGE)
                        }
                    },
                    onNavigateToBottomBar = { item -> navigateToBottomBar(backStackEntry, item) },
                    onOpenPhotoUpload = dropUnlessResumed(lifecycleOwner = backStackEntry, block = openPhotoUpload),
                )
            }

            composable<Feedback> { backStackEntry ->
                FeedbackRoute(
                    onBack = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                    onSubmitted = {
                        showToast(FEEDBACK_SUBMITTED_MESSAGE)
                        navController.popBackStack()
                    },
                    onReauthenticationRequired = navigateToLogin,
                )
            }

            composable<PhotoUpload> { backStackEntry ->
                val upload = backStackEntry.toRoute<PhotoUpload>()
                PhotoUploadRoute(
                    topicDate = LocalDate.parse(upload.topicDate),
                    onBack = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.popBackStack()
                    },
                    onReauthenticationRequired = {
                        navController.navigate(Login) {
                            popUpTo<Home> { inclusive = true }
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
                                quarterTurns = submission.edit.quarterTurns,
                                straightenDegrees = submission.edit.straightenDegrees,
                                cropCenterX = submission.edit.crop.centerX,
                                cropCenterY = submission.edit.crop.centerY,
                                cropWidth = submission.edit.crop.width,
                                cropHeight = submission.edit.crop.height,
                                filter = submission.edit.filter.name,
                                exposure = submission.edit.adjustments.exposure,
                                contrast = submission.edit.adjustments.contrast,
                                highlights = submission.edit.adjustments.highlights,
                                shadows = submission.edit.adjustments.shadows,
                                blackPoint = submission.edit.adjustments.blackPoint,
                                saturation = submission.edit.adjustments.saturation,
                                warmth = submission.edit.adjustments.warmth,
                            ),
                        ) {
                            popUpTo<PhotoUpload> { inclusive = true }
                        }
                    },
                )
            }

            composable<PhotoUploadSuccess> { backStackEntry ->
                val success = backStackEntry.toRoute<PhotoUploadSuccess>()

                PhotoUploadSuccessScreen(
                    imageModel = success.imageModel,
                    edit = success.toPhotoEdit(),
                    caption = success.caption,
                    content = PhotoUploadSuccessContent(
                        date = LocalDate.parse(success.date),
                        topic = success.topic,
                        moderationStatus = success.moderationStatus,
                    ),
                    onConfirmClick = dropUnlessResumed(lifecycleOwner = backStackEntry) {
                        navController.navigate(Display(date = success.date)) {
                            popUpTo<Home> { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

private fun PhotoUploadSuccess.toPhotoEdit() = PhotoEdit(
    quarterTurns = quarterTurns,
    straightenDegrees = straightenDegrees,
    crop = CropWindow(
        centerX = cropCenterX,
        centerY = cropCenterY,
        width = cropWidth,
        height = cropHeight,
    ),
    filter = PhotoFilter.entries.firstOrNull { it.name == filter } ?: PhotoFilter.Original,
    // 성공 화면은 결과만 보여 주므로 적용한 값을 수동 값으로 옮긴다.
    adjustmentLayers = PhotoAdjustmentLayers(
        manual = PhotoAdjustments(
            exposure = exposure,
            contrast = contrast,
            highlights = highlights,
            shadows = shadows,
            blackPoint = blackPoint,
            saturation = saturation,
            warmth = warmth,
        ),
    ),
)

private fun NavHostController.openNotificationTarget(item: NotificationItemUiState) {
    openNotificationTarget(
        type = item.type,
        sourceType = item.sourceType,
        sourceId = item.sourceId,
        notificationId = item.id,
    )
}

internal fun NavHostController.openNotificationTarget(
    type: String?,
    sourceType: String?,
    sourceId: String?,
    notificationId: String?,
) {
    when {
        type == TOPIC -> {
            navigate(Home) {
                popUpTo<Home>()
                launchSingleTop = true
            }
        }

        type == POST_APPROVED && sourceType == POST_SOURCE_TYPE && !sourceId.isNullOrBlank() -> {
            navigate(
                FeedById(
                    postId = sourceId,
                    isOwnedByCurrentUser = true,
                ),
            ) { launchSingleTop = true }
        }

        type == POST_REJECTED && !notificationId.isNullOrBlank() -> {
            navigate(NotificationDetail(notificationId)) { launchSingleTop = true }
        }

        else -> navigate(Notifications) { launchSingleTop = true }
    }
}

private const val POST_APPROVED = "POST_APPROVED"
private const val POST_REJECTED = "POST_REJECTED"
private const val POST_SOURCE_TYPE = "POST"
private const val TOPIC = "TOPIC"

private fun NavHostController.navigateToBottomBar(
    item: ChalkakBottomBarItem,
    analyticsTracker: AnalyticsTracker,
) {
    if (item == ChalkakBottomBarItem.HOME && currentDestination?.hasRoute<Home>() == true) return

    analyticsTracker.trackBottomNavigationSelection(item.analyticsName)

    val isDisplayOpenedFromRecord =
        currentDestination?.hasRoute<Display>() == true &&
            previousBackStackEntry?.destination?.hasRoute<Record>() == true
    if (isDisplayOpenedFromRecord) {
        if (item == ChalkakBottomBarItem.RECORD) {
            popBackStack()
            return
        }
        if (item == ChalkakBottomBarItem.DISPLAY) return

        popBackStack()
    }

    val destination = when (item) {
        ChalkakBottomBarItem.HOME -> Home
        ChalkakBottomBarItem.DISPLAY -> Display(date = "")
        ChalkakBottomBarItem.RECORD -> Record
        ChalkakBottomBarItem.SETTINGS -> Settings
    }

    navigate(destination) {
        launchSingleTop = true
        // Home is the retained root; restoring its saved stack can reopen the Display just popped.
        restoreState = item != ChalkakBottomBarItem.HOME
        popUpTo(Home) {
            saveState = true
        }
    }
}

private val ChalkakBottomBarItem.analyticsName: String
    get() = when (this) {
        ChalkakBottomBarItem.HOME -> "home"
        ChalkakBottomBarItem.DISPLAY -> "display"
        ChalkakBottomBarItem.RECORD -> "record"
        ChalkakBottomBarItem.SETTINGS -> "settings"
    }

private fun NavDestination.analyticsScreen(): AnalyticsScreen? = when {
    hasRoute<Login>() -> AnalyticsScreen(name = "login", screenClass = "Login")
    hasRoute<Terms>() -> AnalyticsScreen(name = "terms", screenClass = "Terms")
    hasRoute<OnboardingSignature>() -> OnboardingSignatureAnalyticsScreen
    hasRoute<ChangeSignature>() -> AnalyticsScreen(name = "change_signature", screenClass = "ChangeSignature")
    hasRoute<OnboardingSignaturePreview>() -> OnboardingSignaturePreviewAnalyticsScreen
    hasRoute<ChangeSignaturePreview>() -> ChangeSignaturePreviewAnalyticsScreen
    hasRoute<Home>() -> AnalyticsScreen(name = "home", screenClass = "Home")
    hasRoute<WebPage>() -> AnalyticsScreen(name = "issue", screenClass = "IssueWeb")
    hasRoute<Notifications>() -> AnalyticsScreen(name = "notifications", screenClass = "Notifications")
    hasRoute<NotificationDetail>() -> AnalyticsScreen(name = "notification_detail", screenClass = "NotificationDetail")
    hasRoute<Display>() -> AnalyticsScreen(name = "display", screenClass = "Display")
    hasRoute<Feed>() || hasRoute<FeedById>() -> AnalyticsScreen(name = "feed", screenClass = "Feed")
    hasRoute<Record>() -> AnalyticsScreen(name = "record", screenClass = "Record")
    hasRoute<Settings>() -> AnalyticsScreen(name = "settings", screenClass = "Settings")
    hasRoute<Feedback>() -> AnalyticsScreen(name = "feedback", screenClass = "Feedback")
    hasRoute<PhotoUpload>() -> AnalyticsScreen(name = "photo_upload", screenClass = "PhotoUpload")
    hasRoute<PhotoUploadSuccess>() -> AnalyticsScreen(name = "photo_upload_success", screenClass = "PhotoUploadSuccess")
    else -> null
}

private fun LegalDocument.analyticsScreen(): AnalyticsScreen = when (this) {
    LegalDocument.PRIVACY_POLICY -> AnalyticsScreen(name = "privacy_policy", screenClass = "PrivacyPolicy")
    LegalDocument.TERMS_OF_SERVICE -> AnalyticsScreen(name = "terms_of_service", screenClass = "TermsOfService")
}

private data class AnalyticsScreen(
    val name: String,
    val screenClass: String,
)

private val OnboardingSignatureAnalyticsScreen = AnalyticsScreen(
    name = "onboarding_signature",
    screenClass = "OnboardingSignature",
)
private val OnboardingSignaturePreviewAnalyticsScreen = AnalyticsScreen(
    name = "onboarding_signature_preview",
    screenClass = "OnboardingSignaturePreview",
)
private val ChangeSignaturePreviewAnalyticsScreen = AnalyticsScreen(
    name = "change_signature_preview",
    screenClass = "ChangeSignaturePreview",
)

private const val SETTINGS_SIGNATURE_UPDATED_KEY = "settings_signature_updated"
private const val POST_DELETED_KEY = "post_deleted"
private const val POST_DELETED_MESSAGE = "게시물을 삭제했어요"
private const val DISPLAY_FEED_LOGIN_REQUIRED_MESSAGE = "게시물 피드를 보려면 로그인이 필요해요"
private const val RECORD_LOGIN_REQUIRED_MESSAGE = "기록을 보려면 로그인이 필요해요"
private const val FEEDBACK_LOGIN_REQUIRED_MESSAGE = "피드백을 보내려면 로그인이 필요해요"
private const val FEEDBACK_SUBMITTED_MESSAGE = "피드백을 보내주셔서 감사해요."
private const val PUSH_PERMISSION_PROMPT_PREFS = "push_notification_permission_prompt"
private const val PUSH_PERMISSION_REQUESTED_KEY = "requested"

private fun Context.shouldRequestPushNotificationPermissionOnce(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED &&
        !getSharedPreferences(PUSH_PERMISSION_PROMPT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PUSH_PERMISSION_REQUESTED_KEY, false)

private fun Context.markPushNotificationPermissionRequested() {
    getSharedPreferences(PUSH_PERMISSION_PROMPT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PUSH_PERMISSION_REQUESTED_KEY, true)
        .apply()
}

private fun NavHostController.navigateToDisplay(date: LocalDate) {
    navigate(Display(date = date.toString())) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToPhotoUpload(topicDate: LocalDate) {
    navigate(PhotoUpload(topicDate = topicDate.toString())) {
        launchSingleTop = true
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(this)
}.getOrNull()
