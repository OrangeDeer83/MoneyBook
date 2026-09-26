package tw.moneybook.app.ui

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.YearMonth

/** 圓餅圖的一塊 */
private class Slice(
    val key: String,
    val label: String,
    val emoji: String,
    val color: Color,
    val amount: Long,
    val children: List<Slice>,
)

private fun byCategory(d: AppData, list: List<Txn>): List<Slice> {
    val groups = list.groupBy { t -> t.categoryId?.let { d.catMap[it] }?.let { d.topOf(it).id } ?: -1L }
    return groups.map { (topId, ts) ->
        val top = d.catMap[topId]
        val subs = ts.groupBy { it.categoryId ?: -1L }
            .filter { (cid, _) -> cid != topId }
            .map { (cid, sts) ->
                val c = d.catMap[cid]
                Slice("c$cid", c?.name ?: "未分類", c?.emoji ?: "📦", catColor(c?.color ?: 8), sts.sumOf { it.amount }, emptyList())
            }
        val direct = ts.filter { it.categoryId == topId }.sumOf { it.amount }
        val children = if (subs.isEmpty()) emptyList() else {
            val all = subs.toMutableList()
            if (direct > 0) all.add(Slice("d$topId", "未細分", top?.emoji ?: "📦", catColor(top?.color ?: 8), direct, emptyList()))
            all.sortedByDescending { it.amount }
        }
        Slice("t$topId", top?.name ?: "未分類", top?.emoji ?: "📦", catColor(top?.color ?: 8), ts.sumOf { it.amount }, children)
    }.sortedByDescending { it.amount }
}

private fun byTag(list: List<Txn>): List<Slice> {
    val m = LinkedHashMap<String, Long>()
    for (t in list) {
        if (t.tags.isEmpty()) m["未加標籤"] = (m["未加標籤"] ?: 0L) + t.amount
        else t.tags.forEach { tag -> m[tag] = (m[tag] ?: 0L) + t.amount }
    }
    return m.entries.sortedByDescending { it.value }.mapIndexed { i, e ->
        Slice("g${e.key}", if (e.key == "未加標籤") e.key else "#${e.key}", "🏷️", if (e.key == "未加標籤") Color(0xFFCFC6BD) else catColor(i), e.value, emptyList())
    }
}

@Composable
fun StatsScreen(vm: MoneyViewModel) {
    val d = vm.data
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 月, 1 年, 2 趨勢
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 支出, 1 收入
    var group by rememberSaveable { mutableIntStateOf(0) } // 0 分類, 1 標籤
    var year by rememberSaveable { mutableIntStateOf(vm.month.year) }
    val expanded = remember { mutableStateOf(setOf<String>()) }
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
                PillSegment(listOf("月", "年", "趨勢"), mode, { mode = it })
            }
        }

        when (mode) {
            0, 1 -> {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (mode == 0) MonthSwitcher(vm.month, { vm.month = it }) else YearSwitcher(year, { year = it })
                        Spacer(Modifier.weight(1f))
                        PillSegment(listOf("支出", "收入"), kind, { kind = it })
                    }
                }
                val scope = if (mode == 0) book.inMonth(vm.month) else book.inYear(year)
                val list = scope.filter { it.type == type }
                val total = list.sumOf { it.amount }

                if (mode == 1) {
                    item { YearOverview(scope, year) }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        CuteChip("依分類", group == 0, { group = 0 })
                        CuteChip("依標籤", group == 1, { group = 1 })
                    }
                }

                if (total == 0L) {
                    item { EmptyHint(d.prefs.mascot, "這段期間沒有${if (kind == 0) "支出" else "收入"}記錄") }
                } else {
                    val slices = if (group == 0) byCategory(d, list) else byTag(list)
                    item {
                        CuteCard(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                                DonutChart(slices.map { it.color to it.amount }, Modifier.size(210.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (kind == 0) "總支出" else "總收入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                    Text(formatMoney(total), style = MaterialTheme.typography.titleLarge)
                                    if (group == 1) Text("標籤可重複計算", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                }
                            }
                        }
                    }
                    sliceRows(slices, total, expanded.value) { key ->
                        expanded.value = if (key in expanded.value) expanded.value - key else expanded.value + key
                    }
                }
            }
            else -> {
                trend(this, book, vm.month)
            }
        }
    }
}

private fun LazyListScope.sliceRows(slices: List<Slice>, total: Long, expanded: Set<String>, onToggle: (String) -> Unit) {
    item {
        CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            slices.forEach { s ->
                SliceRow(s, total, s.children.isNotEmpty(), s.key in expanded) { onToggle(s.key) }
                if (s.key in expanded) {
                    s.children.forEach { c ->
                        Box(Modifier.padding(start = 28.dp)) { SliceRow(c, s.amount, false, false, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceRow(s: Slice, total: Long, expandable: Boolean, open: Boolean, onClick: (() -> Unit)?) {
    val cute = LocalCute.current
    val pct = if (total > 0) s.amount * 100.0 / total else 0.0
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
    Column((if (onClick != null && expandable) base.clickable(onClick = onClick) else base).padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
            Spacer(Modifier.width(8.dp))
            Text("${s.emoji} ${s.label}", modifier = Modifier.weight(1f), maxLines = 1)
            if (expandable) Text(if (open) "▴ " else "▾ ", color = cute.sub)
            Text(String.format("%.1f%%", pct), style = MaterialTheme.typography.labelMedium, color = cute.sub)
            Spacer(Modifier.width(10.dp))
            Text(formatMoney(s.amount), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        RatioBar((s.amount.toFloat() / total.toFloat().coerceAtLeast(1f)), s.color)
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

private fun trend(scope: LazyListScope, book: List<Txn>, end: YearMonth) {
    val months = (5 downTo 0).map { end.minusMonths(it.toLong()) }
    val incs = months.map { book.inMonth(it).incomeSum() }
    val exps = months.map { book.inMonth(it).expenseSum() }
    scope.item {
        val cute = LocalCute.current
        CuteCard(Modifier.fillMaxWidth()) {
            Text("近 6 個月收支", style = MaterialTheme.typography.titleMedium)
            Text("${months.first().year}/${months.first().monthValue} ～ ${end.year}/${end.monthValue}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
            Spacer(Modifier.height(12.dp))
            PairBars(months.map { "${it.monthValue}月" }, incs, exps, cute.income, cute.expense, highlight = 5)
            Spacer(Modifier.height(6.dp))
            Legend()
            val active = exps.count { it > 0 }
            if (active > 0) {
                Spacer(Modifier.height(8.dp))
                val avg = exps.sum() / active
                Text("平均每月支出 ${formatMoney(avg)}", style = MaterialTheme.typography.bodyMedium)
                val last = exps[5]
                val prev = exps[4]
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
