package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.UserSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReauthenticationNavigatorTest {
    private val host = FakeNavigationHost(mutableListOf("Home", "PhotoUpload"))
    private val navigator = ReauthenticationNavigator(host)

    @Test
    fun `재인증이 필요해지면 현재 화면 위에 로그인 화면을 쌓는다`() {
        // Given 백스택 Home → PhotoUpload
        // When  세션이 ReauthenticationRequired("u1")
        // Then  백스택 Home → PhotoUpload → Login
        navigator.onSessionChanged(UserSessionState.Authenticated("u1"))

        navigator.onSessionChanged(UserSessionState.ReauthenticationRequired("u1"))

        assertEquals(listOf("Home", "PhotoUpload", "Login"), host.backStack)
    }

    @Test
    fun `화면이 재인증 실패를 따로 알려도 로그인 화면은 한 번만 쌓인다`() {
        // Given 백스택 Home → PhotoUpload, 세션 ReauthenticationRequired("u1")
        // When  사진 올리기가 onReauthenticationRequired를 부름
        // Then  백스택 Home → PhotoUpload → Login (Login 1개, Home 그대로)
        val session = UserSessionState.ReauthenticationRequired("u1")
        navigator.onSessionChanged(session)

        val handled = navigator.onReauthenticationRequired(session)

        assertTrue(handled)
        assertEquals(listOf("Home", "PhotoUpload", "Login"), host.backStack)
    }

    @Test
    fun `같은 계정으로 다시 로그인하면 원래 화면으로 돌아간다`() {
        // Given 백스택 Home → PhotoUpload → Login(재인증 "u1")
        // When  "u1"로 로그인 성공
        // Then  백스택 Home → PhotoUpload
        navigator.onSessionChanged(UserSessionState.ReauthenticationRequired("u1"))

        val session = UserSessionState.Authenticated("u1")
        navigator.onSessionChanged(session)
        val handled = navigator.onLoginSucceeded(session)

        assertTrue(handled)
        assertEquals(listOf("Home", "PhotoUpload"), host.backStack)
    }

    @Test
    fun `다른 계정으로 로그인하면 이전 화면을 버리고 홈으로 간다`() {
        // Given 백스택 Home → PhotoUpload → Login(재인증 "u1")
        // When  "u2"로 로그인 성공
        // Then  백스택 Home 하나
        navigator.onSessionChanged(UserSessionState.ReauthenticationRequired("u1"))

        val session = UserSessionState.Authenticated("u2")
        val handled = navigator.onLoginSucceeded(session)
        navigator.onSessionChanged(session)

        assertTrue(handled)
        assertEquals(listOf("Home"), host.backStack)
    }

    @Test
    fun `재인증 로그인 화면에서 뒤로가기를 누르면 앱이 백그라운드로 가고 화면은 그대로다`() {
        // Given 백스택 Home → PhotoUpload → Login(재인증 "u1")
        // When  뒤로가기
        // Then  앱이 백그라운드로 감, 백스택 Home → PhotoUpload → Login 그대로
        navigator.onSessionChanged(UserSessionState.ReauthenticationRequired("u1"))

        val handled = navigator.onBackPressed()

        assertTrue(handled)
        assertEquals(1, host.backgroundMoves)
        assertEquals(listOf("Home", "PhotoUpload", "Login"), host.backStack)
    }
}

/** 백스택을 화면 이름 목록으로 흉내 낸다. 실제 구현은 ChalkakNavHost의 NavController다. */
private class FakeNavigationHost(val backStack: MutableList<String>) : ReauthenticationNavigationHost {
    var backgroundMoves = 0

    override val isLoginOnTop: Boolean get() = backStack.lastOrNull() == "Login"

    override fun showLogin() {
        backStack += "Login"
    }

    override fun returnToPreviousScreen() {
        backStack.removeAt(backStack.lastIndex)
    }

    override fun restartFromHome() {
        backStack.clear()
        backStack += "Home"
    }

    override fun moveAppToBackground() {
        backgroundMoves++
    }
}
