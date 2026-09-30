package tw.moneybook.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import tw.moneybook.app.R
import tw.moneybook.app.AppData
import tw.moneybook.app.TxType
import tw.moneybook.app.expenseSum
import tw.moneybook.app.inMonth
import java.time.LocalDate
import java.time.YearMonth

enum class Mood { HAPPY, NORMAL, WORRIED }

/** 可選的吉祥物：key 與預設名字 */
val MascotKinds: List<Pair<String, String>> = listOf(
    "deer" to "橙鹿",
    "cat" to "小貓",
    "bear" to "小熊",
    "bunny" to "小兔",
    "dog" to "小狗",
    "schnauzer" to "雪納瑞",
)

fun mascotDefaultName(kind: String): String = MascotKinds.firstOrNull { it.first == kind }?.second ?: "橙鹿"

fun moodOf(d: AppData, today: LocalDate = LocalDate.now()): Mood {
    val book = d.currentBook
    val m = YearMonth.from(today)
    val list = d.bookTxns
    val spent = list.inMonth(m).expenseSum()
    val budget = book.budgetFor(m)
    if (budget > 0) {
        if (spent > budget) return Mood.WORRIED
        val ratio = spent.toFloat() / budget.toFloat()
        val time = today.dayOfMonth.toFloat() / m.lengthOfMonth().toFloat()
        if (ratio > time + 0.2f) return Mood.WORRIED
    }
    val todayEpoch = today.toEpochDay()
    return if (list.any { it.day == todayEpoch }) Mood.HAPPY else Mood.NORMAL
}

fun mascotLine(mood: Mood, d: AppData, today: LocalDate = LocalDate.now()): String {
    val i = today.dayOfYear
    val book = d.currentBook
    return when (mood) {
        Mood.HAPPY -> listOf(
            "今天也有好好記帳，好棒！",
            "記帳的你最可愛了～",
            "一步一步存錢，一起加油！",
            "錢錢都乖乖待在帳本裡了",
        )[i % 4]
        Mood.NORMAL -> listOf(
            "今天還沒記帳喔，要不要記一筆？",
            "嗨～今天過得好嗎？",
            "花了什麼記得告訴我喔",
        )[i % 3]
        Mood.WORRIED -> {
            val spent = d.bookTxns.inMonth(YearMonth.from(today)).expenseSum()
            val budget = book.budgetFor(YearMonth.from(today))
            if (budget in 1 until spent) "已經超過預算 ${tw.moneybook.app.formatMoney(spent - budget)} 了…這個月要節制一下 🥺"
            else "這個月花得有點快，剩下的日子省一點點喔"
        }
    }
}

private class Look(
    val head: Color,
    val inner: Color,
    val muzzle: Color,
    val extra: Color,
)

private fun lookOf(kind: String): Look = when (kind) {
    "cat" -> Look(Color(0xFFF2B880), Color(0xFFFFD2C2), Color(0xFFFFF3E6), Color(0xFFD88E52))
    "bear" -> Look(Color(0xFFB9825F), Color(0xFFE6C2A2), Color(0xFFF2DDC7), Color(0xFF8C5B3F))
    "bunny" -> Look(Color(0xFFF8F2EC), Color(0xFFFFC2CF), Color(0xFFFFFFFF), Color(0xFFE8DCD2))
    "dog" -> Look(Color(0xFFEBC7A0), Color(0xFFA8744E), Color(0xFFFFF5E8), Color(0xFF8E5E3C))
    "schnauzer" -> Look(Color(0xFF9CA3AA), Color(0xFF6F767E), Color(0xFFEEF1F3), Color(0xFFE3E7EA))
    else -> Look(Color(0xFFF4A261), Color(0xFFFFC9A8), Color(0xFFFFE9D4), Color(0xFF9A6440))
}

