package tw.moneybook.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tw.moneybook.app.R

/** 一組配色：淺色與深色各一套 */
class Pal(
    val key: String,
    val name: String,
    val primary: Long,
    val accent2: Long,
    val bg: Long,
    val card: Long,
    val soft: Long,
    val ink: Long,
    val sub: Long,
    val dPrimary: Long,
    val dBg: Long,
    val dCard: Long,
    val dSoft: Long,
    val dInk: Long,
    val dSub: Long,
    /** 收入、支出的顏色（每套配色可以不同，避免冷暖色衝突） */
    val inc: Long = 0xFF4FA774,
    val exp: Long = 0xFFE0675B,
    val dInc: Long = 0xFF86CFA0,
    val dExp: Long = 0xFFF28B80,
)

val Palettes: List<Pal> = listOf(
    Pal("milktea", "奶茶可可", 0xFFB07F57, 0xFFE7B77B, 0xFFF7EFE6, 0xFFFFFCF8, 0xFFEFDDCB, 0xFF4D3B31, 0xFF9A8272,
        0xFFE0B58E, 0xFF231B16, 0xFF30261F, 0xFF453629, 0xFFF3E5D8, 0xFFBFA590),
    Pal("peach", "蜜桃橘", 0xFFF58A5C, 0xFFFFC857, 0xFFFFF4EA, 0xFFFFFFFF, 0xFFFFE1CF, 0xFF5A3E30, 0xFFA08272,
        0xFFFFA27D, 0xFF241A16, 0xFF32251F, 0xFF4A3024, 0xFFF7E6DA, 0xFFC8A896),
    Pal("mint", "薄荷奶綠", 0xFF4FB38F, 0xFFFFB29E, 0xFFEFF8F2, 0xFFFFFFFF, 0xFFD3EFE2, 0xFF2F4A40, 0xFF7C958A,
        0xFF7DD3B2, 0xFF17211D, 0xFF213029, 0xFF2C4539, 0xFFE2F2EA, 0xFF9DB8AC),
    Pal("strawberry", "草莓牛奶", 0xFFE8738A, 0xFFFFC7A8, 0xFFFFF3F4, 0xFFFFFFFF, 0xFFFFDDE3, 0xFF5B3940, 0xFFA77E86,
        0xFFF59AAC, 0xFF241719, 0xFF33232A, 0xFF4A2C33, 0xFFF8E4E8, 0xFFC9A2AA),
    Pal("blueberry", "藍莓草莓", 0xFF7A8CE0, 0xFFF4A6C0, 0xFFF3F4FC, 0xFFFFFFFF, 0xFFDFE3FA, 0xFF363C5E, 0xFF858AA8,
        0xFFA3B0F2, 0xFF181A26, 0xFF232638, 0xFF30355A, 0xFFE6E8F7, 0xFFA7ABC9,
        inc = 0xFF4FB3A6, exp = 0xFFE57A9A, dInc = 0xFF7FD3C8, dExp = 0xFFF29BB6),
    Pal("lavender", "薰衣草紫", 0xFF8C7BE0, 0xFFC7A8F0, 0xFFF5F3FC, 0xFFFFFFFF, 0xFFE6E0FA, 0xFF3A355E, 0xFF8A84A8,
        0xFFB3A6F2, 0xFF1A1826, 0xFF262338, 0xFF35305A, 0xFFE9E6F7, 0xFFABA7C9,
        inc = 0xFF5AAFC9, exp = 0xFFE0789A, dInc = 0xFF8ACDE0, dExp = 0xFFF09AB5),
    Pal("ocean", "海洋藍綠", 0xFF4A90D9, 0xFF6FCFD6, 0xFFF0F7FA, 0xFFFFFFFF, 0xFFD6EBF3, 0xFF2F4A5A, 0xFF7D98A6,
        0xFF7FB4EC, 0xFF151D22, 0xFF1E2A31, 0xFF294049, 0xFFE2EFF4, 0xFF9DB6C2,
        inc = 0xFF3FB58A, exp = 0xFFEF8A7C, dInc = 0xFF72D2AC, dExp = 0xFFF5A69B),
)

fun palOf(key: String): Pal = Palettes.firstOrNull { it.key == key } ?: Palettes[0]

/** 分類的粉彩色（圖示底色、圖表顏色） */
val CatColors: List<Color> = listOf(
    Color(0xFFFFA77F), Color(0xFFFFCF5C), Color(0xFF86CFA3), Color(0xFFF49AB8), Color(0xFFB3A2EE),
    Color(0xFF93BDF2), Color(0xFFFF9494), Color(0xFF73D0C9), Color(0xFFC7B294), Color(0xFFFFBE9A),
)

