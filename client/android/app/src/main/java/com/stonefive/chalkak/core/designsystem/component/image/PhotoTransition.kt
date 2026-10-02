package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

data class PhotoTransitionKey(
    val postId: String,
    val sourceId: String,
) {
    val sharedElementKey: String = "post-photo:$postId:$sourceId"
}

data class SharedPhotoSnapshot(
    val key: PhotoTransitionKey,
    val imageModel: Any?,
    val signatureModel: Any?,
    val painter: Painter?,
    val image: Image?,
    val memoryCacheKey: MemoryCache.Key?,
    val aspectRatio: Float?,
    val contentScale: ContentScale = ContentScale.FillWidth,
    val sourceShape: CornerBasedShape? = null,
    val signaturePainter: Painter? = null,
)

@Stable
class PhotoTransitionCoordinator {
    private val registeredSnapshots = mutableStateMapOf<PhotoTransitionKey, SharedPhotoSnapshot>()
    private val selectedSnapshots = mutableStateMapOf<String, SharedPhotoSnapshot>()

    fun registerSignature(key: PhotoTransitionKey, model: Any?, painter: Painter) {
        val snapshot = registeredSnapshots[key] ?: return
        registeredSnapshots[key] = snapshot.copy(signatureModel = model, signaturePainter = painter)
    }

    fun register(snapshot: SharedPhotoSnapshot) {
        val existing = registeredSnapshots[snapshot.key]
        val isFallbackOverLoadedSnapshot = snapshot.painter == null &&
            existing?.painter != null
        if (!isFallbackOverLoadedSnapshot) {
            registeredSnapshots[snapshot.key] = snapshot
        }
    }

    fun unregister(key: PhotoTransitionKey) {
        registeredSnapshots.remove(key)
    }

    fun select(key: PhotoTransitionKey): SharedPhotoSnapshot? = registeredSnapshots[key]
        ?.also { snapshot ->
            selectedSnapshots[key.postId] = snapshot
        }

    fun selectedSnapshot(postId: String): SharedPhotoSnapshot? = selectedSnapshots[postId]

    fun clear(postId: String) {
        selectedSnapshots.remove(postId)
    }
}

val LocalPhotoTransitionCoordinator = compositionLocalOf { PhotoTransitionCoordinator() }

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

