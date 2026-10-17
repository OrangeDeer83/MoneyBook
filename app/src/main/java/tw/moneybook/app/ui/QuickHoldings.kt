package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tw.moneybook.app.Account
import tw.moneybook.app.Currencies
import tw.moneybook.app.Markets
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.QuickHolding
import tw.moneybook.app.formatFx
import tw.moneybook.app.formatMoney
import tw.moneybook.app.rateOf
import tw.moneybook.app.rateText
import java.time.LocalDate
import kotlin.math.roundToLong

/** 快速加入庫存畫面裡的一列（一檔持股） */
private class QRow(val key: Int) {
    var code by mutableStateOf("")
    var qty by mutableStateOf("")
    var cost by mutableStateOf("")
    /** null＝依代號自動判斷，點市場標籤換過就是使用者選的 */
    var market by mutableStateOf<String?>(null)
    val codeFocus = FocusRequester()
    fun blank() = code.isBlank() && qty.isBlank() && cost.isBlank()
}

/**
 * 快速加入庫存：一次把現有的持股填進這個投資帳戶。一檔一列，鍵盤「下一個」依序跳代號→股數→成本→下一列，
 * 市場依代號自動判斷（點標籤可換），只記持股，不動任何帳戶餘額（見 withQuickHoldings）。
 */
