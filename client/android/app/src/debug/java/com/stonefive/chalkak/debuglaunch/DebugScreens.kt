package com.stonefive.chalkak.debuglaunch

import androidx.compose.runtime.Composable

/**
 * 바로 열 수 있는 화면 목록: 이름 → 화면.
 *
 * 새 화면을 추가할 때(화면마다 파일 하나, 예: InstagramConsentDebugScreens.kt)
 * - 화면 코드는 실제 Screen/컴포넌트를 그대로 쓰고, 상태만 만든다.
 * - 상태는 앱이 실제로 부르는 함수·생성 경로로 만든다(예: 서버 거절 뒤 상태는 withInvalidInstagramId()).
 *   필드를 하나씩 손으로 채우면 실제로는 나올 수 없는 조합이 생긴다.
 * - 이미지는 앱 리소스(R.drawable.preview_photo 등)를 써서 네트워크 없이 바로 보이게 한다.
 * - 서버 요청을 보내지 않는다.
 */
object DebugScreens {
    val all: Map<String, @Composable () -> Unit> = instagramConsentDebugScreens
}
