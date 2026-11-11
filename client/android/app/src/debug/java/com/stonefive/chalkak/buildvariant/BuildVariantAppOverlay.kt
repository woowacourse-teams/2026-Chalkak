package com.stonefive.chalkak.buildvariant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.developertools.TokenRefreshIndicator
import kotlinx.coroutines.delay

/**
 * 메인 앱 화면 위에 겹치는 debug 전용 표시. 토큰 갱신이 성공할 때마다 [TokenRefreshIndicator]를 약 2초 보여 준다.
 * release·localRelease에는 아무것도 그리지 않는 같은 이름의 함수가 있다.
 * 화면을 처음 그릴 때 이미 있던 갱신 기록은 띄우지 않고, 그 뒤로 횟수가 늘면 띄운다(화면이 멈춰 있던 동안의 갱신은 돌아올 때 한 번).
 */
@Composable
fun BuildVariantAppOverlay(modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as ChalkakApplication
    val repository = application.appContainer.buildVariantServices.developerAuthRepository
    val history by repository.refreshHistory.collectAsStateWithLifecycle()
    var shownCount by remember { mutableIntStateOf(history.count) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(history.count) {
        if (history.count <= shownCount) return@LaunchedEffect
        shownCount = history.count
        visible = true
        delay(INDICATOR_VISIBLE_MILLIS)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        TokenRefreshIndicator(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = ChalkakTheme.spacing.sm),
        )
    }
}

private const val INDICATOR_VISIBLE_MILLIS = 2_000L
