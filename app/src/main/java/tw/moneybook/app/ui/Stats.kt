package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import tw.moneybook.app.CardPages
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
            "g$tag", if (tag.isEmpty()) "未加標籤" else "#$tag", "img:stat_tag", Color.Gray, e.value, n[tag] ?: 0,
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
            CuteChip(shortDate(vm.rangeStart), false, { pick = 1 }, icon = "vec:calendar")
            Text("～", color = LocalCute.current.sub)
            CuteChip(shortDate(vm.rangeEnd), false, { pick = 2 }, icon = "vec:calendar")
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
fun StatsScreen(vm: MoneyViewModel, onSearch: () -> Unit, onDrill: () -> Unit, onLayout: (String) -> Unit) {
    val d = vm.data
    val mode = vm.statMode
    var trendN by vm::statTrendN
    val cute = LocalCute.current
    val book = d.bookTxns
    // 月、年、區間共用一份排版，趨勢另一份
    val page = if (mode == 3) CardPages.TREND else CardPages.PERIOD
    val layout = vm.cardLayout(page)
    val menu = CardMenu(onHide = { vm.hideCard(page, it) }, onLayout = { onLayout(page.key) })

    // 長按圖表看數字：資訊框留著，點其他地方才收起來
    ChartPickArea(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val narrow = isNarrow(maxWidth)
            LazyColumn(
                state = vm.statListState,
                modifier = Modifier.fillMaxHeight().widthIn(max = CardMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 最上面這幾列固定不動，下面才是可以調整排版的卡片
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
                        CompositionLocalProvider(LocalContentColor provides cute.sub) { IconGlyph("vec:search", 18.sp) }
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
                    val t = TrendData(book, vm.month, trendN)
                    cardRows(layout, narrow, { true }, { onLayout(page.key) }) { id, half ->
                        when (id) {
                            "saved" -> SavedCard(t, half, menu)
                            "savings" -> SavingsCard(t, menu)
                            "bars" -> TrendBarsCard(t, menu)
                            "months" -> MonthsCard(t, menu)
                        }
                    }
                } else {
                    item { PeriodBar(vm, mode) }
                    val scope = periodFilter(book, mode, vm)
                    cardRows(layout, narrow, { id -> id != "yearBars" || mode == 1 }, { onLayout(page.key) }) { id, half ->
                        when (id) {
                            "sum" -> SumCard(vm, mode, scope, half, menu)
                            "cat" -> CategoryCard(vm, mode, scope, onDrill, menu)
                            "yearBars" -> YearBarsCard(scope, vm.statYear, menu)
                        }
                    }
                }
            }
        }
    }
}

