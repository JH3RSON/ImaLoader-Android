@file:OptIn(ExperimentalTextApi::class)

package com.ima.imaloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ima.imaloader.R

val NunitoRounded = FontFamily(
    Font(
        resId = R.font.nunito,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.nunito,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.nunito,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    ),
    Font(
        resId = R.font.nunito,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))
    ),
    Font(
        resId = R.font.nunito,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(1000))
    )
)

private val defaultTypography = Typography()

val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.ExtraBold),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.ExtraBold),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Bold),
    
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal),
    
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = NunitoRounded, fontWeight = FontWeight.Normal)
)
