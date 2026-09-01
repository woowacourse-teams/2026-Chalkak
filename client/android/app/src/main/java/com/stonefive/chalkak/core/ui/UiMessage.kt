package com.stonefive.chalkak.core.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface UiMessage {
    data class Toast(val text: String) : UiMessage
}

class UiMessageEmitter {
    private val mutableMessages = MutableSharedFlow<UiMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<UiMessage> = mutableMessages.asSharedFlow()

    fun showToast(text: String) {
        mutableMessages.tryEmit(UiMessage.Toast(text))
    }
}

@Composable
fun UiMessageEffect(messages: Flow<UiMessage>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(messages, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages.collect { message ->
                when (message) {
                    is UiMessage.Toast ->
                        Toast.makeText(context, message.text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