/** 收支卡片：收入、支出、結餘（半排時上下排），下面一行平均每天支出與筆數；區間多一行日期 */
@Composable
private fun SumCard(vm: MoneyViewModel, mode: Int, scope: List<Txn>, half: Boolean, menu: CardMenu) {
    val cute = LocalCute.current
    val inc = scope.incomeSum()
    val exp = scope.expenseSum()
    val today = LocalDate.now()
    // 平均每天：這個月（今年）只算到今天，其他用整個月（整年）、區間的天數
    val days = when (mode) {
        1 -> if (vm.statYear == today.year) today.dayOfYear else java.time.Year.of(vm.statYear).length()
        2 -> (vm.rangeEnd - vm.rangeStart + 1).toInt()
        else -> if (vm.month == YearMonth.from(today)) today.dayOfMonth else vm.month.lengthOfMonth()
    }.coerceAtLeast(1)
    val items = listOf(
        Triple(if (mode == 1) "全年收入" else "收入", formatMoney(inc), cute.income),
        Triple(if (mode == 1) "全年支出" else "支出", formatMoney(exp), cute.expense),
        Triple("結餘", formatMoney(inc - exp), cute.ink),
    )
    CuteCard(cardMod(half)) {
        CardTitle("收支", "sum", menu)
        if (mode == 2) {
            Text("${shortDate(vm.rangeStart)} ～ ${shortDate(vm.rangeEnd)}・共 $days 天", style = MaterialTheme.typography.labelLarge, color = cute.sub)
        }
        Spacer(Modifier.height(6.dp))
        if (half) {
            items.forEach { (l, v, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.titleSmall, color = c, maxLines = 1)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { (l, v, c) ->
                    Column(Modifier.weight(1f)) {
                        Text(l, style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Text(v, style = MaterialTheme.typography.titleMedium, color = c)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("平均每天支出 ${formatMoney(exp / days)}・共 ${scope.size} 筆記錄", style = MaterialTheme.typography.bodySmall, color = cute.sub)
    }
}

/** 分類卡片：支出／收入、依分類／標籤、報銷的切換都在這張裡面，下面是圓環圖和清單 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCard(vm: MoneyViewModel, mode: Int, scope: List<Txn>, onDrill: () -> Unit, menu: CardMenu) {
    val d = vm.data
    val cute = LocalCute.current
    // 這些狀態存在 ViewModel：點進明細再返回時才能還原
    var kind by vm::statKind // 0 支出, 1 收入
    var group by vm::statGroup // 0 分類, 1 標籤
    var gross by vm::statGross // true = 支出含報銷的部分
    var expandedSet by vm::statExpanded
    var selected by vm::statSelected
    val type = if (kind == 0) TxType.EXPENSE else TxType.INCOME
    val amt: (Txn) -> Long = { t -> if (t.adjust) 0L else if (gross && t.type == TxType.EXPENSE) t.paid else t.statAmount }
    val list = scope.filter { it.type == type && amt(it) > 0 }
    val transferFees = if (type == TxType.EXPENSE) scope.filter { it.type == TxType.TRANSFER }.sumOf { it.fee } else 0L
    val reimbGain = if (type == TxType.INCOME) scope.sumOf { it.reimbGain } else 0L
    val total = list.sumOf { amt(it) } + transferFees + reimbGain

    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillSegment(listOf("支出", "收入"), kind, { kind = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip("依分類", group == 0, { group = 0 })
                    CuteChip("依標籤", group == 1, { group = 1 })
                }
            }
            Spacer(Modifier.width(4.dp))
            CardMore("cat", menu)
        }
        if (type == TxType.EXPENSE && scope.any { it.type == TxType.EXPENSE && it.reimb != 0 }) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("報銷：", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                CuteChip("只算自己負擔", !gross, { gross = false })
                CuteChip("含報銷全額", gross, { gross = true })
            }
        }

        if (total == 0L) {
            EmptyHint(d.prefs.mascot, "這段期間沒有${if (kind == 0) "支出" else "收入"}記錄")
            return@CuteCard
        }
        val typeOk: (Txn) -> Boolean = { t -> t.type == type && amt(t) > 0 }
        val base = if (group == 0) byCategory(d, list, amt) else byTag(list, amt)
        val extra = ArrayList<Slice>()
        if (transferFees > 0) {
            val n = scope.count { it.type == TxType.TRANSFER && it.fee > 0 }
            extra.add(Slice("fee", "轉帳手續費", "img:stat_atm", Color.Gray, transferFees, n, { t -> t.type == TxType.TRANSFER && t.fee > 0 }, emptyList()))
        }
        if (reimbGain > 0) {
            val n = scope.count { it.reimbGain > 0 }
            extra.add(Slice("gain", "報銷回饋", "img:stat_cashback", Color.Gray, reimbGain, n, { t -> t.reimbGain > 0 }, emptyList()))
        }
        val slices = recolor(base + extra)
        val sel = slices.getOrNull(selected)
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(8.dp))
        val onOpen: (Slice) -> Unit = { sl ->
            val base2: (Txn) -> Boolean = if (sl.key == "fee" || sl.key == "gain") sl.pred else ({ t: Txn -> typeOk(t) && sl.pred(t) })
            val useAmt: (Txn) -> Long = when (sl.key) {
                "fee" -> ({ t: Txn -> t.fee })
                "gain" -> ({ t: Txn -> t.reimbGain })
                else -> amt
            }
            vm.drill = tw.moneybook.app.Drill(iconLabel(sl.emoji, sl.label), mode, base2, useAmt)
            onDrill()
        }
        slices.forEach { s ->
            val open = s.key in expandedSet
            SliceRow(s, total, big = true, open = open, onOpen = { onOpen(s) },
                onToggle = if (s.children.isNotEmpty()) ({ expandedSet = if (open) expandedSet - s.key else expandedSet + s.key }) else null)
            if (open) {
                s.children.forEach { c ->
                    Box(Modifier.padding(start = 30.dp)) {
                        SliceRow(c, s.amount, big = false, open = false, onOpen = { onOpen(c) }, onToggle = null)
                    }
                }
            }
        }
    }
}

/** 每月收支（只有年）：12 個月的收入、支出長條 */
@Composable
private fun YearBarsCard(scope: List<Txn>, year: Int, menu: CardMenu) {
    val cute = LocalCute.current
    val exp = scope.expenseSum()
    val months = (1..12).map { YearMonth.of(year, it) }
    val incs = months.map { m -> scope.inMonth(m).incomeSum() }
    val exps = months.map { m -> scope.inMonth(m).expenseSum() }
    val now = YearMonth.now()
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("每月收支", "yearBars", menu)
        Spacer(Modifier.height(10.dp))
        PairBars(
            labels = (1..12).map { "$it" },
            a = incs,
            b = exps,
            colorA = cute.income,
            colorB = cute.expense,
            highlight = if (now.year == year) now.monthValue - 1 else -1,
            titles = (1..12).map { "$year 年 $it 月" },
            key = "yearBars",
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

/** 統計列表的一列：點名稱看這一類的所有記錄；右邊的箭頭展開子分類 */
@Composable
private fun SliceRow(s: Slice, total: Long, big: Boolean, open: Boolean, onOpen: () -> Unit, onToggle: (() -> Unit)?) {
    val cute = LocalCute.current
    val pct = if (total > 0) s.amount * 100.0 / total else 0.0
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(4.dp))
        CatBubble(s.emoji, 0, if (big) 38.dp else 30.dp)
        Spacer(Modifier.width(10.dp))
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

/** 趨勢頁的資料：最近 n 個月（到 end 為止）每個月的收入、支出、結餘與累積 */
private class TrendData(book: List<Txn>, val end: YearMonth, val n: Int) {
    val months: List<YearMonth> = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }
    val incs: List<Long> = months.map { book.inMonth(it).incomeSum() }
    val exps: List<Long> = months.map { book.inMonth(it).expenseSum() }
    val nets: List<Long> = months.indices.map { incs[it] - exps[it] }
    val cumulative: List<Long> = nets.runningReduce { acc, v -> acc + v }
    val labels: List<String> = months.map { if (n > 6 && it.monthValue % 2 == 0 && it != end) "" else "${it.monthValue}月" }
    // 長按圖表時資訊框的標題
    val titles: List<String> = months.map { "${it.year} 年 ${it.monthValue} 月" }
}

/** 這個月存下（預設隱藏）：這個月的結餘、近 n 個月一共存下多少 */
@Composable
private fun SavedCard(t: TrendData, half: Boolean, menu: CardMenu) {
    val cute = LocalCute.current
    val net = t.nets.last()
    val saved = t.cumulative.last()
    CuteCard(cardMod(half)) {
        CardTitle(if (t.end == YearMonth.now()) "這個月存下" else "${t.end.monthValue} 月存下", "saved", menu)
        Text(
            if (net >= 0) formatMoney(net) else "多花了 ${formatMoney(-net)}",
            style = MaterialTheme.typography.titleLarge, color = if (net >= 0) cute.income else cute.expense,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (saved >= 0) "近 ${t.n} 個月一共 ${formatMoney(saved)}" else "近 ${t.n} 個月多花了 ${formatMoney(-saved)}",
            style = MaterialTheme.typography.bodySmall, color = cute.sub,
        )
    }
}

@Composable
private fun SavingsCard(t: TrendData, menu: CardMenu) {
    val cute = LocalCute.current
    val n = t.n
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("存錢趨勢", "savings", menu, icon = "img:ui_savings")
        val saved = t.cumulative.last()
        Text(
            if (saved >= 0) "近 $n 個月一共存下 ${formatMoney(saved)}" else "近 $n 個月一共多花了 ${formatMoney(-saved)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (saved >= 0) cute.income else cute.expense,
        )
        Spacer(Modifier.height(12.dp))
        SavingsLine(t.labels, t.cumulative, t.nets, titles = t.titles, incs = t.incs, exps = t.exps)
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
        val best = t.nets.indices.maxByOrNull { t.nets[it] }
        if (best != null && t.nets[best] > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "存最多的是 ${t.months[best].monthValue} 月，存了 ${formatMoney(t.nets[best])}",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
        }
    }
}

@Composable
private fun TrendBarsCard(t: TrendData, menu: CardMenu) {
    val cute = LocalCute.current
    val n = t.n
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("近 $n 個月收支", "bars", menu)
        Text("${t.months.first().year}/${t.months.first().monthValue} ～ ${t.end.year}/${t.end.monthValue}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
        Spacer(Modifier.height(12.dp))
        PairBars(t.labels, t.incs, t.exps, cute.income, cute.expense, highlight = n - 1, titles = t.titles, key = "trendBars")
        Spacer(Modifier.height(6.dp))
        Legend()
        val exps = t.exps
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

/** 每月明細：新到舊，每個月一列（收、支、結餘），放在同一張卡片裡 */
@Composable
private fun MonthsCard(t: TrendData, menu: CardMenu) {
    val cute = LocalCute.current
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("每月明細", "months", menu)
        t.months.indices.reversed().forEach { i ->
            val m = t.months[i]
            val net = t.nets[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${m.year}/${m.monthValue}", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.weight(1f)) {
                    Text("收 ${formatMoney(t.incs[i])}", color = cute.income, style = MaterialTheme.typography.bodySmall)
                    Text("支 ${formatMoney(t.exps[i])}", color = cute.expense, style = MaterialTheme.typography.bodySmall)
                }
                Text(formatMoney(net), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(cute.soft))
        }
    }
}
