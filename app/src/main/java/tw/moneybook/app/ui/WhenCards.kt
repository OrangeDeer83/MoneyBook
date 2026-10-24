package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import tw.moneybook.app.HourSlots
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.dayTotals
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.hourSplit
import tw.moneybook.app.incomeSum
import tw.moneybook.app.weekSplit

/* 統計 → 什麼時候花錢：平日／週末、時段、每天熱度（都算全部支出，含預算外），和點熱度某一天開的那一天明細 */

/** 這段期間實際算到哪一天：還沒過完的只算到今天（未來的日子不算進天數） */
private fun periodDays(vm: MoneyViewModel, mode: Int): Pair<Long, Long> {
    val today = LocalDate.now().toEpochDay()
    val (s, e) = when (mode) {
        1 -> LocalDate.of(vm.statYear, 1, 1).toEpochDay() to LocalDate.of(vm.statYear, 12, 31).toEpochDay()
        2 -> vm.rangeStart to vm.rangeEnd
        else -> vm.month.atDay(1).toEpochDay() to vm.month.atEndOfMonth().toEpochDay()
    }
    return s to minOf(e, maxOf(today, s))
}

/** 平日／週末：平均每天花多少的兩根柱子，下面一句週末是平日的幾倍 */
@Composable
fun WeekdayCard(vm: MoneyViewModel, mode: Int, scope: List<Txn>, half: Boolean, menu: CardMenu) {
    val cute = LocalCute.current
    val (s, u) = periodDays(vm, mode)
    val w = weekSplit(scope, s, u)
    CuteCard(cardMod(half)) {
        CardTitle("平日／週末", "weekday", menu)
        Text("平均每天", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Spacer(Modifier.height(6.dp))
        if (w.weekday == 0L && w.weekend == 0L) {
            Text("這段期間沒有支出", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            return@CuteCard
        }
        val max = maxOf(w.weekday, w.weekend).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            listOf(Triple("平日", w.weekday, cute.expense.copy(alpha = 0.55f)), Triple("週末", w.weekend, cute.expense)).forEach { (label, v, c) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(formatMoney(v), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    // 柱子的高度：最高那根佔滿 90dp，沒花錢的留一條細線
                    Box(
                        Modifier.width(34.dp).height((90f * v / max).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(c)
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val line = when {
            w.weekday > 0 && w.weekend >= w.weekday -> "週末是平日的 ${ratio(w.weekend, w.weekday)} 倍"
            w.weekend > 0 && w.weekday > w.weekend -> "平日是週末的 ${ratio(w.weekday, w.weekend)} 倍"
            w.weekend > 0 -> "只有週末有花錢"
            else -> "只有平日有花錢"
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = cute.sub)
    }
}

private fun ratio(a: Long, b: Long): String = String.format("%.1f", a.toDouble() / b)

/** 時段：五根柱子（早、中、下、晚、夜），最多的那根深色；長按看金額、筆數 */
@Composable
fun HoursCard(scope: List<Txn>, half: Boolean, menu: CardMenu) {
    val cute = LocalCute.current
    val h = hourSplit(scope)
    CuteCard(cardMod(half)) {
        CardTitle("時段", "hours", menu)
        if (h.timedCount == 0) {
            Spacer(Modifier.height(6.dp))
            Text("這段期間的支出都沒有記時間。記一筆時設定時間，這裡就會顯示最常在什麼時候花錢", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            return@CuteCard
        }
        val top = h.amounts.indices.maxByOrNull { h.amounts[it] } ?: 0
        Text("最常在${HourSlots[top]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        SlotBars(
            labels = listOf("早", "中", "下", "晚", "夜"),
            values = h.amounts,
            colors = h.amounts.indices.map { if (it == top) cute.expense else cute.expense.copy(alpha = 0.45f) },
            titles = listOf("早上 5～11", "中午 11～14", "下午 14～18", "晚上 18～23", "深夜 23～5"),
            counts = h.counts,
            key = "hours",
        )
        Spacer(Modifier.height(6.dp))
        Text("有 ${h.timedCount} 筆記了時間", style = MaterialTheme.typography.bodySmall, color = cute.sub)
    }
}

/** 每天熱度（只有月）：月曆，顏色越深花越多；點某一天開那一天的明細 */
@Composable
fun HeatCard(month: YearMonth, scope: List<Txn>, onDay: (Long) -> Unit, menu: CardMenu) {
    val cute = LocalCute.current
    val totals = dayTotals(scope)
    val max = totals.values.maxOrNull() ?: 0L
    val today = LocalDate.now()
    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle("每天熱度", "heat", menu)
        Text("顏色越深花越多（點某天看那天的明細）", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = cute.sub)
            }
        }
        Spacer(Modifier.height(4.dp))
        val lead = month.atDay(1).dayOfWeek.value - 1 // 週一開頭
        val days = month.lengthOfMonth()
        val weeks = (lead + days + 6) / 7
        for (wk in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val n = wk * 7 + c - lead + 1
                    if (n < 1 || n > days) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                        continue
                    }
                    val date = month.atDay(n)
                    val ep = date.toEpochDay()
                    val v = totals[ep] ?: 0L
                    // 分四級：花最多那天的 1/4、1/2、3/4 以上
                    val level = if (v <= 0L || max <= 0L) 0 else (1 + (v * 4 - 1) / max).toInt().coerceIn(1, 4)
                    val bg = if (level == 0) cute.soft.copy(alpha = 0.6f) else cute.expense.copy(alpha = listOf(0f, 0.25f, 0.45f, 0.7f, 0.95f)[level])
                    Box(
                        Modifier.weight(1f).height(44.dp).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                            .then(if (date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onDay(ep) }
                            .padding(start = 5.dp, top = 3.dp),
                    ) {
                        Text("$n", fontSize = 11.sp, color = if (level >= 3) Color.White else cute.ink)
                    }
                }
            }
        }
        if (max > 0L) {
            val best = totals.maxByOrNull { it.value }!!
            val d = LocalDate.ofEpochDay(best.key)
            Spacer(Modifier.height(6.dp))
            Text(
                "花最多：${d.monthValue}/${d.dayOfMonth} ${formatMoney(best.value)}（點了看那天）",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDay(best.key) }.padding(vertical = 2.dp),
            )
        }
    }
}

/** 那一天的明細（從每天熱度點進來）：這天支出，下面和日曆點某天時一樣的記錄，點一筆可以修改 */
@Composable
fun DayScreen(vm: MoneyViewModel, day: Long, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val date = LocalDate.ofEpochDay(day)
    val list = d.bookTxns.filter { it.day == day }
    val recvs = reimbRecvsOf(d.bookTxns, d.settles.map { it.id }.toSet()).filter { it.pay.day == day }
    val entries = ledgerEntries(list, recvs, d.settleLines(d.currentBook.id).filter { it.s.day == day })
    SubPage("${date.monthValue} 月 ${date.dayOfMonth} 日 週${WEEKDAYS[date.dayOfWeek.value - 1]}", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("這天支出", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f))
                        Text(formatMoney(list.expenseSum()), style = MaterialTheme.typography.headlineSmall, color = cute.expense)
                    }
                    val inc = list.incomeSum()
                    if (inc > 0) Text("收入 ${formatMoney(inc)}", style = MaterialTheme.typography.labelMedium, color = cute.income)
                }
            }
            if (entries.isEmpty()) {
                item { Text("這天沒有記錄", color = cute.sub, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center) }
            }
            items(entries, key = { it.key }) { e ->
                val t = e.txn
                val s = e.settle
                val r = e.recv
                when {
                    t != null -> SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t) { onEdit(t.id) } }
                    s != null -> SettleLineRow(d, s, showAcc = d.accounts.size > 1)
                    r != null -> ReimbRecvRow(d, r, showAcc = d.accounts.size > 1, amount = formatMoney(r.pay.amount))
                }
            }
        }
    }
}
