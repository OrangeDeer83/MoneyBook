@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.ReimbPay
import tw.moneybook.app.flowFor
import tw.moneybook.app.Currencies
import tw.moneybook.app.cur
import tw.moneybook.app.fmt
import tw.moneybook.app.fxPlain
import tw.moneybook.app.isForeign
import tw.moneybook.app.parseFx
import tw.moneybook.app.rateOf
import tw.moneybook.app.twdValue
import tw.moneybook.app.avgCost
import tw.moneybook.app.rateText
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.cardCycle
import tw.moneybook.app.cardSpending
import tw.moneybook.app.formatMoney
import tw.moneybook.app.inMonth
import tw.moneybook.app.limitInfo
import tw.moneybook.app.transfersIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 帳戶圖示：文字徽章或表情符號 */
@Composable
fun AccountIcon(a: Account, size: Dp = 40.dp) {
    if (a.badge.isNotBlank()) {
        // 有雙色漸層就用漸層（左上到右下），沒有就用單色
        val grad = a.badgeFrom != 0L && a.badgeTo != 0L
        val c1 = if (grad) Color(a.badgeFrom.toInt()) else badgeColor(a.badgeColor)
        val c2 = if (grad) Color(a.badgeTo.toInt()) else c1
        val fg = if (androidx.compose.ui.graphics.lerp(c1, c2, 0.5f).luminance() > 0.55f) Color(0xFF2B2B2B) else Color.White
        val text = a.badge.trim().take(4)
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
                .background(if (grad) Brush.linearGradient(listOf(c1, c2)) else SolidColor(c1)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * when (text.length) { 1 -> 0.46f; 2 -> 0.34f; 3 -> 0.27f; else -> 0.24f }).sp,
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
    /** 買賣外幣：帳戶 id、true = 買進（台幣轉進來）、false = 賣出（轉回台幣） */
    onFxTrade: (Long, Boolean) -> Unit = { _, _ -> },
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
    var rateDialog by remember { mutableStateOf(false) }
    var fetchAsk by remember { mutableStateOf(false) }
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

    fun flow(t: Txn): Long = t.flowFor(a)
    val flows = monthList.filter { it.accountId == a.id || it.toAccountId == a.id }.map { flow(it) } + reimbIn.map { it.pay.flowFor(a) }
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
                                a.fmt(balance),
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (balance < 0) cute.expense else cute.ink,
                            )
                        }
                        SoftIconButton("vec:pencil", "編輯", { editing = true })
                    }
                    Spacer(Modifier.height(10.dp))
                    SoftButton("更新餘額", { adjusting = true }, Modifier.fillMaxWidth())
                    if (a.isForeign) {
                        val rate = d.rateOf(a.currency)
                        val manual = d.rates.any { it.code == a.currency }
                        val twdv = d.twdValue(a, balance)
                        val avg = d.avgCost(a)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (twdv != null) "約當 ${formatMoney(twdv)}" else "還沒有匯率，無法換算成台幣",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (rate != null) "目前匯率 ${rateText(rate)}（${if (manual) "手動設定" else "最近一次買賣"}）" else "目前匯率：未設定",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.weight(1f),
                            )
                            SoftButton("設定匯率", { rateDialog = true }, compact = true)
                            Spacer(Modifier.width(6.dp))
                            SoftButton(
                                if (vm.ratesFetching) "更新中…" else "上網更新",
                                {
                                    when {
                                        vm.ratesFetching -> {}
                                        d.prefs.priceFetch -> vm.refreshRates()
                                        else -> fetchAsk = true
                                    }
                                },
                                compact = true,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = { onFxTrade(a.id, true) }) { Text("買進 ${a.currency}") }
                            androidx.compose.material3.OutlinedButton(onClick = { onFxTrade(a.id, false) }) { Text("賣出 ${a.currency}") }
                        }
                        if (avg != null) {
                            Text(
                                "平均買進成本 ${rateText(avg)}" +
                                    if (rate != null && kotlin.math.abs(rate - avg) > 1e-6) "（目前比成本${if (rate > avg) "高" else "低"} ${rateText(kotlin.math.abs(rate - avg))}）" else "",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub,
                            )
                        }
                    }
                    val limitInfo = remember(d, a) { d.limitInfo(a) }
                    if (limitInfo != null) {
                        Spacer(Modifier.height(10.dp))
                        val frac = limitInfo.used.toFloat() / limitInfo.limit.toFloat()
                        RatioBar(frac, budgetColor(frac))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已用 ${formatMoney(limitInfo.used)}・可用 ${formatMoney(limitInfo.available)}・額度 ${formatMoney(limitInfo.limit)}",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        // 共用額度時，額度和已用金額是這幾張卡一起算
                        if (limitInfo.shared) {
                            Text(
                                "和 ${limitInfo.members.joinToString("、") { it.name }} 共用額度",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub,
                            )
                        }
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
                                "${dayTimeLabel(pay.day, pay.time)}・${c?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("+" + a.fmt(pay.flowFor(a)), color = cute.income, fontWeight = FontWeight.SemiBold)
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
            acc = a,
            current = balance,
            isCard = a.type == AccountType.CARD || a.type == AccountType.LOAN,
            onConfirm = { target -> vm.adjustBalance(a.id, target); adjusting = false },
            onDismiss = { adjusting = false },
        )
    }
    if (rateDialog) {
        RateDialog(
            code = a.currency, current = d.rateOf(a.currency), manual = d.rates.any { it.code == a.currency },
            fetchOn = d.prefs.priceFetch, onFetchOn = { vm.setPriceFetch(it) },
            onSave = { vm.setRate(a.currency, it); rateDialog = false },
            onDismiss = { rateDialog = false },
        )
    }
    if (fetchAsk) {
        AlertDialog(
            onDismissRequest = { fetchAsk = false },
            title = { Text("上網更新匯率？") },
            text = {
                Text(
                    "記帳本平常完全不連網。開啟後，只有按「上網更新」（或持股頁的「抓最新價格」）時，" +
                        "才會把幣別代碼（例如 USDTWD）傳給 Yahoo Finance 查匯率，不會傳送任何記帳資料。\n" +
                        "之後可以隨時在「設定匯率」裡關閉。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { vm.setPriceFetch(true); fetchAsk = false; vm.refreshRates() }) { Text("開啟並更新") } },
            dismissButton = { TextButton(onClick = { fetchAsk = false }) { Text("不要") } },
        )
    }
    if (editing) {
        AccountDialog(
            acc = a,
            cards = d.accounts.filter { it.type == AccountType.CARD },
            onSave = { na -> vm.saveAccount(a.id, na); editing = false },
            onShares = { vm.setSharedLimits(it) },
            onDelete = { vm.deleteAccount(a.id); editing = false; onBack() },
            onDismiss = { editing = false },
        )
    }
}

/** 設定外幣的目前匯率：1 單位外幣 = 多少台幣；清除就改用最近一次買賣的匯率 */
@Composable
private fun RateDialog(
    code: String, current: Double?, manual: Boolean,
    fetchOn: Boolean, onFetchOn: (Boolean) -> Unit,
    onSave: (Double?) -> Unit, onDismiss: () -> Unit,
) {
    val cute = LocalCute.current
    var text by remember { mutableStateOf(current?.let { rateText(it) } ?: "") }
    val value = text.toDoubleOrNull()?.takeIf { it > 0.0 }
    val tries = rememberNeedTries()
    val need = firstNeed(if (value == null) Need("rate", "請輸入匯率（要大於 0）") else null)
    val nv = NeedView(need, tries.count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定 $code 匯率") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { s -> text = s.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("1 $code = 幾元台幣") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                    isError = nv.on("rate"), supportingText = nv.supporting("rate"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "匯率用來把外幣餘額換成約當台幣；每次買賣外幣時，成交的匯率會另外記在那一筆。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                if (manual) {
                    SoftButton("清除手動匯率（改用最近一次買賣的匯率）", { onSave(null) }, Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("允許上網更新匯率與股價", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = fetchOn, onCheckedChange = onFetchOn)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (need != null) tries.count++ else onSave(value) }) { Text("儲存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 更新餘額：輸入帳戶現在實際的餘額，差額會記成一筆「餘額調整」 */
@Composable
private fun AdjustBalanceDialog(acc: Account, current: Long, isCard: Boolean, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var text by remember { mutableStateOf("") }
    val dec = if (acc.isForeign) acc.cur.decimals else 0
    // 只留數字（外幣可以有小數點），欠款（信用卡）可以在最前面加負號
    val target = parseFx(text, dec)
    fun money(v: Long) = acc.fmt(v)
    val tries = rememberNeedTries()
    val need = firstNeed(if (target == null) Need("target", "請輸入帳戶現在實際的餘額") else null)
    val nv = NeedView(need, tries.count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新餘額") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目前記錄的餘額：${money(current)}", color = cute.sub, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '-' || (c == '.' && dec > 0) }.take(14) },
                    label = { Text("實際的餘額") }, singleLine = true,
                    isError = nv.on("target"), supportingText = nv.supporting("target"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val diff = target?.let { it - current }
                Text(
                    when {
                        diff == null -> if (isCard) "信用卡或貸款的欠款請輸入負數，例如 -3000" else "輸入帳戶現在實際的金額"
                        diff == 0L -> "和記錄的一樣，不用調整"
                        diff > 0 -> "會補記 +${money(diff)}，不算收入"
                        else -> "會補記 −${money(-diff)}，不算支出"
                    },
                    color = cute.sub, style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    need != null || target == null -> tries.count++
                    target == current -> onDismiss()      // 和記錄的一樣，不用調整
                    else -> onConfirm(target)
                }
            }) { Text("更新") }
        },
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
fun AccountLine(a: Account, balance: Long, on: Boolean, onFavorite: ((Account) -> Unit)? = null, onClick: () -> Unit) {
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
        Text((if (a.favorite && onFavorite == null) "★ " else "") + a.name, modifier = Modifier.weight(1f))
        Text(a.fmt(balance), color = cute.sub, style = MaterialTheme.typography.labelLarge)
        if (onFavorite != null) FavoriteStar(a.favorite) { onFavorite(a) }
    }
}

/** 常用帳戶的星號：實心＝已設為常用，空心＝還不是，點一下就切換 */
@Composable
fun FavoriteStar(on: Boolean, onClick: () -> Unit) {
    val cute = LocalCute.current
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).semantics { contentDescription = if (on) "取消常用帳戶" else "設為常用帳戶" },
    ) {
        if (on) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Star, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
        } else {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides cute.sub.copy(alpha = 0.75f)) {
                IconGlyph("vec:star", 22.sp)
            }
        }
    }
}

/** 顏色圓點列 */
/** 一排銀行／行動支付預設徽章（可左右滑），點一個就回報 */
@Composable
fun PresetRow(presets: List<tw.moneybook.app.BadgePreset>, onPick: (tw.moneybook.app.BadgePreset) -> Unit) {
    val cute = LocalCute.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        presets.forEach { p ->
            Column(
                Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(p) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AccountIcon(Account(0L, p.name, "", AccountType.BANK, 0L, 0, badge = p.badge, badgeFrom = p.from, badgeTo = p.to), 40.dp)
                Text(p.name, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
            }
        }
    }
}

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
