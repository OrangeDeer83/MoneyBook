package tw.moneybook.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.moneybook.app.Category
import tw.moneybook.app.Drill
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.catMonthly
import tw.moneybook.app.drillTrendEnd
import tw.moneybook.app.firstMonthOf
import tw.moneybook.app.formatMoney
import tw.moneybook.app.monthlySums
import tw.moneybook.app.monthsEnding
import tw.moneybook.app.trendCats
import tw.moneybook.app.trendStat
import java.time.YearMonth

/** 一條線：名稱（資訊框用）、顏色、每個月的金額；dashed＝和前面的線同色時改成虛線 */
class LineSeries(val name: String, val color: Color, val values: List<Long>, val dashed: Boolean = false)

/** 月份標籤：超過 6 個月時雙數月不寫（最後一個一定寫），和趨勢頁其他圖一樣 */
fun trendLabels(months: List<YearMonth>): List<String> {
    val end = months.lastOrNull()
    return months.map { if (months.size > 6 && it.monthValue % 2 == 0 && it != end) "" else "${it.monthValue}月" }
}

fun trendTitles(months: List<YearMonth>): List<String> = months.map { "${it.year} 年 ${it.monthValue} 月" }

/**
 * 分類走勢折線：每條線一個分類，每個月一個點（直線連起來）。
 * avg 有值時畫一條平均虛線；warn 裡的月份畫紅點（比平均多 30% 以上）。長按看那個月每條線的金額。
 */
@Composable
fun CategoryLines(
    key: String, labels: List<String>, titles: List<String>, series: List<LineSeries>,
    modifier: Modifier = Modifier, avg: Long = 0L, warn: Set<Int> = emptySet(),
    /** 下鑽頁長按選定的月份（明細換成那個月）：那一點加紅圈；-1 沒有 */
    mark: Int = -1,
) {
    val cute = LocalCute.current
    val card = cute.card
    val n = labels.size
    val maxV = (series.flatMap { it.values } + avg + 0L).max()
    val range = maxV.coerceAtLeast(1L).toFloat()
    val sel = LocalChartPick.current?.of(key) ?: -1
    val single = series.size == 1
    Column(modifier) {
        Text(formatMoneyShort(maxV), style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Box(Modifier.chartPick(key, n)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                if (n == 0) return@Canvas
                val top = 10.dp.toPx()
                val bottom = size.height - 10.dp.toPx()
                val h = bottom - top
                val slot = size.width / n
                fun x(i: Int) = slot * i + slot / 2f
                fun y(v: Long) = top + (maxV - v).toFloat() / range * h
                // 0 的基準線
                drawLine(cute.soft, Offset(0f, y(0L)), Offset(size.width, y(0L)), strokeWidth = 1.dp.toPx())
                // 平均（虛線）
                if (avg > 0L) {
                    drawLine(
                        cute.sub, Offset(0f, y(avg)), Offset(size.width, y(avg)), strokeWidth = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)),
                    )
                }
                if (sel in 0 until n) {
                    drawLine(cute.sub, Offset(x(sel), 0f), Offset(x(sel), size.height), strokeWidth = 1.dp.toPx())
                }
                series.forEach { s ->
                    if (n > 1) {
                        val p = Path()
                        p.moveTo(x(0), y(s.values[0]))
                        for (i in 1 until n) p.lineTo(x(i), y(s.values[i]))
                        drawPath(
                            p, s.color,
                            style = Stroke(
                                width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
                            ),
                        )
                    }
                    // 只有一條線時每個月畫點（比平均多很多的紅色大點）；好幾條時只畫選到的那個月
                    s.values.forEachIndexed { i, v ->
                        val c = Offset(x(i), y(v))
                        if (single && i == mark) {
                            drawCircle(s.color, radius = 6.dp.toPx(), center = c)
                            drawCircle(cute.expense, radius = 8.dp.toPx(), center = c, style = Stroke(width = 2.5f.dp.toPx()))
                            return@forEachIndexed
                        }
                        when {
                            single && i in warn -> drawCircle(cute.expense, radius = 6.dp.toPx(), center = c)
                            i == sel -> drawCircle(s.color, radius = 5.dp.toPx(), center = c)
                            single -> {
                                drawCircle(s.color, radius = 3.5f.dp.toPx(), center = c)
                                drawCircle(card, radius = 1.5f.dp.toPx(), center = c)
                            }
                        }
                    }
                }
            }
            if (sel in 0 until n) {
                val lines = series.map { s -> TipLine(s.name, formatMoney(s.values[sel]), s.color) }
                ChartTip(sel, n, titles.getOrElse(sel) { labels[sel] }, lines, if (avg > 0L) listOf(TipLine("平均", formatMoney(avg), cute.sub)) else emptyList())
            }
        }
        Text("0", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(l, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
            }
        }
    }
}

