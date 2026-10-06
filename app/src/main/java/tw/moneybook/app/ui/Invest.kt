package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tw.moneybook.app.Currencies
import tw.moneybook.app.avgCost
import tw.moneybook.app.formatFx
import tw.moneybook.app.rateOf
import tw.moneybook.app.rateText
import kotlin.math.roundToLong
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.isForeign
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Markets
import tw.moneybook.app.Position
import tw.moneybook.app.Trade
import tw.moneybook.app.formatMoney
import tw.moneybook.app.portfolio
import tw.moneybook.app.priceText
import tw.moneybook.app.qtyText
import tw.moneybook.app.tradeAmount
import java.time.LocalDate

/** 損益文字：+1,234（+5.6%） */
private fun gainText(gain: Long, pct: Double?): String {
    val sign = if (gain > 0) "+" else if (gain < 0) "−" else ""
    val money = formatMoney(kotlin.math.abs(gain)).replace("-", "")
    val p = if (pct != null) "（$sign${String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(pct) * 100)}%）" else ""
    return "$sign$money$p"
}

/** 投資帳戶的持股區：總覽、持股列表、買進／賣出、手動更新現價、同步市值到帳戶餘額、買賣記錄 */
@Composable
fun InvestSection(vm: MoneyViewModel, a: Account) {
    val d = vm.data
    val cute = LocalCute.current
    val pf = remember(d) { d.portfolio(a.id) }
    val balance = remember(d) { d.balances()[a.id] ?: 0L }
    val trades = remember(d) { d.trades.filter { it.accountId == a.id }.sortedWith(compareByDescending<Trade> { it.day }.thenByDescending { it.id }) }
    // 對話框：trade＝買進／賣出（tradeBuy 決定預設），price＝改現價，sync＝同步市值
    var dialog by remember { mutableStateOf("") }
    var tradeBuy by remember { mutableStateOf(true) }
    var tradeSymbol by remember { mutableStateOf("") }
    var pricePos by remember { mutableStateOf<Position?>(null) }

    // 已同意上網抓價、而且超過 3 天沒更新，打開這一頁時自動抓一次（每個月就會有幾次價格當代表）
    LaunchedEffect(a.id) {
        val today = LocalDate.now().toEpochDay()
        if (d.prefs.priceFetch && pf.positions.isNotEmpty() && today - d.prefs.priceFetchDay >= 3L) vm.refreshPrices(silent = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CuteCard(Modifier.fillMaxWidth()) {
            Text("持股市值", style = MaterialTheme.typography.labelMedium, color = cute.sub)
            Text(formatMoney(pf.value), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("成本", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(formatMoney(pf.cost), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("未實現損益", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(
                        gainText(pf.gain, if (pf.cost > 0L) pf.gain.toDouble() / pf.cost else null),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pf.gain >= 0L) cute.income else cute.expense,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("已實現損益", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(
                        gainText(pf.realized, null),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pf.realized >= 0L) cute.income else cute.expense,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CuteChip("買進", false, { tradeBuy = true; tradeSymbol = ""; dialog = "trade" }, icon = "vec:coin")
                CuteChip("賣出", false, { tradeBuy = false; tradeSymbol = pf.positions.firstOrNull()?.symbol ?: ""; dialog = "trade" })
                if (pf.positions.isNotEmpty()) {
                    CuteChip(
                        if (vm.fetching) "抓價中…" else "抓最新價格", false,
                        {
                            when {
                                vm.fetching -> {}
                                d.prefs.priceFetch -> { vm.refreshPrices() }
                                else -> { dialog = "fetchAsk" }
                            }
                        },
                    )
                    CuteChip("同步市值到餘額", false, { dialog = "sync" })
                }
            }
        }

        if (pf.positions.isEmpty()) {
            Text("還沒有持股，按「買進」記錄第一筆。", style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.padding(horizontal = 4.dp))
        }
        pf.positions.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card)
                    .clickable { pricePos = p; dialog = "price" }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (p.name.isNotBlank()) "${p.name} ${p.symbol}" else p.symbol,
                            style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (p.market.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                Markets.label(p.market), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(CircleShape).background(cute.soft).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        "${qtyText(p.qty)} 股・均價 ${unitPrice(p.avgPrice, p.currency)}・現價 ${unitPrice(p.price, p.currency)}（${dayLabel(p.priceDay)}）",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 2,
                    )
                    if (p.currency.isNotEmpty()) {
                        Text("單價是 ${p.currency}，市值、成本、損益換成台幣算（匯率 ${rateText(p.rate)}）", style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 2)
                    }
                    if (p.symbol in vm.priceFailed) {
                        Text("抓不到價格，目前用的是舊價格；請確認市場與代號，或點這一列手動輸入", style = MaterialTheme.typography.labelSmall, color = cute.expense)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(p.value), fontWeight = FontWeight.SemiBold)
                    Text(
                        gainText(p.gain, if (p.cost > 0L) p.gainPct else null),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (p.gain >= 0L) cute.income else cute.expense,
                    )
                }
            }
        }

        if (pf.positions.isNotEmpty() && d.prefs.priceFetch) {
            Text(
                "價格來自 Yahoo Finance，只會送出代號。點這裡關閉網路抓價。",
                style = MaterialTheme.typography.labelSmall, color = cute.sub,
                modifier = Modifier.clickable { vm.setPriceFetch(false); vm.toast("已關閉網路抓價") }.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        if (trades.isNotEmpty()) {
            Text("買賣記錄", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            trades.take(30).forEach { t ->
                SwipeRow(onDelete = { vm.deleteTrade(t.id) }) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (t.buy) "買" else "賣", style = MaterialTheme.typography.labelMedium,
                            color = if (t.buy) cute.expense else cute.income,
                            modifier = Modifier.clip(CircleShape).background((if (t.buy) cute.expense else cute.income).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (t.name.isNotBlank()) "${t.name} ${t.symbol}" else t.symbol, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                "${dayLabel(t.day)}・${qtyText(t.qty)} 股 @ ${unitPrice(t.price, t.currency)}" +
                                    (if (t.currency.isNotEmpty() && t.rate > 0.0) "・匯率 ${rateText(t.rate)}" else "") +
                                    if (t.fee > 0) "・手續費 ${formatMoney(t.fee)}" else "",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1,
                            )
                        }
                        Text(formatMoney(tradeAmount(t.qty, t.price * (if (t.currency.isNotEmpty() && t.rate > 0.0) t.rate else 1.0))), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (trades.size > 30) Text("只顯示最近 30 筆", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(start = 4.dp))
        }
    }

    vm.fetchReport?.let { rep ->
        val failed = rep.filter { !it.ok }
        val okList = rep.filter { it.ok }
        AlertDialog(
            onDismissRequest = { vm.fetchReport = null },
            title = { Text("抓價結果") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (failed.isNotEmpty()) {
                        Text("沒抓到（${failed.size} 檔）", style = MaterialTheme.typography.titleSmall, color = cute.expense)
                        failed.forEach { r ->
                            Text(
                                (if (r.name.isNotBlank()) "${r.name} ${r.symbol}" else r.symbol) + if (r.market.isNotBlank()) "（${Markets.label(r.market)}）" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "請確認市場和代號對不對（例如韓股要選「韓股」、日股代號不用加 .T），或點持股那一列手動輸入現價。也可能是網路不通。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                    }
                    if (okList.isNotEmpty()) {
                        Text("已更新（${okList.size} 檔）", style = MaterialTheme.typography.titleSmall)
                        Text("下面是各檔最後一筆成交價（休市時是上一個交易日的收盤價），已經套用到持股的現價。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        okList.forEach { r ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    if (r.name.isNotBlank()) "${r.name} ${r.symbol}" else r.symbol,
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                )
                                Text(unitPrice(r.price ?: 0.0, r.currency), style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.fetchReport = null }) { Text("關閉") } },
        )
    }

    when (dialog) {
        "trade" -> TradeDialog(
            vm = vm, a = a, held = pf.positions, initialBuy = tradeBuy, initialSymbol = tradeSymbol,
            onDismiss = { dialog = "" },
        )
        "price" -> pricePos?.let { p ->
            PriceDialog(
                p = p,
                onConfirm = { price -> vm.setPrice(p.symbol, price, currency = p.currency); dialog = "" },
                onDismiss = { dialog = "" },
            )
        }
        "fetchAsk" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("上網抓最新價格？") },
            text = {
                Text(
                    "記帳本平常完全不連網。開啟後，只有按「抓最新價格」，或打開這一頁而且超過 3 天沒更新時，" +
                        "才會把持股的代號傳給 Yahoo Finance 查價格，不會傳送任何記帳資料。\n" +
                        "每個月會留下幾次價格當作當月的代表。之後可以隨時在持股頁下方關閉。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { vm.setPriceFetch(true); dialog = ""; vm.refreshPrices() }) { Text("開啟並抓價") } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("不要") } },
        )
        "sync" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("同步市值到餘額") },
            text = {
                Text(
                    "持股市值 ${formatMoney(pf.value)}，帳戶目前餘額 ${formatMoney(balance)}。\n" +
                        "更新後帳戶餘額會變成 ${formatMoney(pf.value)}，差額記成「餘額調整」，不算收入或支出。\n" +
                        "（如果這個帳戶裡還放著現金，請改用「更新餘額」自己輸入。）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { vm.adjustBalance(a.id, pf.value); dialog = "" }) { Text("更新") } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
        )
    }
}

/** 單價文字：外幣前面加幣別符號（US$500.00），台幣維持原樣（120.50） */
private fun unitPrice(price: Double, currency: String): String =
    if (currency.isEmpty()) priceText(price) else Currencies.of(currency).symbol + priceText(price)

/** 手動改某檔的現價（記成今天的價格） */
@Composable
private fun PriceDialog(p: Position, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(priceText(p.price).replace(",", "")) }
    val price = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新現價") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.name.isNotBlank()) "${p.name} ${p.symbol}" else p.symbol, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text(if (p.currency.isNotEmpty()) "今天的價格（${p.currency}）" else "今天的價格") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = price != null && price > 0.0, onClick = { price?.let(onConfirm) }) { Text("更新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 買進或賣出：可以同時記一筆從／到哪個帳戶的轉帳 */
@Composable
private fun TradeDialog(
    vm: MoneyViewModel,
    a: Account,
    held: List<Position>,
    initialBuy: Boolean,
    initialSymbol: String,
    onDismiss: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    var buy by remember { mutableStateOf(initialBuy) }
    var symbol by remember { mutableStateOf(initialSymbol) }
    // 賣出時沿用持股的市場；新買進預設台股
    var market by remember { mutableStateOf(held.firstOrNull { it.symbol == initialSymbol }?.market?.ifBlank { null } ?: "TW") }
    var name by remember { mutableStateOf(held.firstOrNull { it.symbol == initialSymbol }?.name ?: "") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var datePick by remember { mutableStateOf(false) }
    // 單價用哪個幣別記：美股是美金、日股是日圓…，台股是台幣（空白）
    val cur = Markets.currencyOf(market)
    // 可以付款的帳戶：台幣帳戶，加上「幣別跟這檔一樣」的外幣帳戶（例如第一證券的美金）
    val cashAccs = d.visibleAccounts.filter { it.id != a.id && it.type != AccountType.INVEST && (!it.isForeign || (cur.isNotEmpty() && it.currency == cur)) }
    // null＝不連動，只記買賣
    var cash by remember { mutableStateOf(cashAccs.firstOrNull { !it.isForeign }?.id ?: cashAccs.firstOrNull()?.id) }
    // 換了市場，原本選的外幣帳戶幣別對不上就改回台幣帳戶
    LaunchedEffect(cur) {
        val c = cash?.let { d.accMap[it] }
        if (c != null && c.isForeign && c.currency != cur) cash = cashAccs.firstOrNull { !it.isForeign }?.id
    }
    val cashAcc = cash?.let { d.accMap[it] }
    val cashFx = cur.isNotEmpty() && cashAcc != null && cashAcc.isForeign && cashAcc.currency == cur
    // 匯率：用外幣帳戶付款預設用那個帳戶的平均買進成本（換美金時實際花的台幣），複委託預設用目前匯率；可以自己改
    var rateInput by remember { mutableStateOf("") }
    var rateTouched by remember { mutableStateOf(false) }
    LaunchedEffect(cur, cash) {
        if (!rateTouched) {
            val r = if (cur.isEmpty()) null else (if (cashFx) d.avgCost(cashAcc!!) else null) ?: d.rateOf(cur)
            rateInput = r?.let { rateText(it) } ?: ""
        }
    }

    val sym = symbol.trim().uppercase()
    val q = qty.toDoubleOrNull() ?: 0.0
    val pr = price.toDoubleOrNull() ?: 0.0
    val rt = if (cur.isEmpty()) 1.0 else rateInput.toDoubleOrNull() ?: 0.0
    // 手續費：用外幣帳戶付款／收款時用外幣記，其他是台幣
    val feNative = if (cashFx) fee.toDoubleOrNull() ?: 0.0 else 0.0
    val fe = if (cashFx) 0L else fee.toLongOrNull() ?: 0L
    val feeTwdShown = if (cashFx) (feNative * rt).roundToLong() else fe
    val holdQty = held.firstOrNull { it.symbol == sym }?.qty ?: 0.0
    val oversell = !buy && q > holdQty + 1e-9
    val ok = sym.isNotEmpty() && q > 0.0 && pr > 0.0 && !oversell && rt > 0.0
    val amountNative = q * pr
    val amount = tradeAmount(q, pr * rt)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (buy) "買進" else "賣出") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip("買進", buy, { buy = true })
                    CuteChip("賣出", !buy, { buy = false })
                    CuteChip(dayLabel(day), false, { datePick = true }, icon = "vec:calendar")
                }
                if (!buy && held.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        held.forEach { p ->
                            CuteChip(p.symbol, sym == p.symbol, { symbol = p.symbol; name = p.name; if (p.market.isNotBlank()) market = p.market })
                        }
                    }
                }
                Text("市場（決定怎麼抓價，選錯會抓不到）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Markets.all.forEach { (code, label) -> CuteChip(label, market == code, { market = code }) }
                }
                OutlinedTextField(
                    symbol, { symbol = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' }.take(12) },
                    label = { Text("代號（${Markets.example(market)}）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    name, { name = it.take(16) }, label = { Text("名稱（選填，自己看得懂就好，例如 元大50）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        qty, { qty = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text("股數") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        price, { price = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text(if (cur.isNotEmpty()) "單價（$cur）" else "單價") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                if (cur.isNotEmpty()) {
                    OutlinedTextField(
                        rateInput, { rateInput = it.filter { c -> c.isDigit() || c == '.' }.take(10); rateTouched = true },
                        label = { Text("匯率（1 $cur = 幾元台幣）") }, singleLine = true,
                        supportingText = { Text(if (cashFx) "預設是「${cashAcc?.name}」的平均買進成本，可以自己改" else "複委託：預設是目前匯率，可以改成券商實際換的匯率") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    fee, { fee = if (cashFx) it.filter { c -> c.isDigit() || c == '.' }.take(10) else it.filter { c -> c.isDigit() }.take(9) },
                    label = {
                        val what = if (buy) "手續費（選填" else "手續費與證交稅（選填"
                        Text(what + (if (cashFx) "，$cur）" else if (cur.isNotEmpty()) "，台幣）" else "）"))
                    }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (cashFx) KeyboardType.Decimal else KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Text(if (buy) "從哪個帳戶付款" else "賣出的錢轉到哪個帳戶", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cashAccs.forEach { c -> CuteChip(c.name + if (c.isForeign) "（${c.currency}）" else "", cash == c.id, { cash = c.id; rateTouched = false }) }
                    CuteChip("不連動", cash == null, { cash = null; rateTouched = false })
                }
                Text(
                    when {
                        oversell -> "賣出的股數比持有的多（目前持有 ${qtyText(holdQty)}）"
                        cur.isNotEmpty() && rt <= 0.0 -> "請輸入匯率（1 $cur = 幾元台幣）"
                        q > 0.0 && pr > 0.0 && cur.isNotEmpty() ->
                            "金額 ${formatFx(Math.round(amountNative * Math.pow(10.0, Currencies.of(cur).decimals.toDouble())), cur)}（約 ${formatMoney(amount)}）" +
                                (if (feeTwdShown > 0) "＋手續費 ${formatMoney(feeTwdShown)}" else "") +
                                (when {
                                    cashFx -> "。會從「${cashAcc?.name}」${if (buy) "扣掉" else "收進"}美金（含手續費），不算收入或支出。"
                                    cash != null -> "。會同時記一筆台幣轉帳，不算收入或支出（手續費除外）。"
                                    else -> "。只記買賣，不動其他帳戶餘額。"
                                })
                        q > 0.0 && pr > 0.0 ->
                            "金額 ${formatMoney(amount)}" + (if (fe > 0) "＋手續費 ${formatMoney(fe)}" else "") +
                                (if (cash != null) "。會同時記一筆轉帳，不算收入或支出（手續費除外）。" else "。只記買賣，不動其他帳戶餘額。")
                        else -> "輸入股數和單價"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (oversell) cute.expense else cute.sub,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ok,
                onClick = {
                    vm.saveTrade(a.id, sym, name, day, buy, q, pr, fe, cash, market, cur, rt, feNative)
                    // 順便把這次的成交價當成現價，持股市值才不會是空的
                    vm.setPrice(sym, pr, day, cur)
                    onDismiss()
                },
            ) { Text("記錄") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (datePick) {
        CuteDatePickerDialog(initial = day, onPick = { day = it; datePick = false }, onDismiss = { datePick = false })
    }
}
