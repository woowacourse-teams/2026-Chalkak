package com.stonefive.chalkak.feature.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.core.designsystem.component.button.ChalkakButton
import com.stonefive.chalkak.core.designsystem.theme.ChalkakInputBackground
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.terms.component.TermsAllConsentRow
import com.stonefive.chalkak.feature.terms.component.TermsDivider
import com.stonefive.chalkak.feature.terms.component.TermsRequiredConsentRow

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
            Spacer(modifier = Modifier.height(50.dp))

            Text(
                text = "찰캌에\n오신 것을 환영합니다.",
                color = ChalkakTheme.colors.textPrimary,
                style = ChalkakTheme.typography.title1,
            )

            Spacer(modifier = Modifier.height(57.dp))

            TermsAllConsentRow(
                checked = uiState.isAllAgreed,
                onClick = { onAction(TermsUiAction.AllConsentClicked) },
            )

            Spacer(modifier = Modifier.height(3.dp))

            TermsRequiredConsentRow(
                text = "(필수) 서비스 이용약관",
                checked = uiState.serviceTermsAgreed,
                onCheckedChange = { onAction(TermsUiAction.ServiceTermsClicked) },
                onViewClick = onServiceTermsViewClick,
            )
            TermsDivider()
            TermsRequiredConsentRow(
                text = "(필수) 개인정보 처리방침",
                checked = uiState.privacyPolicyAgreed,
                onCheckedChange = { onAction(TermsUiAction.PrivacyPolicyClicked) },
                onViewClick = onPrivacyPolicyViewClick,
            )
            TermsDivider()
        }

        Spacer(modifier = Modifier.weight(1f))

        ChalkakButton(
            text = "다음",
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
