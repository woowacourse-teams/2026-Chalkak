package com.stonefive.chalkak.core.designsystem.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Stable
class SharedPhotoImageRevealState(private val isEnterFinished: State<Boolean>) {
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
fun rememberSharedPhotoImageRevealState(originalImageModel: Any?): SharedPhotoImageRevealState {
    val enterFinished = rememberUpdatedState(rememberPhotoTransitionEnterFinished())
    return remember(originalImageModel) {
        SharedPhotoImageRevealState(isEnterFinished = enterFinished)
    }
}

fun shouldRevealOriginal(
    isOriginalLoaded: Boolean,
    isEnterFinished: Boolean,
): Boolean = isOriginalLoaded && isEnterFinished
