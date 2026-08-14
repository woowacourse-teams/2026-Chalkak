package com.stonefive.chalkak.feature.terms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.component.checkbox.ChalkakCheckbox
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

private val TermsCardShape = RoundedCornerShape(16.dp)
private val TermsCardBorder = Color(0xFFE3E1DD)

@Composable
fun TermsRoute(
    onNextClick: () -> Unit,
    onServiceTermsViewClick: () -> Unit = {},
    onPrivacyPolicyViewClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var serviceTermsAgreed by rememberSaveable { mutableStateOf(false) }
    var privacyPolicyAgreed by rememberSaveable { mutableStateOf(false) }

    val uiState = TermsUiState(
        serviceTermsAgreed = serviceTermsAgreed,
        privacyPolicyAgreed = privacyPolicyAgreed,
    )

    TermsScreen(
        uiState = uiState,
        onAction = { action ->
            val nextState = uiState.reduce(action)
            serviceTermsAgreed = nextState.serviceTermsAgreed
            privacyPolicyAgreed = nextState.privacyPolicyAgreed
        },
        onNextClick = onNextClick,
        onServiceTermsViewClick = onServiceTermsViewClick,
        onPrivacyPolicyViewClick = onPrivacyPolicyViewClick,
        modifier = modifier,
    )
}

@Composable
fun TermsScreen(
    uiState: TermsUiState,
    onAction: (TermsUiAction) -> Unit,
    onNextClick: () -> Unit,
    onServiceTermsViewClick: () -> Unit = {},
    onPrivacyPolicyViewClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(ChalkakInputBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(92.dp))

            Text(
                text = stringResource(R.string.terms_title),
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.title1,
            )

            Spacer(modifier = Modifier.height(57.dp))

            AllConsentRow(
                checked = uiState.isAllAgreed,
                onClick = { onAction(TermsUiAction.AllConsentClicked) },
            )

            Spacer(modifier = Modifier.height(3.dp))

            RequiredConsentRow(
                text = stringResource(R.string.terms_service_terms),
                checked = uiState.serviceTermsAgreed,
                onCheckedChange = { onAction(TermsUiAction.ServiceTermsClicked) },
                onViewClick = onServiceTermsViewClick,
            )
            TermsDivider()
            RequiredConsentRow(
                text = stringResource(R.string.terms_privacy_policy),
                checked = uiState.privacyPolicyAgreed,
                onCheckedChange = { onAction(TermsUiAction.PrivacyPolicyClicked) },
                onViewClick = onPrivacyPolicyViewClick,
            )
            TermsDivider()
        }

        Spacer(modifier = Modifier.weight(1f))

        ChalkakButton(
            text = stringResource(R.string.terms_next),
            onClick = onNextClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChalkakTheme.spacing.screenHorizontal)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            enabled = uiState.isAllAgreed,
            disabledContainerColor = ChalkakTheme.colors.actionPrimary,
            disabledContentColor = ChalkakTheme.colors.onActionPrimary,
        )
    }
}

@Composable
private fun AllConsentRow(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(TermsCardShape)
            .border(BorderStroke(1.dp, TermsCardBorder), TermsCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            ).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChalkakCheckbox(checked = checked)
        Text(
            text = stringResource(R.string.terms_all_consent),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.title3.copy(
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun RequiredConsentRow(
    text: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val viewInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Checkbox,
                    onClick = onCheckedChange,
                ).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChalkakCheckbox(checked = checked)
            Text(
                text = text,
                color = ChalkakTheme.colors.textPrimary
                    .copy(alpha = 0.85f),
                style = ChalkakTheme.typography.body,
            )
        }

        Text(
            text = stringResource(R.string.terms_view),
            color = ChalkakTheme.colors.textMuted,
            style = ChalkakTheme.typography.callout,
            modifier = Modifier
                .clickable(
                    interactionSource = viewInteractionSource,
                    indication = null,
                    onClick = onViewClick,
                ).padding(start = 12.dp, end = 4.dp)
                .semantics { role = Role.Button },
        )
    }
}

@Composable
private fun TermsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ChalkakTheme.colors.border),
    )
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun TermsScreenPreview() {
    ChalkakTheme {
        TermsRoute(onNextClick = {})
    }
}

@Preview(
    showBackground = true,
    widthDp = 402,
    heightDp = 874,
)
@Composable
private fun TermsScreenAgreedPreview() {
    ChalkakTheme {
        TermsScreen(
            uiState = TermsUiState(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
            ),
            onAction = {},
            onNextClick = {},
        )
    }
}
