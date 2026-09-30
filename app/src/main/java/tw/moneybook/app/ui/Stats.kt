package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.inMonth
import tw.moneybook.app.inYear
import tw.moneybook.app.incomeSum
import tw.moneybook.app.statAmount
import java.time.YearMonth
import java.time.LocalDate

/** 圓餅圖的一塊 */
private data class Slice(
    val key: String,
    val label: String,
    val emoji: String,
    val color: Color,
    val amount: Long,
    val count: Int,
    val pred: (Txn) -> Boolean,
    val children: List<Slice>,
)

/** 圓餅圖依排名上色：暖色到冷色的漸層，相鄰顏色不會撞在一起 */
private val ChartSeq = listOf(
    Color(0xFFF2994A), Color(0xFFF5B42E), Color(0xFFF2CC2C), Color(0xFFC3CF45), Color(0xFF8FCB5A),
    Color(0xFF5CC98E), Color(0xFF45BFB0), Color(0xFF4FA7D9), Color(0xFF8C8FE0), Color(0xFFC28FD9),
)

private fun seqColor(i: Int): Color = ChartSeq[i % ChartSeq.size]

private fun recolor(list: List<Slice>): List<Slice> =
    list.sortedByDescending { it.amount }.mapIndexed { i, sl ->
        val c = seqColor(i)
        sl.copy(color = c, children = sl.children.map { ch -> ch.copy(color = c) })
    }

private fun byCategory(d: AppData, list: List<Txn>, amt: (Txn) -> Long): List<Slice> {
    val groups = list.groupBy { t -> t.categoryId?.let { d.catMap[it] }?.let { d.topOf(it).id } ?: -1L }
    return groups.map { (topId, ts) ->
        val top = d.catMap[topId]
        val kidIds = d.childrenOf(topId).map { it.id }.toSet()
        val subs = ts.groupBy { it.categoryId ?: -1L }
            .filter { (cid, _) -> cid != topId }
            .map { (cid, sts) ->
                val c = d.catMap[cid]
                Slice("c$cid", c?.name ?: "未分類", c?.emoji ?: "img:cat_box", Color.Gray, sts.sumOf { amt(it) }, sts.size,
                    { t -> t.categoryId == cid }, emptyList())
            }
        val directList = ts.filter { it.categoryId == topId }
        val children = if (subs.isEmpty()) emptyList() else {
            val all = subs.toMutableList()
            if (directList.isNotEmpty()) {
                all.add(Slice("d$topId", "未細分", top?.emoji ?: "img:cat_box", Color.Gray, directList.sumOf { amt(it) }, directList.size,
                    { t -> t.categoryId == topId }, emptyList()))
            }
            all.sortedByDescending { it.amount }
        }
        val pred: (Txn) -> Boolean = if (topId == -1L) ({ t -> t.categoryId == null || d.catMap[t.categoryId] == null })
        else ({ t -> t.categoryId == topId || (t.categoryId != null && t.categoryId in kidIds) })
        Slice("t$topId", top?.name ?: "未分類", top?.emoji ?: "img:cat_box", Color.Gray, ts.sumOf { amt(it) }, ts.size, pred, children)
    }
}

private fun byTag(list: List<Txn>, amt: (Txn) -> Long): List<Slice> {
    val m = LinkedHashMap<String, Long>()
    val n = HashMap<String, Int>()
    for (t in list) {
        val keys = if (t.tags.isEmpty()) listOf("") else t.tags
        keys.forEach { tag ->
            m[tag] = (m[tag] ?: 0L) + amt(t)
            n[tag] = (n[tag] ?: 0) + 1
        }
    }
    return m.entries.map { e ->
        val tag = e.key
        Slice(
            "g$tag", if (tag.isEmpty()) "未加標籤" else "#$tag", "🏷️", Color.Gray, e.value, n[tag] ?: 0,
            if (tag.isEmpty()) ({ t -> t.tags.isEmpty() }) else ({ t -> tag in t.tags }),
            emptyList(),
        )
    }
}

/** 依期間篩選：0 月, 1 年, 2 區間, 其他 = 全部 */
fun periodFilter(list: List<Txn>, mode: Int, vm: MoneyViewModel): List<Txn> = when (mode) {
    0 -> list.inMonth(vm.month)
    1 -> list.inYear(vm.statYear)
    2 -> list.filter { it.day in vm.rangeStart..vm.rangeEnd }
    else -> list
}

/** 期間切換列：月、年或區間 */
@Composable
fun PeriodBar(vm: MoneyViewModel, mode: Int) {
    when (mode) {
        0 -> MonthSwitcher(vm.month, { vm.month = it })
        1 -> YearSwitcher(vm.statYear, { vm.statYear = it })
        2 -> RangeBar(vm)
        else -> {}
    }
}