@Composable
fun QuickHoldingsDialog(vm: MoneyViewModel, a: Account, onClose: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val focus = LocalFocusManager.current
    var nextKey by remember { mutableStateOf(1) }
    val rows = remember { mutableStateListOf(QRow(0)) }
    // 要把游標移到哪一列的代號欄（新增一列、或一打開畫面時）
    var focusKey by remember { mutableStateOf<Int?>(0) }
    var byTotal by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var datePick by remember { mutableStateOf(false) }
    // 外幣市場的匯率：使用者改過的字串，沒改過就用目前匯率
    val rateInputs = remember { mutableStateMapOf<String, String>() }
    val tries = rememberNeedTries()

    fun addRow() {
        val r = QRow(nextKey++)
        rows.add(r)
        focusKey = r.key
    }
    LaunchedEffect(focusKey) {
        val k = focusKey ?: return@LaunchedEffect
        // 新的一列還沒排好版時 requestFocus 會丟例外，忽略就好
        try { rows.firstOrNull { it.key == k }?.codeFocus?.requestFocus() } catch (_: IllegalStateException) {}
        focusKey = null
    }

    fun marketOf(r: QRow) = r.market ?: Markets.guess(r.code)
    fun rateStr(cur: String) = rateInputs[cur] ?: d.rateOf(cur)?.let { rateText(it) } ?: ""
    fun rateOfCur(cur: String) = rateStr(cur).toDoubleOrNull() ?: 0.0
    /** 這一列每股成本（單價幣別）；填的是總成本就除以股數 */
    fun avgOf(r: QRow): Double {
        val q = r.qty.toDoubleOrNull() ?: 0.0
        val c = r.cost.toDoubleOrNull() ?: 0.0
        return if (q <= 0.0) 0.0 else if (byTotal) c / q else c
    }

    val filled = rows.filter { !it.blank() }
    val curs = filled.map { Markets.currencyOf(marketOf(it)) }.filter { it.isNotEmpty() }.distinct()
    val needs = ArrayList<Need?>()
    if (filled.isEmpty()) needs.add(Need("code${rows.first().key}", "請至少填一檔持股"))
    for (r in filled) {
        needs.add(if (r.code.trim().isEmpty()) Need("code${r.key}", "請輸入代號") else null)
        needs.add(if ((r.qty.toDoubleOrNull() ?: 0.0) <= 0.0) Need("qty${r.key}", "請輸入股數") else null)
        needs.add(if ((r.cost.toDoubleOrNull() ?: 0.0) <= 0.0) Need("cost${r.key}", if (byTotal) "請輸入總成本" else "請輸入每股成本") else null)
    }
    for (c in curs) needs.add(if (rateOfCur(c) <= 0.0) Need("rate$c", "請輸入匯率（1 $c = 幾元台幣）") else null)
    val need = needs.firstOrNull { it != null }
    val nv = NeedView(need, tries.count)

    // 成本合計：台幣加上各外幣（外幣另外列出）
    var twdTotal = 0L
    val fxTotals = LinkedHashMap<String, Double>()
    for (r in filled) {
        val q = r.qty.toDoubleOrNull() ?: 0.0
        val total = if (byTotal) r.cost.toDoubleOrNull() ?: 0.0 else q * (r.cost.toDoubleOrNull() ?: 0.0)
        val cur = Markets.currencyOf(marketOf(r))
        if (cur.isEmpty()) twdTotal += total.roundToLong() else fxTotals[cur] = (fxTotals[cur] ?: 0.0) + total
    }

    fun submit() {
        if (need != null) { tries.count++; return }
        val items = filled.map { r -> QuickHolding(r.code.trim().uppercase(), r.qty.toDouble(), avgOf(r), marketOf(r)) }
        if (vm.addQuickHoldings(a.id, day, items, curs.associateWith { rateOfCur(it) })) onClose()
    }

    FullScreenDialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(AppIcons.ChevronLeft, contentDescription = "返回") }
                Text("快速加入庫存", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { submit() }) { Text("全部加入" + if (filled.isNotEmpty()) "（${filled.size} 檔）" else "") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("把現在已經持有的股票填進來，只記持股，不會動到任何帳戶餘額。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("成本填", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    CuteChip("每股均價", !byTotal, { byTotal = false })
                    CuteChip("總成本", byTotal, { byTotal = true })
                    Spacer(Modifier.weight(1f))
                    CuteChip(dayLabel(day), false, { datePick = true }, icon = "vec:calendar")
                }
                if (curs.isNotEmpty()) {
                    curs.forEach { c ->
                        OutlinedTextField(
                            rateStr(c), { rateInputs[c] = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) },
                            label = { Text("匯率（1 $c = 幾元台幣）") }, singleLine = true,
                            isError = nv.on("rate$c"), supportingText = nv.supporting("rate$c") ?: ({ Text("預設是目前匯率，可以改成券商實際換的匯率") }),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().needInView(nv, "rate$c"),
                        )
                    }
                }
                rows.forEachIndexed { i, r ->
                    val isLast = i == rows.lastIndex
                    val market = marketOf(r)
                    val cur = Markets.currencyOf(market)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cute.card).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                r.code, { r.code = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == '-' }.uppercase().take(12) },
                                label = { Text("代號") }, singleLine = true,
                                isError = nv.on("code${r.key}"), supportingText = nv.supporting("code${r.key}"),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                                modifier = Modifier.weight(1.1f).focusRequester(r.codeFocus).needInView(nv, "code${r.key}"),
                            )
                            OutlinedTextField(
                                r.qty, { r.qty = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                                label = { Text("股數") }, singleLine = true,
                                isError = nv.on("qty${r.key}"), supportingText = nv.supporting("qty${r.key}"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                                modifier = Modifier.weight(1f).needInView(nv, "qty${r.key}"),
                            )
                            OutlinedTextField(
                                r.cost, { r.cost = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                                label = { Text(if (byTotal) "總成本" else "每股均價") }, singleLine = true,
                                isError = nv.on("cost${r.key}"), supportingText = nv.supporting("cost${r.key}"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                // 最後一列填好成本再按「下一個」：自動多一列、游標跳到它的代號
                                keyboardActions = KeyboardActions(onNext = { if (isLast && !r.blank()) addRow() else focus.moveFocus(FocusDirection.Next) }),
                                modifier = Modifier.weight(1f).needInView(nv, "cost${r.key}"),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 市場標籤：依代號自動判斷，點一下換下一個市場
                            Text(
                                Markets.label(market) + if (r.market != null) "" else "・自動",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(CircleShape).background(cute.soft)
                                    .clickable {
                                        val all = Markets.all.map { it.first }
                                        r.market = all[(all.indexOf(market) + 1) % all.size]
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            val q = r.qty.toDoubleOrNull() ?: 0.0
                            val c = r.cost.toDoubleOrNull() ?: 0.0
                            val total = if (byTotal) c else q * c
                            Text(
                                when {
                                    q <= 0.0 || c <= 0.0 -> "點左邊的標籤可以換市場"
                                    cur.isEmpty() -> "成本 ${formatMoney(total.roundToLong())}"
                                    else -> {
                                        val dec = Currencies.of(cur).decimals
                                        val rt = rateOfCur(cur)
                                        "成本 ${formatFx(Math.round(total * Math.pow(10.0, dec.toDouble())), cur)}" + if (rt > 0.0) "（約 ${formatMoney((total * rt).roundToLong())}）" else ""
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.weight(1f),
                            )
                            if (rows.size > 1) {
                                Text(
                                    "刪除", style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                    modifier = Modifier.clip(CircleShape).clickable { rows.remove(r) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "＋ 再加一檔", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft).clickable { addRow() }.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (filled.isNotEmpty()) {
                    Text(
                        "已填 ${filled.size} 檔　成本合計 " +
                            (listOfNotNull(if (twdTotal > 0L || fxTotals.isEmpty()) formatMoney(twdTotal) else null) +
                                fxTotals.map { (c, v) -> formatFx(Math.round(v * Math.pow(10.0, Currencies.of(c).decimals.toDouble())), c) }).joinToString("＋"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "每一檔會記成一筆買進（不連動帳戶、手續費 0），之後再按「買進」平均成本會接著算。" +
                        if (vm.data.prefs.priceFetch) "加入後會自動抓一次現價。" else "沒有開網路抓價時，現價先用成本價，可以點持股那一列手動改。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (datePick) {
        CuteDatePickerDialog(initial = day, onPick = { day = it; datePick = false }, onDismiss = { datePick = false })
    }
}
