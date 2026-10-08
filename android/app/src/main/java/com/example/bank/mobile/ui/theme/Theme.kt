@file:OptIn(ExperimentalTextApi::class)

package com.example.bank.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.bank.mobile.R

/** The Lime palette: one green family, ink for text, and greys only where they still pass contrast. */
object Lime {
    val Green = Color(0xFF4D7C0F)      // buttons, links (white text on it: 5.4:1)
    val GreenDark = Color(0xFF3F6212)  // header band
    val GreenTint = Color(0xFFECF5DD)  // icon tiles, chips
    val GreenOnDark = Color(0xFFD9EBC0) // secondary text on the header band
    val Bright = Color(0xFFA3E635)     // accents on dark surfaces only
    val Ink = Color(0xFF0E1A2B)
    val Muted = Color(0xFF5B6675)
    val Background = Color(0xFFF6F7F9)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFFE3E7EC)
    val Divider = Color(0xFFEEF1F4)
    val Negative = Color(0xFFB42318)
    val Positive = Color(0xFF3F6212)
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val DmSans = FontFamily(
    variable(R.font.dm_sans, 400),
    variable(R.font.dm_sans, 500),
    variable(R.font.dm_sans, 600),
    variable(R.font.dm_sans, 700),
)

/** Khmer text (and the whole UI when the app is in Khmer). */
val KantumruyPro = FontFamily(
    variable(R.font.kantumruy_pro, 400),
    variable(R.font.kantumruy_pro, 500),
    variable(R.font.kantumruy_pro, 600),
    variable(R.font.kantumruy_pro, 700),
)

/** Amounts and account numbers: monospaced digits line up in lists. */
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

object MoneyStyle {
    val Large = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = (-0.5).sp)
    val Medium = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 18.sp)
    val Small = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val Tiny = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 12.sp)
}

private fun typography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f().copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.f().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.f().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f().copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.f().copy(fontSize = 15.sp),
        bodySmall = base.bodySmall.f().copy(fontSize = 13.sp, color = Lime.Muted),
        labelLarge = base.labelLarge.f().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = base.labelMedium.f().copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = base.labelSmall.f().copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    )
}

@Composable
fun LimeTheme(content: @Composable () -> Unit) {
    val khmer = LocalConfiguration.current.locales[0].language == "km"
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Lime.Green,
            onPrimary = Color.White,
            primaryContainer = Lime.GreenTint,
            onPrimaryContainer = Lime.GreenDark,
            background = Lime.Background,
            onBackground = Lime.Ink,
            surface = Lime.Card,
            onSurface = Lime.Ink,
            onSurfaceVariant = Lime.Muted,
            outline = Lime.Border,
            outlineVariant = Lime.Divider,
            error = Lime.Negative,
        ),
        typography = typography(if (khmer) KantumruyPro else DmSans),
        content = content,
    )
}
