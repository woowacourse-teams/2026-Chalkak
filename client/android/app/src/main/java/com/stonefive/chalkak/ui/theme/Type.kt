package com.stonefive.chalkak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stonefive.chalkak.R

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val Continuous = FontFamily(
    Font(R.font.continuous_regular, FontWeight.Normal),
)

private val GriunHangeulBanguri = FontFamily(
    Font(R.font.griun_hangeul_banguri_regular, FontWeight.Normal),
)

private val Display = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 41.sp,
    letterSpacing = 0.sp,
)

private val Title1 = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = 0.sp,
)

private val Title2 = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp,
)

private val Title3 = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 25.sp,
    letterSpacing = 0.sp,
)

private val Headline = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
)

private val Body = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
)

private val Callout = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
)

private val Subheadline = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

private val Footnote = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

private val Caption = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
)

private val Brand = TextStyle(
    fontFamily = Continuous,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.42).sp,
)

private val Handwriting = TextStyle(
    fontFamily = GriunHangeulBanguri,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.sp,
)

@Immutable
data class ChalkakTypography(
    val display: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val brand: TextStyle,
    val handwriting: TextStyle,
)

val DefaultChalkakTypography = ChalkakTypography(
    display = Display,
    title1 = Title1,
    title2 = Title2,
    title3 = Title3,
    headline = Headline,
    body = Body,
    callout = Callout,
    subheadline = Subheadline,
    footnote = Footnote,
    caption = Caption,
    brand = Brand,
    handwriting = Handwriting,
)

val ChalkakMaterialTypography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = Title1,
    headlineMedium = Title2,
    headlineSmall = Title3,
    titleLarge = Headline,
    titleMedium = Headline,
    titleSmall = Callout,
    bodyLarge = Body,
    bodyMedium = Callout,
    bodySmall = Subheadline,
    labelLarge = Callout,
    labelMedium = Footnote,
    labelSmall = Caption,
)
