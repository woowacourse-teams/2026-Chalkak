package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.UserSessionState

/** [ReauthenticationNavigator]가 실제 백스택을 바꿀 때 쓰는 동작. ChalkakNavHost가 NavController로 구현한다. */
internal interface ReauthenticationNavigationHost {
    val isLoginOnTop: Boolean

    fun showLogin()

    fun returnToPreviousScreen()

    fun restartFromHome()

    fun moveAppToBackground()
}

/**
 * 로그인이 만료됐을 때(세션 [UserSessionState.ReauthenticationRequired])의 백스택 정책.
 *
 * - 재인증이 필요해지면 지금 화면을 그대로 두고 그 위에 로그인 화면을 한 번만 쌓는다.
 * - 같은 계정으로 다시 로그인하면 로그인 화면만 빼서 보던 화면으로 돌아간다.
 * - 다른 계정으로 로그인하면 이전 계정의 화면을 버리고 홈부터 다시 시작한다.
 * - 재인증 로그인 화면의 뒤로가기는 앱을 백그라운드로 보낸다(로그인 없이 이전 화면으로 가지 않게).
 * - 게스트 둘러보기·로그아웃은 AuthGate가 앱 화면을 새로 만들므로 여기서 다루지 않는다.
 *
 * 예: 백스택 Home → PhotoUpload에서 재인증 필요("u1") → Home → PhotoUpload → Login,
 *     "u1" 로그인 성공 → Home → PhotoUpload, "u2" 로그인 성공 → Home.
 */
internal class ReauthenticationNavigator(private val host: ReauthenticationNavigationHost) {
    // 재인증 로그인 화면을 띄운 계정. null이면 재인증 중이 아니다.
    private var reauthenticatingUserId: String? = null

    /** 세션 상태가 바뀔 때마다 부른다. */
    fun onSessionChanged(session: UserSessionState) {
        when (session) {
            is UserSessionState.ReauthenticationRequired -> showLoginFor(session.userId)
            is UserSessionState.Authenticated -> finishReauthentication(session.userId)
            UserSessionState.Guest, UserSessionState.SignedOut -> reauthenticatingUserId = null
            UserSessionState.Loading -> Unit
        }
    }

    /**
     * 화면이 서버의 재인증 필요 응답을 따로 알렸을 때 부른다.
     * 세션이 재인증 필요 상태면 처리하고 true, 아니면 false(부른 쪽이 기존 로그인 이동을 한다).
     */
    fun onReauthenticationRequired(session: UserSessionState): Boolean {
        if (session !is UserSessionState.ReauthenticationRequired) return false
        showLoginFor(session.userId)
        return true
    }

    /**
     * 로그인 화면이 로그인 성공을 알렸을 때 부른다. 세션 변화와 어느 쪽이 먼저 와도 결과가 같다.
     * 재인증 로그인이었으면(또는 이미 처리해 로그인 화면이 없으면) true, 일반 로그인이면 false.
     */
    fun onLoginSucceeded(session: UserSessionState): Boolean {
        if (reauthenticatingUserId == null) return !host.isLoginOnTop
        val userId = (session as? UserSessionState.Authenticated)?.userId ?: return true
        finishReauthentication(userId)
        return true
    }

    /** 재인증 로그인 화면에서 뒤로가기를 눌렀을 때. 처리했으면 true. */
    fun onBackPressed(): Boolean {
        if (reauthenticatingUserId == null || !host.isLoginOnTop) return false
        host.moveAppToBackground()
        return true
    }

    private fun showLoginFor(userId: String) {
        reauthenticatingUserId = userId
        if (!host.isLoginOnTop) host.showLogin()
    }

    private fun finishReauthentication(userId: String) {
        val expectedUserId = reauthenticatingUserId ?: return
        reauthenticatingUserId = null
        if (userId == expectedUserId) {
            if (host.isLoginOnTop) host.returnToPreviousScreen()
        } else {
            host.restartFromHome()
        }
    }
}
