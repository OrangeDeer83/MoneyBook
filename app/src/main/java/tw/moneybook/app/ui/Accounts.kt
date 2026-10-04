@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.ReimbPay
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.cardCycle
import tw.moneybook.app.cardSpending
import tw.moneybook.app.formatMoney
import tw.moneybook.app.inMonth
import tw.moneybook.app.transfersIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 帳戶圖示：文字徽章或表情符號 */
@Composable
fun AccountIcon(a: Account, size: Dp = 40.dp) {
    if (a.badge.isNotBlank()) {
        val bg = badgeColor(a.badgeColor)
        val fg = if (bg.luminance() > 0.55f) Color(0xFF2B2B2B) else Color.White
        val text = a.badge.trim().take(2)
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * if (text.length >= 2) 0.34f else 0.46f).sp,
                maxLines = 1,
            )
        }
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(LocalCute.current.soft),
            contentAlignment = Alignment.Center,
        ) { IconGlyph(a.emoji, (size.value * 0.5f).sp) }
    }
}

/** 用在按鈕文字上的帳戶名稱 */
fun accLabel(a: Account): String = if (a.badge.isNotBlank()) a.name else iconLabel(a.emoji, a.name)

private fun daysLeft(to: LocalDate): String {
    val n = ChronoUnit.DAYS.between(LocalDate.now(), to)
    return when {
        n == 0L -> "今天"
        n > 0 -> "還有 $n 天"
        else -> "已過 ${-n} 天"
    }
}

/** 帳戶明細裡一筆報銷收款（idx 是這筆記錄的第幾筆收款） */
private data class ReimbIn(val t: Txn, val who: String, val pay: ReimbPay, val idx: Int)

