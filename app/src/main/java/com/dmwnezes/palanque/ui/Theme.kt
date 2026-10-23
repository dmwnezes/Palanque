package com.dmwnezes.palanque.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.R

/**
 * Paleta provisória "Plenário": azul-petróleo de gabinete, creme para o texto,
 * ouro para o jogador, verde para o que vai bem e vermelho para o que vai mal.
 */
object Ink {
    val ocean = Color(0xFF0F1A22)
    val oceanDeep = Color(0xFF0A1218)
    val land = Color(0xFF1C2D3A)
    val panel = Color(0xF2152430)
    val panelSolid = Color(0xFF152430)
    val raised = Color(0xFF1D2F3D)
    val line = Color(0xFF2E4252)
    val text = Color(0xFFF0E9D8)
    val muted = Color(0xFFA3AFB5)
    val faint = Color(0xFF66757E)
    val gold = Color(0xFFE3B341)
    val goldDeep = Color(0xFFB8891F)
    val onGold = Color(0xFF241A04)
    val teal = Color(0xFF5FB3A6)
    val good = Color(0xFF6CC28F)
    val coral = Color(0xFFE5604F)
    val amber = Color(0xFFE8A93A)

    fun tone(t: Int) = when (t) { 1 -> good; 2 -> amber; 3 -> coral; else -> muted }

    /** 0..100 → vermelho, âmbar, verde */
    fun scale(v: Float): Color = when {
        v < 35f -> coral
        v < 50f -> amber
        else -> good
    }

    /** Cor contínua para o mapa (0..100): vermelho → âmbar → verde. */
    fun heat(v: Float): Color {
        val t = (v / 100f).coerceIn(0f, 1f)
        return if (t < 0.5f) lerp(Color(0xFFB8463A), Color(0xFFC99A3A), t / 0.5f)
        else lerp(Color(0xFFC99A3A), Color(0xFF3F9B6B), (t - 0.5f) / 0.5f)
    }

    fun lerp(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
    )
}

object Shapes {
    val card = RoundedCornerShape(26.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val chip = RoundedCornerShape(50)
    val tile = RoundedCornerShape(18.dp)
    val small = RoundedCornerShape(12.dp)
}

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

object Type {
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp)
    val heading = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val body = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val small = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val number = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum")
    val big = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, fontFeatureSettings = "tnum")
}

@Composable
fun PalanqueTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.o() = copy(fontFamily = Outfit)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.gold, onPrimary = Ink.onGold, secondary = Ink.teal,
            background = Ink.ocean, surface = Ink.panelSolid, onSurface = Ink.text, onBackground = Ink.text,
            error = Ink.coral, surfaceVariant = Ink.raised, outline = Ink.line,
        ),
        typography = Typography(
            bodyLarge = base.bodyLarge.o(), bodyMedium = base.bodyMedium.o(), bodySmall = base.bodySmall.o(),
            titleLarge = base.titleLarge.o(), titleMedium = base.titleMedium.o(), titleSmall = base.titleSmall.o(),
            labelLarge = base.labelLarge.o(), labelMedium = base.labelMedium.o(), labelSmall = base.labelSmall.o(),
            headlineSmall = base.headlineSmall.o(), headlineMedium = base.headlineMedium.o(),
        ),
        content = content,
    )
}
