package com.stonefive.chalkak.core.designsystem.component.bottombar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakAction
import com.stonefive.chalkak.core.designsystem.theme.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite

enum class ChalkakBottomBarItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TODAY(R.string.bottom_bar_today, R.drawable.ic_bottom_today),
    DISPLAY(R.string.bottom_bar_display, R.drawable.ic_bottom_display),
    RECORD(R.string.bottom_bar_record, R.drawable.ic_bottom_record),
    SETTINGS(R.string.bottom_bar_settings, R.drawable.ic_bottom_setting),
}

@Composable
fun ChalkakBottomBar(
    selectedItem: ChalkakBottomBarItem,
    onItemSelected: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ChalkakWhite)
            .navigationBarsPadding()
            .selectableGroup()
            .padding(
                top = 15.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarItem(
            item = ChalkakBottomBarItem.TODAY,
            selected = selectedItem == ChalkakBottomBarItem.TODAY,
            onClick = { onItemSelected(ChalkakBottomBarItem.TODAY) },
        )
        BottomBarItem(
            item = ChalkakBottomBarItem.DISPLAY,
            selected = selectedItem == ChalkakBottomBarItem.DISPLAY,
            onClick = { onItemSelected(ChalkakBottomBarItem.DISPLAY) },
        )
        ChalkakAddButton(onClick = onAddClick)
        BottomBarItem(
            item = ChalkakBottomBarItem.RECORD,
            selected = selectedItem == ChalkakBottomBarItem.RECORD,
            onClick = { onItemSelected(ChalkakBottomBarItem.RECORD) },
        )
        BottomBarItem(
            item = ChalkakBottomBarItem.SETTINGS,
            selected = selectedItem == ChalkakBottomBarItem.SETTINGS,
            onClick = { onItemSelected(ChalkakBottomBarItem.SETTINGS) },
        )
    }
}

@Composable
private fun RowScope.BottomBarItem(
    item: ChalkakBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) ChalkakAction else ChalkakBottomBar

    Column(
        modifier = modifier
            .weight(1f)
            .sizeIn(
                minWidth = 48.dp,
                minHeight = 48.dp,
            ).selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(23.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = stringResource(item.labelRes),
            color = color,
            style = ChalkakTheme.typography.footnote.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun RowScope.ChalkakAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bottom_write),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(40.dp)
                .semantics {
                    contentDescription = "추가"
                }.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakBottomBarPreview() {
    ChalkakTheme {
        var selectedItem by remember {
            mutableStateOf(ChalkakBottomBarItem.TODAY)
        }

        ChalkakBottomBar(
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
            onAddClick = {},
            modifier = Modifier.width(400.dp),
        )
    }
}
