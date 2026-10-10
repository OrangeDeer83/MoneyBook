package tw.moneybook.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.time.temporal.ChronoUnit
import tw.moneybook.app.Currencies
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.formatFx
import tw.moneybook.app.formatMoney
import tw.moneybook.app.fxPendingList
import tw.moneybook.app.fxPlain
import tw.moneybook.app.fxSpendNote
import tw.moneybook.app.fxSpendRate
import tw.moneybook.app.fxToTwd
import tw.moneybook.app.impliedRate
import tw.moneybook.app.parseFx
import tw.moneybook.app.rateOf
import tw.moneybook.app.rateText

/** 台幣帳戶刷外幣：記一筆時的「外幣」對話框初始值 */
class FxSpendInit(
    /** 幣別碼；空白代表還沒選（新的一筆） */
    val cur: String,
    /** 外幣金額（純數字文字，沒有就是空白） */
    val amount: String,
    /** true＝還沒請款（用預估匯率算預估台幣）；false＝已請款（填實際請款的台幣） */
    val pending: Boolean,
    /** 目前的台幣金額（修改既有的一筆時預填；0＝沒有） */
    val twd: Long,
)

/**
 * 外幣消費：選幣別、輸入外幣金額，還沒請款就用匯率算預估台幣（預設是網路上抓到的目前匯率，可以改），
 * 已請款就填銀行實際請款的台幣，匯率由金額算出來。確定後回傳（幣別、外幣金額最小單位、台幣金額、是否待請款）。
 */
