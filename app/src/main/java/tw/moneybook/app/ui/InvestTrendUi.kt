package tw.moneybook.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AccountType
import tw.moneybook.app.InvestPoint
import tw.moneybook.app.InvestRanges
import tw.moneybook.app.InvestStep
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.defaultInvestRange
import tw.moneybook.app.firstTradeDay
import tw.moneybook.app.formatMoney
import tw.moneybook.app.investDays
import tw.moneybook.app.investSeries
import tw.moneybook.app.periodGain
import tw.moneybook.app.portfolio
import java.time.LocalDate

/** 百分比文字：+7.6%、−2.0% */
private fun pctText(p: Double): String =
    (if (p > 0.0) "+" else if (p < 0.0) "−" else "") + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(p) * 100) + "%"

/**
 * 投資成效卡片：現值（實線＋面積）與成本（虛線），可以切換成報酬率；範圍 1 個月／3 個月／1 年／全部。
 * accountIds 是 null＝所有投資帳戶；symbol 有填就只算那一檔。key 用來記住這張卡片選的範圍、長按的圖。
 * 預設範圍：第一次買進超過 3 個月看 3 個月，不到看 1 個月；選過就記住（到關掉 App 為止）。
 */
@Composable
fun InvestTrendCard(
    vm: MoneyViewModel, key: String, title: String, accountIds: Set<Long>?, symbol: String? = null,
    menuId: String? = null, menu: CardMenu? = null,
) {
    val d = vm.data
    val cute = LocalCute.current
    val today = remember { LocalDate.now().toEpochDay() }
    val first = remember(d, accountIds, symbol) { d.firstTradeDay(accountIds, symbol) }
    var range by remember(key, first) { mutableIntStateOf(vm.investRanges[key] ?: first?.let { defaultInvestRange(it, today) } ?: 3) }
    var pct by remember(key) { mutableStateOf(vm.investPct[key] == true) }
    val plan = remember(range, first) { first?.let { investDays(range, it, today) } }
    val pts = remember(d, plan, accountIds, symbol) { plan?.let { d.investSeries(it.first, accountIds, symbol, today) } ?: emptyList() }
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            PillSegment(listOf("金額", "報酬率"), if (pct) 1 else 0, { pct = it == 1; vm.investPct[key] = pct })
            if (menu != null && menuId != null) {
                Spacer(Modifier.width(4.dp))
                CardMore(menuId, menu)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (first == null || plan == null) {
            Text("還沒有買賣記錄，買進後這裡會畫出現值和成本的變化。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            return@CuteCard
        }
        ChipRow {
            InvestRanges.all.forEach { (r, label) -> CuteChip(label, r == range, { range = r; vm.investRanges[key] = r }) }
        }
        Spacer(Modifier.height(8.dp))
        if (pts.size < 2) {
            Text("買進後隔天開始畫折線。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            return@CuteCard
        }
        if (LocalChartPick.current == null) ChartPickArea { InvestLines(key, pts, pct, plan.second, today) }
        else InvestLines(key, pts, pct, plan.second, today)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!pct) {
                Box(Modifier.size(width = 16.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                Text(" 現值", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(10.dp))
                DashSwatch()
                Text(" 成本", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                Spacer(Modifier.weight(1f))
                val g = periodGain(pts)
                Text(
                    (if (range == 0) "累計 " else "${InvestRanges.label(range)}來 ") + signedMoney(g),
                    style = MaterialTheme.typography.labelMedium, color = if (g >= 0L) cute.income else cute.expense,
                )
            } else {
                Text("報酬率＝未實現損益 ÷ 成本", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.weight(1f))
                val p = pts.last().pct
                if (p != null) Text("目前 ${pctText(p)}", style = MaterialTheme.typography.labelMedium, color = if (p >= 0.0) cute.income else cute.expense)
            }
        }
    }
}

/** 圖例的虛線（成本） */
@Composable
private fun DashSwatch() {
    val c = LocalCute.current.sub
    Canvas(Modifier.size(width = 16.dp, height = 3.dp)) {
        drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    }
}

/** 折線本體：金額＝現值（實線、下面淡色面積）＋成本（虛線）；報酬率＝一條線加 0% 虛線。長按拖曳看那一天 */
@Composable
private fun InvestLines(key: String, pts: List<InvestPoint>, pct: Boolean, step: InvestStep, today: Long) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val n = pts.size
    val sel = LocalChartPick.current?.of(key) ?: -1
    val vals = if (pct) pts.map { (it.pct ?: 0.0) * 100.0 } else pts.map { it.value.toDouble() }
    val costs = if (pct) emptyList() else pts.map { it.cost.toDouble() }
    val all = vals + costs + (if (pct) listOf(0.0) else emptyList())
    var hi = all.max()
    var lo = all.min()
    if (hi - lo < 1e-6) { hi += 1.0; lo -= 1.0 }
    val pad = (hi - lo) * 0.08
    hi += pad
    lo -= pad
    fun axis(v: Double) = if (pct) String.format(java.util.Locale.US, "%.1f%%", v) else formatMoneyShort(Math.round(v))
    fun label(i: Int): String {
        val day = LocalDate.ofEpochDay(pts[i].day)
        return if (step == InvestStep.MONTH && pts[i].day < today) "${day.year % 100}/${day.monthValue}" else "${day.monthValue}/${day.dayOfMonth}"
    }
    fun title(i: Int): String {
        val day = LocalDate.ofEpochDay(pts[i].day)
        return when {
            pts[i].day >= today -> "今天"
            step == InvestStep.MONTH -> "${day.year} 年 ${day.monthValue} 月底"
            else -> "${day.year}/${day.monthValue}/${day.dayOfMonth}"
        }
    }
    val last = pts.last()
    Column {
        Text(axis(hi), style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Box(
            Modifier.chartPick(key, n).semantics {
                contentDescription = "投資成效折線：${title(0)} 到 ${title(n - 1)}，現值 ${formatMoney(last.value)}，成本 ${formatMoney(last.cost)}"
            },
        ) {
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val slot = size.width / n
                fun x(i: Int) = slot * i + slot / 2f
                fun y(v: Double) = ((hi - v) / (hi - lo) * size.height).toFloat()
                fun path(vs: List<Double>): Path = Path().apply {
                    moveTo(x(0), y(vs[0]))
                    for (i in 1 until n) lineTo(x(i), y(vs[i]))
                }
                if (pct) {
                    drawLine(
                        cute.sub, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
                if (sel in 0 until n) drawLine(cute.sub, Offset(x(sel), 0f), Offset(x(sel), size.height), strokeWidth = 1.dp.toPx())
                if (!pct) {
                    val area = path(vals).apply {
                        lineTo(x(n - 1), size.height)
                        lineTo(x(0), size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.25f), primary.copy(alpha = 0.02f))))
                    drawPath(
                        path(costs), cute.sub,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
                    )
                }
                drawPath(path(vals), primary, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // 最後一點（今天）和長按選到的那一點畫圓點；報酬率的點用賺賠的顏色
                for (i in listOf(n - 1, sel).distinct().filter { it in 0 until n }) {
                    val c = if (pct) (if (vals[i] >= 0.0) cute.income else cute.expense) else primary
                    drawCircle(c, radius = 5.dp.toPx(), center = Offset(x(i), y(vals[i])))
                }
            }
            if (sel in 0 until n) {
                val p = pts[sel]
                ChartTip(
                    sel, n, title(sel),
                    listOf(
                        TipLine("現值", formatMoney(p.value), primary),
                        TipLine("成本", formatMoney(p.cost), cute.sub),
                        TipLine("損益", signedMoney(p.gain) + (p.pct?.let { "（${pctText(it)}）" } ?: ""), if (p.gain >= 0L) cute.income else cute.expense),
                    ),
                    tipWidth = 200.dp,
                )
            }
        }
        Text(axis(lo), style = MaterialTheme.typography.labelSmall, color = cute.sub)
        // 日期只標頭、尾和中間兩個
        val marks = listOf(0, n / 3, 2 * n / 3, n - 1).distinct()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            marks.forEach { Text(label(it), style = MaterialTheme.typography.labelSmall, color = cute.sub) }
        }
    }
}

/** 全部投資帳戶加起來：總覽、投資成效、各投資帳戶的市值與報酬率（點了進那個帳戶） */
@Composable
fun InvestAllScreen(vm: MoneyViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val pf = remember(d) { d.portfolio() }
    val accs = remember(d) { d.visibleAccounts.filter { it.type == AccountType.INVEST } }
    SubPage("全部投資", onBack) {
        ChartPickArea(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PortfolioSummary(pf) }
                item { InvestTrendCard(vm, "invest:all", "投資成效", null) }
                if (accs.isNotEmpty()) {
                    item { Text("各帳戶", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
                }
                items(accs, key = { it.id }) { a ->
                    val p = remember(d, a.id) { d.portfolio(a.id) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card)
                            .clickable { onOpen(a.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                if (p.positions.isEmpty()) "沒有持股" else "${p.positions.size} 檔・成本 ${formatMoney(p.cost)}",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatMoney(p.value), fontWeight = FontWeight.SemiBold)
                            if (p.cost > 0L) {
                                Text(
                                    gainText(p.gain, p.gain.toDouble() / p.cost), style = MaterialTheme.typography.labelMedium,
                                    color = if (p.gain >= 0L) cute.income else cute.expense,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
