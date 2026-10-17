package com.stonefive.chalkak.buildvariant

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.developertools.DeveloperToolsActivity
import com.stonefive.chalkak.feature.settings.component.SettingsArrowIcon
import com.stonefive.chalkak.feature.settings.component.SettingsCard
import com.stonefive.chalkak.feature.settings.component.SettingsRow
import com.stonefive.chalkak.feature.settings.component.SettingsSectionLabel

@Composable
fun BuildVariantSettingsEntry(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xxl))
        SettingsSectionLabel(text = stringResource(R.string.developer_tools_section))
        Spacer(modifier = Modifier.height(ChalkakTheme.spacing.lg))
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                text = stringResource(R.string.developer_tools_title),
                modifier = Modifier.fillMaxWidth(),
                onClick = { context.startActivity(Intent(context, DeveloperToolsActivity::class.java)) },
                trailingContent = { SettingsArrowIcon() },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BuildVariantSettingsEntryPreview() {
    ChalkakTheme {
        BuildVariantSettingsEntry()
    }
}
