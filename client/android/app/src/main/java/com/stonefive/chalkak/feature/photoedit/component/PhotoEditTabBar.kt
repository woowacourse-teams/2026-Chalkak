package com.stonefive.chalkak.feature.photoedit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.photoedit.PhotoEditTab
import com.stonefive.chalkak.feature.photoedit.label

@Composable
fun PhotoEditTabBar(
    selectedTab: PhotoEditTab,
    onTabSelect: (PhotoEditTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.selectableGroup()) {
        PhotoEditTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabSelect(tab) },
                    ).padding(vertical = ChalkakTheme.spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    style = ChalkakTheme.typography.subheadline,
                    color = if (selected) {
                        ChalkakTheme.colors.photoEditContent
                    } else {
                        ChalkakTheme.colors.photoEditContentMuted
                    },
                )
            }
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun PhotoEditTabBarPreview() {
    ChalkakTheme {
        PhotoEditTabBar(
            selectedTab = PhotoEditTab.Crop,
            onTabSelect = {},
            modifier = Modifier
                .fillMaxWidth()
                .background(ChalkakTheme.colors.photoEditBackground),
        )
    }
}
