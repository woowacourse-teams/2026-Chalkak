package com.stonefive.chalkak.feature.home.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.logo.ChalkakLogo
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun HomeTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .padding(
                start = ChalkakTheme.spacing.screenHorizontal,
                top = ChalkakTheme.spacing.lg,
                end = ChalkakTheme.spacing.screenHorizontal,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChalkakLogo()
        Spacer(modifier = Modifier.weight(1f))
    }
}