/** 單一帳戶的明細 */
@Composable
fun AccountDetailScreen(
    vm: MoneyViewModel,
    accountId: Long,
    onEdit: (Long) -> Unit,
    onPayCard: (Long, Long?) -> Unit,
    onBack: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val a = d.accMap[accountId]
    if (a == null) {
        SubPage("帳戶", onBack) { EmptyHint(d.prefs.mascot, "找不到這個帳戶") }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(vm.month) }
    val balance = remember(d) { d.balances()[a.id] ?: 0L }
    // 這個帳戶相關的記錄（所有帳本）
    val all = d.txns.filter { it.accountId == a.id || it.toAccountId == a.id || it.items.any { i -> i.pays.any { pay -> pay.accountId == a.id } } }
    val monthList = all.inMonth(month)
    // 這個帳戶這個月收到的報銷款（每一筆收款各算一筆）
    val reimbIn = d.txns.flatMap { t ->
        // idx：這一筆記錄所有報銷收款的編號，要和 runningBalances 的 key 對得上
        t.items.flatMap { i -> i.pays.map { pay -> i.who to pay } }.mapIndexed { idx, (who, pay) -> ReimbIn(t, who, pay, idx) }
    }
        .filter { it.pay.accountId == a.id }
        .filter { val rd = LocalDate.ofEpochDay(it.pay.day); rd.year == month.year && rd.monthValue == month.monthValue }
    // 每一筆做完之後的餘額
    val running = remember(d, a.id) { d.runningBalances(a.id) }

    fun flow(t: Txn): Long = when (t.type) {
        TxType.EXPENSE -> if (t.accountId == a.id) -t.paid else 0L
        TxType.INCOME -> if (t.accountId == a.id) t.paid else 0L
        TxType.TRANSFER -> (if (t.toAccountId == a.id) t.amount else 0L) - (if (t.accountId == a.id) t.amount + t.fee else 0L)
    }
    val flows = monthList.filter { it.accountId == a.id || it.toAccountId == a.id }.map { flow(it) } + reimbIn.map { it.pay.amount }
    val inflow = flows.filter { it > 0 }.sum()
    val outflow = -flows.filter { it < 0 }.sum()

    SubPage(a.name, onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountIcon(a, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.type.label, style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Text(
                                formatMoney(balance),
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (balance < 0) cute.expense else cute.ink,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { editing = true }) { Text("編輯") }
                            TextButton(onClick = { adjusting = true }) { Text("更新餘額") }
                        }
                    }
                    if (a.type == AccountType.CARD && a.creditLimit > 0) {
                        Spacer(Modifier.height(10.dp))
                        val used = (-balance).coerceAtLeast(0L)
                        val frac = used.toFloat() / a.creditLimit.toFloat()
                        RatioBar(frac, budgetColor(frac))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已用 ${formatMoney(used)}・可用 ${formatMoney((a.creditLimit - used).coerceAtLeast(0L))}・額度 ${formatMoney(a.creditLimit)}",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                    }
                }
            }
            if (a.type == AccountType.CARD) {
                item { CardBillCard(vm, a, onPayCard) }
            }
            if (a.type == AccountType.INVEST) {
                item { InvestSection(vm, a) }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MonthSwitcher(month, { month = it })
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("流入 ${formatMoney(inflow)}", color = cute.income, style = MaterialTheme.typography.labelLarge)
                        Text("流出 ${formatMoney(outflow)}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (monthList.isEmpty() && reimbIn.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "這個月這個帳戶沒有記錄") }
            }
            reimbIn.forEachIndexed { n, (t, who, pay, idx) ->
                item(key = "r${t.id}_$n") {
                    val c = t.categoryId?.let { d.catMap[it] }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card)
                            .clickable { onEdit(t.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatBubble("img:acc_receipt", 2, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("報銷入帳" + if (who.isNotBlank()) "・$who" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${dayLabel(pay.day)}・${c?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("+" + formatMoney(pay.amount), color = cute.income, fontWeight = FontWeight.SemiBold)
                            running["p${t.id}_$idx"]?.let { b ->
                                Text("餘額 ${formatMoney(b)}", style = MaterialTheme.typography.labelSmall, color = if (b < 0) cute.expense else cute.sub, maxLines = 1)
                            }
                        }
                    }
                }
            }
            monthList.filter { it.accountId == a.id || it.toAccountId == a.id }.groupBy { it.day }.forEach { (day, list) ->
                item(key = "d$day") { DayHeader(day, list) }
                items(list, key = { it.id }) { t ->
                    SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t, signedFor = a.id, balance = running["t${t.id}"]) { onEdit(t.id) } }
                }
            }
        }
    }
    if (adjusting) {
        AdjustBalanceDialog(
            current = balance,
            isCard = a.type == AccountType.CARD || a.type == AccountType.LOAN,
            onConfirm = { target -> vm.adjustBalance(a.id, target); adjusting = false },
            onDismiss = { adjusting = false },
        )
    }
    if (editing) {
        AccountDialog(
            acc = a,
            onSave = { na -> vm.saveAccount(a.id, na); editing = false },
            onDelete = { vm.deleteAccount(a.id); editing = false; onBack() },
            onDismiss = { editing = false },
        )
    }
}

