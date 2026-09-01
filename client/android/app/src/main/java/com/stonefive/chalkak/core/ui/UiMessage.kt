package com.stonefive.chalkak.core.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

sealed interface UiMessage {
    val id: Long

    data class Toast(
        override val id: Long,
        val text: String,
    ) : UiMessage
}

@Composable
fun UiMessageEffect(
    message: UiMessage?,
    onMessageShown: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(message?.id, lifecycleOwner) {
        val currentMessage = message ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            when (currentMessage) {
                is UiMessage.Toast ->
                    Toast.makeText(context, currentMessage.text, Toast.LENGTH_SHORT).show()
            }
            onMessageShown(currentMessage.id)
        }
    }
}