/** 比平均多／少幾 %（整數，四捨五入） */
private fun pctVs(v: Long, avg: Long): Long = Math.round((v - avg) * 100.0 / avg)

/**
 * 統計下鑽頁（從支出分類點進來）上面的「近 12 個月」走勢。
 * 長按某個月：onFocus(那個月)，下面的明細換成那個月；點一下圖（不論點到哪裡）：onFocus(null) 回到原本的期間。
 */
@Composable
fun DrillTrendCard(vm: MoneyViewModel, dr: Drill, cat: Category, focus: YearMonth?, onFocus: (YearMonth?) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val now = YearMonth.now()
    val end = drillTrendEnd(dr.mode, vm.month, vm.statYear, vm.rangeEnd, now)
    val months = remember(end) { monthsEnding(end, 12) }
    val values = remember(d, dr, end) { monthlySums(d.bookTxns, months, dr.pred, dr.amt) }
    val first = remember(d, dr) { firstMonthOf(d.bookTxns, dr.pred) }
    val st = trendStat(values, months, first, now)
    val color = catColor(cat.color)
    val pick = LocalChartPick.current
    val sel = pick?.of("drillTrend") ?: -1
    // 長按選到（手不放左右移動也算）就把明細換成那個月；資訊框收起來後明細還是停在那個月
    androidx.compose.runtime.LaunchedEffect(sel) { if (sel in months.indices) onFocus(months[sel]) }
    CuteCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text("近 12 個月", style = MaterialTheme.typography.titleSmall)
        val last = values.last()
        val head = "${if (end == now) "這個月" else "${end.monthValue} 月"} ${formatMoney(last)}"
        if (st.avg > 0L) {
            val p = pctVs(last, st.avg)
            Text(
                head + when {
                    p > 0 -> "，比平均 ${formatMoney(st.avg)} 多 $p%"
                    p < 0 -> "，比平均 ${formatMoney(st.avg)} 少 ${-p}%"
                    else -> "，和平均 ${formatMoney(st.avg)} 一樣"
                } + if (end == now) "（還沒過完）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (p >= 30) cute.expense else if (p < 0) cute.income else cute.sub,
            )
        } else {
            Text(head, style = MaterialTheme.typography.bodySmall, color = cute.sub)
        }
        Spacer(Modifier.height(8.dp))
        // 點一下圖：回到原本的期間（長按放開時那一下已經被長按吃掉，不會算成點一下）
        Box(Modifier.pointerInput(Unit) { detectTapGestures { pick?.clear(); onFocus(null) } }) {
            CategoryLines(
                "drillTrend", trendLabels(months), trendTitles(months), listOf(LineSeries(cat.name, color, values)),
                avg = st.avg, warn = st.warn, mark = focus?.let { months.indexOf(it) } ?: -1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (st.avg > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashMark(cute.sub)
                    Text(" 平均 ${formatMoney(st.avg)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                }
            }
            if (st.warn.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(cute.expense))
                    Text(" 比平均多 30% 以上", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                }
            }
        }
    }
}

/** 圖例的一小段虛線 */
@Composable
private fun DashMark(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
    }
}

