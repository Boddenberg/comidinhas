package br.com.boddenb.comidinhas.ui.screen.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Nunito via Google Fonts — ativa após gradle sync com ui-text-google-fonts
// Por ora usamos FontFamily.Default que o sistema resolve em Roboto
// Quando quiser ativar Google Fonts descomente o bloco abaixo:
//
// import androidx.compose.ui.text.googlefonts.Font
// import androidx.compose.ui.text.googlefonts.GoogleFont
// private val provider = GoogleFont.Provider(...)
// private val NunitoFont = GoogleFont("Nunito")
// val NunitoFamily = FontFamily( Font(NunitoFont, provider, FontWeight.Normal), ... )

val NunitoFamily: FontFamily = FontFamily.Default // substitui com GoogleFont após sync

val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp),
    displayMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    displaySmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),

    headlineLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 29.sp, letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),

    titleLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,     fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,     fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),

    bodyLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.10.sp),
    bodySmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.20.sp),

    labelLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp),
    labelSmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