@Composable
fun RangeBar(vm: MoneyViewModel) {
    var pick by remember { mutableIntStateOf(0) } // 1 起, 2 迄
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CuteChip("📅 ${shortDate(vm.rangeStart)}", false, { pick = 1 })
            Text("～", color = LocalCute.current.sub)
            CuteChip("📅 ${shortDate(vm.rangeEnd)}", false, { pick = 2 })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val t = today.toEpochDay()
            CuteChip("近 7 天", false, { vm.setRange(t - 6, t) })
            CuteChip("近 30 天", false, { vm.setRange(t - 29, t) })
            CuteChip("近 3 個月", false, { vm.setRange(today.minusMonths(3).plusDays(1).toEpochDay(), t) })
            CuteChip("本月", false, { vm.setRange(today.withDayOfMonth(1).toEpochDay(), t) })
            CuteChip("今年", false, { vm.setRange(today.withDayOfYear(1).toEpochDay(), t) })
        }
    }
    if (pick == 1) CuteDatePickerDialog(vm.rangeStart, { vm.setRange(it, vm.rangeEnd); pick = 0 }, { pick = 0 }, "開始日期")
    if (pick == 2) CuteDatePickerDialog(vm.rangeEnd, { vm.setRange(vm.rangeStart, it); pick = 0 }, { pick = 0 }, "結束日期")
}

@Composable
fun StatsScreen(vm: MoneyViewModel, onSearch: () -> Unit, onDrill: () -> Unit) {
    val d = vm.data
    val mode = vm.statMode
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 支出, 1 收入
    var group by rememberSaveable { mutableIntStateOf(0) } // 0 分類, 1 標籤
    var trendN by rememberSaveable { mutableIntStateOf(6) }
    var gross by rememberSaveable { mutableStateOf(false) } // true = 支出含報銷的部分
    val expanded = remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableIntStateOf(-1) }
    val cute = LocalCute.current
    val type = if (kind == 0) TxType.EXPENSE else TxType.INCOME
    val book = d.bookTxns

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("統計", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                PillSegment(listOf("月", "年", "區間", "趨勢"), mode, { vm.statMode = it })
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(cute.card).clickable(onClick = onSearch)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("搜尋記錄：備註、分類、標籤、金額…", color = cute.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (mode == 3) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip("近 6 個月", trendN == 6, { trendN = 6 })
                    CuteChip("近 12 個月", trendN == 12, { trendN = 12 })
                }
            }
            trend(this, book, vm.month, trendN)
        } else {
            item { PeriodBar(vm, mode) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PillSegment(listOf("支出", "收入"), kind, { kind = it })
                    Spacer(Modifier.weight(1f))
                    CuteChip("依分類", group == 0, { group = 0 })
                    Spacer(Modifier.width(6.dp))
                    CuteChip("依標籤", group == 1, { group = 1 })
                }
            }
            val scope = periodFilter(book, mode, vm)
            val amt: (Txn) -> Long = { t -> if (gross && t.type == TxType.EXPENSE) t.paid else t.statAmount }
            val list = scope.filter { it.type == type && amt(it) > 0 }
            val transferFees = if (type == TxType.EXPENSE) scope.filter { it.type == TxType.TRANSFER }.sumOf { it.fee } else 0L
            val reimbGain = if (type == TxType.INCOME) scope.sumOf { it.reimbGain } else 0L
            val total = list.sumOf { amt(it) } + transferFees + reimbGain
            if (type == TxType.EXPENSE && scope.any { it.type == TxType.EXPENSE && it.reimb != 0 }) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("報銷：", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        CuteChip("只算自己負擔", !gross, { gross = false })
                        CuteChip("含報銷全額", gross, { gross = true })
                    }
                }
            }

            if (total == 0L) {
                item { EmptyHint(d.prefs.mascot, "這段期間沒有${if (kind == 0) "支出" else "收入"}記錄") }
            } else {
                val typeOk: (Txn) -> Boolean = { t -> t.type == type && amt(t) > 0 }
                val base = if (group == 0) byCategory(d, list, amt) else byTag(list, amt)
                val extra = ArrayList<Slice>()
                if (transferFees > 0) {
                    val n = scope.count { it.type == TxType.TRANSFER && it.fee > 0 }
                    extra.add(Slice("fee", "轉帳手續費", "🏧", Color.Gray, transferFees, n, { t -> t.type == TxType.TRANSFER && t.fee > 0 }, emptyList()))
                }
                if (reimbGain > 0) {
                    val n = scope.count { it.reimbGain > 0 }
                    extra.add(Slice("gain", "報銷回饋", "🎁", Color.Gray, reimbGain, n, { t -> t.reimbGain > 0 }, emptyList()))
                }
                val slices = recolor(base + extra)
                val sel = slices.getOrNull(selected)
                item {
                    CuteCard(Modifier.fillMaxWidth()) {
                        LabeledDonut(
                            labels = slices.map { it.label },
                            colors = slices.map { it.color },
                            values = slices.map { it.amount },
                            selected = selected,
                            onSelect = { selected = if (it == selected) -1 else it },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (sel != null) {
                                    Text(iconLabel(sel.emoji, sel.label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    Text(formatMoney(sel.amount), style = MaterialTheme.typography.titleMedium, color = sel.color)
                                    Text(
                                        String.format("%.1f%%・%d 筆", sel.amount * 100.0 / total, sel.count),
                                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                                    )
                                } else {
                                    Text(if (kind == 0) "總支出" else "總收入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                    Text(formatMoney(total), style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        if (group == 1) "標籤可重複計算" else "點圓餅看細節",
                                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                                    )
                                }
                            }
                        }
                    }
                }
                sliceRows(
                    slices, total, expanded.value,
                    onToggle = { key -> expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key },
                    onOpen = { sl ->
                        val base2: (Txn) -> Boolean = if (sl.key == "fee" || sl.key == "gain") sl.pred else ({ t: Txn -> typeOk(t) && sl.pred(t) })
                        val useAmt: (Txn) -> Long = when (sl.key) {
                            "fee" -> ({ t: Txn -> t.fee })
                            "gain" -> ({ t: Txn -> t.reimbGain })
                            else -> amt
                        }
                        vm.drill = tw.moneybook.app.Drill(iconLabel(sl.emoji, sl.label), mode, base2, useAmt)
                        onDrill()
                    },
                )
            }
            if (mode == 1) item { YearOverview(scope, vm.statYear) }
            if (mode == 2) item { RangeOverview(scope, vm.rangeStart, vm.rangeEnd) }
        }
    }
}

