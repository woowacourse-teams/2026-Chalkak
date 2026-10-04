package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.Image
import coil3.memory.MemoryCache

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

    fun registerSignature(
        key: PhotoTransitionKey,
        model: Any?,
        painter: Painter,
    ) {
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
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

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
