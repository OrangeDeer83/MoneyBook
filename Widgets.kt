package tw.moneybook.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// ───────────────────────── 圖表 ─────────────────────────

/** 甜甜圈圖，每段之間留一點縫，看起來比較可愛 */
@Composable
fun DonutChart(parts: List<Pair<Color, Long>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.toFloat()
    val track = LocalCute.current.soft
    Canvas(modifier) {
        val thick = size.minDimension * 0.16f
        val d = size.minDimension - thick
        val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(width = thick))
        if (total <= 0f) return@Canvas
        val gap = if (parts.size > 1) 3f else 0f
        var start = -90f
        for ((c, v) in parts) {
            val sweep = v.toFloat() / total * 360f
            if (sweep > gap) {
                drawArc(
                    color = c,
                    startAngle = start + gap / 2f,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = tl,
                    size = Size(d, d),
                    style = Stroke(width = thick, cap = StrokeCap.Butt),
                )
            }
            start += sweep
        }
    }
}

/** 成對長條圖：每組兩根（收入、支出），下方有標籤 */
@Composable
fun PairBars(
    labels: List<String>,
    a: List<Long>,
    b: List<Long>,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
) {
    val max = (a + b).maxOrNull()?.takeIf { it > 0 } ?: 1L
    val grid = LocalCute.current.soft
    val sub = LocalCute.current.sub
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.28f).coerceAtMost(14.dp.toPx())
            for (k in 1..3) {
                val y = size.height * k / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            for (i in 0 until n) {
                val cx = slot * i + slot / 2f
                if (i == highlight) {
                    drawRoundRect(grid.copy(alpha = 0.6f), Offset(slot * i + 2f, 0f), Size(slot - 4f, size.height), CornerRadius(8.dp.toPx()))
                }
                val ha = size.height * (a.getOrElse(i) { 0L }.toFloat() / max)
                val hb = size.height * (b.getOrElse(i) { 0L }.toFloat() / max)
                val r = CornerRadius(barW / 2f)
                if (ha > 0f) drawRoundRect(colorA, Offset(cx - barW - 1.5f, size.height - ha), Size(barW, ha), r)
                if (hb > 0f) drawRoundRect(colorB, Offset(cx + 1.5f, size.height - hb), Size(barW, hb), r)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(
                    l,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = sub,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 橫向比例條 */
@Composable
fun RatioBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = LocalCute.current.soft
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

// ───────────────────────── 計算機鍵盤 ─────────────────────────

@Composable
fun Keypad(onKey: (String) -> Unit, doneLabel: String, doneEnabled: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "-"),
        listOf("C", "0", "00", "OK"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    val isOk = k == "OK"
                    val isOp = k == "+" || k == "-" || k == "⌫" || k == "C"
                    val bg = when {
                        isOk -> if (doneEnabled) primary else primary.copy(alpha = 0.4f)
                        isOp -> cute.soft
                        else -> cute.card
                    }
                    val fg = when {
                        isOk -> MaterialTheme.colorScheme.onPrimary
                        isOp -> primary
                        else -> cute.ink
                    }
                    val label = when (k) {
                        "OK" -> doneLabel
                        "-" -> "−"
                        else -> k
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isOk) onDone() else onKey(k)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = fg, fontSize = if (isOk) 17.sp else 21.sp)
                    }
                }
            }
        }
    }
}

// ───────────────────────── 記帳小動畫 ─────────────────────────

private class Piece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean,
)

/** trigger 每增加一次就撒一次花 */
@Composable
fun ConfettiOverlay(trigger: Int) {
    if (trigger == 0) return
    val anim = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        List(46) {
            Piece(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.25f,
                speed = 0.8f + Random.nextFloat() * 0.6f,
                drift = Random.nextFloat() * 2f - 1f,
                spin = Random.nextFloat() * 720f - 360f,
                color = CatColors[Random.nextInt(CatColors.size)],
                round = Random.nextBoolean(),
            )
        }
    }
    LaunchedEffect(trigger) {
        anim.animateTo(1f, tween(durationMillis = 1700, easing = LinearEasing))
    }
    val p = anim.value
    if (p >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val w = 9.dp.toPx()
        val h = 5.dp.toPx()
        for (pc in pieces) {
            val t = ((p - pc.delay) / (1f - pc.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val y = -20f + t * pc.speed * size.height * 1.1f
            val x = pc.x * size.width + pc.drift * t * 60.dp.toPx()
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            val c = pc.color.copy(alpha = alpha)
            if (pc.round) {
                drawCircle(c, radius = h * 0.7f, center = Offset(x, y))
            } else {
                rotate(pc.spin * t, pivot = Offset(x, y)) {
                    drawRect(c, topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
                }
            }
        }
    }
}
