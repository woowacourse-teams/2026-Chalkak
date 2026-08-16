package com.stonefive.chalkak.feature.upload.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.core.designsystem.theme.ChalkakWhite

@Composable
fun PhotoUploadActionButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 4.dp,
                    color = Color.Black.copy(alpha = 0.2f),
                    offset = DpOffset(0.dp, 2.dp),
                ),
            ).clip(CircleShape)
            .background(ChalkakWhite.copy(alpha = 0.66f))
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoUploadActionButtonPreview() {
    ChalkakTheme {
        PhotoUploadActionButton(
            iconRes = R.drawable.ic_photo_library,
            description = "앨범에서 사진 선택",
            onClick = {},
        )
    }
}