/** 趨勢頁「分類走勢」卡片：挑 1～3 個分類，每個月花多少疊在一起；「切換分類」換要看的分類 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatTrendCard(vm: MoneyViewModel, months: List<YearMonth>, menu: CardMenu) {
    val d = vm.data
    val cute = LocalCute.current
    val end = months.last()
    val ids = remember(d, end) { d.trendCats(end) }
    var pick by remember { mutableStateOf(false) }
    val series = remember(d, ids, months) {
        val used = HashSet<Int>()
        ids.mapNotNull { id ->
            val c = d.catMap[id] ?: return@mapNotNull null
            // 顏色和前面的線一樣（例如大分類和它的子分類）就改成虛線
            val dashed = !used.add(Math.floorMod(c.color, CatColors.size))
            LineSeries(c.name, catColor(c.color), d.catMonthly(id, months), dashed)
        }
    }
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("分類走勢", "catTrend", menu)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                series.forEach { s ->
                    Row(
                        Modifier.clip(CircleShape).background(cute.soft).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 虛線的那條用一小段虛線當標記，分得出是哪一條
                        if (s.dashed) DashMark(s.color) else Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(5.dp))
                        Text(s.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "切換分類", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { pick = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (series.isEmpty()) {
            Text("最近 12 個月還沒有支出，記幾筆之後這裡會畫出每個分類的走勢", style = MaterialTheme.typography.bodySmall, color = cute.sub)
        } else {
            CategoryLines("catTrend", trendLabels(months), trendTitles(months), series)
            Spacer(Modifier.height(4.dp))
            Text("長按圖可以看那個月每個分類花多少", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        }
    }
    if (pick) {
        CatTrendPickDialog(vm, ids, { vm.setTrendCats(it); pick = false }, { pick = false })
    }
}

/** 「切換分類」視窗：大分類和它底下的子分類（縮排），最多選 3 個、至少 1 個；只有支出分類 */
@Composable
private fun CatTrendPickDialog(vm: MoneyViewModel, initial: List<Long>, onDone: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val picked = remember { mutableStateListOf<Long>().apply { addAll(initial) } }
    var warn by remember { mutableStateOf<String?>(null) }
    val tops = remember(d) { d.topCategories(TxType.EXPENSE) }
    // 展開的大分類：一打開時只展開有選子分類的（只選大分類或沒選的收起來）
    val open = remember { mutableStateListOf<Long>().apply { addAll(tops.filter { t -> d.childrenOf(t.id).any { it.id in initial } }.map { it.id }) } }
    val rows = tops.flatMap { t -> listOf(t to false) + if (t.id in open) d.childrenOf(t.id).map { it to true } else emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("要看哪幾個分類") },
        text = {
            Column {
                Text(
                    warn ?: "最多 3 個（已選 ${picked.size}）",
                    style = MaterialTheme.typography.bodySmall, color = if (warn != null) MaterialTheme.colorScheme.error else cute.sub,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(rows, key = { it.first.id }) { (c, child) ->
                        val on = c.id in picked
                        val full = picked.size >= 3
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).alpha(if (full && !on) 0.4f else 1f)
                                // 畫面測試用描述找到這一列（底下的卡片也有同名的分類）
                                .semantics { contentDescription = "挑分類：${c.name}" }
                                .clickable {
                                    when {
                                        on -> { picked.remove(c.id); warn = null }
                                        full -> warn = "最多 3 個，先取消一個再選"
                                        else -> { picked.add(c.id); warn = null }
                                    }
                                }
                                .padding(start = if (child) 28.dp else 4.dp, top = 2.dp, bottom = 2.dp, end = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) primary else cute.soft),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Text("✓", color = MaterialTheme.colorScheme.onPrimary) }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.size(12.dp).clip(CircleShape).background(catColor(c.color)))
                            Spacer(Modifier.width(8.dp))
                            val kids = if (child) emptyList() else d.childrenOf(c.id)
                            val isOpen = c.id in open
                            // 收起來時提醒裡面選了哪幾個子分類
                            val pickedKids = if (isOpen) emptyList() else kids.filter { it.id in picked }
                            // 名稱＋提醒吃掉剩下的寬度，箭頭固定在最右邊
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, style = if (child) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                                if (pickedKids.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "已選 " + pickedKids.joinToString("、") { it.name }, style = MaterialTheme.typography.labelMedium,
                                        color = primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                            if (kids.isNotEmpty()) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).clickable { if (isOpen) open.remove(c.id) else open.add(c.id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (isOpen) AppIcons.ChevronDown else AppIcons.ChevronRight,
                                        contentDescription = if (isOpen) "收起${c.name}" else "展開${c.name}", tint = cute.sub,
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (picked.isEmpty()) warn = "至少要選 1 個" else onDone(picked.toList()) }) { Text("完成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
