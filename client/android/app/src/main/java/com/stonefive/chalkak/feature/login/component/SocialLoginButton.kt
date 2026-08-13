package com.stonefive.chalkak.feature.login.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.domain.model.SocialLoginProvider

@get:DrawableRes
private val SocialLoginProvider.logoResId: Int
    get() = when (this) {
        SocialLoginProvider.GOOGLE -> R.drawable.img_google_logo
        SocialLoginProvider.KAKAO -> R.drawable.img_kakao_logo
    }

@get:StringRes
private val SocialLoginProvider.labelResId: Int
    get() = when (this) {
        SocialLoginProvider.GOOGLE -> R.string.login_continue_with_google
        SocialLoginProvider.KAKAO -> R.string.login_continue_with_kakao
    }

@Composable
fun SocialLoginButton(
    provider: SocialLoginProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ChalkakTheme.shapes.button,
        border = BorderStroke(
            width = 1.dp,
            color = ChalkakTheme.colors.border,
        ),
        contentPadding = PaddingValues(
            horizontal = ChalkakTheme.spacing.xl,
            vertical = ChalkakTheme.spacing.lg,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ChalkakTheme.colors.textPrimary,
            disabledContentColor = ChalkakTheme.colors.textMuted,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(provider.logoResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(provider.labelResId),
                style = ChalkakTheme.typography.callout,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialLoginButtonPreview() {
    ChalkakTheme {
        Column(
            modifier = Modifier.padding(ChalkakTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ChalkakTheme.spacing.lg),
        ) {
            SocialLoginProvider.entries.forEach { provider ->
                SocialLoginButton(
                    provider = provider,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
