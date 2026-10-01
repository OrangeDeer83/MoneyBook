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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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

/**
 * 圓餅圖（甜甜圈）＋外圈標籤。沒有分隔線；點一塊會凸出來，並在中間顯示細節。
 * 佔比小於 4% 的區塊不顯示外圈標籤，避免擠在一起。
 */
@Composable
fun LabeledDonut(
    labels: List<String>,
    colors: List<Color>,
    values: List<Long>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val total = values.sum().toFloat().coerceAtLeast(1f)
    val fracs = values.map { it / total }
    val mids = ArrayList<Float>()
    run {
        var acc = 0f
        for (f in fracs) {
            mids.add(-90f + (acc + f / 2f) * 360f)
            acc += f
        }
    }
    val track = LocalCute.current.soft
    val sub = LocalCute.current.sub
    Layout(
        modifier = modifier.fillMaxWidth().height(280.dp),
        content = {
            Canvas(
                Modifier.pointerInput(values, selected) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val d = minOf(w, h) * 0.56f
                        val thick = d * 0.22f
                        val dx = pos.x - w / 2f
                        val dy = pos.y - h / 2f
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist < d / 2f - thick / 2f - 8f || dist > d / 2f + thick / 2f + 24f) {
                            onSelect(-1)
                        } else {
                            var ang = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 90f
                            if (ang < 0f) ang += 360f
                            var acc = 0f
                            var hit = -1
                            for (i in fracs.indices) {
                                acc += fracs[i] * 360f
                                if (ang <= acc) { hit = i; break }
                            }
                            onSelect(hit)
                        }
                    }
                }
            ) {
                val d = minOf(size.width, size.height) * 0.56f
                val thick = d * 0.22f
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(width = thick))
                var start = -90f
                values.forEachIndexed { i, v ->
                    val sweep = v / total * 360f
                    if (sweep > 0f) {
                        val isSel = i == selected
                        val grow = if (isSel) 7.dp.toPx() else 0f
                        val dd = d + grow
                        val t2 = if (isSel) thick + grow else thick
                        drawArc(
                            color = colors[i],
                            startAngle = start - 0.3f,
                            sweepAngle = sweep + 0.6f,
                            useCenter = false,
                            topLeft = Offset((size.width - dd) / 2f, (size.height - dd) / 2f),
                            size = Size(dd, dd),
                            style = Stroke(width = t2, cap = StrokeCap.Butt),
                            alpha = if (selected >= 0 && !isSel) 0.45f else 1f,
                        )
                    }
                    start += sweep
                }
            }
            Box { center() }
            labels.forEachIndexed { i, l ->
                if (fracs[i] >= 0.04f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(l, style = MaterialTheme.typography.labelSmall, color = if (i == selected) colors[i] else sub, maxLines = 1)
                        Text(String.format("%.1f%%", fracs[i] * 100f), style = MaterialTheme.typography.labelSmall, color = sub)
                    }
                } else {
                    Box {}
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val canvas = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
        val centerP = measurables[1].measure(loose)
        val labelPs = measurables.drop(2).map { it.measure(loose) }
        layout(w, h) {
            canvas.place(0, 0)
            centerP.place((w - centerP.width) / 2, (h - centerP.height) / 2)
            val d = minOf(w, h) * 0.56f
            // 圓環外緣（含被選到時凸出的部分）再留一點空隙，標籤依自己的大小往外放，才不會蓋到圓環
            val rOuter = d / 2f + d * 0.11f + 7.dp.toPx() + 6.dp.toPx()
            labelPs.forEachIndexed { i, p ->
                val a = Math.toRadians(mids[i].toDouble())
                val ext = kotlin.math.abs(kotlin.math.cos(a)) * p.width / 2f + kotlin.math.abs(kotlin.math.sin(a)) * p.height / 2f
                val r = rOuter + ext
                val cx = w / 2f + (kotlin.math.cos(a) * r).toFloat()
                val cy = h / 2f + (kotlin.math.sin(a) * r).toFloat()
                val x = (cx - p.width / 2f).toInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                val y = (cy - p.height / 2f).toInt().coerceIn(0, (h - p.height).coerceAtLeast(0))
                p.place(x, y)
            }
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

/** 存錢折線圖：累積存款（粗線＋漸層）與每月結餘（細線） */
@Composable
fun SavingsLine(labels: List<String>, cumulative: List<Long>, monthly: List<Long>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val cute = LocalCute.current
    val card = cute.card
    val n = cumulative.size
    val all = cumulative + monthly + 0L
    val maxV = all.maxOrNull() ?: 0L
    val minV = all.minOrNull() ?: 0L
    val range = (maxV - minV).coerceAtLeast(1L).toFloat()
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(formatMoneyShort(maxV), style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            if (n == 0) return@Canvas
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val h = bottom - top
            val slot = size.width / n
            fun x(i: Int) = slot * i + slot / 2f
            fun y(v: Long) = top + (maxV - v).toFloat() / range * h

            // 0 的基準線
            val zy = y(0L)
            drawLine(
                cute.sub.copy(alpha = 0.5f), Offset(0f, zy), Offset(size.width, zy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )

            fun smooth(values: List<Long>): Path {
                val p = Path()
                p.moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) {
                    val x0 = x(i - 1); val y0 = y(values[i - 1])
                    val x1 = x(i); val y1 = y(values[i])
                    val cx = (x0 + x1) / 2f
                    p.cubicTo(cx, y0, cx, y1, x1, y1)
                }
                return p
            }

            // 每月結餘（細線）
            if (n > 1) drawPath(smooth(monthly), cute.accent2, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            monthly.forEachIndexed { i, v -> drawCircle(cute.accent2, radius = 2.5.dp.toPx(), center = Offset(x(i), y(v))) }

            // 累積存款：漸層面積 + 粗線
            if (n > 1) {
                val area = smooth(cumulative)
                area.lineTo(x(n - 1), zy)
                area.lineTo(x(0), zy)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.02f)), startY = top, endY = bottom))
                drawPath(smooth(cumulative), primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            cumulative.forEachIndexed { i, v ->
                val last = i == n - 1
                val r = if (last) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(primary, radius = r, center = Offset(x(i), y(v)))
                drawCircle(card, radius = r - 2.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatMoneyShort(minV), style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(
                    l,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = cute.sub,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatMoneyShort(v: Long): String =
    (if (v < 0) "-$" else "$") + tw.moneybook.app.formatShort(kotlin.math.abs(v))

/** 橫向比例條 */
@Composable
fun RatioBar(fraction: Float, color: Color, modifier: Modifier = Modifier, thick: Boolean = false) {
    val track = LocalCute.current.soft
    Canvas(modifier.fillMaxWidth().height(if (thick) 12.dp else 8.dp)) {
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
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
    val round: Boolean,
)

/** 新增記錄時從「＋」的位置噴出一小撮彩紙；播過一次就不會再重播 */
@Composable
fun ConfettiOverlay(vm: tw.moneybook.app.MoneyViewModel) {
    val tick = vm.celebrateTick
    if (tick == 0 || tick <= vm.celebratePlayed) {
        return
    }
    val anim = remember(tick) { Animatable(0f) }
    val pieces = remember(tick) {
        List(22) {
            Piece(
                angle = (-90f + (Random.nextFloat() * 2f - 1f) * 38f) * (Math.PI.toFloat() / 180f),
                speed = 0.55f + Random.nextFloat() * 0.45f,
                spin = Random.nextFloat() * 720f - 360f,
                color = CatColors[Random.nextInt(CatColors.size)],
                round = Random.nextBoolean(),
            )
        }
    }
    LaunchedEffect(tick) {
        anim.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
        vm.celebratePlayed = tick
    }
    val p = anim.value
    if (p >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val w = 8.dp.toPx()
        val h = 4.5.dp.toPx()
        val ox = size.width / 2f
        val oy = size.height - 90.dp.toPx()
        val v0 = size.height * 0.95f
        val g = size.height * 1.6f
        val alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
        for (pc in pieces) {
            val t = p
            val x = ox + kotlin.math.cos(pc.angle) * v0 * pc.speed * t
            val y = oy + kotlin.math.sin(pc.angle) * v0 * pc.speed * t + 0.5f * g * t * t
            val c = pc.color.copy(alpha = alpha)
            if (pc.round) {
                drawCircle(c, radius = h * 0.75f, center = Offset(x, y))
            } else {
                rotate(pc.spin * t, pivot = Offset(x, y)) {
                    drawRect(c, topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
                }
            }
        }
    }
}