/** 吉祥物：橙鹿用貼紙圖（依心情換表情），其他用 Canvas 畫的；都會輕輕上下晃動 */
@Composable
fun Mascot(kind: String, mood: Mood, modifier: Modifier = Modifier, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "bob")
    val bob by t.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) -3f else 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    val bobbing = modifier.graphicsLayer { translationY = bob * density }
    if (kind == "deer") {
        val res = when (mood) {
            Mood.HAPPY -> R.drawable.mascot_deer_happy
            Mood.NORMAL -> R.drawable.mascot_deer_normal
            Mood.WORRIED -> R.drawable.mascot_deer_worried
        }
        Image(painterResource(res), null, bobbing, contentScale = ContentScale.Fit)
    } else {
        Canvas(bobbing) {
            drawMascot(kind, mood)
        }
    }
}

private fun DrawScope.drawMascot(kind: String, mood: Mood) {
    val s = size.minDimension / 120f
    val dx = (size.width - 120f * s) / 2f
    val dy = (size.height - 120f * s) / 2f
    fun o(x: Float, y: Float) = Offset(dx + x * s, dy + y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, deg: Float = 0f) {
        rotate(deg, pivot = o(cx, cy)) {
            drawOval(c, topLeft = o(cx - rx, cy - ry), size = sz(rx * 2, ry * 2))
        }
    }
    fun circle(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(c, radius = r * s, center = o(cx, cy))
    fun stroke(w: Float) = Stroke(width = w * s, cap = StrokeCap.Round)

    val lk = lookOf(kind)
    val dark = Color(0xFF3B2A20)
    val nose = Color(0xFF6B3F2A)

    // ── 耳朵、角（畫在頭後面）
    when (kind) {
        "cat" -> {
            val l = Path().apply { moveTo(o(28f, 58f).x, o(28f, 58f).y); lineTo(o(30f, 18f).x, o(30f, 18f).y); lineTo(o(58f, 36f).x, o(58f, 36f).y); close() }
            val r = Path().apply { moveTo(o(92f, 58f).x, o(92f, 58f).y); lineTo(o(90f, 18f).x, o(90f, 18f).y); lineTo(o(62f, 36f).x, o(62f, 36f).y); close() }
            drawPath(l, lk.head); drawPath(r, lk.head)
            val li = Path().apply { moveTo(o(34f, 48f).x, o(34f, 48f).y); lineTo(o(35f, 27f).x, o(35f, 27f).y); lineTo(o(50f, 37f).x, o(50f, 37f).y); close() }
            val ri = Path().apply { moveTo(o(86f, 48f).x, o(86f, 48f).y); lineTo(o(85f, 27f).x, o(85f, 27f).y); lineTo(o(70f, 37f).x, o(70f, 37f).y); close() }
            drawPath(li, lk.inner); drawPath(ri, lk.inner)
        }
        "bear" -> {
            circle(30f, 36f, 14f, lk.head); circle(90f, 36f, 14f, lk.head)
            circle(30f, 36f, 8f, lk.inner); circle(90f, 36f, 8f, lk.inner)
        }
        "bunny" -> {
            oval(44f, 22f, 9f, 24f, lk.head, -10f); oval(76f, 22f, 9f, 24f, lk.head, 10f)
            oval(44f, 24f, 4.5f, 17f, lk.inner, -10f); oval(76f, 24f, 4.5f, 17f, lk.inner, 10f)
        }
        "dog", "schnauzer" -> {}
        else -> { // deer
            val antler = Path().apply {
                moveTo(o(42f, 36f).x, o(42f, 36f).y); cubicTo(o(36f, 26f).x, o(36f, 26f).y, o(30f, 20f).x, o(30f, 20f).y, o(24f, 12f).x, o(24f, 12f).y)
                moveTo(o(34f, 25f).x, o(34f, 25f).y); lineTo(o(22f, 26f).x, o(22f, 26f).y)
                moveTo(o(78f, 36f).x, o(78f, 36f).y); cubicTo(o(84f, 26f).x, o(84f, 26f).y, o(90f, 20f).x, o(90f, 20f).y, o(96f, 12f).x, o(96f, 12f).y)
                moveTo(o(86f, 25f).x, o(86f, 25f).y); lineTo(o(98f, 26f).x, o(98f, 26f).y)
            }
            drawPath(antler, lk.extra, style = stroke(5f))
            oval(24f, 56f, 14f, 8f, lk.head, -25f); oval(96f, 56f, 14f, 8f, lk.head, 25f)
            oval(24f, 56f, 8f, 4f, lk.inner, -25f); oval(96f, 56f, 8f, 4f, lk.inner, 25f)
        }
    }

    // ── 頭
    circle(60f, 68f, 36f, lk.head)

    // ── 頭上的花紋 / 垂耳
    when (kind) {
        "cat" -> {
            drawLine(lk.extra, o(60f, 36f), o(60f, 46f), strokeWidth = 3.5f * s, cap = StrokeCap.Round)
            drawLine(lk.extra, o(52f, 37f), o(53f, 45f), strokeWidth = 3f * s, cap = StrokeCap.Round)
            drawLine(lk.extra, o(68f, 37f), o(67f, 45f), strokeWidth = 3f * s, cap = StrokeCap.Round)
        }
        "dog" -> {
            oval(27f, 64f, 11f, 22f, lk.inner, 18f); oval(93f, 64f, 11f, 22f, lk.inner, -18f)
            oval(72f, 58f, 10f, 9f, lk.extra.copy(alpha = 0.35f))
        }
        "schnauzer" -> {
            // 雪納瑞：向前折的三角耳
            val l = Path().apply {
                moveTo(o(27f, 50f).x, o(27f, 50f).y)
                cubicTo(o(24f, 34f).x, o(24f, 34f).y, o(36f, 26f).x, o(36f, 26f).y, o(50f, 32f).x, o(50f, 32f).y)
                lineTo(o(44f, 40f).x, o(44f, 40f).y)
                cubicTo(o(38f, 46f).x, o(38f, 46f).y, o(34f, 54f).x, o(34f, 54f).y, o(33f, 60f).x, o(33f, 60f).y)
                close()
            }
            val r = Path().apply {
                moveTo(o(93f, 50f).x, o(93f, 50f).y)
                cubicTo(o(96f, 34f).x, o(96f, 34f).y, o(84f, 26f).x, o(84f, 26f).y, o(70f, 32f).x, o(70f, 32f).y)
                lineTo(o(76f, 40f).x, o(76f, 40f).y)
                cubicTo(o(82f, 46f).x, o(82f, 46f).y, o(86f, 54f).x, o(86f, 54f).y, o(87f, 60f).x, o(87f, 60f).y)
                close()
            }
            drawPath(l, lk.inner); drawPath(r, lk.inner)
        }
        "deer" -> {
            circle(50f, 44f, 3.2f, Color(0xFFFFF3E6)); circle(62f, 40f, 2.6f, Color(0xFFFFF3E6)); circle(71f, 47f, 2.2f, Color(0xFFFFF3E6))
        }
        "bunny" -> {}
        "bear" -> {}
        else -> {
            circle(50f, 44f, 3.2f, Color(0xFFFFF3E6)); circle(62f, 40f, 2.6f, Color(0xFFFFF3E6)); circle(71f, 47f, 2.2f, Color(0xFFFFF3E6))
        }
    }

    // ── 嘴巴周圍
    if (kind == "schnauzer") {
        // 雪納瑞的大鬍子
        oval(60f, 94f, 18f, 13f, lk.muzzle)
        oval(47f, 86f, 14f, 11f, lk.muzzle); oval(73f, 86f, 14f, 11f, lk.muzzle)
        oval(52f, 79f, 10f, 7f, lk.muzzle); oval(68f, 79f, 10f, 7f, lk.muzzle)
    } else {
        oval(60f, 84f, 20f, 14f, lk.muzzle)
    }

    // ── 眼睛
    when (mood) {
        Mood.HAPPY -> {
            val eyes = Path().apply {
                moveTo(o(39f, 67f).x, o(39f, 67f).y); cubicTo(o(41f, 60f).x, o(41f, 60f).y, o(49f, 60f).x, o(49f, 60f).y, o(51f, 67f).x, o(51f, 67f).y)
                moveTo(o(69f, 67f).x, o(69f, 67f).y); cubicTo(o(71f, 60f).x, o(71f, 60f).y, o(79f, 60f).x, o(79f, 60f).y, o(81f, 67f).x, o(81f, 67f).y)
            }
            drawPath(eyes, dark, style = stroke(3.6f))
        }
        Mood.NORMAL -> {
            circle(45f, 66f, 4.8f, dark); circle(75f, 66f, 4.8f, dark)
            circle(46.6f, 64.2f, 1.6f, Color.White); circle(76.6f, 64.2f, 1.6f, Color.White)
        }
        Mood.WORRIED -> {
            circle(45f, 67f, 4.2f, dark); circle(75f, 67f, 4.2f, dark)
            circle(46.4f, 65.6f, 1.3f, Color.White); circle(76.4f, 65.6f, 1.3f, Color.White)
            drawLine(dark, o(38f, 56f), o(49f, 59f), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
            drawLine(dark, o(82f, 56f), o(71f, 59f), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
            // 汗滴
            val drop = Path().apply {
                moveTo(o(92f, 42f).x, o(92f, 42f).y)
                cubicTo(o(88f, 50f).x, o(88f, 50f).y, o(88f, 55f).x, o(88f, 55f).y, o(92f, 55f).x, o(92f, 55f).y)
                cubicTo(o(96f, 55f).x, o(96f, 55f).y, o(96f, 50f).x, o(96f, 50f).y, o(92f, 42f).x, o(92f, 42f).y)
                close()
            }
            drawPath(drop, Color(0xFF8CC8F0))
        }
    }

    // ── 雪納瑞的濃眉
    if (kind == "schnauzer") {
        val tilt = if (mood == Mood.WORRIED) 15f else -8f
        oval(44f, 54f, 9f, 4.5f, lk.extra, tilt)
        oval(76f, 54f, 9f, 4.5f, lk.extra, -tilt)
    }

    // ── 腮紅
    oval(35f, 79f, 6.5f, 3.8f, Color(0xFFFF7E7E).copy(alpha = 0.5f))
    oval(85f, 79f, 6.5f, 3.8f, Color(0xFFFF7E7E).copy(alpha = 0.5f))

    // ── 鼻子
    oval(
        60f, 76f, if (kind == "schnauzer") 6f else 5.5f, if (kind == "schnauzer") 4.2f else 3.8f,
        when (kind) {
            "bunny" -> Color(0xFFF59AAE)
            "schnauzer" -> Color(0xFF2E2A2A)
            else -> nose
        },
    )
    val mouthColor = if (kind == "schnauzer") Color(0xFF7A6F6A) else nose

    // ── 嘴巴
    val mouth = Path()
    when (mood) {
        Mood.HAPPY -> {
            mouth.moveTo(o(52f, 84f).x, o(52f, 84f).y)
            mouth.cubicTo(o(55f, 92f).x, o(55f, 92f).y, o(65f, 92f).x, o(65f, 92f).y, o(68f, 84f).x, o(68f, 84f).y)
            drawPath(mouth, mouthColor, style = stroke(2.6f))
        }
        Mood.NORMAL -> {
            mouth.moveTo(o(54f, 85f).x, o(54f, 85f).y)
            mouth.cubicTo(o(56f, 89f).x, o(56f, 89f).y, o(64f, 89f).x, o(64f, 89f).y, o(66f, 85f).x, o(66f, 85f).y)
            drawPath(mouth, mouthColor, style = stroke(2.4f))
        }
        Mood.WORRIED -> {
            mouth.moveTo(o(53f, 89f).x, o(53f, 89f).y)
            mouth.cubicTo(o(56f, 85f).x, o(56f, 85f).y, o(58f, 85f).x, o(58f, 85f).y, o(60f, 88f).x, o(60f, 88f).y)
            mouth.cubicTo(o(62f, 91f).x, o(62f, 91f).y, o(64f, 91f).x, o(64f, 91f).y, o(67f, 87f).x, o(67f, 87f).y)
            drawPath(mouth, mouthColor, style = stroke(2.4f))
        }
    }

    // ── 貓鬍鬚
    if (kind == "cat") {
        val w = Color(0xFFB98A6A)
        drawLine(w, o(30f, 82f), o(18f, 79f), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        drawLine(w, o(30f, 87f), o(18f, 89f), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        drawLine(w, o(90f, 82f), o(102f, 79f), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        drawLine(w, o(90f, 87f), o(102f, 89f), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
    }
}
