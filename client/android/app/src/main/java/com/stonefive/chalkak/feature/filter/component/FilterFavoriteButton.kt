package com.stonefive.chalkak.feature.filter.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

/** 사진 위에 얹는 즐겨찾기 별. 유리 효과 없이 반투명 흰 원만 쓴다. */
@Composable
fun FilterFavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(FavoriteButtonSize)
            .background(
                ChalkakTheme.colors.surface
                    .copy(alpha = 0.8f),
                CircleShape,
            ).semantics { contentDescription = "즐겨찾기" }
            .toggleable(
                value = isFavorite,
                interactionSource = null,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star),
            contentDescription = null,
            tint = ChalkakTheme.colors.iconPrimary,
            modifier = Modifier.size(FavoriteIconSize),
        )
    }
}

private val FavoriteButtonSize = 40.dp
private val FavoriteIconSize = 24.dp

@Preview(showBackground = true)
@Composable
private fun FilterFavoriteButtonPreview() {
    ChalkakTheme {
        FilterFavoriteButton(isFavorite = true, onToggle = {})
    }
}