@Composable
private fun RangeOverview(scope: List<Txn>, start: Long, end: Long) {
    val cute = LocalCute.current
    val inc = scope.incomeSum()
    val exp = scope.expenseSum()
    val days = (end - start + 1).coerceAtLeast(1)
    CuteCard(Modifier.fillMaxWidth()) {
        Text("${shortDate(start)} ～ ${shortDate(end)}・共 $days 天", style = MaterialTheme.typography.labelLarge, color = cute.sub)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("收入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(inc), style = MaterialTheme.typography.titleMedium, color = cute.income)
            }
            Column(Modifier.weight(1f)) {
                Text("支出", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(exp), style = MaterialTheme.typography.titleMedium, color = cute.expense)
            }
            Column(Modifier.weight(1f)) {
                Text("結餘", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(inc - exp), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("平均每天支出 ${formatMoney(exp / days)}・共 ${scope.size} 筆記錄", style = MaterialTheme.typography.bodySmall, color = cute.sub)
    }
}

private fun LazyListScope.sliceRows(
    slices: List<Slice>,
    total: Long,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (Slice) -> Unit,
) {
    item {
        CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            slices.forEach { s ->
                SliceRow(s, total, big = true, open = s.key in expanded, onOpen = { onOpen(s) },
                    onToggle = if (s.children.isNotEmpty()) ({ onToggle(s.key) }) else null)
                if (s.key in expanded) {
                    s.children.forEach { c ->
                        SliceRow(c, s.amount, big = false, open = false, onOpen = { onOpen(c) }, onToggle = null)
                    }
                }
            }
        }
    }
}

/** 統計列表的一列：點名稱看這一類的所有記錄；右邊的箭頭展開子分類 */
@Composable
private fun SliceRow(s: Slice, total: Long, big: Boolean, open: Boolean, onOpen: () -> Unit, onToggle: (() -> Unit)?) {
    val cute = LocalCute.current
    val pct = if (total > 0) s.amount * 100.0 / total else 0.0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!big) {
            Spacer(Modifier.width(4.dp))
            CatBubble(s.emoji, 0, 34.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (big) iconLabel(s.emoji, s.label) else s.label,
                    style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Text(String.format("%.1f%%", pct), style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text("  ›", color = cute.sub, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(s.amount),
                        fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal,
                        style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    )
                    Text("${s.count} 筆", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(0.72f)) {
                RatioBar((s.amount.toFloat() / total.toFloat().coerceAtLeast(1f)), s.color, thick = big)
            }
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (onToggle != null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (open) AppIcons.ChevronUp else AppIcons.ChevronDown, contentDescription = if (open) "收合" else "展開子分類", tint = cute.sub)
                }
            }
        }
    }
}

