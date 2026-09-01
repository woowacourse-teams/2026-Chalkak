package com.stonefive.chalkak.core.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiMessageEmitterTest {
    @Test
    fun `구독 중인 화면에는 Toast 메시지를 전달한다`() = runTest {
        val emitter = UiMessageEmitter()
        val message = async { emitter.messages.first() }
        runCurrent()

        emitter.showToast("오류 메시지")

        assertEquals(UiMessage.Toast("오류 메시지"), message.await())
    }

    @Test
    fun `화면이 다시 구독해도 지난 Toast 메시지를 재생하지 않는다`() = runTest {
        val emitter = UiMessageEmitter()

        emitter.showToast("지난 오류")
        val received = mutableListOf<UiMessage>()
        val collection = backgroundScope.launch {
            emitter.messages.collect(received::add)
        }
        runCurrent()

        assertTrue(received.isEmpty())
        collection.cancel()
    }
}
