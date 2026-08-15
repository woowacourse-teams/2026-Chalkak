package com.stonefive.chalkak.feature.signature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakOutlinedButton
import com.stonefive.chalkak.core.designsystem.component.image.ChalkakSignedImage
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun 가(
    imageModel: Any?,
    signatureModel: Any?,
    onRedrawClick: () -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SignaturePreviewScreen(
        imageModel = imageModel,
        signatureModel = signatureModel,
        onRedrawClick = onRedrawClick,
        onStartClick = onStartClick,
        modifier = modifier,
    )
}

@Composable
fun SignaturePreviewScreen(
    imageModel: Any?,
    signatureModel: Any?,
    onRedrawClick: () -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChalkakTheme.colors.background)
            .systemBarsPadding()
            .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "이렇게 보여요",
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title1,
        )

        Spacer(modifier = Modifier.height(50.dp))

        ChalkakSignedImage(
            imageModel = imageModel,
            signatureModel = signatureModel,
            contentDescription = "사진에 사인이 적용된 모습",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 6f)
                .clip(ChalkakTheme.shapes.xlarge),
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChalkakOutlinedButton(
                text = "다시 그리기",
                onClick = onRedrawClick,
                modifier = Modifier.weight(1f),
            )

            ChalkakButton(
                text = "시작하기",
                onClick = onStartClick,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun SignaturePreviewScreenPreview() {
    ChalkakTheme {
        SignaturePreviewScreen(
            imageModel = R.drawable.preview_photo,
            signatureModel = R.drawable.preview_signature,
            onRedrawClick = {},
            onStartClick = {},
        )
    }
}
