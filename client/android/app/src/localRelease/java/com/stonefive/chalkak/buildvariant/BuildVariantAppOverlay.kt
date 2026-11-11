package com.stonefive.chalkak.buildvariant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// release 빌드에는 토큰 갱신 표시가 없다(debug 전용).
@Composable
@Suppress("UNUSED_PARAMETER")
fun BuildVariantAppOverlay(modifier: Modifier = Modifier) = Unit
