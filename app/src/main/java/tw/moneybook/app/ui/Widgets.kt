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
import androidx.compose.ui.graphics.StrokeJoin
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
import tw.moneybook.app.formatMoney
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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

/**
 * 圖表長按選到的那一點：哪一張圖（key）、第幾個、那張圖在畫面上的範圍。
 * 選到後資訊框一直留著，點到那張圖以外的地方才收起來（見 ChartPickArea）。
 */
@Stable
class ChartPick {
    var key by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(-1)
    var area: Rect = Rect.Zero
    fun clear() { key = null; index = -1 }
    fun of(k: String): Int = if (key == k) index else -1
}

val LocalChartPick = staticCompositionLocalOf<ChartPick?> { null }

/** 包住有可長按圖表的整頁：手指按到選中的那張圖以外的地方（包括捲動），就把資訊框收起來 */
@Composable
fun ChartPickArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pick = remember { ChartPick() }
    val origin = remember { arrayOf(Offset.Zero) }
    CompositionLocalProvider(LocalChartPick provides pick) {
        Box(
            modifier.onGloballyPositioned { origin[0] = it.positionInWindow() }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (pick.key != null && !pick.area.contains(down.position + origin[0])) pick.clear()
                }
            },
        ) { content() }
    }
}

/** 長按（約半秒）選這張圖最近的那一組，手指不放左右拖可以換；n 是有幾組（平均分在整個寬度） */
@Composable
private fun Modifier.chartPick(key: String, n: Int): Modifier {
    val pick = LocalChartPick.current ?: return this
    if (n <= 0) return this
    val haptic = LocalHapticFeedback.current
    val area = remember { arrayOf(Rect.Zero) }
    return onGloballyPositioned {
        area[0] = it.boundsInWindow()
        if (pick.key == key) pick.area = area[0]
    }.pointerInput(key, n) {
        fun idx(x: Float) = (x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
        detectDragGesturesAfterLongPress(
            onDragStart = { pos ->
                pick.key = key
                pick.area = area[0]
                pick.index = idx(pos.x)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, _ ->
                change.consume()
                pick.index = idx(change.position.x)
            },
        )
    }
}

/** 資訊框的一行：標籤在左、數字在右 */
class TipLine(val label: String, val value: String, val color: Color)

/** 長按圖表跳出的資訊框：放在選到的那一組旁邊（右半邊的放左邊），寬度固定 */
@Composable
private fun ChartTip(index: Int, n: Int, title: String, lines: List<TipLine>, footer: List<TipLine> = emptyList()) {
    val cute = LocalCute.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tipW = 176.dp
        val slot = maxWidth / n
        val cx = slot * index + slot / 2
        val left = (if (index >= n / 2.0) cx - tipW - 10.dp else cx + 10.dp).coerceIn(0.dp, (maxWidth - tipW).coerceAtLeast(0.dp))
        Column(
            Modifier.offset(x = left).width(tipW).clip(RoundedCornerShape(12.dp)).background(cute.card)
                .border(1.dp, cute.soft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = cute.ink)
            lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.label, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.weight(1f))
                    Text(l.value, style = MaterialTheme.typography.bodyMedium, color = l.color, maxLines = 1)
                }
            }
            if (footer.isNotEmpty()) {
                Row {
                    footer.forEachIndexed { i, l ->
                        Text(
                            l.label + " " + l.value, style = MaterialTheme.typography.labelSmall, color = l.color, maxLines = 1,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 結餘的寫法：正的加 +，負的 - */
fun signedMoney(v: Long): String = (if (v >= 0) "+" else "-") + formatMoney(kotlin.math.abs(v))

/**
 * 成對長條圖：每組兩根（收入、支出），下方有標籤。titles 不是空的就可以長按看那一組的收入、支出、結餘（titles 是資訊框的標題，例如「2026 年 9 月」）。
 */
@Composable
fun PairBars(
    labels: List<String>,
    a: List<Long>,
    b: List<Long>,
    colorA: Color,
    colorB: Color,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    titles: List<String> = emptyList(),
    key: String = "bars",
) {
    val max = (a + b).maxOrNull()?.takeIf { it > 0 } ?: 1L
    val cute = LocalCute.current
    val grid = cute.soft
    val sub = cute.sub
    val n = labels.size.coerceAtLeast(1)
    val sel = if (titles.isNotEmpty()) LocalChartPick.current?.of(key) ?: -1 else -1
    Column(modifier) {
        Box(if (titles.isNotEmpty()) Modifier.chartPick(key, labels.size) else Modifier) {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val slot = size.width / n
                val barW = (slot * 0.28f).coerceAtMost(14.dp.toPx())
                for (k in 1..3) {
                    val y = size.height * k / 4f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                for (i in 0 until n) {
                    val cx = slot * i + slot / 2f
                    if (i == highlight || i == sel) {
                        drawRoundRect(if (i == sel) sub.copy(alpha = 0.25f) else grid.copy(alpha = 0.6f), Offset(slot * i + 2f, 0f), Size(slot - 4f, size.height), CornerRadius(8.dp.toPx()))
                    }
                    val ha = size.height * (a.getOrElse(i) { 0L }.toFloat() / max)
                    val hb = size.height * (b.getOrElse(i) { 0L }.toFloat() / max)
                    val r = CornerRadius(barW / 2f)
                    if (ha > 0f) drawRoundRect(colorA, Offset(cx - barW - 1.5f, size.height - ha), Size(barW, ha), r)
                    if (hb > 0f) drawRoundRect(colorB, Offset(cx + 1.5f, size.height - hb), Size(barW, hb), r)
                }
            }
            if (sel in labels.indices) {
                val ia = a.getOrElse(sel) { 0L }
                val ib = b.getOrElse(sel) { 0L }
                ChartTip(
                    sel, n, titles.getOrElse(sel) { labels[sel] },
                    listOf(
                        TipLine("收入", formatMoney(ia), colorA),
                        TipLine("支出", formatMoney(ib), colorB),
                        TipLine("結餘", signedMoney(ia - ib), if (ia >= ib) cute.income else cute.expense),
                    ),
                )
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

/**
 * 存錢折線圖：累積存款（粗線＋漸層）與每月結餘（細線）。每個月只有一個點（那個月的總數），點和點之間用直線連，不代表中間的日子。
 * titles 不是空的就可以長按看那個月的每月結餘、累積存款、收入、支出（incs／exps 和 monthly 對齊）。
 */
@Composable
fun SavingsLine(
    labels: List<String>, cumulative: List<Long>, monthly: List<Long>, modifier: Modifier = Modifier,
    titles: List<String> = emptyList(), incs: List<Long> = emptyList(), exps: List<Long> = emptyList(),
) {
    val primary = MaterialTheme.colorScheme.primary
    val cute = LocalCute.current
    val card = cute.card
    val n = cumulative.size
    val all = cumulative + monthly + 0L
    val maxV = all.maxOrNull() ?: 0L
    val minV = all.minOrNull() ?: 0L
    val range = (maxV - minV).coerceAtLeast(1L).toFloat()
    val key = "savings"
    val sel = if (titles.isNotEmpty()) LocalChartPick.current?.of(key) ?: -1 else -1
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(formatMoneyShort(maxV), style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.weight(1f))
        }
        Box(if (titles.isNotEmpty()) Modifier.chartPick(key, n) else Modifier) {
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
                // 長按選到的那個月：一條直線
                if (sel in 0 until n) {
                    drawLine(cute.sub, Offset(x(sel), 0f), Offset(x(sel), size.height), strokeWidth = 1.dp.toPx())
                }

                // 每個月只有一個點：用直線連起來（曲線看起來像有每天的資料，容易誤會）
                fun line(values: List<Long>): Path {
                    val p = Path()
                    p.moveTo(x(0), y(values[0]))
                    for (i in 1 until values.size) p.lineTo(x(i), y(values[i]))
                    return p
                }

                // 每月結餘（細線）
                if (n > 1) drawPath(line(monthly), cute.accent2, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                monthly.forEachIndexed { i, v -> drawCircle(cute.accent2, radius = (if (i == sel) 5f else 2.5f).dp.toPx(), center = Offset(x(i), y(v))) }

                // 累積存款：漸層面積 + 粗線
                if (n > 1) {
                    val area = line(cumulative)
                    area.lineTo(x(n - 1), zy)
                    area.lineTo(x(0), zy)
                    area.close()
                    drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.02f)), startY = top, endY = bottom))
                    drawPath(line(cumulative), primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                cumulative.forEachIndexed { i, v ->
                    val big = i == n - 1 || i == sel
                    val r = if (big) 6.dp.toPx() else 4.dp.toPx()
                    drawCircle(primary, radius = r, center = Offset(x(i), y(v)))
                    // 選到的那一點實心，其他空心
                    if (i != sel) drawCircle(card, radius = r - 2.dp.toPx(), center = Offset(x(i), y(v)))
                }
            }
            if (sel in 0 until n) {
                val net = monthly[sel]
                ChartTip(
                    sel, n, titles.getOrElse(sel) { labels[sel] },
                    listOf(
                        TipLine("每月結餘", signedMoney(net), if (net >= 0) cute.income else cute.expense),
                        TipLine("累積存款", formatMoney(cumulative[sel]), primary),
                    ),
                    if (incs.size == n && exps.size == n) listOf(TipLine("收", formatMoney(incs[sel]), cute.income), TipLine("支", formatMoney(exps[sel]), cute.expense)) else emptyList(),
                )
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

/** 總資產圖的顏色（和圓餅圖一樣是固定的圖表色，深淺色模式都看得清楚）：現金銀行藍、投資紫；負債用支出色 */
val AssetCashColor = Color(0xFF6FA3D6)
val AssetInvestColor = Color(0xFFA88BD0)

/** 長條圖下面的月份標籤：太多個時只留幾個（最後一個一定留） */
fun sparseLabels(labels: List<String>, keep: Int = 7): List<String> {
    val step = ((labels.size + keep - 1) / keep).coerceAtLeast(1)
    return labels.mapIndexed { i, l -> if ((labels.size - 1 - i) % step == 0) l else "" }
}

/**
 * 總資產堆疊柱：每個月一根，0 以上現金銀行（藍）＋投資（紫）往上疊，0 以下是負債（紅）；黑線是總資產（資產－負債）。
 * 長按看那個月各塊的金額、佔資產的百分比、比上個月；onPick 告訴外面現在選到哪個月（放開後還是那個月）。
 */
@Composable
fun AssetBars(
    labels: List<String>,
    points: List<tw.moneybook.app.NetWorthPoint>,
    titles: List<String>,
    modifier: Modifier = Modifier,
    key: String = "assetBars",
    onPick: (Int) -> Unit = {},
) {
    val cute = LocalCute.current
    val debtColor = cute.expense.copy(alpha = 0.7f)
    val n = points.size
    val sel = LocalChartPick.current?.of(key) ?: -1
    LaunchedEffect(sel) { if (sel in 0 until n) onPick(sel) }
    val top = (points.maxOfOrNull { maxOf(it.assets, it.net) } ?: 0L).coerceAtLeast(1L)
    val bottom = (points.minOfOrNull { minOf(-it.debt, it.net) } ?: 0L).coerceAtMost(0L)
    val range = (top - bottom).toFloat()
    Column(modifier) {
        Box(Modifier.chartPick(key, n)) {
            Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                if (n == 0) return@Canvas
                val slot = size.width / n
                val barW = (slot * 0.62f).coerceAtMost(18.dp.toPx())
                fun y(v: Long) = (top - v) / range * size.height
                fun cx(i: Int) = slot * i + slot / 2f
                for (i in 0 until n) {
                    if (i == sel) drawRoundRect(cute.sub.copy(alpha = 0.22f), Offset(slot * i + 1f, 0f), Size(slot - 2f, size.height), CornerRadius(6.dp.toPx()))
                    val p = points[i]
                    val x0 = cx(i) - barW / 2f
                    if (p.cash > 0) drawRect(AssetCashColor, Offset(x0, y(p.cash)), Size(barW, y(0) - y(p.cash)))
                    if (p.invest > 0) drawRect(AssetInvestColor, Offset(x0, y(p.cash + p.invest)), Size(barW, y(p.cash) - y(p.cash + p.invest)))
                    if (p.debt > 0) drawRect(debtColor, Offset(x0, y(0)), Size(barW, y(-p.debt) - y(0)))
                }
                drawLine(cute.sub, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
                if (n > 1) {
                    val path = Path()
                    path.moveTo(cx(0), y(points[0].net))
                    for (i in 1 until n) path.lineTo(cx(i), y(points[i].net))
                    drawPath(path, cute.ink, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                for (i in 0 until n) drawCircle(cute.ink, radius = (if (i == sel) 5f else 2.5f).dp.toPx(), center = Offset(cx(i), y(points[i].net)))
            }
            if (sel in 0 until n) {
                val p = points[sel]
                fun pct(v: Long) = if (p.assets > 0) "・${Math.round(v * 100.0 / p.assets)}%" else ""
                val lines = mutableListOf(
                    TipLine("現金銀行", formatMoney(p.cash) + pct(p.cash), AssetCashColor),
                    TipLine("投資", formatMoney(p.invest) + pct(p.invest), AssetInvestColor),
                    TipLine("負債", if (p.debt > 0) "-" + formatMoney(p.debt) else formatMoney(0), cute.expense),
                    TipLine("總資產", formatMoney(p.net), cute.ink),
                )
                if (sel > 0) {
                    val diff = p.net - points[sel - 1].net
                    lines.add(TipLine("比上個月", signedMoney(diff), if (diff >= 0) cute.income else cute.expense))
                }
                ChartTip(sel, n, titles.getOrElse(sel) { labels[sel] }, lines)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            sparseLabels(labels).forEach { l ->
                Text(l, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 百分比堆疊：每個月一根一樣高，下面現金銀行、上面投資，看投資佔資產的比例 */
@Composable
fun AssetShareBars(labels: List<String>, points: List<tw.moneybook.app.NetWorthPoint>, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    val n = points.size
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(18.dp.toPx())
            for (i in 0 until n) {
                val p = points[i]
                val x0 = slot * i + slot / 2f - barW / 2f
                if (p.assets <= 0) {
                    drawRect(cute.soft, Offset(x0, 0f), Size(barW, size.height))
                    continue
                }
                val ci = size.height * p.invest / p.assets
                drawRect(AssetInvestColor, Offset(x0, 0f), Size(barW, ci))
                drawRect(AssetCashColor, Offset(x0, ci), Size(barW, size.height - ci))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            sparseLabels(labels).forEach { l ->
                Text(l, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun formatMoneyShort(v: Long): String =
    (if (v < 0) "-" else "") + tw.moneybook.app.Money.twd + tw.moneybook.app.formatShort(kotlin.math.abs(v))

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
fun Keypad(onKey: (String) -> Unit, doneLabel: String, doneEnabled: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier, dotKey: Boolean = false) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "-"),
        listOf("C", "0", if (dotKey) "." else "00", "OK"),
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
