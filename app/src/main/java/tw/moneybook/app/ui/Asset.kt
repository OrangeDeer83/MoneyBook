package tw.moneybook.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AppData
import tw.moneybook.app.CardPages
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.NetWorthPoint
import tw.moneybook.app.firstMonth
import tw.moneybook.app.formatMoney
import tw.moneybook.app.netWorthMonths
import tw.moneybook.app.netWorthReasons
import java.time.LocalDate
import java.time.YearMonth

/** 總資產的月份：近 n 個月（0＝全部，從有記錄的第一個月開始），到這個月為止 */
fun AppData.assetMonths(n: Int, today: LocalDate = LocalDate.now()): List<YearMonth> {
    val now = YearMonth.from(today)
    val first = if (n <= 0) firstMonth(today) else now.minusMonths((n - 1).toLong())
    val out = ArrayList<YearMonth>()
    var m = first
    while (m <= now) { out.add(m); m = m.plusMonths(1) }
    return out
}

/** 帳戶頁、趨勢頁用：現在的總資產、比上個月底多多少（沒有上個月的記錄是 null）、近 12 個月的總資產 */
class AssetSummary(val now: Long, val vsLastMonth: Long?, val trail: List<Long>)

fun AppData.assetSummary(today: LocalDate = LocalDate.now()): AssetSummary {
    val months = assetMonths(12, today).filter { it >= firstMonth(today) }
    val pts = netWorthMonths(months, today).map { it.net }
    return AssetSummary(pts.last(), if (pts.size >= 2) pts.last() - pts[pts.size - 2] else null, pts)
}

/** 總資產頁：最上面切換期間，下面是可以調整排版的卡片（現在的組成、總資產變化、變化的原因、投資佔資產的比例） */
@Composable
fun AssetScreen(vm: MoneyViewModel, onLayout: (String) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val d = vm.data
    val cute = LocalCute.current
    val page = CardPages.ASSET
    val layout = vm.cardLayout(page)
    val menu = CardMenu(onHide = { vm.hideCard(page, it) }, onLayout = { onLayout(page.key) })
    val months = remember(d, vm.assetRange) { d.assetMonths(vm.assetRange) }
    val points = remember(d, months) { d.netWorthMonths(months) }
    val labels = months.map { if (it.monthValue == 1 || it == months.first()) "${it.year % 100}/${it.monthValue}" else "${it.monthValue}月" }
    val titles = months.map { "${it.year} 年 ${it.monthValue} 月" }
    // 「變化的原因」看哪個月：長按圖上的月份會換過去，放開後還是那個月
    var picked by remember(months) { mutableIntStateOf(months.size - 1) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        SubTopBar("總資產", onBack)
        ChartPickArea(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val narrow = isNarrow(maxWidth)
                LazyColumn(
                    modifier = Modifier.fillMaxHeight().widthIn(max = CardMaxWidth).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CuteChip("近 6 個月", vm.assetRange == 6, { vm.assetRange = 6 })
                            CuteChip("近 12 個月", vm.assetRange == 12, { vm.assetRange = 12 })
                            CuteChip("全部", vm.assetRange == 0, { vm.assetRange = 0 })
                        }
                    }
                    cardRows(layout, narrow, { true }, { onLayout(page.key) }) { id, _ ->
                        when (id) {
                            "compose" -> ComposeCard(points.last(), menu)
                            "change" -> ChangeCard(vm.assetRange, months, labels, titles, points, menu) { picked = it }
                            "reasons" -> ReasonsCard(d, months[picked.coerceIn(months.indices)], menu)
                            "share" -> ShareCard(labels, points, menu)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

/** 現在的組成：資產裡現金銀行、投資各佔幾 %，負債是資產的幾 % */
@Composable
private fun ComposeCard(p: NetWorthPoint, menu: CardMenu) {
    val cute = LocalCute.current
    fun pct(v: Long, of: Long) = if (of > 0) Math.round(v * 100.0 / of) else 0L
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("現在的組成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatMoney(p.net), style = MaterialTheme.typography.titleMedium, color = if (p.net < 0) cute.expense else cute.ink)
            Spacer(Modifier.width(4.dp))
            CardMore("compose", menu)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
            if (p.assets <= 0) {
                drawRect(cute.soft)
                return@Canvas
            }
            val w = size.width * p.cash / p.assets
            drawRect(AssetCashColor, Offset.Zero, Size(w, size.height))
            drawRect(AssetInvestColor, Offset(w, 0f), Size(size.width - w, size.height))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(AssetCashColor)
            Text(" 現金銀行 ${formatMoney(p.cash)}・${pct(p.cash, p.assets)}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(AssetInvestColor)
            Text(" 投資 ${formatMoney(p.invest)}・${pct(p.invest, p.assets)}%", style = MaterialTheme.typography.bodySmall)
        }
        if (p.debt > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(cute.expense.copy(alpha = 0.7f))
                Text(
                    " 負債 ${formatMoney(p.debt)}" + if (p.assets > 0) "，是資產的 ${pct(p.debt, p.assets)}%" else "",
                    style = MaterialTheme.typography.bodySmall, color = cute.expense,
                )
            }
        }
    }
}

/** 總資產變化：堆疊柱＋總資產線，長按看那個月 */
@Composable
private fun ChangeCard(
    range: Int, months: List<YearMonth>, labels: List<String>, titles: List<String>, points: List<NetWorthPoint>,
    menu: CardMenu, onPick: (Int) -> Unit,
) {
    val cute = LocalCute.current
    val diff = points.last().net - points.first().net
    val span = if (range > 0) "近 $range 個月" else "${months.first().year}/${months.first().monthValue} 到現在"
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("總資產變化", "change", menu)
        if (points.size >= 2) {
            Text(
                if (diff >= 0) "${span}多了 ${formatMoney(diff)}" else "${span}少了 ${formatMoney(-diff)}",
                style = MaterialTheme.typography.bodyMedium, color = if (diff >= 0) cute.income else cute.expense,
            )
        }
        Spacer(Modifier.height(12.dp))
        AssetBars(labels, points, titles, onPick = onPick)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(AssetCashColor to "現金銀行", AssetInvestColor to "投資", cute.expense.copy(alpha = 0.7f) to "負債").forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(c))
                    Text(" $l", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 3.dp).background(cute.ink))
                Text(" 總資產", style = MaterialTheme.typography.labelMedium, color = cute.sub)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("總資產＝資產－負債；長按看那個月的數字", style = MaterialTheme.typography.labelSmall, color = cute.sub)
    }
}

