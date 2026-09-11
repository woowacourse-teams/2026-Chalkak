package com.stonefive.chalkak.core.designsystem.component.checkbox

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

@Composable
fun ChalkakCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val drawableRes = if (checked) {
        R.drawable.ic_checkbox_selected
    } else {
        R.drawable.ic_checkbox_unselected
    }

    Icon(
        painter = painterResource(drawableRes),
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = modifier.size(22.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun ChalkakCheckboxSelectedPreview() {
    ChalkakTheme {
        ChalkakCheckbox(checked = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChalkakCheckboxUnselectedPreview() {
    ChalkakTheme {
        ChalkakCheckbox(checked = false)
    }
}