@Composable
private fun YearOverview(scope: List<Txn>, year: Int) {
    val cute = LocalCute.current
    val inc = scope.incomeSum()
    val exp = scope.expenseSum()
    val months = (1..12).map { YearMonth.of(year, it) }
    val incs = months.map { m -> scope.inMonth(m).incomeSum() }
    val exps = months.map { m -> scope.inMonth(m).expenseSum() }
    val now = YearMonth.now()
    CuteCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("全年收入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(inc), style = MaterialTheme.typography.titleMedium, color = cute.income)
            }
            Column(Modifier.weight(1f)) {
                Text("全年支出", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(exp), style = MaterialTheme.typography.titleMedium, color = cute.expense)
            }
            Column(Modifier.weight(1f)) {
                Text("結餘", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(inc - exp), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        PairBars(
            labels = (1..12).map { "$it" },
            a = incs,
            b = exps,
            colorA = cute.income,
            colorB = cute.expense,
            highlight = if (now.year == year) now.monthValue - 1 else -1,
        )
        Spacer(Modifier.height(6.dp))
        Legend()
        val activeMonths = exps.count { it > 0 }
        if (activeMonths > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "平均每月支出 ${formatMoney(exp / activeMonths)}・最高是 ${exps.indexOf(exps.max()) + 1} 月",
                style = MaterialTheme.typography.bodySmall,
                color = cute.sub,
            )
        }
    }
}

@Composable
private fun Legend() {
    val cute = LocalCute.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cute.income))
            Text(" 收入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cute.expense))
            Text(" 支出", style = MaterialTheme.typography.labelMedium, color = cute.sub)
        }
    }
}

private fun trend(scope: LazyListScope, book: List<Txn>, end: YearMonth, n: Int) {
    val months = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }
    val incs = months.map { book.inMonth(it).incomeSum() }
    val exps = months.map { book.inMonth(it).expenseSum() }
    val nets = months.indices.map { incs[it] - exps[it] }
    val cumulative = nets.runningReduce { acc, v -> acc + v }
    val labels = months.map { if (n > 6 && it.monthValue % 2 == 0 && it != end) "" else "${it.monthValue}月" }
    scope.item {
        val cute = LocalCute.current
        CuteCard(Modifier.fillMaxWidth()) {
            Text("💰 存錢趨勢", style = MaterialTheme.typography.titleMedium)
            val saved = cumulative.last()
            Text(
                if (saved >= 0) "近 $n 個月一共存下 ${formatMoney(saved)}" else "近 $n 個月一共多花了 ${formatMoney(-saved)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (saved >= 0) cute.income else cute.expense,
            )
            Spacer(Modifier.height(12.dp))
            SavingsLine(labels, cumulative, nets)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text(" 累積存款", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 14.dp, height = 4.dp).clip(CircleShape).background(cute.accent2))
                    Text(" 每月結餘", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                }
            }
            val best = nets.indices.maxByOrNull { nets[it] }
            if (best != null && nets[best] > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "存最多的是 ${months[best].monthValue} 月，存了 ${formatMoney(nets[best])}",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }
    scope.item {
        val cute = LocalCute.current
        CuteCard(Modifier.fillMaxWidth()) {
            Text("近 $n 個月收支", style = MaterialTheme.typography.titleMedium)
            Text("${months.first().year}/${months.first().monthValue} ～ ${end.year}/${end.monthValue}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
            Spacer(Modifier.height(12.dp))
            PairBars(labels, incs, exps, cute.income, cute.expense, highlight = n - 1)
            Spacer(Modifier.height(6.dp))
            Legend()
            val active = exps.count { it > 0 }
            if (active > 0) {
                Spacer(Modifier.height(8.dp))
                val avg = exps.sum() / active
                Text("平均每月支出 ${formatMoney(avg)}", style = MaterialTheme.typography.bodyMedium)
                val last = exps[n - 1]
                val prev = exps[n - 2]
                if (prev > 0) {
                    val diff = last - prev
                    Text(
                        if (diff <= 0) "這個月比上個月少花 ${formatMoney(-diff)}，很棒喔！" else "這個月比上個月多花 ${formatMoney(diff)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff <= 0) cute.income else cute.expense,
                    )
                }
            }
        }
    }
    scope.items(months.indices.reversed().toList()) { i ->
        val cute = LocalCute.current
        val m = months[i]
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cute.card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${m.year}/${m.monthValue}", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.weight(1f)) {
                Text("收 ${formatMoney(incs[i])}", color = cute.income, style = MaterialTheme.typography.bodySmall)
                Text("支 ${formatMoney(exps[i])}", color = cute.expense, style = MaterialTheme.typography.bodySmall)
            }
            Text(formatMoney(incs[i] - exps[i]), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
}
