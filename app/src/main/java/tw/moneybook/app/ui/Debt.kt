package tw.moneybook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.Currencies
import tw.moneybook.app.DebtGroup
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.debtCurrency
import tw.moneybook.app.debtGroups
import tw.moneybook.app.fxSpendNote
import tw.moneybook.app.isFxSpend
import tw.moneybook.app.debtRemainingTwd
import tw.moneybook.app.debtRemainingUnits
import tw.moneybook.app.debtTotalUnits
import tw.moneybook.app.fmt
import tw.moneybook.app.formatFx
import tw.moneybook.app.formatMoney
import tw.moneybook.app.fxPlain
import tw.moneybook.app.isForeign
import tw.moneybook.app.parseFx
import tw.moneybook.app.scaleRound
import tw.moneybook.app.settledDebts

/**
 * 記一筆的「欠款」：欠誰。欠款是別人先墊、我還沒付的支出，記成一筆支出但先不從任何帳戶扣，還款時才扣（見 Debt.kt）。
 * names：以前欠過的人（點一下帶入）；locked：已經有還款記錄，只能改欠誰、不能取消欠款；onClear 不是 null 時多一個「取消欠款」。
 */
@Composable
fun DebtWhoDialog(
    names: List<String>,
    initial: String,
    locked: Boolean,
    onConfirm: (String) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cute = LocalCute.current
    var who by remember { mutableStateOf(initial) }
    val tries = rememberNeedTries()
    val need = if (who.isBlank()) Need("who", "請輸入欠誰") else null
    val nv = NeedView(need, tries.count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("欠款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "別人先墊、我還沒付的錢：記成一筆支出（統計照這天算），但先不從任何帳戶扣，之後在「欠款」頁還款時才扣。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                OutlinedTextField(
                    who, { who = it.take(16) },
                    label = { Text("欠誰") }, singleLine = true,
                    isError = nv.on("who"), supportingText = nv.supporting("who"),
                    modifier = Modifier.fillMaxWidth().needInView(nv, "who"),
                )
                if (names.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.take(10).forEach { n -> CuteChip(n, who.trim() == n, { who = n }) }
                    }
                }
                if (locked) {
                    Text("這筆已經有還款記錄：金額不能改，也不能取消欠款；要改請先到「欠款」頁刪掉還款。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (need != null) tries.count++ else onConfirm(who.trim()) }) { Text("確定") } },
        dismissButton = {
            Row {
                if (onClear != null && !locked) TextButton(onClick = onClear) { Text("取消欠款") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 欠款的金額文字：台幣欠款是台幣；外幣欠款是外幣，後面接約當台幣（預估匯率） */
private fun debtAmountText(units: Long, twd: Long, currency: String): String =
    if (currency.isEmpty()) formatMoney(units) else formatFx(units, currency) + "（約 ${formatMoney(twd)}）"

private fun debtDate(day: Long): String = LocalDate.ofEpochDay(day).let { "${it.monthValue}/${it.dayOfMonth}" }

/**
 * 欠款頁：還欠著的錢依人分組（台幣欠款和外幣欠款分開還），每一組可以還款；每一筆下面列出還款記錄，可以刪掉；
 * 下面「已還清」收著結清的欠款（還看得到歷史、可以刪掉還款重新變成還欠著）。點一筆欠款可以修改。
 */
@Composable
fun DebtScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val groups = d.debtGroups()
    val settled = d.settledDebts()
    var repayKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSettled by remember { mutableStateOf(false) }

    @Composable
    fun DebtLine(t: Txn) {
        val what = listOf(catPath(d, t), t.note.trim().lineSequence().firstOrNull()?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString("・")
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { onEdit(t.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(what, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(debtDate(t.day) + if (t.isFxSpend) "・${t.fxSpendNote()}" else "", style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
                }
                Text(
                    when {
                        t.debtDone && t.debtPays.isEmpty() -> "免還"
                        t.debtDone -> formatMoney(t.amount)
                        t.debtPays.isEmpty() -> debtAmountText(t.debtTotalUnits, t.debtOrig, t.debtCurrency)
                        else -> "剩 " + debtAmountText(t.debtRemainingUnits, t.debtRemainingTwd, t.debtCurrency)
                    },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (t.debtDone) cute.sub else cute.expense, maxLines = 1,
                )
            }
            t.debtPays.forEachIndexed { i, p ->
                val acc = p.accountId?.let { d.accMap[it] }
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已還 ${debtDate(p.day)}  -" + (if (acc != null) acc.fmt(if (acc.isForeign) p.fxAmount else p.amount) else formatMoney(p.amount)) +
                            (acc?.let { "・${it.name}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = cute.income, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    TextButton(onClick = { vm.deleteDebtPay(t.id, i) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("刪除還款", style = MaterialTheme.typography.labelSmall, color = cute.expense)
                    }
                }
            }
        }
    }

    SubPage("欠款", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("還欠著", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Text(formatMoney(groups.sumOf { it.remainingTwd }), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${groups.map { it.who }.distinct().size} 人、${groups.sumOf { it.txns.size }} 筆。別人先墊、我還沒轉帳的錢，記的那天就算支出，還款時才從帳戶扣。",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub,
                    )
                }
            }
            if (groups.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "沒有欠款\n記一筆支出時選「欠款」，就會出現在這裡") }
            }
            items(groups, key = { "${it.who}|${it.currency}" }) { g ->
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.who + if (g.currency.isNotEmpty()) "（${g.currency}）" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(debtAmountText(g.remainingUnits, g.remainingTwd, g.currency), fontWeight = FontWeight.SemiBold, color = cute.expense)
                    }
                    Spacer(Modifier.height(6.dp))
                    g.txns.forEach { DebtLine(it) }
                    Spacer(Modifier.height(8.dp))
                    SoftButton("還款", { repayKey = g.who to g.currency }, compact = true)
                }
            }
            if (settled.isNotEmpty()) {
                item {
                    Text(
                        "已還清（${settled.size} 筆）" + if (showSettled) "  ▲" else "  ▼",
                        style = MaterialTheme.typography.labelLarge, color = cute.sub,
                        modifier = Modifier.fillMaxWidth().clickable { showSettled = !showSettled }.padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
                    )
                }
                if (showSettled) {
                    items(settled, key = { "s${it.id}" }) { t ->
                        CuteCard(Modifier.fillMaxWidth()) {
                            Text(t.debtTo, style = MaterialTheme.typography.titleSmall)
                            DebtLine(t)
                        }
                    }
                }
            }
        }
    }
    repayKey?.let { k ->
        val g = groups.firstOrNull { it.who == k.first && it.currency == k.second }
        if (g == null) repayKey = null else RepayDialog(vm, g, onDismiss = { repayKey = null })
    }
}

/**
 * 還款：還了多少（台幣欠款是台幣、外幣欠款是外幣）、從哪個帳戶還、哪一天。
 * 還得比欠的少時問「還沒還完」或「這樣就算還清了」；台幣欠款還得比較多算還清，多的加在最後一筆的支出上；
 * 用台幣帳戶還外幣欠款要填實際扣掉的台幣，用同幣別的外幣帳戶還就直接扣外幣（見 withDebtRepaid）。
 */
@Composable
private fun RepayDialog(vm: MoneyViewModel, g: DebtGroup, onDismiss: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val fx = g.currency.isNotEmpty()
    val dec = Currencies.of(g.currency).decimals
    val remaining = g.remainingUnits
    // 台幣欠款只能用台幣帳戶還；外幣欠款也可以用同幣別的外幣帳戶還。投資帳戶不能拿來還錢
    val accounts = d.visibleAccounts.filter { it.type != AccountType.INVEST && (!it.isForeign || (fx && it.currency == g.currency)) }
    var accId by remember { mutableStateOf(d.frequentAccounts(5).firstOrNull { f -> accounts.any { it.id == f.id } }?.id ?: accounts.firstOrNull()?.id) }
    var pickAcc by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(if (fx) fxPlain(remaining, dec) else remaining.toString()) }
    // 用台幣帳戶還外幣欠款：實際扣掉的台幣；沒改過就依預估匯率帶入
    var twdInput by remember { mutableStateOf<String?>(null) }
    var settle by remember { mutableStateOf(false) }
    val acc = accId?.let { d.accMap[it] }

    val units = if (fx) (parseFx(amountText, dec) ?: 0L) else (amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L)
    val estTwd = scaleRound(g.remainingTwd, units.coerceAtMost(remaining), remaining)
    val twdAsk = fx && acc != null && !acc.isForeign
    val twd = twdInput?.filter { it.isDigit() }?.toLongOrNull() ?: estTwd
    val short = units in 1 until remaining
    val over = !fx && units > remaining

    val tries = rememberNeedTries()
    val need = firstNeed(
        if (acc == null) Need("acc", "請選擇帳戶") else null,
        if (units <= 0L) Need("amount", "請輸入還了多少") else null,
        if (fx && units > remaining) Need("amount", "不能超過還欠的 ${formatFx(remaining, g.currency)}") else null,
        if (twdAsk && twd <= 0L) Need("twd", "請輸入實際扣掉的台幣金額") else null,
    )
    val nv = NeedView(need, tries.count)
    val unitText: (Long) -> String = { if (fx) formatFx(it, g.currency) else formatMoney(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("還款給 ${g.who}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "共欠 ${debtAmountText(remaining, g.remainingTwd, g.currency)}。有好幾筆時由舊的開始還。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                OutlinedTextField(
                    amountText,
                    { v -> amountText = if (fx) v.filter { c -> c.isDigit() || (c == '.' && dec > 0) }.take(14) else v.filter { c -> c.isDigit() }.take(10) },
                    label = { Text(if (fx) "還了多少外幣（${g.currency}）" else "還了多少") },
                    prefix = if (fx) null else ({ Text(tw.moneybook.app.Money.twd) }),
                    singleLine = true,
                    isError = nv.on("amount"), supportingText = nv.supporting("amount"),
                    keyboardOptions = KeyboardOptions(keyboardType = if (fx) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().needInView(nv, "amount"),
                )
                if (short) {
                    Text("少 ${unitText(remaining - units)}", style = MaterialTheme.typography.labelLarge, color = cute.expense)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        CuteChip("還沒還完，剩下的繼續欠", !settle, { settle = false })
                        CuteChip("這樣就算還清了", settle, { settle = true })
                    }
                    if (settle) {
                        Text("少付的 ${unitText(remaining - units)} 不用還，從那幾筆的支出裡扣掉。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
                if (over) {
                    Text("多 ${unitText(units - remaining)}", style = MaterialTheme.typography.labelLarge, color = cute.expense)
                    Text("多付的錢（手續費、湊整）算在最後一筆的支出上，這樣就還清了。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
                PickerButton(acc?.let { it.name + if (it.isForeign) "（${it.currency}）" else "" } ?: "選擇帳戶", { pickAcc = true })
                nv.Message("acc")
                if (pickAcc) {
                    AccountPickDialog(
                        vm = vm, accounts = accounts, selectedId = accId,
                        onPick = { accId = it.id; pickAcc = false }, onDismiss = { pickAcc = false }, title = "從哪個帳戶還",
                    )
                }
                if (twdAsk) {
                    OutlinedTextField(
                        twdInput ?: estTwd.toString(), { twdInput = it.filter { c -> c.isDigit() }.take(10) },
                        label = { Text("實際扣掉的台幣") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                        isError = nv.on("twd"), supportingText = nv.supporting("twd"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().needInView(nv, "twd"),
                    )
                    val diff = twd - estTwd
                    if (units > 0L && estTwd > 0L && diff != 0L) {
                        Text(
                            "預估 ${formatMoney(estTwd)} → 實際 ${formatMoney(twd)}，${if (diff > 0) "多" else "少"} ${formatMoney(kotlin.math.abs(diff))}；還清後支出改成實際金額。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                    }
                } else if (fx && acc != null) {
                    Text("直接從「${acc.name}」扣 ${formatFx(units, g.currency)}，支出照記的時候的預估匯率。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
                CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                if (acc != null && units > 0L) {
                    Text(
                        "→ ${acc.name} -" + (if (acc.isForeign) formatFx(units, g.currency) else formatMoney(if (twdAsk) twd else units)) + "，不算收入或支出",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (need != null) { tries.count++; return@TextButton }
                val t = java.time.LocalTime.now()
                if (vm.repayDebt(g.who, g.currency, accId!!, day, t.hour * 60 + t.minute, units, if (twdAsk) twd else 0L, short && settle)) onDismiss()
            }) { Text("還款") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pickDate) CuteDatePickerDialog(initial = day, onPick = { day = it; pickDate = false }, onDismiss = { pickDate = false })
}
