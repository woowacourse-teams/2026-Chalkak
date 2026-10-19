package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPhotoElement(
    key: PhotoTransitionKey,
    sourceShape: Shape? = null,
    animateCorners: Boolean = true,
): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return this

    val selectedShape = LocalPhotoTransitionCoordinator.current
        .selectedSnapshot(key.postId)
        ?.sourceShape
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
            // Let each image preserve its aspect ratio as the crop bounds change.
            resizeMode = ResizeMode.RemeasureToBounds,
            placeholderSize = SharedTransitionScope.PlaceholderSize.ContentSize,
            // The overlay clip path only refreshes on redraw and can lag behind the settled bounds.
            // Clip inside the recorded layer instead so the overlay and in-place frames match.
            clipInOverlayDuringTransition = NoOverlayClip,
            boundsTransform = { _, _ ->
                tween(
                    durationMillis = PHOTO_TRANSITION_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                )
            },
        ).clip(overlayShape)
    }
}

/** Keeps screen controls above the photo while both are drawn outside their parent layers. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.photoTransitionChrome(animateVisibility: Boolean = true): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current ?: return this
    val overlayModifier = with(sharedTransitionScope) {
        renderInSharedTransitionScopeOverlay(zIndexInOverlay = PHOTO_CHROME_OVERLAY_Z_INDEX)
    }
    if (!animateVisibility) return overlayModifier
    return overlayModifier.then(
        with(animatedVisibilityScope) {
            Modifier.animateEnterExit(
                enter = fadeIn(tween(PHOTO_CONTENT_FADE_DURATION_MILLIS)),
                exit = fadeOut(tween(PHOTO_CONTENT_FADE_DURATION_MILLIS)),
                label = "photo-transition-chrome",
            )
        },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
private object NoOverlayClip : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? = null
}

fun PhotoTransitionKey.signatureKey() = copy(sourceId = "$sourceId:signature")

internal class AnimatedPhotoShape(
    private val sourceShape: Shape,
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
                rect = Rect(
                    corners.left * progress,
                    corners.top * progress,
                    size.width + (corners.right - size.width) * progress,
                    size.height + (corners.bottom - size.height) * progress,
                ),
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

private const val PHOTO_SOURCE_HIDE_DURATION_MILLIS = 0
private const val PHOTO_TRANSITION_DURATION_MILLIS = 300

private const val PHOTO_CHROME_OVERLAY_Z_INDEX = 1f

// Match iOS: the photo moves first while the surrounding screen fades more gradually.
internal const val PHOTO_CONTENT_FADE_DURATION_MILLIS = 700
