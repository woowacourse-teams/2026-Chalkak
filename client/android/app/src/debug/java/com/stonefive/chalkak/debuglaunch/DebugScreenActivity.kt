package com.stonefive.chalkak.debuglaunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/**
 * 디버그 빌드 전용: 로그인·서버·오늘 주제 조건 없이 화면 하나를 가짜 상태로 바로 연다.
 *
 * 화면 자동 확인 스크립트가 이 Activity로 화면을 열고 판정한다(하네스 문서: client/.agents/skills/client-tdd/references/screen-auto-check.md).
 * 화면 코드는 그대로 쓰고 상태만 [DebugScreens]에 등록한 함수가 만든다.
 * 창 설정(edge-to-edge, adjustResize)은 MainActivity와 같게 둔다. MainActivity 설정이 바뀌면 여기도 같이 바꾼다.
 *
 * 여는 법: adb shell am start -S -n com.stonefive.chalkak.debug/com.stonefive.chalkak.debuglaunch.DebugScreenActivity --es screen <이름>
 * 이름이 없거나 모르는 이름이면 등록된 이름 목록을 보여 준다.
 */
class DebugScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        val name = intent.getStringExtra(EXTRA_SCREEN)
        val screen = DebugScreens.all[name]
        setContent {
            ChalkakTheme {
                if (screen != null) {
                    screen()
                } else {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                        Text("모르는 화면: $name")
                        DebugScreens.all.keys
                            .sorted()
                            .forEach { Text(it) }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}
