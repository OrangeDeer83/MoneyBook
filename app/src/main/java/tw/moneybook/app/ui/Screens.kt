package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.flowFor
import tw.moneybook.app.formatFx
import tw.moneybook.app.fmt
import tw.moneybook.app.fxNote
import tw.moneybook.app.isForeign
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.formatShort
import tw.moneybook.app.inMonth
import tw.moneybook.app.incomeSum
import tw.moneybook.app.pendingReimb
import java.time.LocalDate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// ───────────────────────── 共用：一筆記錄 ─────────────────────────

@Composable
fun TxnRow(d: AppData, t: Txn, signedFor: Long? = null, balance: Long? = null, onClick: () -> Unit) {
    val cute = LocalCute.current
    val cat = t.categoryId?.let { d.catMap[it] }
    val acc = t.accountId?.let { d.accMap[it] }
    val title: androidx.compose.ui.text.AnnotatedString
    val emoji: String
    val color: Int
    val details = ArrayList<String>()
    if (t.adjust) {
        title = androidx.compose.ui.text.AnnotatedString("餘額調整")
        emoji = "img:ui_ledger"
        color = 5
        if (acc != null && d.accounts.size > 1) details.add(acc.name)
    } else if (t.type == TxType.TRANSFER) {
        title = androidx.compose.ui.text.AnnotatedString("轉帳")
        emoji = "img:ui_transfer"
        color = 5
        val to = t.toAccountId?.let { d.accMap[it] }
        details.add("${acc?.name ?: "?"} → ${to?.name ?: "?"}")
    } else {
        // 標題一律從大分類開始：「餐飲」或「餐飲 › 午餐」，不會一筆顯示大分類、一筆顯示子分類
        val parent = cat?.parentId?.let { d.catMap[it] }
        title = if (cat != null && parent != null) androidx.compose.ui.text.buildAnnotatedString {
            append(parent.name)
            withStyle(androidx.compose.ui.text.SpanStyle(color = cute.sub)) { append(" › ${cat.name}") }
        } else androidx.compose.ui.text.AnnotatedString(cat?.name ?: "未分類")
        emoji = cat?.emoji ?: "img:cat_box"
        color = cat?.color ?: 8
        if (acc != null && d.accounts.size > 1) details.add(acc.name)
    }
    if (t.instTotal > 1) details.add("分期 ${t.instIndex}/${t.instTotal}")
    if (t.discount > 0) details.add("優惠 ${formatMoney(t.discount)}")
    if (t.fee > 0) details.add("手續費 ${formatMoney(t.fee)}")
    // 外幣：在外幣帳戶的明細裡主金額是外幣、備註放台幣；其他地方主金額是台幣、備註放外幣
    val signedAcc = signedFor?.let { d.accMap[it] }
    val inForeign = signedAcc?.isForeign == true
    d.fxNote(t, inForeign)?.let { details.add(it) }
    if (t.note.isNotBlank()) details.add(t.note.lineSequence().first())

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t.reimb != 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (t.reimb == 1) "待報銷" else "已報銷") + when {
                            t.reimb == 1 && t.reimbOutstanding < t.reimbAmount -> " 剩${formatMoney(t.reimbOutstanding)}"
                            t.reimbAmount < t.paid -> " ${formatMoney(t.reimbAmount)}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (t.reimb == 1) cute.expense else cute.income,
                        modifier = Modifier.clip(CircleShape)
                            .background((if (t.reimb == 1) cute.expense else cute.income).copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
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
        val (sign, c) = when {
            t.type == TxType.EXPENSE -> "-" to cute.expense
            t.type == TxType.INCOME -> "+" to cute.income
            signedFor != null && t.toAccountId == signedFor -> "+" to cute.income
            signedFor != null && t.accountId == signedFor -> "-" to cute.expense
            else -> "" to cute.sub
        }
        val shown = when {
            inForeign && signedAcc != null -> kotlin.math.abs(t.flowFor(signedAcc))
            t.type == TxType.TRANSFER && signedFor != null && t.accountId == signedFor -> t.amount + t.fee
            else -> t.paid
        }
        val shownText = if (inForeign && signedAcc != null) formatFx(shown, signedAcc.currency) else formatMoney(shown)
        // 金額下面小字：這一筆做完之後這個帳戶的餘額（只有帳戶明細會帶 balance），和左邊的標題／備註一大一小
        Column(horizontalAlignment = Alignment.End) {
            Text(sign + shownText, color = c, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (balance != null) {
                Text(
                    "餘額 ${signedAcc?.fmt(balance) ?: formatMoney(balance)}", style = MaterialTheme.typography.labelSmall,
                    color = if (balance < 0) cute.expense else cute.sub, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DayHeader(day: Long, list: List<Txn>) {
    val cute = LocalCute.current
    val exp = list.expenseSum()
    val inc = list.incomeSum()
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f))
        if (inc > 0) Text("收 ${formatMoney(inc)}  ", style = MaterialTheme.typography.labelMedium, color = cute.income)
        if (exp > 0) Text("支 ${formatMoney(exp)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
    }
}

/** 明細與日曆共用的切換鈕：目前是明細就顯示日曆圖示，點一下切過去；反之亦然 */
@Composable
fun ViewToggle(vm: MoneyViewModel) {
    val cute = LocalCute.current
    val cal = vm.homeCalendar
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(cute.card).clickable { vm.homeCalendar = !cal },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (cal) AppIcons.ListAlt else AppIcons.Calendar,
            contentDescription = if (cal) "切換成明細列表" else "切換成日曆",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ───────────────────────── 首頁（明細） ─────────────────────────

@Composable
fun HomeScreen(
    vm: MoneyViewModel,
    onEdit: (Long) -> Unit,
    onManageBooks: () -> Unit,
    onReimb: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val month = vm.month
    val list = d.bookTxns.inMonth(month)
    val income = list.incomeSum()
    val expense = list.expenseSum()
    val book = d.currentBook
    val mascot = d.prefs.mascot
    var showBooks by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    LazyColumn(
        state = vm.homeListState,
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
                    IconGlyph(book.emoji, 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(book.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
                    Text(" ▾", color = cute.sub)
                }
                Spacer(Modifier.weight(1f))
                ViewToggle(vm)
                Spacer(Modifier.width(8.dp))
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

        val budget = book.budgetFor(month)
        item {
            if (budget > 0) {
                val frac = expense.toFloat() / budget.toFloat()
                val color = budgetColor(frac)
                val level = when {
                    frac > 1f -> 2
                    frac >= 0.8f -> 1
                    else -> 0
                }
                // 越接近上限卡片越醒目；超支時邊框會輕輕閃
                val pulse = rememberInfiniteTransition(label = "budget")
                val glow by pulse.animateFloat(
                    initialValue = 0.35f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
                )
                val bg = when (level) {
                    2 -> cute.expense.copy(alpha = 0.16f)
                    1 -> cute.accent2.copy(alpha = 0.22f)
                    else -> cute.card
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).background(bg)
                        .then(
                            if (level == 2) Modifier.border(2.dp, cute.expense.copy(alpha = glow), RoundedCornerShape(20.dp))
                            else if (level == 1) Modifier.border(1.5.dp, cute.accent2, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { showBudget = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalContentColor provides (if (level == 2) cute.expense else cute.ink)) {
                            IconGlyph(when (level) { 2 -> "vec:siren"; 1 -> "vec:warning"; else -> "vec:target" }, 16.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (level) {
                                2 -> "已經超出預算了！"
                                1 -> "預算快用完了"
                                else -> "${month.monthValue} 月預算"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when (level) { 2 -> cute.expense; else -> cute.ink },
                            fontWeight = if (level > 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(frac * 100).toInt()}%",
                            style = if (level > 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    RatioBar(frac, color, thick = level > 0)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (frac > 1f) "超出 ${formatMoney(expense - budget)}（預算 ${formatMoney(budget)}）"
                        else "已花 ${formatMoney(expense)}・還剩 ${formatMoney(budget - expense)}（預算 ${formatMoney(budget)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (level == 2) cute.expense else cute.sub,
                        fontWeight = if (level == 2) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            } else {
                Row(
                    Modifier.clip(CircleShape).clickable { showBudget = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { IconGlyph("vec:target", 16.sp) }
                    Spacer(Modifier.width(6.dp))
                    Text("設定每月預算", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                val pending = d.bookTxns.pendingReimb()
                if (pending.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(CircleShape).background(cute.soft).clickable(onClick = onReimb)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconGlyph("vec:receipt", 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "待報銷 ${pending.size} 筆・${formatMoney(pending.sumOf { it.reimbOutstanding })}  ›",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
            items(dayList, key = { it.id }) { t ->
                SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t) { onEdit(t.id) } }
            }
        }
    }

    if (showBudget) BudgetDialog(vm, month) { showBudget = false }

    if (showBooks) {
        AlertDialog(
            onDismissRequest = { showBooks = false },
            title = { Text("切換帳本") },
            text = {
                // 帳本多的時候要能往下滑
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.books.forEach { b ->
                        val on = b.id == book.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.switchBook(b.id); showBooks = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconGlyph(b.emoji, 20.sp)
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
    var selected by vm::calSelected
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

    // 長按拖曳：記錄每個日期格子的位置
    val cellRects = remember(month) { HashMap<Long, Rect>() }
    var dragging by remember { mutableStateOf<Txn?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var boxPos by remember { mutableStateOf(Offset.Zero) }
    val hoverDay: Long? = if (dragging != null) cellRects.entries.firstOrNull { it.value.contains(dragPos) }?.key else null

    Box(Modifier.fillMaxSize().onGloballyPositioned { boxPos = it.positionInRoot() }) {
    LazyColumn(
        state = vm.calListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("日曆", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                ViewToggle(vm)
                Spacer(Modifier.width(8.dp))
                MonthSwitcher(month, { vm.month = it })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("本月收入 ${formatMoney(list.incomeSum())}", color = cute.income, style = MaterialTheme.typography.labelLarge)
                Text("本月支出 ${formatMoney(list.expenseSum())}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
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
                                Spacer(Modifier.weight(1f).height(64.dp))
                            } else {
                                val date = month.atDay(dayNum)
                                val ep = date.toEpochDay()
                                val dl = byDay[ep] ?: emptyList()
                                val exp = dl.expenseSum()
                                val inc = dl.incomeSum()
                                val isSel = ep == selected
                                val isToday = date == today
                                val isHover = hoverDay == ep
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .onGloballyPositioned { cellRects[ep] = it.boundsInRoot() }
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                isHover -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                isSel -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                            else Modifier
                                        )
                                        .clickable { selected = ep },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else cute.ink,
                                    )
                                    if (inc > 0) Text("+" + formatShort(inc), fontSize = 9.sp, lineHeight = 11.sp, color = cute.income, maxLines = 1)
                                    if (exp > 0) Text("-" + formatShort(exp), fontSize = 9.sp, lineHeight = 11.sp, color = cute.expense, maxLines = 1)
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
                val i = selList.incomeSum()
                val e = selList.expenseSum()
                if (i > 0) Text("收 ${formatMoney(i)}  ", color = cute.income, style = MaterialTheme.typography.labelLarge)
                if (e > 0) Text("支 ${formatMoney(e)}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
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
        if (selList.isNotEmpty()) {
            item {
                Text(
                    "小技巧：長按記錄可以拖到其他日期，左滑可以刪除",
                    style = MaterialTheme.typography.labelSmall,
                    color = cute.sub,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(selList, key = { it.id }) { t ->
            SwipeRow(
                onDelete = { vm.deleteWithUndo(t.id) },
                drag = DragCallbacks(
                    onStart = { p -> dragging = t; dragPos = p },
                    onMove = { p -> dragPos = p },
                    onEnd = {
                        val target = cellRects.entries.firstOrNull { it.value.contains(dragPos) }?.key
                        if (target != null && target != t.day) {
                            vm.moveTxnDay(t.id, target)
                            selected = target
                        }
                        dragging = null
                    },
                    onCancel = { dragging = null },
                ),
            ) { TxnRow(d, t) { onEdit(t.id) } }
        }
    }
        // 拖曳中跟著手指的小卡
        val dt = dragging
        if (dt != null) {
            val c = dt.categoryId?.let { d.catMap[it] }
            Row(
                Modifier
                    .offset { IntOffset((dragPos.x - boxPos.x).roundToInt() - 60, (dragPos.y - boxPos.y).roundToInt() - 90) }
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${iconLabel(c?.emoji ?: "img:ui_transfer", c?.name ?: "轉帳")} ${formatMoney(dt.paid)}" +
                        (hoverDay?.let { "  →  ${LocalDate.ofEpochDay(it).monthValue}/${LocalDate.ofEpochDay(it).dayOfMonth}" } ?: ""),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
