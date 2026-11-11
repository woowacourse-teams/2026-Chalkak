package com.stonefive.chalkak.feature.filter.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun FilterCard(
    name: String,
    description: String,
    coverImageRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = null,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        ChalkakImage(
            model = coverImageRes,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ChalkakTheme.shapes.photoCard),
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
        Text(
            text = name,
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.callout,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xs))
        Text(
            text = description,
            color = ChalkakTheme.colors.textSecondary,
            style = ChalkakTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 180)
@Composable
private fun FilterCardPreview() {
    ChalkakTheme {
        FilterCard(
            name = "0000필터",
            description = "언제 언제 쓰면 예뻐요",
            coverImageRes = R.drawable.home_feed_photo,
            onClick = {},
        )
    }
}
