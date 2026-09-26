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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.formatShort
import tw.moneybook.app.inMonth
import tw.moneybook.app.incomeSum
import java.time.LocalDate

// ───────────────────────── 共用：一筆記錄 ─────────────────────────

@Composable
fun TxnRow(d: AppData, t: Txn, onClick: () -> Unit) {
    val cute = LocalCute.current
    val cat = t.categoryId?.let { d.catMap[it] }
    val acc = t.accountId?.let { d.accMap[it] }
    val title: String
    val emoji: String
    val color: Int
    val details = ArrayList<String>()
    if (t.type == TxType.TRANSFER) {
        title = "轉帳"
        emoji = "🔁"
        color = 5
        val to = t.toAccountId?.let { d.accMap[it] }
        details.add("${acc?.name ?: "?"} → ${to?.name ?: "?"}")
    } else {
        title = cat?.name ?: "未分類"
        emoji = cat?.emoji ?: "📦"
        color = cat?.color ?: 8
        cat?.parentId?.let { pid -> d.catMap[pid]?.let { details.add(it.name) } }
        if (acc != null && d.accounts.size > 1) details.add(acc.name)
    }
    if (t.instTotal > 1) details.add("分期 ${t.instIndex}/${t.instTotal}")
    if (t.note.isNotBlank()) details.add(t.note)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cute.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble(emoji, color, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                    color = cute.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (t.tags.isNotEmpty()) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    t.tags.take(3).forEach { tag ->
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(CircleShape).background(cute.soft).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        val (sign, c) = when (t.type) {
            TxType.EXPENSE -> "-" to cute.expense
            TxType.INCOME -> "+" to cute.income
            TxType.TRANSFER -> "" to cute.sub
        }
        Text(sign + formatMoney(t.amount), color = c, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DayHeader(day: Long, list: List<Txn>) {
    val cute = LocalCute.current
    val exp = list.expenseSum()
    val inc = list.incomeSum()
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f))
        if (inc > 0) Text("收 ${formatMoney(inc)}  ", style = MaterialTheme.typography.labelMedium, color = cute.income)
        if (exp > 0) Text("支 ${formatMoney(exp)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
    }
}

// ───────────────────────── 首頁（明細） ─────────────────────────

@Composable
fun HomeScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onManageBooks: () -> Unit, onManageAccounts: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val month = vm.month
    val list = d.bookTxns.inMonth(month)
    val income = list.incomeSum()
    val expense = list.expenseSum()
    val book = d.currentBook
    val mascot = d.prefs.mascot
    var showBooks by remember { mutableStateOf(false) }
    val balances = remember(d) { d.balances() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clip(CircleShape).background(cute.card).clickable { showBooks = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(book.emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(book.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
                    Text(" ▾", color = cute.sub)
                }
                Spacer(Modifier.weight(1f))
                MonthSwitcher(month, { vm.month = it })
            }
        }

        if (mascot != "none") {
            item {
                val mood = moodOf(d)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Mascot(mascot, mood, Modifier.size(68.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                            .background(cute.card)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        val name = d.prefs.mascotName.ifBlank { mascotDefaultName(mascot) }
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(mascotLine(mood, d), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("本月支出", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                Text(formatMoney(expense), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("收入", formatMoney(income), cute.income, Modifier.weight(1f))
                    MiniStat("結餘", formatMoney(income - expense), cute.ink, Modifier.weight(1f))
                }
                if (book.budget > 0) {
                    Spacer(Modifier.height(12.dp))
                    val over = expense > book.budget
                    val frac = expense.toFloat() / book.budget.toFloat()
                    RatioBar(frac, if (over) cute.expense else if (frac > 0.8f) cute.accent2 else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (over) "已超出預算 ${formatMoney(expense - book.budget)}（預算 ${formatMoney(book.budget)}）"
                        else "預算 ${formatMoney(book.budget)}・還剩 ${formatMoney(book.budget - expense)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) cute.expense else cute.sub,
                    )
                }
            }
        }

        val accs = d.visibleAccounts
        if (accs.size > 1) {
            item {
              Column {
                SectionTitle("我的帳戶") {
                    Text(
                        "管理",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onManageAccounts).padding(8.dp),
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accs, key = { it.id }) { a ->
                        CuteCard(Modifier.width(130.dp), padding = PaddingValues(12.dp), onClick = onManageAccounts) {
                            Text("${a.emoji} ${a.name}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val b = balances[a.id] ?: 0L
                            Text(formatMoney(b), color = if (b < 0) cute.expense else cute.ink, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                }
              }
            }
        }

        if (list.isEmpty()) {
            item { EmptyHint(mascot, "這個月還沒有記錄\n按下方的 ＋ 記下第一筆吧") }
        }
        val groups = list.groupBy { it.day }
        groups.forEach { (day, dayList) ->
            item(key = "d$day") { DayHeader(day, dayList) }
            items(dayList, key = { it.id }) { t -> TxnRow(d, t) { onEdit(t.id) } }
        }
    }

    if (showBooks) {
        AlertDialog(
            onDismissRequest = { showBooks = false },
            title = { Text("切換帳本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.books.forEach { b ->
                        val on = b.id == book.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.switchBook(b.id); showBooks = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(b.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(b.name, modifier = Modifier.weight(1f))
                            if (on) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBooks = false; onManageBooks() }) { Text("管理帳本") } },
            dismissButton = { TextButton(onClick = { showBooks = false }) { Text("關閉") } },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(LocalCute.current.soft.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalCute.current.sub)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

// ───────────────────────── 日曆 ─────────────────────────

@Composable
fun CalendarScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val month = vm.month
    val list = d.bookTxns.inMonth(month)
    val byDay = list.groupBy { it.day }
    val today = LocalDate.now()
    var selected by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    LaunchedEffect(month) {
        val sel = LocalDate.ofEpochDay(selected)
        if (sel.year != month.year || sel.monthValue != month.monthValue) {
            selected = if (today.year == month.year && today.monthValue == month.monthValue) today.toEpochDay()
            else month.atDay(1).toEpochDay()
        }
    }
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // 週日開頭
    val days = month.lengthOfMonth()
    val cells = lead + days
    val weeks = (cells + 6) / 7
    val selList = byDay[selected] ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("日曆", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                MonthSwitcher(month, { vm.month = it })
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                        Text(
                            w,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = cute.sub,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = w * 7 + c
                            val dayNum = idx - lead + 1
                            if (dayNum < 1 || dayNum > days) {
                                Spacer(Modifier.weight(1f).height(58.dp))
                            } else {
                                val date = month.atDay(dayNum)
                                val ep = date.toEpochDay()
                                val dl = byDay[ep] ?: emptyList()
                                val exp = dl.expenseSum()
                                val inc = dl.incomeSum()
                                val isSel = ep == selected
                                val isToday = date == today
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .then(
                                            if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                            else Modifier
                                        )
                                        .clickable { selected = ep },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("$dayNum", style = MaterialTheme.typography.labelLarge)
                                    if (exp > 0) Text(formatShort(exp), fontSize = 9.sp, color = cute.expense, maxLines = 1)
                                    if (inc > 0) Text(formatShort(inc), fontSize = 9.sp, color = cute.income, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val e = selList.expenseSum()
                if (e > 0) Text("支出 ${formatMoney(e)}", color = cute.sub, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (selList.isEmpty()) {
            item {
                Text(
                    "這天沒有記錄",
                    color = cute.sub,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        items(selList, key = { it.id }) { t -> TxnRow(d, t) { onEdit(t.id) } }
    }
}