@Composable
fun FxSpendDialog(
    vm: MoneyViewModel,
    init: FxSpendInit,
    hasValue: Boolean,
    onConfirm: (String, Long, Long, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val startCur = init.cur.ifEmpty { d.txns.firstOrNull { it.fxSpendCur.isNotEmpty() }?.fxSpendCur ?: "JPY" }
    var cur by remember { mutableStateOf(startCur) }
    var pickCur by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(init.amount) }
    var pending by remember { mutableStateOf(init.pending) }
    var rateStr by remember { mutableStateOf("") }
    var rateTouched by remember { mutableStateOf(false) }
    var twdText by remember { mutableStateOf(if (!init.pending && init.twd > 0L) init.twd.toString() else "") }
    var askConsent by remember { mutableStateOf(false) }

    val code = cur.trim().uppercase()
    val codeOk = Currencies.validCode(code)
    val dec = Currencies.of(code).decimals
    val minor = parseFx(amountText, dec)?.takeIf { it > 0L }
    val rate = rateStr.toDoubleOrNull()?.takeIf { it > 0.0 }
    val twdActual = twdText.filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0L }

    // 修改既有的待請款：預填當初的匯率；新的一筆預填目前匯率（沒有的話，同意上網就自動抓一次）
    LaunchedEffect(code) {
        if (rateTouched || !codeOk) return@LaunchedEffect
        val keep = if (init.pending && init.twd > 0L && code == init.cur) {
            val m = parseFx(init.amount, dec) ?: 0L
            impliedRate(init.twd, m, dec)
        } else null
        val now = keep ?: d.rateOf(code)
        rateStr = now?.let { rateText(it) } ?: ""
        if (now == null && d.prefs.priceFetch) {
            vm.fetchRateOf(code) { r -> if (r != null && !rateTouched) rateStr = rateText(r) }
        }
    }

    val tries = rememberNeedTries()
    val need = firstNeed(
        if (!codeOk) Need("cur", "請輸入 3 個英文字母的幣別代碼（例如 THB）") else null,
        if (minor == null) Need("amount", "請輸入外幣金額") else null,
        if (pending && rate == null) Need("rate", "請輸入匯率（要大於 0）") else null,
        if (!pending && twdActual == null) Need("twd", "請輸入銀行實際請款的台幣金額") else null,
    )
    val nv = NeedView(need, tries.count)
    val estTwd = if (minor != null && rate != null) fxToTwd(minor, dec, rate) else 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外幣消費") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerButton(currencyLabel(code), { pickCur = true })
                if (pickCur) CurrencyPickDialog(cur, { cur = it; pickCur = false }, { pickCur = false })
                OutlinedTextField(
                    amountText, { amountText = it.filter { ch -> ch.isDigit() || (ch == '.' && dec > 0) }.take(14) },
                    label = { Text("外幣金額（${code.ifEmpty { "外幣" }}）") }, singleLine = true,
                    isError = nv.on("amount"), supportingText = nv.supporting("amount"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip("還沒請款（預估）", pending, { pending = true })
                    CuteChip("已請款（填實際台幣）", !pending, { pending = false })
                }
                if (pending) {
                    OutlinedTextField(
                        rateStr, { rateStr = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12); rateTouched = true },
                        label = { Text("匯率（1 ${code.ifEmpty { "外幣" }} = 幾元台幣）") }, singleLine = true,
                        isError = nv.on("rate"),
                        supportingText = nv.supporting("rate") ?: ({ Text("預設是目前匯率（上網抓到的或你設定的），可以自己改") }),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SoftButton(
                        "上網更新匯率",
                        {
                            if (!codeOk) return@SoftButton
                            if (d.prefs.priceFetch) {
                                vm.fetchRateOf(code) { r -> if (r != null) { rateStr = rateText(r); rateTouched = true } }
                            } else askConsent = true
                        },
                        compact = true,
                    )
                    if (estTwd > 0L) {
                        Text(
                            "預估 ${formatMoney(estTwd)}（${formatFx(minor ?: 0L, code)} × ${rate?.let { rateText(it) }}）。請款後再填銀行實際的台幣金額。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                    }
                } else {
                    OutlinedTextField(
                        twdText, { twdText = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("實際請款的台幣金額（含海外手續費）") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                        isError = nv.on("twd"), supportingText = nv.supporting("twd"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val real = if (minor != null && twdActual != null) impliedRate(twdActual, minor, dec) else null
                    if (real != null) {
                        Text("實際匯率 ${formatMoney(twdActual ?: 0L)} ÷ ${formatFx(minor ?: 0L, code)} = ${rateText(real)}", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
                if (hasValue) SoftButton("不是外幣消費", onClear, Modifier.fillMaxWidth(), danger = true, compact = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (need != null) { tries.count++; return@TextButton }
                onConfirm(code, minor ?: 0L, if (pending) estTwd else (twdActual ?: 0L), pending)
            }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text("上網更新匯率？") },
            text = { Text("只會傳送幣別代碼（例如 $code）去抓目前匯率，不會傳送任何記帳資料。之後可以在「設定匯率」裡關閉。") },
            confirmButton = {
                TextButton(onClick = {
                    askConsent = false
                    vm.setPriceFetch(true)
                    vm.fetchRateOf(code) { r -> if (r != null) { rateStr = rateText(r); rateTouched = true } }
                }) { Text("允許並更新") }
            },
            dismissButton = { TextButton(onClick = { askConsent = false }) { Text("不要") } },
        )
    }
}

/** 請款：填銀行實際請款的台幣金額，算出實際匯率；確定後標記消失 */
@Composable
fun SettleFxDialog(t: Txn, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var text by remember { mutableStateOf("") }
    val twd = text.filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0L }
    val dec = Currencies.of(t.fxSpendCur).decimals
    val real = if (twd != null) impliedRate(twd, t.fxSpendAmount, dec) else null
    val tries = rememberNeedTries()
    val need = firstNeed(if (twd == null) Need("twd", "請輸入銀行實際請款的台幣金額") else null)
    val nv = NeedView(need, tries.count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已請款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.fxSpendNote() ?: "", style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                OutlinedTextField(
                    text, { text = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = { Text("實際請款的台幣金額（含海外手續費）") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                    isError = nv.on("twd"), supportingText = nv.supporting("twd"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (real != null && twd != null) {
                    val diff = twd - t.amount
                    Text(
                        "實際匯率 ${rateText(real)}（預估是 ${t.fxSpendRate()?.let { rateText(it) } ?: "-"}），" +
                            if (diff == 0L) "和預估一樣" else "比預估${if (diff > 0) "多" else "少"} ${formatMoney(kotlin.math.abs(diff))}",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub,
                    )
                }
                Text("消費日期不變；如果銀行把它算進下一期帳單，請在修改畫面用「入帳」調整。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            }
        },
        confirmButton = { TextButton(onClick = { if (need != null) tries.count++ else onConfirm(twd ?: 0L) }) { Text("已請款") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 還沒請款的外幣消費清單（最久的在最上面），每筆可以直接填實際金額 */
@Composable
fun FxPendingScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val list = d.fxPendingList()
    var settle by remember { mutableStateOf<Txn?>(null) }
    val today = LocalDate.now()
    SubPage("待請款外幣", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("還沒請款的外幣消費", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Text("預估 ${formatMoney(list.sumOf { it.amount })}", style = MaterialTheme.typography.headlineMedium)
                    Text("${list.size} 筆。請款後填銀行實際的台幣金額，標記就會消失。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            }
            if (list.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "沒有待請款的外幣消費\n刷外幣時選「外幣」就會出現在這裡") }
            }
            items(list, key = { it.id }) { t ->
                val days = ChronoUnit.DAYS.between(t.date, today).coerceAtLeast(0L)
                val acc = t.accountId?.let { d.accMap[it] }
                val what = listOf(catPath(d, t), t.note.trim().lineSequence().firstOrNull()?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString("・")
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(what, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(t.fxSpendNote() ?: "", style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1)
                            Text(
                                "${t.date.monthValue}/${t.date.dayOfMonth} 刷" + (acc?.let { "・${it.name}" } ?: "") + "・" + (if (days >= 1L) "已經 $days 天" else "今天"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (days > 30L) cute.expense else cute.sub,
                                fontWeight = if (days > 30L) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("約 ${formatMoney(t.amount)}", fontWeight = FontWeight.SemiBold, color = cute.expense)
                            Spacer(Modifier.height(4.dp))
                            SoftButton("已請款", { settle = t }, compact = true)
                        }
                    }
                }
            }
        }
    }
    settle?.let { t ->
        SettleFxDialog(t, onConfirm = { twd -> if (vm.settleFx(t.id, twd)) settle = null }, onDismiss = { settle = null })
    }
}