/** 更新餘額：輸入帳戶現在實際的餘額，差額會記成一筆「餘額調整」 */
@Composable
private fun AdjustBalanceDialog(current: Long, isCard: Boolean, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var text by remember { mutableStateOf("") }
    // 只留數字，欠款（信用卡）可以在最前面加負號
    val target = text.trim().let { s ->
        val neg = s.startsWith("-")
        s.filter { it.isDigit() }.toLongOrNull()?.let { if (neg) -it else it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新餘額") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目前記錄的餘額：${formatMoney(current)}", color = cute.sub, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '-' }.take(12) },
                    label = { Text("實際的餘額") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val diff = target?.let { it - current }
                Text(
                    when {
                        diff == null -> if (isCard) "信用卡或貸款的欠款請輸入負數，例如 -3000" else "輸入帳戶現在實際的金額"
                        diff == 0L -> "和記錄的一樣，不用調整"
                        diff > 0 -> "會補記 +${formatMoney(diff)}，不算收入"
                        else -> "會補記 −${formatMoney(-diff)}，不算支出"
                    },
                    color = cute.sub, style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(enabled = target != null && target != current, onClick = { target?.let(onConfirm) }) { Text("更新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CardBillCard(vm: MoneyViewModel, a: Account, onPayCard: (Long, Long?) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now()
    val cyc = cardCycle(a, today)
    val balance = remember(d) { d.balances()[a.id] ?: 0L }
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconGlyph("img:acc_card", 22.sp)
            Spacer(Modifier.width(6.dp))
            Text("信用卡帳單", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        var remain = 0L
        if (cyc == null) {
            Text("在「編輯」裡設定結帳日和繳款日，就能看到每期帳單和繳款倒數。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
        } else {
            val prevStart = cyc.lastStatement.minusMonths(1).plusDays(1)
            val lastBill = d.cardSpending(a.id, prevStart, cyc.lastStatement).coerceAtLeast(0L)
            val paid = d.transfersIn(a.id, cyc.lastStatement.plusDays(1), today)
            val current = d.cardSpending(a.id, cyc.lastStatement.plusDays(1), today) + paid
            remain = (lastBill - paid).coerceAtLeast(0L)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("上期帳單", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(formatMoney(lastBill), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "結帳 ${cyc.lastStatement.monthValue}/${cyc.lastStatement.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("本期累積", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(formatMoney(current.coerceAtLeast(0L)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "下次結帳 ${cyc.nextStatement.monthValue}/${cyc.nextStatement.dayOfMonth}（${daysLeft(cyc.nextStatement)}）",
                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                    )
                }
            }
            val due = cyc.lastDue
            if (due != null && lastBill > 0) {
                Spacer(Modifier.height(10.dp))
                val warn = remain > 0 && ChronoUnit.DAYS.between(today, due) in 0..3
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (warn) cute.expense.copy(alpha = 0.12f) else cute.soft.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (remain == 0L) {
                            CompositionLocalProvider(LocalContentColor provides cute.income) { IconGlyph("vec:check", 16.sp) }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (remain == 0L) "上期已繳清" else "繳款日 ${due.monthValue}/${due.dayOfMonth}（${daysLeft(due)}）",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (warn) cute.expense else cute.ink,
                        )
                    }
                    Text(
                        "已繳 ${formatMoney(paid)}" + if (remain > 0) "・還要繳 ${formatMoney(remain)}" else "",
                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                    )
                }
            }
        }
        if (balance > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "目前溢繳 ${formatMoney(balance)}，之後的刷卡會先從這筆扣。",
                style = MaterialTheme.typography.labelMedium, color = cute.income,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (remain > 0) {
                androidx.compose.material3.Button(onClick = { onPayCard(a.id, remain) }) { Text("繳清上期 ${formatMoney(remain)}") }
            }
            androidx.compose.material3.OutlinedButton(onClick = { onPayCard(a.id, null) }) { Text("繳卡費（自訂金額）") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "繳卡費會記成一筆「從銀行轉到這張卡」的轉帳，可以多繳（溢繳）。",
            style = MaterialTheme.typography.labelSmall, color = cute.sub,
        )
    }
}

/** 預算越接近上限，顏色從主題色漸漸變成警告色、再變成紅色 */
@Composable
fun budgetColor(frac: Float): Color {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val f = frac.coerceIn(0f, 1.2f)
    return when {
        f <= 0.4f -> primary
        f <= 0.75f -> androidx.compose.ui.graphics.lerp(primary, cute.accent2, (f - 0.4f) / 0.35f)
        f <= 1f -> androidx.compose.ui.graphics.lerp(cute.accent2, cute.expense, (f - 0.75f) / 0.25f)
        else -> cute.expense
    }
}

/** 帳戶選擇列（圖示 + 名字 + 餘額），給對話框使用 */
@Composable
fun AccountLine(a: Account, balance: Long, on: Boolean, onClick: () -> Unit) {
    val cute = LocalCute.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIcon(a, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text((if (a.favorite) "★ " else "") + a.name, modifier = Modifier.weight(1f))
        Text(formatMoney(balance), color = cute.sub, style = MaterialTheme.typography.labelLarge)
    }
}

/** 顏色圓點列 */
@Composable
fun ColorDots(colors: List<Color>, selected: Int, onPick: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEachIndexed { i, c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c)
                    .then(if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(i) }
            )
        }
    }
}