/** 這個月變化的原因：收入－支出、投資漲跌、報銷先墊／收回、其他，加起來等於這個月的變化 */
@Composable
private fun ReasonsCard(d: AppData, m: YearMonth, menu: CardMenu) {
    val cute = LocalCute.current
    val r = remember(d, m) { d.netWorthReasons(m) }
    val rows = buildList {
        add("收入－支出（存下來的）" to r.saved)
        add("投資漲跌（同步市值）" to r.invest)
        if (r.reimb != 0L) add((if (r.reimb < 0) "先墊、還沒收回的報銷" else "收回先墊的報銷") to r.reimb)
        if (r.other != 0L) add("其他（餘額調整、換匯、匯率）" to r.other)
    }
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.monthValue} 月變化的原因", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(signedMoney(r.change), style = MaterialTheme.typography.titleMedium, color = if (r.change >= 0) cute.income else cute.expense)
            Spacer(Modifier.width(4.dp))
            CardMore("reasons", menu)
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { (l, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    signedMoney(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    color = when {
                        v > 0 -> cute.income
                        v < 0 -> cute.expense
                        else -> cute.sub
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("長按上面圖的月份，這張也會換成那個月", style = MaterialTheme.typography.labelSmall, color = cute.sub)
    }
}

/** 投資佔資產的比例（預設隱藏）：百分比堆疊 */
@Composable
private fun ShareCard(labels: List<String>, points: List<NetWorthPoint>, menu: CardMenu) {
    val cute = LocalCute.current
    fun share(p: NetWorthPoint) = if (p.assets > 0) Math.round(p.invest * 100.0 / p.assets) else 0L
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("投資佔資產的比例", "share", menu)
        Spacer(Modifier.height(10.dp))
        AssetShareBars(labels, points)
        Spacer(Modifier.height(6.dp))
        val a = share(points.first())
        val b = share(points.last())
        Text(if (points.size >= 2 && a != b) "投資從 $a% 變成 $b%" else "投資佔資產的 $b%", style = MaterialTheme.typography.bodySmall, color = cute.sub)
    }
}
