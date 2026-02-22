package br.com.boddenb.comidinhas.ui.screen.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
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
    displayLarge  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    displaySmall  = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium= TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,      fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium   = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall     = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal,    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall    = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)