fun catColor(i: Int): Color = CatColors[((i % CatColors.size) + CatColors.size) % CatColors.size]

/** Material 以外、這個 App 自己用的顏色 */
data class Cute(
    val income: Color,
    val expense: Color,
    val card: Color,
    val soft: Color,
    val accent2: Color,
    val sub: Color,
    val ink: Color,
    val dark: Boolean,
)

val LocalCute = staticCompositionLocalOf {
    Cute(Color(0xFF5FAE7E), Color(0xFFE0675B), Color.White, Color(0xFFEFDDCB), Color(0xFFE7B77B), Color.Gray, Color.Black, false)
}

val Huninn = FontFamily(Font(R.font.huninn))

private fun TextStyle.h(): TextStyle = copy(fontFamily = Huninn)

private val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.h(), displayMedium = t.displayMedium.h(), displaySmall = t.displaySmall.h(),
        headlineLarge = t.headlineLarge.h(), headlineMedium = t.headlineMedium.h(), headlineSmall = t.headlineSmall.h(),
        titleLarge = t.titleLarge.h(), titleMedium = t.titleMedium.h(), titleSmall = t.titleSmall.h(),
        bodyLarge = t.bodyLarge.h(), bodyMedium = t.bodyMedium.h(), bodySmall = t.bodySmall.h(),
        labelLarge = t.labelLarge.h(), labelMedium = t.labelMedium.h(), labelSmall = t.labelSmall.h(),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MoneyTheme(paletteKey: String, dark: Boolean, content: @Composable () -> Unit) {
    val p = palOf(paletteKey)
    val scheme = if (dark) {
        val primary = Color(p.dPrimary)
        darkColorScheme(
            primary = primary, onPrimary = Color(p.dBg),
            primaryContainer = Color(p.dSoft), onPrimaryContainer = Color(p.dInk),
            secondary = Color(p.accent2), onSecondary = Color(p.dBg),
            secondaryContainer = Color(p.dSoft), onSecondaryContainer = Color(p.dInk),
            tertiary = Color(p.accent2), onTertiary = Color(p.dBg),
            background = Color(p.dBg), onBackground = Color(p.dInk),
            surface = Color(p.dBg), onSurface = Color(p.dInk),
            surfaceVariant = Color(p.dSoft), onSurfaceVariant = Color(p.dSub),
            surfaceContainerLowest = Color(p.dBg), surfaceContainerLow = Color(p.dCard),
            surfaceContainer = Color(p.dCard), surfaceContainerHigh = Color(p.dCard),
            surfaceContainerHighest = Color(p.dSoft),
            outline = Color(p.dSub), outlineVariant = Color(p.dSoft),
        )
    } else {
        val primary = Color(p.primary)
        lightColorScheme(
            primary = primary, onPrimary = Color.White,
            primaryContainer = Color(p.soft), onPrimaryContainer = Color(p.ink),
            secondary = Color(p.accent2), onSecondary = Color(p.ink),
            secondaryContainer = Color(p.soft), onSecondaryContainer = Color(p.ink),
            tertiary = Color(p.accent2), onTertiary = Color(p.ink),
            background = Color(p.bg), onBackground = Color(p.ink),
            surface = Color(p.bg), onSurface = Color(p.ink),
            surfaceVariant = Color(p.soft), onSurfaceVariant = Color(p.sub),
            surfaceContainerLowest = Color(p.card), surfaceContainerLow = Color(p.card),
            surfaceContainer = Color(p.card), surfaceContainerHigh = Color(p.card),
            surfaceContainerHighest = Color(p.soft),
            outline = Color(p.sub), outlineVariant = Color(p.soft),
        )
    }
    val cute = if (dark) {
        Cute(Color(p.dInc), Color(p.dExp), Color(p.dCard), Color(p.dSoft), Color(p.accent2), Color(p.dSub), Color(p.dInk), true)
    } else {
        Cute(Color(p.inc), Color(p.exp), Color(p.card), Color(p.soft), Color(p.accent2), Color(p.sub), Color(p.ink), false)
    }
    CompositionLocalProvider(LocalCute provides cute) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** 帳戶文字徽章可選的底色 */
val BadgeColors: List<Color> = listOf(
    Color(0xFF2E8B57), Color(0xFF1F6FB2), Color(0xFFD64541), Color(0xFFE67E22), Color(0xFF8E44AD),
    Color(0xFF16A085), Color(0xFFC2185B), Color(0xFF5D6D7E), Color(0xFF2C2C2C), Color(0xFFB8860B),
    Color(0xFF7FB3D5), Color(0xFFF5B7B1),
)

fun badgeColor(i: Int): Color = BadgeColors[((i % BadgeColors.size) + BadgeColors.size) % BadgeColors.size]