private val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun rememberPhotoTransitionCoordinator(): PhotoTransitionCoordinator = remember {
    PhotoTransitionCoordinator()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoTransitionProvider(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    coordinator: PhotoTransitionCoordinator = rememberPhotoTransitionCoordinator(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPhotoTransitionCoordinator provides coordinator,
        LocalSharedTransitionScope provides sharedTransitionScope,
        LocalAnimatedVisibilityScope provides animatedVisibilityScope,
    ) {
        content()
    }
}

data class SharedPhotoSource(
    val modifier: Modifier,
    val onSuccess: (AsyncImagePainter.State.Success) -> Unit,
    val select: () -> SharedPhotoSnapshot?,
    val signatureModifier: Modifier,
    val onSignatureSuccess: (AsyncImagePainter.State.Success) -> Unit,
)

@Composable
fun rememberSharedPhotoSource(
    key: PhotoTransitionKey,
    imageModel: Any?,
    signatureModel: Any?,
    aspectRatio: Float? = null,
    contentScale: ContentScale = ContentScale.FillWidth,
    sourceShape: CornerBasedShape? = null,
): SharedPhotoSource {
    val coordinator = LocalPhotoTransitionCoordinator.current
    val sourceModifier = Modifier.sharedPhotoElement(key, sourceShape)
    val signatureModifier = Modifier.sharedPhotoElement(key.signatureKey(), animateCorners = false)
    var loadedSignature by remember(key, signatureModel) {
        mutableStateOf<AsyncImagePainter.State.Success?>(null)
    }

    LaunchedEffect(coordinator, key, imageModel, signatureModel, aspectRatio, contentScale, sourceShape) {
        coordinator.register(
            SharedPhotoSnapshot(
                key = key,
                imageModel = imageModel,
                signatureModel = loadedSignature?.result?.request?.data ?: signatureModel,
                signaturePainter = loadedSignature?.painter,
                painter = null,
                image = null,
                memoryCacheKey = null,
                aspectRatio = aspectRatio,
                contentScale = contentScale,
                sourceShape = sourceShape,
            ),
        )
    }

    DisposableEffect(coordinator, key) {
        onDispose { coordinator.unregister(key) }
    }

    return remember(key, imageModel, signatureModel, sourceModifier, coordinator, contentScale, sourceShape) {
        SharedPhotoSource(
            modifier = sourceModifier,
            onSuccess = { state ->
                coordinator.register(
                    SharedPhotoSnapshot(
                        key = key,
                        imageModel = state.result.request.data,
                        signatureModel = loadedSignature?.result?.request?.data ?: signatureModel,
                        signaturePainter = loadedSignature?.painter,
                        painter = state.painter,
                        image = state.result.image,
                        memoryCacheKey = state.result.memoryCacheKey,
                        aspectRatio = aspectRatioForSize(
                            width = state.result.image.width,
                            height = state.result.image.height,
                        ) ?: aspectRatio,
                        contentScale = contentScale,
                        sourceShape = sourceShape,
                    ),
                )
            },
            select = { coordinator.select(key) },
            signatureModifier = signatureModifier,
            onSignatureSuccess = { state ->
                loadedSignature = state
                coordinator.registerSignature(key, state.result.request.data, state.painter)
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhotoElement(
    key: PhotoTransitionKey,
    sourceShape: CornerBasedShape? = null,
    animateCorners: Boolean = true,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return this

    val selectedShape = LocalPhotoTransitionCoordinator.current.selectedSnapshot(key.postId)?.sourceShape
    val roundedShape = if (animateCorners) sourceShape ?: selectedShape else null
    val cornerFraction = animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { tween(PHOTO_TRANSITION_DURATION_MILLIS, easing = FastOutSlowInEasing) },
        label = "shared-photo-corners",
    ) { state ->
        val isVisible = state == EnterExitState.Visible
        if (isVisible == (sourceShape != null)) 1f else 0f
    }
    val overlayShape = remember(roundedShape, cornerFraction) {
        roundedShape?.let { AnimatedPhotoShape(it, cornerFraction) } ?: RectangleShape
    }

    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key.sharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = fadeOut(tween(durationMillis = PHOTO_SOURCE_HIDE_DURATION_MILLIS)),
            resizeMode = ResizeMode.scaleToBounds(ContentScale.FillBounds),
            placeholderSize = SharedTransitionScope.PlaceholderSize.ContentSize,
            clipInOverlayDuringTransition = OverlayClip(overlayShape),
            boundsTransform = { _, _ ->
                tween(
                    durationMillis = PHOTO_TRANSITION_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                )
            },
        )
    }
}

private fun PhotoTransitionKey.signatureKey() = copy(sourceId = "$sourceId:signature")

private class AnimatedPhotoShape(
    private val sourceShape: CornerBasedShape,
    private val fraction: State<Float>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = sourceShape.createOutline(size, layoutDirection, density)
        if (outline !is Outline.Rounded) return outline
        val corners = outline.roundRect
        val progress = fraction.value.coerceIn(0f, 1f)
        fun CornerRadius.scaled() = CornerRadius(x * progress, y * progress)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                topLeft = corners.topLeftCornerRadius.scaled(),
                topRight = corners.topRightCornerRadius.scaled(),
                bottomRight = corners.bottomRightCornerRadius.scaled(),
                bottomLeft = corners.bottomLeftCornerRadius.scaled(),
            ),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberPhotoTransitionEnterFinished(): Boolean {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val transition = LocalAnimatedVisibilityScope.current?.transition
    val enterSettled = transition == null ||
        (
            transition.currentState == EnterExitState.Visible &&
                transition.targetState == EnterExitState.Visible &&
                !transition.isRunning
            )

    return enterSettled && sharedTransitionScope?.isTransitionActive != true
}

@Composable
fun SharedFeedImage(
    key: PhotoTransitionKey,
    originalImageModel: Any?,
    thumbnailImageModel: Any?,
    signatureModel: Any?,
    thumbnailSignatureModel: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    fallbackAspectRatio: Float? = null,
    signatureModifier: Modifier = Modifier.size(
        width = 70.dp,
        height = 52.dp,
    ),
) {
    val coordinator = LocalPhotoTransitionCoordinator.current
    val selectedSnapshot = coordinator.selectedSnapshot(key.postId)
    val sourceImageModel = selectedSnapshot?.imageModel ?: thumbnailImageModel
    val sourceSignatureModel = selectedSnapshot?.signatureModel ?: thumbnailSignatureModel
    var loadedAspectRatio by remember(key, thumbnailImageModel) { mutableStateOf<Float?>(null) }
    val aspectRatio = selectedSnapshot?.aspectRatio
        ?: fallbackAspectRatio
        ?: loadedAspectRatio
        ?: DEFAULT_PHOTO_ASPECT_RATIO
    val sourceContentScale = selectedSnapshot?.contentScale ?: contentScale
    val revealState = rememberSharedFeedImageRevealState(originalImageModel)
    val originalAlpha by animateFloatAsState(
        targetValue = if (revealState.shouldShowOriginal) 1f else 0f,
        animationSpec = tween(durationMillis = PHOTO_CROSSFADE_DURATION_MILLIS),
        label = "shared-feed-photo-alpha",
    )
    val resolvedModifier = modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)

    Box(modifier = resolvedModifier) {
        Box(
            modifier = Modifier
                .sharedPhotoElement(key)
                .fillMaxSize(),
        ) {
            ChalkakImage(
                model = sourceImageModel,
                contentDescription = contentDescription,
                contentScale = sourceContentScale,
                loadedPainter = selectedSnapshot?.painter,
                onSuccess = { state ->
                    if (selectedSnapshot?.aspectRatio == null) {
                        loadedAspectRatio = aspectRatioForSize(
                            width = state.result.image.width,
                            height = state.result.image.height,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (originalImageModel != null) {
                ChalkakImage(
                    model = originalImageModel,
                    contentDescription = null,
                    thumbnailModel = sourceImageModel,
                    contentScale = contentScale,
                    onSuccess = { revealState.onOriginalLoadSuccess() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = originalAlpha },
                )
            }
        }

        if (sourceSignatureModel != null || signatureModel != null) {
            var originalSignatureLoaded by remember(signatureModel) { mutableStateOf(false) }
            val signatureAlpha by animateFloatAsState(
                targetValue = if (originalSignatureLoaded && revealState.shouldShowOriginal) 1f else 0f,
                animationSpec = tween(PHOTO_CROSSFADE_DURATION_MILLIS),
                label = "shared-feed-signature-alpha",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .sharedPhotoElement(key.signatureKey(), animateCorners = false)
                    .then(signatureModifier),
            ) {
                if (sourceSignatureModel != null) {
                    ChalkakImage(
                        model = sourceSignatureModel,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        loadedPainter = selectedSnapshot?.signaturePainter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (signatureModel != null) {
                    ChalkakImage(
                        model = signatureModel,
                        contentDescription = null,
                        thumbnailModel = sourceSignatureModel,
                        contentScale = ContentScale.Fit,
                        onSuccess = { originalSignatureLoaded = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = signatureAlpha },
                    )
                }
            }
        }
    }
}

@Stable
class SharedFeedImageRevealState internal constructor(private val isEnterFinished: State<Boolean>) {
    private var isOriginalLoaded by mutableStateOf(false)

    val shouldShowOriginal: Boolean
        get() = shouldRevealOriginal(
            isOriginalLoaded = isOriginalLoaded,
            isEnterFinished = isEnterFinished.value,
        )

    fun onOriginalLoadSuccess() {
        isOriginalLoaded = true
    }
}

@Composable
fun rememberSharedFeedImageRevealState(originalImageModel: Any?): SharedFeedImageRevealState {
    val enterFinished = rememberUpdatedState(rememberPhotoTransitionEnterFinished())
    return remember(originalImageModel) {
        SharedFeedImageRevealState(isEnterFinished = enterFinished)
    }
}

fun shouldRevealOriginal(
    isOriginalLoaded: Boolean,
    isEnterFinished: Boolean,
): Boolean = isOriginalLoaded && isEnterFinished

private fun aspectRatioForSize(
    width: Int,
    height: Int,
): Float? = if (width > 0 && height > 0) {
    width.toFloat() / height
} else {
    null
}

private const val DEFAULT_PHOTO_ASPECT_RATIO = 1f
private const val PHOTO_SOURCE_HIDE_DURATION_MILLIS = 0
private const val PHOTO_TRANSITION_DURATION_MILLIS = 300
private const val PHOTO_CROSSFADE_DURATION_MILLIS = 180

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun SharedFeedImagePreview() {
    ChalkakTheme {
        SharedFeedImage(
            key = PhotoTransitionKey("preview", "preview"),
            originalImageModel = R.drawable.home_feed_photo,
            thumbnailImageModel = R.drawable.home_feed_photo,
            signatureModel = R.drawable.preview_signature,
            thumbnailSignatureModel = null,
            contentDescription = "사진",
            fallbackAspectRatio = 0.75f,
        )
    }
}
