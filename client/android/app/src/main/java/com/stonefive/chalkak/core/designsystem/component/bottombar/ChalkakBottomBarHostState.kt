package com.stonefive.chalkak.core.designsystem.component.bottombar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.scroll.BottomBarScrollState
import com.stonefive.chalkak.core.designsystem.scroll.rememberBottomBarScrollState
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** Shares each tab's scroll offset and measured bar height with the persistent host. */
@Stable
class ChalkakBottomBarHostState {
    private val states = ChalkakBottomBarItem.entries.associateWith { BottomBarScrollState() }

    fun stateFor(item: ChalkakBottomBarItem): BottomBarScrollState = states.getValue(item)

    fun updateHeight(height: Int) {
        states.values.forEach { it.height = height }
    }
}

val LocalBottomBarHostState = compositionLocalOf<ChalkakBottomBarHostState?> { null }

@Composable
fun rememberScreenBottomBarScrollState(item: ChalkakBottomBarItem): BottomBarScrollState {
    val fallback = rememberBottomBarScrollState()
    return LocalBottomBarHostState.current?.stateFor(item) ?: fallback
}

/** Keeps standalone screens/previews functional while hosted screens reserve only the bar's space. */
@Composable
fun ChalkakScreenBottomBar(
    selectedItem: ChalkakBottomBarItem,
    onItemSelected: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: BottomBarScrollState? = null,
) {
    val host = LocalBottomBarHostState.current
    if (host == null) {
        ChalkakBottomBar(
            selectedItem,
            onItemSelected,
            onAddClick,
            modifier
                .onSizeChanged { scrollState?.height = it.height }
                .graphicsLayer { translationY = scrollState?.offset ?: 0f },
        )
    } else {
        val height = with(LocalDensity.current) {
            host
                .stateFor(selectedItem)
                .height
                .toDp()
        }
        Spacer(modifier.height(height))
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakScreenBottomBarPreview() {
    ChalkakTheme {
        ChalkakScreenBottomBar(ChalkakBottomBarItem.HOME, {}, {})
    }
}
