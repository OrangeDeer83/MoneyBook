package tw.moneybook.app.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import tw.moneybook.app.filterSuggestions
import tw.moneybook.app.settleOf
import tw.moneybook.app.debtNames
import androidx.compose.foundation.layout.heightIn
import tw.moneybook.app.mergeSameNames
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.ReimbCodec
import tw.moneybook.app.ReimbItem
import tw.moneybook.app.ReimbPay
import tw.moneybook.app.ClaimInput
import tw.moneybook.app.ClaimOutcome
import tw.moneybook.app.ReceiptLine
import tw.moneybook.app.allocateReceipt
import tw.moneybook.app.Account
import tw.moneybook.app.SplitResult
import tw.moneybook.app.splitAmounts
import tw.moneybook.app.cur
import tw.moneybook.app.formatFx
import tw.moneybook.app.Currencies
import tw.moneybook.app.scaleRound
import tw.moneybook.app.fxAccountOf
import tw.moneybook.app.fxExpr
import tw.moneybook.app.fxToTwdAt
import tw.moneybook.app.isForeign
import tw.moneybook.app.parseFx
import tw.moneybook.app.twdToFxAt
import tw.moneybook.app.ReimbReceipt
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.AccountType
import tw.moneybook.app.DebtGroup
import tw.moneybook.app.DebtRef
import tw.moneybook.app.debtCurrency
import tw.moneybook.app.debtGroups
import tw.moneybook.app.DebtRepayPart
import tw.moneybook.app.offsetDebtTwd
import tw.moneybook.app.offsetFxTwd
import tw.moneybook.app.offsetFxDefaultUnits
import tw.moneybook.app.fmt
import tw.moneybook.app.fxPlain
import tw.moneybook.app.isDebt
import tw.moneybook.app.remainingUnits
import tw.moneybook.app.totalUnits
import tw.moneybook.app.DebtItem
import tw.moneybook.app.DebtCodec
import tw.moneybook.app.effective
import tw.moneybook.app.remainingTwd
import tw.moneybook.app.formatMoney
import tw.moneybook.app.pendingReimb
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// ───────────────────────── 共用小元件 ─────────────────────────

/** 精簡的輸入框：淡淡的底色，用灰色提示字代替標籤，不會把文字擠成兩行 */
@Composable
internal fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    prefix: String = "",
    decimal: Boolean = false,
    /** 必填沒填好：亮紅框 */
    error: Boolean = false,
    /** 游標進來／離開（名字欄要知道正在打哪一欄，下面才放建議） */
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val cute = LocalCute.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (number) KeyboardType.Number else KeyboardType.Text),
        // 數字欄位（金額、外幣、匯率）打字時顯示千分位
        visualTransformation = if (number || decimal) ThousandsVisual else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = if (onFocus != null) modifier.onFocusChanged { onFocus(it.isFocused) } else modifier,
        decorationBox = { inner ->
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(cute.soft)
                    .then(if (error) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix.isNotEmpty()) {
                    Text(prefix, color = cute.sub, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(4.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = cute.sub.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

private fun ownerLabel(who: String): String = if (who.isBlank()) "沒填對象" else who

/** 某筆帳的某個報銷對象（一項待收或已收的款） */
private class Claim(val txn: Txn, val index: Int, val item: ReimbItem) {
    val key: String get() = "${txn.id}:$index"
    val who: String get() = item.who.trim()
}

private fun claimsOf(txns: List<Txn>): List<Claim> =
    txns.filter { it.type == TxType.EXPENSE && it.reimb != 0 }
        .flatMap { t -> t.items.mapIndexed { i, item -> Claim(t, i, item) } }

/** 分類的完整路徑：子分類前面帶上大分類（餐飲 › 午餐），沒分類是「未分類」 */
fun catPath(d: AppData, t: Txn): String {
    val c = t.categoryId?.let { d.catMap[it] } ?: return "未分類"
    val top = d.topOf(c)
    return if (top.id == c.id) c.name else "${top.name} › ${c.name}"
}

/** 備註的第一行（太長就截斷）；沒有備註回傳空字串 */
private fun billNote(t: Txn): String {
    val line = t.note.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    return if (line.length > 24) line.take(24) + "…" else line
}

/** 這筆是什麼：有備註就用備註（最能看出實際的項目），沒有就用分類路徑，後面接日期 */
private fun billLabel(d: AppData, t: Txn): String {
    val date = LocalDate.ofEpochDay(t.day)
    val md = if (date.year == LocalDate.now().year) "${date.monthValue}/${date.dayOfMonth}" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"
    return billNote(t).ifEmpty { catPath(d, t) } + " " + md
}

private fun daysSince(day: Long): Long = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(day), LocalDate.now()).coerceAtLeast(0L)

// ───────────────────────── 記一筆：這筆的報銷／欠款（整頁） ─────────────────────────

/** 這一列可能是還能編輯的對象，也可能是已有收款（報銷）或還款（欠款）、只能看的（locked／lockedDebt） */
internal class ReimbRow(val locked: ReimbItem?, who: String, amt: String, val lockedDebt: DebtItem? = null) {
    var who by mutableStateOf(who)
    var amt by mutableStateOf(amt)

    /** 「平分剩下的」模式下，這一列的金額由系統自動算（使用者手動改過就變固定） */
    var auto by mutableStateOf(false)

    /** 使用者自己改過金額（包括清空）：填名字時就不再自動填滿 */
    var amtTouched by mutableStateOf(false)

    val isLocked: Boolean get() = locked != null || lockedDebt != null
    val lockedEffective: Long get() = locked?.effective ?: lockedDebt?.effective ?: 0L
}

/**
 * 這筆的報銷／欠款：上面是這筆的分配摘要（我付、別人先付、可報銷、自己負擔），下面切換兩份名單，各自保留。
 * 報銷＝別人要還我（我先付了）；欠款＝別人先幫我付、我要還他（那部分先不從帳戶扣）。有人、有金額就算，不用另外開開關。
 * debtAllowed＝false 時（外幣帳戶的消費、分期）欠款那邊不能填。
 */
@Composable
fun ReimbEditPage(
    title: String,
    actual: Long,
    cap: Long,
    /** 外幣消費：幣別與這筆的外幣金額（最小單位）；每個人的金額直接用外幣輸入，台幣用這筆的匯率換算。台幣消費不用填 */
    fxCur: String = "",
    fxActual: Long = 0L,
    origItems: List<ReimbItem>,
    initFull: Boolean,
    initJson: String,
    initWho: String,
    names: List<String>,
    /** 欠款：目前的名單（JSON 文字，見 DebtCodec.encode）、以前欠過的人、能不能記欠款、一開始顯示哪一邊 */
    initDebtJson: String = "",
    debtNames: List<String> = emptyList(),
    debtAllowed: Boolean = true,
    debtNotAllowedWhy: String = "",
    startOnDebt: Boolean = false,
    onDone: (on: Boolean, full: Boolean, json: String, who: String, debtJson: String) -> Unit,
    onClose: () -> Unit,
) {
    val cute = LocalCute.current
    // 外幣消費：每個人的金額用外幣輸入（r.amt 是外幣），換算台幣用「這一筆」的匯率（外幣金額 ÷ 台幣實付）
    val fxOn = fxCur.isNotEmpty() && fxActual > 0L && actual > 0L
    val fdec = Currencies.of(fxCur).decimals
    fun fxOf(twd: Long): Long = if (twd == actual) fxActual else scaleRound(twd, fxActual, actual)
    fun twdOf(fx: Long): Long = if (fx == fxActual) actual else scaleRound(fx, actual, fxActual)
    fun rowTwd(r: ReimbRow): Long = if (fxOn) twdOf(parseFx(r.amt, fdec) ?: 0L) else (r.amt.toLongOrNull() ?: 0L)
    fun amtText(twd: Long): String = if (fxOn) fxExpr(fxOf(twd), fdec) else twd.toString()
    // 報銷名單
    val rows = remember {
        mutableStateListOf<ReimbRow>().apply {
            val src = if (initFull) origItems.map { if (it.pays.isEmpty() && !it.closed) it.copy(amount = actual) else it } else ReimbCodec.decode(initJson)
            src.forEach { add(if (it.pays.isNotEmpty() || it.closed) ReimbRow(it, it.who, it.amount.toString()) else ReimbRow(null, it.who, amtText(it.amount))) }
            // 還沒有報銷：先放一列空的，填了名字和金額才算
            if (isEmpty()) add(ReimbRow(null, initWho, ""))
        }
    }
    // 欠款名單
    val drows = remember {
        mutableStateListOf<ReimbRow>().apply {
            DebtCodec.decode(initDebtJson).forEach {
                add(if (it.pays.isNotEmpty() || it.done) ReimbRow(null, it.who, it.amount.toString(), lockedDebt = it) else ReimbRow(null, it.who, amtText(it.amount)))
            }
            if (isEmpty()) add(ReimbRow(null, "", ""))
        }
    }
    var tab by remember { mutableStateOf(if (startOnDebt && debtAllowed) 1 else 0) }
    val cur = if (tab == 0) rows else drows
    val reimbTotal = rows.sumOf { if (it.isLocked) it.lockedEffective else rowTwd(it) }
    val debtTotal = drows.sumOf { if (it.isLocked) it.lockedDebt!!.amount else rowTwd(it) }
    val reimbCount = rows.count { it.isLocked || rowTwd(it) > 0L }
    val debtCount = drows.count { it.isLocked || rowTwd(it) > 0L }
    var err by remember { mutableStateOf("") }

    fun finish() {
        // 欠款：有金額就要有名字；合計不能超過實付
        // 還沒輸入帳目金額時，只有一個有名字的人＝整筆（金額之後跟著帳目走）
        fun pending1(list: List<ReimbRow>) = actual <= 0L && list.count { !it.isLocked } == 1 && list.any { !it.isLocked && it.who.isNotBlank() && rowTwd(it) <= 0L }
        val dl = drows.filter { it.isLocked || rowTwd(it) > 0L || (pending1(drows) && it.who.isNotBlank()) }
        if (debtAllowed && dl.any { !it.isLocked && it.who.isBlank() }) { tab = 1; err = "欠款要填欠誰"; return }
        if (debtAllowed && actual > 0L && debtTotal > actual) { tab = 1; err = "欠款合計 ${formatMoney(debtTotal)} 超過實付 ${formatMoney(actual)}"; return }
        // 名字一樣的幾欄合成一個人、金額加總（已經有收款／還款的那欄不能改，不合併）；外幣消費用外幣加總再換台幣
        fun units(r: ReimbRow): Long = if (fxOn) parseFx(r.amt, fdec) ?: 0L else r.amt.toLongOrNull() ?: 0L
        fun merged(list: List<ReimbRow>): List<Pair<String, Long>> =
            mergeSameNames(list.filter { !it.isLocked }.map { it.who to units(it) }).map { (w, u) -> w to (if (fxOn) twdOf(u) else u) }
        val debtList = if (!debtAllowed) emptyList() else
            dl.mapNotNull { it.lockedDebt } + merged(dl).map { (w, v) -> DebtItem(w, v) }
        val dj = DebtCodec.encode(debtList)
        val rl = rows.filter { it.isLocked || rowTwd(it) > 0L }
        val rLocked = rl.mapNotNull { it.locked }
        val rFree = merged(rl)
        when {
            rl.isEmpty() && pending1(rows) -> onDone(true, true, initJson, rows.first { !it.isLocked && it.who.isNotBlank() }.who.trim(), dj)
            rl.isEmpty() -> onDone(false, true, "", "", dj)
            // 只有一個人、而且是全額：存成「一人・全額」，之後帳目金額改了，報銷金額跟著走
            rLocked.isEmpty() && rFree.size == 1 && rFree[0].second == actual -> onDone(true, true, initJson, rFree[0].first, dj)
            else -> onDone(true, false, ReimbCodec.encode(rLocked + rFree.map { (w, v) -> ReimbItem(w, v) }), "", dj)
        }
    }

    fun addName(name: String) {
        val blank = cur.firstOrNull { !it.isLocked && it.who.isBlank() }
        if (blank != null) blank.who = name else cur.add(ReimbRow(null, name, ""))
        // 只有一個人時金額預設填滿（報銷：實付；欠款：還沒分出去的）
        val editable = cur.filter { !it.isLocked }
        if (editable.size == 1 && rowTwd(editable[0]) <= 0L) {
            val pool = (actual - cur.sumOf { if (it.isLocked) it.lockedEffective else 0L }).coerceAtLeast(0L)
            if (pool > 0L) editable[0].amt = amtText(pool)
        }
    }

    var splitMenu by remember { mutableStateOf(false) }
    // 「平分剩下的」模式：沒填金額的人由系統自動分剩下的（含自己），手動改過金額的人就固定；只用在目前這一邊
    var restMode by remember { mutableStateOf(false) }
    // 正在打名字的是第幾欄（-1 是沒有），那一欄下面才放名字建議
    var nameFocus by remember { mutableStateOf(-1) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(tab) { restMode = false }
    val lockedSum = cur.sumOf { if (it.isLocked) it.lockedEffective else 0L }

    /** 把 poolTwd 分給 targets 這幾列；外幣消費時用外幣金額分、小數位照幣別 */
    fun fillRows(targets: List<ReimbRow>, includeSelf: Boolean, poolTwd: Long) {
        if (fxOn) {
            val res = splitAmounts(includeSelf, fxOf(poolTwd), targets.size)
            targets.forEachIndexed { i, r -> r.amt = fxExpr(res.amounts[i], fdec) }
        } else {
            val res = splitAmounts(includeSelf, poolTwd, targets.size)
            targets.forEachIndexed { i, r -> r.amt = res.amounts[i].toString() }
        }
    }

    fun splitAll(includeSelf: Boolean) {
        val editable = cur.filter { !it.isLocked }
        if (editable.isEmpty()) return
        restMode = false
        editable.forEach { it.auto = false }
        fillRows(editable, includeSelf, (actual - lockedSum).coerceAtLeast(0L))
    }

    fun startRest() {
        cur.filter { !it.isLocked }.forEach { it.auto = rowTwd(it) <= 0L }
        restMode = true
    }

    // 平分剩下的：固定金額的人不動，自動那幾列每次都依「剩下的」重算
    val restAuto = if (restMode) cur.filter { !it.isLocked && it.auto } else emptyList()
    val restFixed = cur.filter { !it.isLocked && !it.auto }.sumOf { rowTwd(it) }
    val restPool = (actual - lockedSum - restFixed).coerceAtLeast(0L)
    LaunchedEffect(restMode, restAuto.size, restPool) {
        if (restMode) {
            if (restAuto.isEmpty()) restMode = false else fillRows(restAuto, true, restPool)
        }
    }

    FullScreenDialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().navigationBarsPadding().imePadding()
        ) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(AppIcons.ChevronLeft, contentDescription = "返回") }
                Text("這筆的報銷／欠款", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { finish() }) { Text("完成") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 這筆是什麼、實付多少；分配的比例和摘要固定在最下面（見 ReimbSummaryBar）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = cute.sub, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("實付 ${formatMoney(actual)}", style = MaterialTheme.typography.titleMedium)
                        if (fxOn) Text(formatFx(fxActual, fxCur), style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    }
                }
                if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodyMedium, color = cute.expense)
                PillSegment(
                    listOf("報銷" + if (reimbCount > 0) " $reimbCount 人" else "", "欠款" + if (debtCount > 0) " $debtCount 人" else ""),
                    tab, { tab = it; err = "" }, Modifier.fillMaxWidth(), equal = true,
                )
                if (tab == 1 && !debtAllowed) {
                    Text(debtNotAllowedWhy, style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                } else {
                    Text(
                        if (tab == 0) "誰要還我（我先幫他付的）" else "我欠誰（別人先幫我付的，先不從帳戶扣，還款時才扣）",
                        style = MaterialTheme.typography.labelMedium, color = cute.sub,
                    )
                    cur.forEachIndexed { i, r ->
                        if (r.isLocked) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.who.ifBlank { "（沒填對象）" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    val lk = r.locked
                                    val ld = r.lockedDebt
                                    Text(
                                        if (lk != null) "已收 ${formatMoney(lk.received)}" + if (lk.closed) "・已結案" else "・還剩 ${formatMoney(lk.remaining)}"
                                        else "已還 ${formatMoney(ld!!.pays.sumOf { it.amount })}" + if (ld.done) "・已還清" else "・還剩 ${formatMoney(ld.remainingTwd)}",
                                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                                    )
                                }
                                Text(formatMoney(r.locked?.amount ?: r.lockedDebt!!.amount), style = MaterialTheme.typography.bodyLarge, color = cute.sub)
                            }
                        } else {
                            val setWho: (String) -> Unit = { v ->
                                r.who = v.take(12); err = ""
                                // 只有一個人、還沒填金額：金額預設填滿（報銷是實付，欠款是整筆，之後可以改）
                                if (v.isNotBlank() && r.amt.isBlank() && !r.amtTouched && cur.count { !it.isLocked } == 1) {
                                    val pool = (actual - lockedSum).coerceAtLeast(0L)
                                    if (pool > 0L) r.amt = amtText(pool)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactField(
                                    r.who, setWho,
                                    if (tab == 0) "對象（選填）" else "欠誰", Modifier.weight(1f),
                                    onFocus = { on -> if (on) nameFocus = i else if (nameFocus == i) nameFocus = -1 },
                                )
                                CompactField(
                                    r.amt, { v -> r.auto = false; r.amtTouched = true; err = ""; r.amt = if (fxOn) fxInput(v, fdec) else v.filter { c -> c.isDigit() }.take(9) },
                                    "金額", Modifier.width(if (fxOn) 132.dp else 112.dp), number = true, decimal = fxOn && fdec > 0,
                                    prefix = if (fxOn) Currencies.of(fxCur).symbol.trim() else tw.moneybook.app.Money.twd,
                                )
                                Text(
                                    "✕", color = cute.sub, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clip(CircleShape).clickable { cur.removeAt(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            // 正在打這一欄的名字：下面列出符合的名字（照用過的次數），點一下填進這一欄
                            if (nameFocus == i && r.who.isNotBlank()) {
                                val others = cur.filterIndexed { j, o -> j != i }.map { it.who.trim() }.toSet()
                                SuggestRow(
                                    if (tab == 0) "以前的對象" else "以前欠過的人",
                                    filterSuggestions((if (tab == 0) names else debtNames).filter { it !in others }, r.who).take(8),
                                    r.who, { nm -> setWho(nm); focus.clearFocus() },
                                )
                            }
                            if (fxOn && rowTwd(r) > 0L) {
                                Text("≈ ${formatMoney(rowTwd(r))}", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(start = 4.dp))
                            }
                            if (restMode && r.auto) {
                                Text(
                                    "自動：剩下 ${if (fxOn) formatFx(fxOf(restPool), fxCur) else formatMoney(restPool)} ÷ ${restAuto.size + 1} 人（含我）",
                                    style = MaterialTheme.typography.labelSmall, color = cute.income, modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CuteChip("＋ 加一個人", false, { cur.add(ReimbRow(null, "", "")) })
                        val n = cur.count { !it.isLocked }
                        if (n >= 1) CuteChip("平分 ▾", restMode, { splitMenu = true })
                    }
                    val used = cur.map { it.who.trim() }.toSet()
                    val suggest = (if (tab == 0) names else debtNames).filter { it !in used }.take(8)
                    if (suggest.isNotEmpty()) {
                        Text(if (tab == 0) "常用對象，點一下加入" else "以前欠過的人，點一下加入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggest.forEach { nm -> CuteChip(nm, false, { addName(nm) }) }
                        }
                    }
                    if (cur.any { it.isLocked }) {
                        Text(
                            if (tab == 0) "已有收款的對象不能在這裡改，請到「報銷／欠款」頁處理。" else "已有還款的對象不能在這裡改，請到「報銷／欠款」頁處理。",
                            style = MaterialTheme.typography.labelSmall, color = cute.sub,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            // 固定在最下面：名單再長都看得到，開鍵盤時跟著往上
            ReimbSummaryBar(actual, debtTotal, reimbTotal, cap)
        }
        if (splitMenu) {
            val editable = cur.filter { !it.isLocked }
            val nE = editable.size
            fun fmt(v: Long) = if (fxOn) formatFx(v, fxCur) else formatMoney(v)
            val poolAll = (actual - lockedSum).coerceAtLeast(0L)
            fun resAll(self: Boolean): SplitResult = splitAmounts(self, if (fxOn) fxOf(poolAll) else poolAll, nE)
            val a1 = resAll(false)
            val a2 = resAll(true)
            val blanks = editable.count { rowTwd(it) <= 0L }
            val fixedCnt = nE - blanks
            val restOk = blanks >= 1 && fixedCnt >= 1
            val restPoolNow = (actual - lockedSum - editable.sumOf { rowTwd(it) }).coerceAtLeast(0L)
            AlertDialog(
                onDismissRequest = { splitMenu = false },
                title = { Text("平分") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SplitOption(
                            "平分給 $nE 人", if (tab == 0) "我只是幫忙付，不含我自己" else "全部都是別人先付的，我沒有自己付",
                            "每人 ${fmt(a1.amounts.firstOrNull() ?: 0L)}", true,
                        ) { splitAll(false); splitMenu = false }
                        SplitOption(
                            "平分給 ${nE + 1} 人（含我）", if (tab == 0) "我也分一份，除不盡的零頭算我自己" else "我也付一份（從帳戶扣），除不盡的零頭算我自己",
                            "每人 ${fmt(a2.amounts.firstOrNull() ?: 0L)}・我 ${fmt(a2.self)}", true,
                        ) { splitAll(true); splitMenu = false }
                        SplitOption(
                            "平分剩下的（含我）", "先填有指定金額的人，沒填的人和我平分剩下的",
                            if (restOk) "剩 ${formatMoney(restPoolNow)} ÷ ${blanks + 1} 人" else if (fixedCnt == 0) "先填至少一人的金額" else "每個人都填了金額",
                            restOk,
                        ) { startRest(); splitMenu = false }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { splitMenu = false }) { Text("取消") } },
            )
        }
    }
}

/** 一條分段的比例條：每段（比例 0～1、顏色）依序排，沒填滿的部分是淡色底 */
@Composable
internal fun SplitBar(parts: List<Pair<Float, androidx.compose.ui.graphics.Color>>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 10.dp) {
    val shown = parts.map { it.first.coerceIn(0f, 1f) to it.second }.filter { it.first > 0f }
    val rest = (1f - shown.sumOf { it.first.toDouble() }.toFloat()).coerceAtLeast(0f)
    Row(modifier.fillMaxWidth().height(height).clip(CircleShape).background(LocalCute.current.soft)) {
        shown.forEach { (f, c) -> Box(Modifier.weight(f).fillMaxHeight().background(c)) }
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

/** 百分比（四捨五入到整數） */
private fun pctOf(v: Long, total: Long): Int = if (total <= 0L) 0 else Math.round(v * 100.0 / total).toInt()

/**
 * 記一筆「這筆的報銷／欠款」最下面固定的摘要：誰付的（有欠款才有）、誰負擔兩條比例條，加自己負擔、可報銷兩個數字；
 * 點一下展開完整四行（我付、別人先付、自己負擔、可報銷，各寫百分比）。
 */
@Composable
private fun ReimbSummaryBar(actual: Long, debtTotal: Long, reimbTotal: Long, cap: Long) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(false) }
    val selfPay = (actual - debtTotal).coerceAtLeast(0L)
    val reimb = reimbTotal.coerceAtMost(actual).coerceAtLeast(0L)
    val bear = (actual - reimbTotal).coerceAtLeast(0L)
    fun frac(v: Long) = if (actual > 0L) v.toFloat() / actual.toFloat() else 0f
    // 兩段的百分比加起來是 100：第二段用 100 減第一段
    val payMe = pctOf(selfPay, actual)
    val bearMe = pctOf(bear, actual)
    Box(Modifier.fillMaxWidth().height(1.dp).background(cute.soft))
    Column(
        Modifier.fillMaxWidth().background(cute.card).clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun barLine(label: String, parts: List<Pair<Float, androidx.compose.ui.graphics.Color>>, right: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.width(52.dp))
                SplitBar(parts, Modifier.weight(1f))
                Text(right, style = MaterialTheme.typography.labelMedium, color = cute.sub, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            }
        }
        if (debtTotal > 0L) barLine("誰付的", listOf(frac(selfPay) to primary, frac(debtTotal) to cute.accent2), "我 $payMe%・別人 ${if (actual > 0L) 100 - payMe else 0}%")
        barLine("誰負擔", listOf(frac(bear) to primary, frac(reimb) to cute.income), "我 $bearMe%・報銷 ${if (actual > 0L) 100 - bearMe else 0}%")
        if (expanded) {
            @Composable
            fun line(color: androidx.compose.ui.graphics.Color, label: String, pct: Int, v: Long, vColor: androidx.compose.ui.graphics.Color = cute.ink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(" $pct%", style = MaterialTheme.typography.bodyMedium, color = cute.sub, modifier = Modifier.weight(1f))
                    Text(formatMoney(v), style = MaterialTheme.typography.bodyMedium, color = vColor)
                }
            }
            line(primary, "我付（從帳戶扣）", payMe, selfPay)
            line(cute.accent2, "別人先付（欠款）", if (actual > 0L) 100 - payMe else 0, debtTotal)
            line(primary, "自己負擔（算進支出）", bearMe, bear, primary)
            line(cute.income, "可報銷（別人要還我）", if (actual > 0L) 100 - bearMe else 0, reimbTotal, cute.income)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("自己負擔 ", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(bear), style = MaterialTheme.typography.titleMedium, color = primary)
                Spacer(Modifier.width(14.dp))
                Text("可報銷 ", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Text(formatMoney(reimbTotal), style = MaterialTheme.typography.titleMedium, color = cute.income, modifier = Modifier.weight(1f))
            }
        }
        Text(if (expanded) "點一下收起" else "點一下展開完整四行", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        if (cap <= 0L) {
            // 先填報銷、後填帳目金額：金額還沒輸入時不用提醒上限，儲存時才會依金額限制
            Text("還沒輸入帳目金額，報銷金額之後不會超過實付。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        } else if (reimbTotal > cap) {
            Text("報銷合計超過上限 ${formatMoney(cap)}，超過的部分不會算。", style = MaterialTheme.typography.labelSmall, color = cute.expense)
        } else if (reimbTotal > actual) {
            Text("報銷比實付多 ${formatMoney(reimbTotal - actual)}，多的部分收到後算成報銷回饋收入。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        }
        if (actual > 0L && debtTotal > actual) {
            Text("欠款合計超過實付 ${formatMoney(debtTotal - actual)}，請改小一點。", style = MaterialTheme.typography.labelSmall, color = cute.expense)
        }
    }
}

/** 平分選單的一個選項：標題、說明、算好的結果；不能選時變淡 */
@Composable
private fun SplitOption(title: String, desc: String, result: String, enabled: Boolean, onClick: () -> Unit) {
    val cute = LocalCute.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) cute.ink else cute.sub)
        Text(desc, style = MaterialTheme.typography.labelSmall, color = cute.sub)
        Text(result, style = MaterialTheme.typography.labelMedium, color = if (enabled) cute.income else cute.sub)
    }
}

// ───────────────────────── 報銷／欠款 ─────────────────────────

private sealed class ReimbPage {
    object Home : ReimbPage()
    class Receive(val who: String) : ReimbPage()
    class Person(val who: String) : ReimbPage()
    class Repay(val who: String) : ReimbPage()
    class Offset(val who: String) : ReimbPage()
    object Settle : ReimbPage()
}

/**
 * 報銷／欠款：報銷（別人欠我，我先付的）和欠款（我欠別人，別人先付的）放在同一頁，依人看淨額；
 * 同一個人兩個方向都有時可以互相抵銷。
 */
@Composable
fun ReimbScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    // 從人頁點帳單去記一筆修改，回來時這一頁會重新建立：停在原本那個人的頁面（記在 vm 裡）
    var page by remember { mutableStateOf<ReimbPage>(vm.reimbPerson?.let { ReimbPage.Person(it) } ?: ReimbPage.Home) }
    androidx.compose.runtime.SideEffect { vm.reimbPerson = (page as? ReimbPage.Person)?.who }
    val leave = { vm.reimbPerson = null; vm.reimbTab = 0; vm.reimbLists.clear(); vm.reimbScrolls.clear(); onBack() }
    // 從人頁進去的收款／還款／抵銷，做完回到人頁；從總覽進去的回到總覽
    var fromPerson by remember { mutableStateOf<String?>(null) }
    fun back() { page = fromPerson?.let { ReimbPage.Person(it) } ?: ReimbPage.Home }
    when (val p = page) {
        is ReimbPage.Home -> ReimbHome(
            vm, onEdit, leave,
            onReceive = { fromPerson = null; page = ReimbPage.Receive(it) },
            onPerson = { page = ReimbPage.Person(it) },
            onRepay = { fromPerson = null; page = ReimbPage.Repay(it) },
            onOffset = { fromPerson = null; page = ReimbPage.Offset(it) },
            onSettle = { fromPerson = null; page = ReimbPage.Settle },
        )
        is ReimbPage.Settle -> SettlePage(vm, onBack = { page = ReimbPage.Home }, onDone = { page = ReimbPage.Home })
        is ReimbPage.Receive -> ReimbReceivePage(vm, p.who, onBack = { back() }, onDone = { fromPerson = null; page = ReimbPage.Home })
        is ReimbPage.Repay -> DebtRepayPage(vm, p.who, onBack = { back() }, onDone = { fromPerson = null; page = ReimbPage.Home })
        is ReimbPage.Offset -> OffsetPage(vm, p.who, onBack = { back() }, onDone = { fromPerson = null; page = ReimbPage.Home })
        is ReimbPage.Person -> ReimbPersonPage(
            vm, p.who, onBack = { fromPerson = null; page = ReimbPage.Home },
            onEdit = onEdit,
            onReceive = { fromPerson = p.who; page = ReimbPage.Receive(p.who) },
            onRepay = { fromPerson = p.who; page = ReimbPage.Repay(p.who) },
            onOffset = { fromPerson = p.who; page = ReimbPage.Offset(p.who) },
        )
    }
}

/** 一個人的往來：他欠我（報銷還沒收的）、我欠他（欠款還沒還的，依幣別） */
private class Mutual(val who: String, val claims: List<Claim>, val debts: List<DebtGroup>) {
    val oweMe: Long get() = claims.sumOf { it.item.remaining }
    val iOwe: Long get() = debts.sumOf { it.remainingTwd }
    val net: Long get() = oweMe - iOwe
    /** 可以互相抵銷：他欠我、我也欠他（台幣或外幣） */
    val canOffset: Boolean get() = oweMe > 0L && debts.any { it.remainingUnits > 0L }
    val oldestDay: Long get() = minOf(claims.minOfOrNull { it.txn.day } ?: Long.MAX_VALUE, debts.minOfOrNull { g -> g.refs.minOf { it.t.day } } ?: Long.MAX_VALUE)
}

private fun AppData.mutuals(): List<Mutual> {
    val open = claimsOf(bookTxns).filter { !it.item.closed && it.item.remaining > 0L }.groupBy { it.who }
    val debts = debtGroups().groupBy { it.who }
    return (open.keys + debts.keys).distinct().map { w -> Mutual(w, open[w] ?: emptyList(), debts[w] ?: emptyList()) }.sortedBy { it.oldestDay }
}

/** 淨額的文字：他還欠我寫「淨收」、我還欠他寫「淨欠」、相等寫「兩清」 */
private fun netText(net: Long): String = when {
    net > 0L -> "淨收 ${formatMoney(net)}"
    net < 0L -> "淨欠 ${formatMoney(-net)}"
    else -> "兩清"
}

/**
 * 一條進度：上面是標題和百分比，中間是比例條，下面左邊寫總共／已經多少，右邊是還剩多少（單獨一格，一行一個金額）
 */
@Composable
private fun ProgressBlock(
    title: String, pct: String, frac: Float, color: androidx.compose.ui.graphics.Color,
    detail: String, leftLabel: String, leftValue: String,
    /** 百分比的字色（欠款的橘色比較淡，字改用支出色） */
    pctColor: androidx.compose.ui.graphics.Color = color,
) {
    val cute = LocalCute.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f))
        Text(pct, style = MaterialTheme.typography.titleSmall, color = pctColor)
    }
    Spacer(Modifier.height(6.dp))
    SplitBar(listOf(frac to color))
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(detail, style = MaterialTheme.typography.labelMedium, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("$leftLabel ", style = MaterialTheme.typography.labelMedium, color = cute.sub, maxLines = 1)
        Text(leftValue, style = MaterialTheme.typography.titleMedium, color = cute.expense, maxLines = 1)
    }
}

/** 一行一個金額：標籤在左、金額靠右（金額太長時標籤縮短，不會擠成好幾行） */
@Composable
private fun AmountLine(label: String, value: String, color: androidx.compose.ui.graphics.Color, strong: Boolean = false) {
    val cute = LocalCute.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun AmountDivider() {
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalCute.current.soft))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ReimbHome(
    vm: MoneyViewModel,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    onReceive: (String) -> Unit,
    onPerson: (String) -> Unit,
    onRepay: (String) -> Unit,
    onOffset: (String) -> Unit,
    onSettle: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    var tab by vm::reimbTab
    val pending = d.bookTxns.pendingReimb()
    val claims = claimsOf(d.bookTxns)
    val people = d.mutuals()
    val oweMe = pending.sumOf { it.reimbOutstanding }
    val iOwe = d.debtGroups().sumOf { it.remainingTwd }
    val debtTxns = d.bookTxns.filter { t -> t.debtItems.any { it.remainingUnits > 0L } }

    SubPage("報銷／欠款", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = vm.reimbLists.getOrPut(tab) { androidx.compose.foundation.lazy.LazyListState() },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // 一行一個金額：標籤在左、金額靠右，數字再大也放得下
                CuteCard(Modifier.fillMaxWidth()) {
                    AmountLine("別人欠我", formatMoney(oweMe), cute.income)
                    AmountLine("我欠別人", formatMoney(iOwe), cute.expense)
                    AmountDivider()
                    AmountLine(if (oweMe >= iOwe) "淨收" else "淨欠", formatMoney(kotlin.math.abs(oweMe - iOwe)), cute.ink, strong = true)
                }
            }
            // 用分帳軟體算完整團怎麼轉之後，照它的結論一次結清（收到的錢不一定是欠我的人給的）
            if (people.isNotEmpty()) item {
                Button(onClick = onSettle, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("依分帳結論結清", style = MaterialTheme.typography.titleSmall) }
            }
            item { PillSegment(listOf("依對象", "依帳單", "紀錄"), tab, { tab = it }, Modifier.fillMaxWidth(), equal = true) }

            when (tab) {
                0 -> {
                    if (people.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "沒有待報銷或欠款\n記帳時點「報銷／欠款」就會出現在這裡") }
                    }
                    items(people, key = { "g" + it.who }) { m ->
                        val days = daysSince(m.oldestDay)
                        CuteCard(Modifier.fillMaxWidth().clickable { onPerson(m.who) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(cute.soft),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (m.who.isBlank()) "?" else m.who.take(1), style = MaterialTheme.typography.titleMedium) }
                                Spacer(Modifier.width(12.dp))
                                Text(ownerLabel(m.who), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(if (m.net > 0L) "淨收 " else if (m.net < 0L) "淨欠 " else "兩清", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                if (m.net != 0L) Text(formatMoney(kotlin.math.abs(m.net)), style = MaterialTheme.typography.titleMedium, color = if (m.net > 0L) cute.income else cute.expense)
                                Spacer(Modifier.width(4.dp))
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = cute.sub)
                            }
                            Spacer(Modifier.height(6.dp))
                            // 一個方向一條細 bar：報銷看收回多少、欠款看還了多少（外幣用約當台幣），只算還沒結清的帳
                            @Composable
                            fun progress(text: String, color: androidx.compose.ui.graphics.Color, pct: String, frac: Float, barColor: androidx.compose.ui.graphics.Color) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                    Text(pct, style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                }
                                Spacer(Modifier.height(4.dp))
                                SplitBar(listOf(frac to barColor), height = 6.dp)
                                Spacer(Modifier.height(4.dp))
                            }
                            if (m.claims.isNotEmpty()) {
                                val due = m.claims.sumOf { it.item.amount }
                                val got = m.claims.sumOf { it.item.received }
                                progress("${ownerLabel(m.who)}欠我 ${formatMoney(m.oweMe)}（共 ${m.claims.size} 筆）", cute.income, "已收 ${pctOf(got, due)}%", got.toFloat() / due.coerceAtLeast(1L), cute.income)
                                val oldest = m.claims.minWith(compareBy({ it.txn.day }, { it.txn.id }))
                                Text(
                                    "欠最久：${billLabel(d, oldest.txn)} ${formatMoney(oldest.item.remaining)}" + if (m.claims.size > 1) "（另有 ${m.claims.size - 1} 筆）" else "",
                                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            if (m.debts.isNotEmpty()) {
                                val refs = m.debts.flatMap { it.refs }
                                val total = refs.sumOf { it.item.amount }
                                val left = refs.sumOf { it.item.remainingTwd }
                                val parts = m.debts.sortedBy { if (it.currency.isEmpty()) 0 else 1 }
                                    .map { g -> if (g.currency.isEmpty()) formatMoney(g.remainingUnits) else formatFx(g.remainingUnits, g.currency) }
                                // 各幣別的金額用＋連起來，有外幣時後面接約多少台幣和筆數：NT$1,268＋¥22,934（約 NT$6,313・共 9 筆）
                                val fx = m.debts.any { it.currency.isNotEmpty() }
                                val amt = parts.joinToString("＋") + if (fx) "（約 ${formatMoney(m.iOwe)}・共 ${refs.size} 筆）" else "（共 ${refs.size} 筆）"
                                progress("我欠${ownerLabel(m.who)} $amt", cute.expense, "已還 ${pctOf((total - left).coerceAtLeast(0L), total)}%", (total - left).toFloat() / total.coerceAtLeast(1L), cute.accent2)
                            }
                            Text(
                                if (days >= 1L) "最久的已經 $days 天" else "今天記的",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (days >= 30L) cute.expense else cute.sub,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (m.claims.isNotEmpty()) SoftButton("收款", { onReceive(m.who) }, compact = true)
                                if (m.debts.isNotEmpty()) SoftButton("還款", { onRepay(m.who) }, compact = true)
                                if (m.canOffset) SoftButton("互相抵銷", { onOffset(m.who) }, compact = true)
                            }
                        }
                    }
                }
                1 -> {
                    if (pending.isEmpty() && debtTxns.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "沒有待報銷或欠款\n記帳時點「報銷／欠款」就會出現在這裡") }
                    }
                    if (pending.isNotEmpty()) item { Text("別人欠我（待報銷）", style = MaterialTheme.typography.labelLarge, color = cute.sub) }
                    items(pending, key = { "b" + it.id }) { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TxnRow(d, t) { onEdit(t.id) }
                            t.items.forEach { item ->
                                if (!item.closed) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp).clickable { onReceive(item.who.trim()) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(ownerLabel(item.who.trim()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            "應收 ${formatMoney(item.amount)}" + (if (item.received > 0L) "・已收 ${formatMoney(item.received)}" else "") + "・還剩 ${formatMoney(item.remaining)}",
                                            style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (debtTxns.isNotEmpty()) item { Text("我欠別人（欠款）", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.padding(top = 6.dp)) }
                    items(debtTxns, key = { "db" + it.id }) { t ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TxnRow(d, t) { onEdit(t.id) }
                            t.debtItems.forEach { item ->
                                if (item.remainingUnits > 0L) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp).clickable { onRepay(item.who.trim()) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("欠 " + item.who.trim(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            "欠 ${formatMoney(item.amount)}" + (if (item.pays.isNotEmpty()) "・已還 ${formatMoney(item.pays.sumOf { it.amount })}" else "") + "・還剩 ${formatMoney(item.remainingTwd)}",
                                            style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val recs = records(d, claims)
                    if (recs.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "還沒有收款、還款紀錄") }
                    }
                    items(recs.size, key = { "p$it" }) { n ->
                        val r = recs[n]
                        CuteCard(Modifier.fillMaxWidth().clickable { onPerson(r.who) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${dayTimeLabel(r.day, r.time)}　${r.title}", style = MaterialTheme.typography.bodyLarge)
                                    Text(r.sub, style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                }
                                Text(r.amount, style = MaterialTheme.typography.titleMedium, color = r.color(cute))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收款、還款、互相抵銷的一筆紀錄。總覽的「紀錄」分頁：title（對象）、sub、amount；
 * 人頁：line（「收到 $800」「還款 $500」「互相抵銷 $300」）、sub。
 */
private class Rec(
    val who: String, val day: Long, val time: Int, val title: String, val sub: String, val amount: String,
    val kind: Int, // 0 收款、1 還款、2 抵銷
    val line: String, val personSub: String,
    val onDelete: (MoneyViewModel) -> Unit,
    /** 收款可以修改：報銷的哪一筆、第幾筆收款 */
    val claim: Claim? = null, val payIndex: Int = -1, val pay: ReimbPay? = null,
) {
    fun color(c: Cute) = when (kind) { 0 -> c.income; 1 -> c.expense; else -> c.sub }
}

/** 某些報銷帳與欠款的收款／還款／抵銷紀錄，新的在前；抵銷兩邊只列一次 */
private fun records(d: AppData, claims: List<Claim>, who: String? = null): List<Rec> {
    val out = ArrayList<Rec>()
    // 互相抵銷抵到外幣欠款時，紀錄寫抵掉多少外幣
    val fxOff = HashMap<Long, MutableList<String>>()
    d.bookTxns.filter { it.isDebt && it.debtCurrency.isNotEmpty() }.forEach { t ->
        t.debtItems.forEach { item -> item.pays.forEach { p -> if (p.offsetId != 0L) fxOff.getOrPut(p.offsetId) { ArrayList() }.add(formatFx(p.units, t.debtCurrency)) } }
    }
    fun offFx(id: Long): String = fxOff[id]?.let { "・抵掉 " + it.joinToString("＋") } ?: ""
    // 分帳結清：總覽的紀錄每次結清一列（寫實際轉的錢）；某人的頁面每一項各一列
    fun movesText(s: tw.moneybook.app.Settle): String = s.moves.joinToString("、") { m ->
        (if (m.receive) "收 " else "付給 ") + m.who.ifBlank { "對方" } + " " + formatMoney(m.amount) + (m.accountId?.let { d.accMap[it] }?.let { "（${it.name}）" } ?: "")
    }
    if (who == null) {
        d.settles.filter { it.bookId == d.currentBook.id }.forEach { s ->
            val n = d.bookTxns.sumOf { t -> t.items.count { i -> i.pays.any { it.offsetId == s.id } } + t.debtItems.count { i -> i.pays.any { it.offsetId == s.id } } }
            out.add(Rec(
                "", s.day, s.time, "分帳結清", movesText(s) + "・結清 $n 筆", (if (s.net >= 0L) "+" else "-") + formatMoney(kotlin.math.abs(s.net)), 2,
                "分帳結清", movesText(s), { vm -> vm.deleteSettle(s.id) },
            ))
        }
    }
    claims.filter { who == null || it.who == who }.forEach { c ->
        c.item.pays.forEachIndexed { n, pay ->
            val acc = pay.accountId?.let { d.accMap[it] }
            val st = d.settleOf(pay.offsetId)
            if (st != null) {
                if (who != null) out.add(Rec(
                    c.who, pay.day, pay.time, "分帳結清", "${billLabel(d, c.txn)}・${movesText(st)}", formatMoney(pay.amount), 2,
                    "分帳結清 ${formatMoney(pay.amount)}", "${billLabel(d, c.txn)}・${movesText(st)}", { vm -> vm.deleteSettle(st.id) },
                ))
                return@forEachIndexed
            }
            out.add(
                if (pay.offsetId != 0L) Rec(
                    c.who, pay.day, pay.time, "和${ownerLabel(c.who)}互相抵銷", "${billLabel(d, c.txn)}・不動帳戶" + offFx(pay.offsetId), formatMoney(pay.amount), 2,
                    "互相抵銷 ${formatMoney(pay.amount)}", "${billLabel(d, c.txn)}・不動帳戶" + offFx(pay.offsetId), { vm -> vm.deleteOffset(pay.offsetId) },
                )
                else Rec(
                    c.who, pay.day, pay.time, ownerLabel(c.who),
                    "${billLabel(d, c.txn)}・存入 ${acc?.name ?: "未指定帳戶"}" + if (acc != null && acc.isForeign && pay.fxAmount > 0L) "（${formatFx(pay.fxAmount, acc.currency)}）" else "",
                    "+" + formatMoney(pay.amount), 0,
                    "收到 ${payText(d, pay)}", "${billLabel(d, c.txn)}・存入 ${acc?.name ?: "未指定帳戶"}",
                    { vm -> vm.deleteReimbPay(c.txn.id, c.index, n) }, c, n, pay,
                )
            )
        }
    }
    d.bookTxns.filter { it.isDebt }.forEach { t ->
        t.debtItems.forEachIndexed { i, item ->
            if (who != null && item.who.trim() != who) return@forEachIndexed
            item.pays.forEachIndexed { n, p ->
                val st = d.settleOf(p.offsetId)
                if (st != null && who != null) out.add(Rec(
                    item.who.trim(), p.day, p.time, "分帳結清", "${billLabel(d, t)}・${movesText(st)}", formatMoney(p.amount), 2,
                    "分帳結清 ${formatMoney(p.amount)}", "${billLabel(d, t)}・${movesText(st)}", { vm -> vm.deleteSettle(st.id) },
                ))
                if (p.offsetId != 0L) return@forEachIndexed
                val acc = p.accountId?.let { d.accMap[it] }
                val amt = if (acc != null && acc.isForeign) acc.fmt(p.fxAmount) else formatMoney(p.amount)
                out.add(Rec(
                    item.who.trim(), p.day, p.time, "還款給${ownerLabel(item.who.trim())}", "${billLabel(d, t)}・從 ${acc?.name ?: "未指定帳戶"}", "-$amt", 1,
                    "還款 $amt", "${billLabel(d, t)}・從 ${acc?.name ?: "未指定帳戶"}", { vm -> vm.deleteDebtPay(t.id, i, n) },
                ))
            }
        }
    }
    return out.sortedByDescending { it.day * 1440L + maxOf(it.time, 0) }
}

// ───────────────────────── 收款 ─────────────────────────

/** 外幣的輸入文字：只留數字、一個小數點，小數位數不超過幣別的位數 */
private fun fxInput(v: String, dec: Int): String {
    var dot = false
    val sb = StringBuilder()
    for (c in v) when {
        c.isDigit() -> sb.append(c)
        c == '.' && !dot && dec > 0 -> { dot = true; sb.append(c) }
    }
    val t = sb.toString()
    val at = t.indexOf('.')
    return (if (at >= 0 && t.length - at - 1 > dec) t.take(at + 1 + dec) else t).take(14)
}

/** 收款的金額文字：收外幣（有 fxAmount）是「US$20.50（$646）」，台幣是「$646」 */
private fun payText(d: AppData, pay: ReimbPay): String {
    val acc = pay.accountId?.let { d.accMap[it] }
    return if (acc != null && acc.isForeign && pay.fxAmount > 0L) "${formatFx(pay.fxAmount, acc.currency)}（${formatMoney(pay.amount)}）" else formatMoney(pay.amount)
}

/** 收款頁裡的一種幣別：用哪個帳戶收、收多少（輸入的文字） */
private class RLine(accId: Long?, text: String) {
    var accId by mutableStateOf(accId)
    var text by mutableStateOf(text)
}

@Composable
private fun ReimbReceivePage(vm: MoneyViewModel, who: String, onBack: () -> Unit, onDone: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val claims = claimsOf(d.bookTxns).filter { !it.item.closed && it.who == who }.sortedWith(compareBy({ it.txn.day }, { it.txn.id }))
    val totalRemaining = claims.sumOf { it.item.remaining }
    val twdAcc = d.visibleAccounts.firstOrNull { !it.isForeign }
    val lines = remember { mutableStateListOf(RLine(twdAcc?.id, totalRemaining.toString())) }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var timeMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val overrides = remember { mutableStateMapOf<String, String>() }
    val chase = remember { mutableStateMapOf<String, Boolean>() }
    var askChase by remember { mutableStateOf(false) }
    // 正在選收款帳戶的那一列（-1＝沒有）
    var pickAccFor by remember { mutableStateOf(-1) }
    val multi = lines.size > 1
    // 勾選這次對方「已經給了」的帳（預設全部勾選）：只分配到勾選的，最上面統計勾選的合計
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    fun isOn(c: Claim) = selected[c.key] != false
    val active = claims.filter { isOn(it) }
    val selTotal = active.sumOf { it.item.remaining }
    // 使用者自己改過收到的金額，就不再隨勾選自動更新
    var amountTouched by remember { mutableStateOf(false) }

    fun accOf(l: RLine): Account? = l.accId?.let { d.accMap[it] }
    fun curOf(l: RLine): String = accOf(l)?.takeIf { it.isForeign }?.currency ?: ""
    fun decOf(l: RLine): Int = accOf(l)?.takeIf { it.isForeign }?.cur?.decimals ?: 0
    fun parseLine(l: RLine): Long = (if (curOf(l).isNotEmpty()) parseFx(l.text, decOf(l)) else l.text.toLongOrNull()) ?: 0L
    fun claimCur(t: Txn): String = d.fxAccountOf(t)?.currency ?: ""
    fun unitText(cur: String, u: Long) = if (cur.isNotEmpty()) formatFx(u, cur) else formatMoney(u)
    fun defaultText(a: Account?): String =
        if (a != null && a.isForeign) fxExpr(active.filter { claimCur(it.txn) == a.currency }.sumOf { it.txn.twdToFxAt(it.item.remaining) }, a.cur.decimals)
        else selTotal.toString()

    val parsed = lines.map { ReceiptLine(it.accId, curOf(it), parseLine(it)) }
    // 只有一種幣別時才開放逐筆改金額；多種幣別一起收時由系統自動分配
    val overrideAmounts: Map<String, Long> = if (multi) emptyMap() else {
        val l = lines[0]
        overrides.mapNotNull { (k, v) -> (if (curOf(l).isNotEmpty()) parseFx(v, decOf(l)) else v.toLongOrNull())?.let { k to it } }.toMap()
    }
    val activeOut = allocateReceipt(active.map { ClaimInput(it.key, it.txn, it.item.remaining) }, parsed, ::claimCur, overrideAmounts)
    // 沒勾選的帳這次不收：給一個空的結果，位置和 claims 對齊
    val outcomes = claims.map { c -> activeOut.firstOrNull { it.key == c.key } ?: ClaimOutcome(c.key, c.item.remaining, emptyList()) }
    val sumCredit = outcomes.sumOf { it.credit }
    // 勾選變了，收到的金額預設跟著變成勾選的合計（逐筆改的金額和追款選擇都重來）
    val selKey = active.joinToString(",") { it.key }
    LaunchedEffect(selKey) {
        overrides.clear()
        chase.clear()
        if (!multi && !amountTouched) lines[0].text = defaultText(accOf(lines[0]))
    }
    // 必填：什麼都沒收到不能確認；標出第一個還沒填金額的那一種幣別
    val tries = rememberNeedTries()
    val need = when {
        sumCredit > 0L -> null
        active.isEmpty() -> Need("line0", "請先勾選對方已經給的帳")
        else -> Need("line${parsed.indexOfFirst { it.amount <= 0L }.coerceAtLeast(0)}", "請輸入這次收到的金額")
    }
    val nv = NeedView(need, tries.count)
    val needChaseIdx = claims.indices.filter { outcomes[it].short && chase[claims[it].key] == null }

    fun submit() {
        val list = ArrayList<ReimbReceipt>()
        outcomes.forEachIndexed { i, o ->
            o.parts.forEach { p ->
                val ln = parsed[p.line]
                list.add(ReimbReceipt(claims[i].txn.id, claims[i].index, p.credit, chase[claims[i].key] != false, if (ln.currency.isNotEmpty()) p.units else 0L, ln.accountId))
            }
        }
        vm.receiveReimb(list, lines.firstOrNull()?.accId, day, timeMin)
        onDone()
    }

    // 可以選的收款帳戶：台幣帳戶，加上「有外幣帳可以用它收」的外幣帳戶
    val fxChoices = d.visibleAccounts.filter { a -> a.isForeign && claims.any { c -> claimCur(c.txn) == a.currency } }
    val allChoices = d.visibleAccounts.filter { !it.isForeign } + fxChoices
    fun currencyOfAcc(a: Account) = if (a.isForeign) a.currency else ""
    // 還沒被其他列用掉的幣別：可以再加一列
    val unusedChoice: Account? = allChoices.firstOrNull { a -> lines.none { curOf(it) == currencyOfAcc(a) } }

    // 這一列換收款帳戶：只有一種幣別時，金額換成那個幣別的預設值（全部還欠的）；多種幣別時清空讓使用者自己填
    fun pickAccount(l: RLine, a: Account) {
        l.accId = a.id
        overrides.clear()
        chase.clear()
        l.text = if (multi) "" else defaultText(a)
        amountTouched = false
    }

    SubPage("收款・${ownerLabel(who)}", onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ownerLabel(who)}還欠你（共 ${claims.size} 筆）", style = MaterialTheme.typography.bodyLarge, color = cute.sub, modifier = Modifier.weight(1f))
                        Text(formatMoney(totalRemaining), style = MaterialTheme.typography.titleLarge, color = cute.expense)
                    }
                    val l0 = lines[0]
                    if (!multi && curOf(l0).isNotEmpty()) {
                        val tot = claims.filter { claimCur(it.txn) == curOf(l0) }.sumOf { it.txn.twdToFxAt(it.item.remaining) }
                        Text("其中可以用 ${curOf(l0)} 收的：${formatFx(tot, curOf(l0))}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(cute.soft))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("這次勾選 ${active.size} 筆，合計", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatMoney(selTotal), style = MaterialTheme.typography.titleLarge, color = cute.income)
                    }
                    if (!multi && curOf(l0).isNotEmpty()) {
                        val selFx = active.filter { claimCur(it.txn) == curOf(l0) }.sumOf { it.txn.twdToFxAt(it.item.remaining) }
                        Text("可以用 ${curOf(l0)} 收的：${formatFx(selFx, curOf(l0))}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("全選", { claims.forEach { selected[it.key] = true } }, compact = true)
                        SoftButton("全不選", { claims.forEach { selected[it.key] = false } }, compact = true)
                    }
                }
                lines.forEachIndexed { li, l ->
                    val cur = curOf(l)
                    val dec = decOf(l)
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (multi) "第 ${li + 1} 種：" else "") + (if (cur.isNotEmpty()) "這次收到多少（$cur）" else "這次收到多少"),
                                style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.weight(1f),
                            )
                            if (multi) {
                                Text(
                                    "✕", color = cute.sub, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clip(CircleShape).clickable { lines.removeAt(li); overrides.clear(); chase.clear(); if (lines.size == 1) lines[0].text = defaultText(accOf(lines[0])) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        CompactField(
                            l.text, { v -> amountTouched = true; l.text = if (cur.isNotEmpty()) fxInput(v, dec) else v.filter { c -> c.isDigit() }.take(9); overrides.clear() },
                            "金額", Modifier.fillMaxWidth().needInView(nv, "line$li"), number = true, decimal = cur.isNotEmpty() && dec > 0,
                            prefix = accOf(l)?.takeIf { it.isForeign }?.cur?.symbol?.trim() ?: tw.moneybook.app.Money.twd,
                            error = nv.on("line$li"),
                        )
                        nv.Message("line$li")
                        Spacer(Modifier.height(8.dp))
                        Text("存進哪個帳戶", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Spacer(Modifier.height(4.dp))
                        PickerButton(accOf(l)?.name ?: "選擇帳戶", { pickAccFor = li })
                        if (pickAccFor == li) {
                            // 同一次收款每種幣別只能有一列：已經被別列用掉的幣別不列出（自己這一列的幣別除外）
                            AccountPickDialog(
                                vm = vm,
                                accounts = allChoices.filter { a -> currencyOfAcc(a) == cur || lines.none { o -> o !== l && curOf(o) == currencyOfAcc(a) } },
                                selectedId = l.accId,
                                onPick = { a -> pickAccount(l, a); pickAccFor = -1 },
                                onDismiss = { pickAccFor = -1 },
                                title = "存進哪個帳戶",
                            )
                        }
                    }
                }
                if (unusedChoice != null) {
                    CuteChip("＋ 再加一種幣別（外幣、台幣一起收）", false, {
                        lines.forEach { it.text = "" }
                        overrides.clear()
                        chase.clear()
                        lines.add(RLine(unusedChoice.id, ""))
                    })
                }
                if (fxChoices.isNotEmpty() && !multi && curOf(lines[0]).isEmpty()) {
                    Text("有幾筆是用外幣付的，對方還外幣的話，選外幣帳戶收。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
                ChipRow {
                    CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                    CuteChip(tw.moneybook.app.formatTime(timeMin), false, { pickTime = true }, icon = "vec:clock")
                }

                Text(
                    if (multi) "勾選對方這次已經給的帳（外幣先沖同幣別的帳，台幣再沖剩下的，由舊到新）" else "勾選對方這次已經給的帳（由舊到新自動分配，可以直接改金額）",
                    style = MaterialTheme.typography.labelLarge, color = cute.sub,
                )
                claims.forEachIndexed { i, c ->
                    val o = outcomes[i]
                    val l0 = lines[0]
                    val single = !multi
                    val cur0 = curOf(l0)
                    val eligible0 = cur0.isEmpty() || claimCur(c.txn) == cur0
                    val part0 = o.parts.firstOrNull { it.line == 0 }
                    val remU0 = if (cur0.isEmpty()) c.item.remaining else if (eligible0) c.txn.twdToFxAt(c.item.remaining) else 0L
                    CuteCard(Modifier.fillMaxWidth().alpha(if (isOn(c)) 1f else 0.55f)) {
                      Row(verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.Checkbox(checked = isOn(c), onCheckedChange = { selected[c.key] = it })
                        Column(Modifier.weight(1f)) {
                        if (single && !eligible0) {
                            Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyLarge, color = cute.sub)
                            if (billNote(c.txn).isNotEmpty()) Text(catPath(d, c.txn), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                            Text("還剩 ${formatMoney(c.item.remaining)}・不是用 $cur0 付的，要改選台幣帳戶才能收", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyLarge)
                                    // 有備註時，分類路徑另外一行（備註看實際項目，分類看歸在哪）
                                    if (billNote(c.txn).isNotEmpty()) Text(catPath(d, c.txn), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                    Text(
                                        "還剩 ${formatMoney(c.item.remaining)}" + (if (single && cur0.isNotEmpty()) "（${formatFx(remU0, cur0)}）" else ""),
                                        style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                    )
                                    if (multi) {
                                        Text(
                                            if (o.parts.isEmpty()) "這次不收" else o.parts.joinToString("＋") { p -> unitText(parsed[p.line].currency, p.units) } + "，沖掉 ${formatMoney(o.credit)}",
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (single && isOn(c)) {
                                    CompactField(
                                        overrides[c.key] ?: (if (cur0.isNotEmpty()) fxExpr(part0?.units ?: 0L, decOf(l0)) else (part0?.units ?: 0L).toString()),
                                        { v -> overrides[c.key] = if (cur0.isNotEmpty()) fxInput(v, decOf(l0)) else v.filter { ch -> ch.isDigit() }.take(9) },
                                        "收", Modifier.width(120.dp), number = true, decimal = cur0.isNotEmpty() && decOf(l0) > 0,
                                        prefix = accOf(l0)?.takeIf { it.isForeign }?.cur?.symbol?.trim() ?: tw.moneybook.app.Money.twd,
                                    )
                                }
                            }
                            if (o.short) {
                                Spacer(Modifier.height(8.dp))
                                val lack = if (single && cur0.isNotEmpty()) formatFx(remU0 - (part0?.units ?: 0L), cur0) else formatMoney(c.item.remaining - o.credit)
                                Text("還差 $lack，要繼續追嗎？", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CuteChip("繼續追剩下的", chase[c.key] == true, { chase[c.key] = true })
                                    CuteChip("不追了（自己負擔）", chase[c.key] == false, { chase[c.key] = false })
                                }
                            }
                        }
                        }
                      }
                    }
                }
                val over = sumCredit - selTotal
                if (over > 0L) {
                    Text("多收 ${formatMoney(over)}，多的部分會算成報銷回饋收入。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
                if (sumCredit > 0L && (multi || curOf(lines[0]).isNotEmpty())) {
                    Text("這次收款共沖掉 ${formatMoney(sumCredit)}（外幣用各筆消費當時的匯率換算）。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
            }
            Box(Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                val shown = parsed.filter { it.amount > 0L }.joinToString("＋") { unitText(it.currency, it.amount) }
                Button(
                    onClick = { if (need != null) tries.count++ else if (needChaseIdx.isNotEmpty()) askChase = true else submit() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("確認收款 " + shown.ifEmpty { formatMoney(0L) }) }
            }
        }
    }
    if (pickDate) {
        CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "收到報銷款的日期")
    }
    if (pickTime) {
        TimePickerDialog(timeMin, { timeMin = it; pickTime = false }, { pickTime = false })
    }
    if (askChase) {
        AlertDialog(
            onDismissRequest = { askChase = false },
            title = { Text("還有沒收到的款項") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    needChaseIdx.forEach { i ->
                        val o = outcomes[i]
                        val cur0 = curOf(lines[0])
                        val lack = if (!multi && cur0.isNotEmpty()) formatFx(claims[i].txn.twdToFxAt(claims[i].item.remaining) - (o.parts.firstOrNull()?.units ?: 0L), cur0)
                        else formatMoney(claims[i].item.remaining - o.credit)
                        Text("${billLabel(d, claims[i].txn)} 還差 $lack")
                    }
                    Text("要繼續追剩下的嗎？選「不追了」的差額會算成你自己的支出。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    needChaseIdx.forEach { chase[claims[it].key] = true }
                    askChase = false
                    submit()
                }) { Text("繼續追") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askChase = false }) { Text("取消") }
                    TextButton(onClick = {
                        needChaseIdx.forEach { chase[claims[it].key] = false }
                        askChase = false
                        submit()
                    }) { Text("不追了") }
                }
            },
        )
    }
}

// ───────────────────────── 某人的報銷與欠款 ─────────────────────────

@Composable
private fun ReimbPersonPage(
    vm: MoneyViewModel, who: String, onBack: () -> Unit, onReceive: () -> Unit,
    onRepay: () -> Unit, onOffset: () -> Unit,
    /** 點帳單：到記一筆修改那一筆 */
    onEdit: (Long) -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val claims = claimsOf(d.bookTxns).filter { it.who == who }.sortedWith(compareByDescending<Claim> { it.txn.day }.thenByDescending { it.txn.id })
    val owed = claims.filter { !it.item.closed }.sumOf { it.item.remaining }
    val debtRefs = d.bookTxns.filter { it.isDebt }.sortedWith(compareByDescending<Txn> { it.day }.thenByDescending { it.id })
        .flatMap { t -> t.debtItems.indices.map { DebtRef(t, it) } }.filter { it.who == who }
    val groups = d.debtGroups().filter { it.who == who }
    val iOwe = groups.sumOf { it.remainingTwd }
    val net = owed - iOwe
    val recs = records(d, claims, who)
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Triple<Claim, Int, ReimbPay>?>(null) }

    // 從這裡點帳單去記一筆修改，回來時停在原本的位置
    val scroll = remember(who) { vm.reimbScrolls.getOrPut("p:$who") { androidx.compose.foundation.ScrollState(0) } }
    SubPage(ownerLabel(who), onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 進度：報銷一條（收回多少了）、欠款每種幣別一條（還了多少了），只算還沒結清的帳；最後是淨額（外幣用預估匯率換成台幣）
                CuteCard(Modifier.fillMaxWidth()) {
                    val openClaims = claims.filter { !it.item.closed }
                    if (openClaims.isNotEmpty()) {
                        val due = openClaims.sumOf { it.item.amount }
                        val got = openClaims.sumOf { it.item.received }
                        ProgressBlock(
                            "報銷：收回多少了", "已收 ${pctOf(got, due)}%", got.toFloat() / due.coerceAtLeast(1L), cute.income,
                            "應收 ${formatMoney(due)}・已收 ${formatMoney(got)}", "${ownerLabel(who)}還欠你", formatMoney(owed),
                        )
                    } else if (claims.isNotEmpty()) {
                        AmountLine("${ownerLabel(who)}還欠你", formatMoney(0L), cute.expense)
                        AmountLine("已收", formatMoney(claims.sumOf { it.item.received }), cute.income)
                    }
                    if (debtRefs.isNotEmpty() && groups.isEmpty()) AmountLine("你欠他", formatMoney(0L), cute.expense)
                    groups.sortedBy { if (it.currency.isEmpty()) 0 else 1 }.forEachIndexed { i, g ->
                        val total = g.refs.sumOf { it.item.totalUnits }
                        val left = g.remainingUnits
                        fun u(v: Long) = if (g.currency.isEmpty()) formatMoney(v) else formatFx(v, g.currency)
                        if (openClaims.isNotEmpty() || i > 0) Spacer(Modifier.height(12.dp))
                        ProgressBlock(
                            "欠款（${if (g.currency.isEmpty()) "台幣" else Currencies.of(g.currency).name}）", "已還 ${pctOf(total - left, total)}%",
                            (total - left).toFloat() / total.coerceAtLeast(1L), cute.accent2,
                            "欠 ${u(total)}・已還 ${u(total - left)}", "還欠", u(left), cute.expense,
                        )
                    }
                    if (debtRefs.isNotEmpty()) {
                        // 兩個方向都有時，寫淨額
                        AmountDivider()
                        AmountLine(
                            "淨額",
                            when {
                                net > 0L -> "${ownerLabel(who)}還欠你 ${formatMoney(net)}"
                                net < 0L -> "你還欠${ownerLabel(who)} ${formatMoney(-net)}"
                                else -> "兩清"
                            },
                            if (net >= 0L) cute.income else cute.expense, strong = true,
                        )
                    }
                }
                if (claims.isNotEmpty()) {
                    Text("${ownerLabel(who)}欠我（報銷）", style = MaterialTheme.typography.titleMedium)
                    claims.forEach { c ->
                        val item = c.item
                        val status = when {
                            !item.closed -> "待收 ${formatMoney(item.remaining)}"
                            item.received < item.amount -> "不追了，少收 ${formatMoney(item.amount - item.received)}"
                            else -> "已收齊"
                        }
                        CuteCard(Modifier.fillMaxWidth().clickable { onEdit(c.txn.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyLarge)
                                    Text("應收 ${formatMoney(item.amount)}" + if (item.received > 0L) "・已收 ${formatMoney(item.received)}" else "", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                }
                                Text(status, style = MaterialTheme.typography.labelLarge, color = if (!item.closed) cute.income else cute.sub)
                            }
                        }
                    }
                }
                if (debtRefs.isNotEmpty()) {
                    Text("我欠${ownerLabel(who)}（欠款）", style = MaterialTheme.typography.titleMedium)
                    debtRefs.forEach { r ->
                        val item = r.item
                        val back = item.pays.sumOf { it.amount }
                        val status = when {
                            !item.done -> "待還 " + if (r.currency.isEmpty()) formatMoney(item.remainingTwd) else formatFx(item.remainingUnits, r.currency)
                            item.effective < item.amount -> "已還清，免 ${formatMoney(item.amount - item.effective)}"
                            item.effective > item.amount -> "已還清，多付 ${formatMoney(item.effective - item.amount)}"
                            else -> "已還清"
                        }
                        CuteCard(Modifier.fillMaxWidth().clickable { onEdit(r.t.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(billLabel(d, r.t), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "欠 " + (if (r.currency.isEmpty()) formatMoney(item.amount) else "${formatFx(item.totalUnits, r.currency)}（約 ${formatMoney(item.amount)}）") +
                                            if (back > 0L) "・已還 ${formatMoney(back)}" else "",
                                        style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                    )
                                }
                                Text(status, style = MaterialTheme.typography.labelLarge, color = if (!item.done) cute.expense else cute.sub)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("紀錄", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("點右邊的 ⋯ 可以修改或刪除", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
                if (recs.isEmpty()) {
                    Text(if (debtRefs.isEmpty()) "還沒有收款紀錄" else "還沒有收款、還款紀錄", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
                recs.forEachIndexed { n, r ->
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${dayTimeLabel(r.day, r.time)}　${r.line}", style = MaterialTheme.typography.bodyLarge)
                                Text(r.personSub, style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                            Box {
                                TextButton(onClick = { menuFor = n }) { Text("⋯", style = MaterialTheme.typography.titleLarge) }
                                DropdownMenu(expanded = menuFor == n, onDismissRequest = { menuFor = null }) {
                                    if (r.claim != null && r.pay != null) {
                                        DropdownMenuItem(text = { Text("修改") }, onClick = { menuFor = null; editing = Triple(r.claim, r.payIndex, r.pay) })
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (r.kind == 2) "刪除這次抵銷（兩邊一起）" else "刪除") },
                                        onClick = { menuFor = null; r.onDelete(vm) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (owed > 0L || groups.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (owed > 0L) Button(onClick = onReceive, modifier = Modifier.weight(1f)) { Text("收款") }
                    // 按鈕固定最多三顆：幣別再多也只有一顆「還款」，進去一頁還完全部幣別
                    if (groups.isNotEmpty()) Button(onClick = onRepay, modifier = Modifier.weight(1f)) { Text("還款") }
                    if (owed > 0L && groups.isNotEmpty()) Button(onClick = onOffset, modifier = Modifier.weight(1f)) { Text("抵銷") }
                }
            }
        }
    }

    editing?.let { entry ->
        ReimbPayEditDialog(vm, entry, onClose = { editing = null })
    }
}

/** 修改一筆收款（金額、帳戶、日期時間） */
@Composable
private fun ReimbPayEditDialog(vm: MoneyViewModel, entry: Triple<Claim, Int, ReimbPay>, onClose: () -> Unit) {
    val d = vm.data
    val (c, n, pay) = entry
    val payAcc0 = pay.accountId?.let { d.accMap[it] }
    var amt by remember { mutableStateOf(if (payAcc0 != null && payAcc0.isForeign) fxExpr(pay.fxAmount, payAcc0.cur.decimals) else pay.amount.toString()) }
    var accId by remember { mutableStateOf(pay.accountId) }
    val editAcc = accId?.let { d.accMap[it] }
    val editFx = editAcc?.takeIf { it.isForeign }
    val editDec = editFx?.cur?.decimals ?: 0
    // 這筆帳是外幣付的，才能改成收外幣（同幣別的外幣帳戶）
    val fxOptions = d.visibleAccounts.filter { a -> a.isForeign && d.fxAccountOf(c.txn)?.currency == a.currency }
    fun switchAccount(a: Account) {
        val toFx = a.isForeign
        if (toFx != (editFx != null)) {
            amt = if (toFx) fxExpr(c.txn.twdToFxAt(amt.toLongOrNull() ?: 0L), a.cur.decimals)
            else c.txn.fxToTwdAt(parseFx(amt, editDec) ?: 0L).toString()
        }
        accId = a.id
    }
    var day by remember { mutableStateOf(pay.day) }
    var timeMin by remember { mutableStateOf(pay.time) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickPayAcc by remember { mutableStateOf(false) }
    val payTries = rememberNeedTries()
    val payAmt = if (editFx != null) parseFx(amt, editDec) ?: 0L else amt.toLongOrNull() ?: 0L
    val payNeed = firstNeed(if (payAmt <= 0L) Need("amt", "請輸入這次收到的金額") else null)
    val payView = NeedView(payNeed, payTries.count)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("修改收款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactField(
                    amt, { v -> amt = if (editFx != null) fxInput(v, editDec) else v.filter { ch -> ch.isDigit() }.take(9) },
                    "金額", Modifier.fillMaxWidth(), number = true, decimal = editFx != null && editDec > 0, prefix = editFx?.cur?.symbol?.trim() ?: tw.moneybook.app.Money.twd,
                    error = payView.on("amt"),
                )
                payView.Message("amt")
                PickerButton(editAcc?.name ?: "選擇帳戶", { pickPayAcc = true })
                if (pickPayAcc) {
                    AccountPickDialog(
                        vm = vm,
                        accounts = d.visibleAccounts.filter { !it.isForeign } + fxOptions,
                        selectedId = accId,
                        onPick = { a -> switchAccount(a); pickPayAcc = false },
                        onDismiss = { pickPayAcc = false },
                        title = "存進哪個帳戶",
                    )
                }
                if (editFx != null) {
                    Text("收外幣：沖掉的台幣用這筆消費當時的匯率換算。", style = MaterialTheme.typography.labelSmall, color = LocalCute.current.sub)
                }
                ChipRow {
                    CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                    CuteChip(if (timeMin >= 0) tw.moneybook.app.formatTime(timeMin) else "未設定時間", false, { pickTime = true }, icon = "vec:clock")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (payNeed != null) { payTries.count++; return@TextButton }
                val newPay = if (editFx != null) {
                    val fxv = parseFx(amt, editDec) ?: 0L
                    ReimbPay(day, accId, c.txn.fxToTwdAt(fxv), timeMin, fxv)
                } else ReimbPay(day, accId, amt.toLongOrNull() ?: 0L, timeMin)
                vm.editReimbPay(c.txn.id, c.index, n, newPay)
                onClose()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
    if (pickDate) {
        CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "收到報銷款的日期")
    }
    if (pickTime) {
        TimePickerDialog(timeMin, { timeMin = it; pickTime = false }, { pickTime = false })
    }
}

// ───────────────────────── 還款（勾選要還的帳） ─────────────────────────

/** 還款頁裡的一種幣別：用哪個帳戶還、還多少（沒自己改過就跟著勾選的合計）、實際扣掉的台幣、少還時算不算還清 */
private class RepaySec(accId: Long?) {
    var accId by mutableStateOf(accId)
    var amountText by mutableStateOf<String?>(null)
    var twdInput by mutableStateOf<String?>(null)
    var settle by mutableStateOf(false)
}

/** 台幣欠款只能用台幣帳戶還；外幣欠款也可以用同幣別的外幣帳戶還。投資帳戶不能拿來還錢 */
private fun repayAccounts(d: AppData, currency: String): List<Account> =
    d.visibleAccounts.filter { it.type != AccountType.INVEST && (!it.isForeign || (currency.isNotEmpty() && it.currency == currency)) }

/** 幣別的名字：台幣、日圓 JPY */
private fun currencyName(c: String): String = if (c.isEmpty()) "台幣" else "${Currencies.of(c).name} $c"

/** 還款頁一種幣別的計算結果 */
private class RepayCalc(d: AppData, val g: DebtGroup, val s: RepaySec, isOn: (DebtRef) -> Boolean) {
    val currency = g.currency
    val fx = currency.isNotEmpty()
    val dec = Currencies.of(currency).decimals
    val active = g.refs.filter(isOn)
    val selUnits = active.sumOf { it.item.remainingUnits }
    val selTwd = active.sumOf { it.item.remainingTwd }
    val accounts = repayAccounts(d, currency)
    val acc = s.accId?.let { d.accMap[it] }
    val shown = s.amountText ?: if (selUnits <= 0L) "" else if (fx) fxPlain(selUnits, dec) else selUnits.toString()
    val units = if (fx) (parseFx(shown, dec) ?: 0L) else (shown.filter { it.isDigit() }.toLongOrNull() ?: 0L)
    val estTwd = if (selUnits > 0L) scaleRound(selTwd, units.coerceAtMost(selUnits), selUnits) else 0L
    val twdAsk = fx && acc != null && !acc.isForeign
    val twd = s.twdInput?.filter { it.isDigit() }?.toLongOrNull() ?: estTwd
    val short = units in 1 until selUnits
    val over = !fx && units > selUnits

    /** 這一區這次要還：有勾選、金額大於 0（全部不勾或金額清成 0 就跳過） */
    val on = active.isNotEmpty() && units > 0L
    fun unitText(v: Long) = if (fx) formatFx(v, currency) else formatMoney(v)
    fun part() = DebtRepayPart(currency, s.accId!!, units, if (twdAsk) twd else 0L, short && s.settle, active.map { it.key }.toSet())
}

/**
 * 還款給某個人：一頁還完全部幣別，每種幣別一區（做法和收款頁一樣，勾選這次要還的帳、金額預設是勾選的合計，可以改），
 * 每一區各自選帳戶；日期時間共用。不想還的那一區全部不勾，或金額清成 0 就跳過。
 * 還得比勾選的少：選「繼續欠」或「這樣就算還清」；台幣欠款還得比較多：算還清，多的加在最後一項的支出上。
 * 用台幣帳戶還外幣欠款要填實際扣掉的台幣；用同幣別的外幣帳戶還就直接扣外幣（見 withDebtRepaid）。
 */
@Composable
private fun DebtRepayPage(vm: MoneyViewModel, who: String, onBack: () -> Unit, onDone: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    // 台幣排最前面，外幣照欠最久的順序
    val groups = d.debtGroups().filter { it.who == who }.sortedBy { if (it.currency.isEmpty()) 0 else 1 }
    val selected = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    fun isOn(r: DebtRef) = selected[r.key] != false
    val secs = remember(groups.joinToString(",") { it.currency }) {
        groups.associate { g ->
            val accs = repayAccounts(d, g.currency)
            g.currency to RepaySec(d.frequentAccounts(5).firstOrNull { f -> accs.any { it.id == f.id } }?.id ?: accs.firstOrNull()?.id)
        }
    }
    val calcs = groups.map { g -> RepayCalc(d, g, secs.getValue(g.currency)) { isOn(it) } }
    val parts = calcs.filter { it.on }
    var pickAccFor by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var timeMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val tries = rememberNeedTries()
    val need = when {
        calcs.isEmpty() -> null
        calcs.all { it.active.isEmpty() } -> Need("amount${calcs.first().currency}", "請先勾選這次要還的帳")
        parts.isEmpty() -> Need("amount${calcs.first { it.active.isNotEmpty() }.currency}", "請輸入還了多少")
        else -> parts.firstNotNullOfOrNull { c ->
            firstNeed(
                if (c.fx && c.units > c.selUnits) Need("amount${c.currency}", "外幣不能還得比勾選的多（${formatFx(c.selUnits, c.currency)}）") else null,
                if (c.acc == null) Need("acc${c.currency}", "請選擇帳戶") else null,
                if (c.twdAsk && c.twd <= 0L) Need("twd${c.currency}", "請輸入實際扣掉的台幣金額") else null,
            )
        }
    }
    val nv = NeedView(need, tries.count)

    SubPage("還款・${ownerLabel(who)}", onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 總結：一行一種幣別
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("我還欠${ownerLabel(who)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Spacer(Modifier.height(4.dp))
                    calcs.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(currencyName(c.currency), style = MaterialTheme.typography.bodyLarge, color = cute.sub, modifier = Modifier.weight(1f))
                            Text(
                                c.unitText(c.g.remainingUnits) + (if (c.fx) "（約 ${formatMoney(c.g.remainingTwd)}・共 ${c.g.refs.size} 筆）" else "（共 ${c.g.refs.size} 筆）"),
                                style = MaterialTheme.typography.bodyLarge, color = cute.expense,
                            )
                        }
                    }
                    if (calcs.isEmpty()) Text("沒有要還的欠款", style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton("全選", { calcs.forEach { c -> c.g.refs.forEach { selected[it.key] = true }; c.s.amountText = null } }, compact = true)
                        SoftButton("全不選", { calcs.forEach { c -> c.g.refs.forEach { selected[it.key] = false }; c.s.amountText = null } }, compact = true)
                    }
                }
                ChipRow {
                    CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                    CuteChip(tw.moneybook.app.formatTime(timeMin), false, { pickTime = true }, icon = "vec:clock")
                }
                calcs.forEach { c ->
                    val k = c.currency
                    CuteCard(Modifier.fillMaxWidth()) {
                        Text(currencyName(k), style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("這次勾選 ${c.active.size} 筆，合計", style = MaterialTheme.typography.bodyMedium, color = cute.sub, modifier = Modifier.weight(1f))
                            Text(c.unitText(c.selUnits), style = MaterialTheme.typography.titleMedium, color = cute.expense)
                        }
                        Spacer(Modifier.height(6.dp))
                        c.g.refs.forEach { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(cute.soft)
                                    .alpha(if (isOn(r)) 1f else 0.55f).padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(checked = isOn(r), onCheckedChange = { selected[r.key] = it; c.s.amountText = null })
                                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(billLabel(d, r.t), style = MaterialTheme.typography.bodyLarge)
                                    if (billNote(r.t).isNotEmpty()) Text(catPath(d, r.t), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                    Text(
                                        "還剩 ${c.unitText(r.item.remainingUnits)}" + (if (c.fx) "（約 ${formatMoney(r.item.remainingTwd)}）" else "") +
                                            if (r.item.pays.isNotEmpty()) "・已還 ${formatMoney(r.item.pays.sumOf { it.amount })}" else "",
                                        style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (c.fx) "這次還了多少（$k）" else "這次還了多少", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Spacer(Modifier.height(4.dp))
                        CompactField(
                            c.shown, { v -> c.s.amountText = if (c.fx) fxInput(v, c.dec) else v.filter { ch -> ch.isDigit() }.take(9) },
                            "金額", Modifier.fillMaxWidth().needInView(nv, "amount$k"), number = true, decimal = c.fx && c.dec > 0,
                            prefix = if (c.fx) Currencies.of(k).symbol.trim() else tw.moneybook.app.Money.twd,
                            error = nv.on("amount$k"),
                        )
                        nv.Message("amount$k")
                        if (c.active.isNotEmpty() && c.units <= 0L && calcs.size > 1) {
                            Text("金額是 0：這次不還${currencyName(k)}", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                        if (c.short) {
                            Spacer(Modifier.height(6.dp))
                            Text("比勾選的少 ${c.unitText(c.selUnits - c.units)}", style = MaterialTheme.typography.labelLarge, color = cute.expense)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CuteChip("還沒還完，剩下的繼續欠", !c.s.settle, { c.s.settle = false })
                                CuteChip("這樣就算還清了", c.s.settle, { c.s.settle = true })
                            }
                            if (c.s.settle) Text("少付的 ${c.unitText(c.selUnits - c.units)} 不用還，從那幾筆的支出裡扣掉。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                        if (c.over) {
                            Spacer(Modifier.height(6.dp))
                            Text("比勾選的多 ${c.unitText(c.units - c.selUnits)}：算還清，多付的錢（手續費、湊整）加在最後一筆的支出上。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("從哪個帳戶還", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Spacer(Modifier.height(4.dp))
                        PickerButton(c.acc?.let { it.name + if (it.isForeign) "（${it.currency}）" else "" } ?: "選擇帳戶", { pickAccFor = k })
                        nv.Message("acc$k")
                        if (c.twdAsk) {
                            Spacer(Modifier.height(8.dp))
                            CompactField(
                                c.s.twdInput ?: c.estTwd.toString(), { v -> c.s.twdInput = v.filter { ch -> ch.isDigit() }.take(10) },
                                "實際扣掉的台幣", Modifier.fillMaxWidth().needInView(nv, "twd$k"), number = true, prefix = tw.moneybook.app.Money.twd, error = nv.on("twd$k"),
                            )
                            nv.Message("twd$k")
                            val diff = c.twd - c.estTwd
                            if (c.units > 0L && c.estTwd > 0L && diff != 0L) {
                                Text("預估 ${formatMoney(c.estTwd)} → 實際 ${formatMoney(c.twd)}，${if (diff > 0) "多" else "少"} ${formatMoney(kotlin.math.abs(diff))}；還清後支出改成實際金額。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                            }
                        } else if (c.fx && c.acc != null) {
                            Text("直接從「${c.acc.name}」扣 ${formatFx(c.units, k)}，支出照記的時候的預估匯率。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                    }
                }
                Text("還款會從帳戶扣錢，但不算支出（這筆在記的那天已經算過了）。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            }
            Box(Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = {
                        if (need != null) { tries.count++; return@Button }
                        if (vm.repayDebts(who, parts.map { it.part() }, day, timeMin)) onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("確認還款 " + parts.joinToString("＋") { it.unitText(it.units) }.ifEmpty { formatMoney(0L) }) }
            }
        }
    }
    pickAccFor?.let { k ->
        val c = calcs.firstOrNull { it.currency == k }
        if (c != null) AccountPickDialog(
            vm = vm, accounts = c.accounts, selectedId = c.s.accId,
            onPick = { c.s.accId = it.id; c.s.twdInput = null; pickAccFor = null }, onDismiss = { pickAccFor = null }, title = "從哪個帳戶還",
        )
    }
    if (pickDate) CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "還款的日期")
    if (pickTime) TimePickerDialog(timeMin, { timeMin = it; pickTime = false }, { pickTime = false })
}

// ───────────────────────── 互相抵銷 ─────────────────────────

/** 互相抵銷頁裡的一種外幣：匯率和抵掉的外幣兩格，改一格另一格跟著算（都是 null＝用記的時候的預估匯率） */
private class OffsetFx {
    var rate by mutableStateOf<String?>(null)
    var units by mutableStateOf<String?>(null)
}

/**
 * 和某個人互相抵銷：兩邊各自勾選要抵的帳（他欠我的報銷、我欠他的欠款），抵的金額是兩邊合計比較小的那一個（外幣欠款用預估台幣比），
 * 不動任何帳戶、不算收入或支出（見 withOffset）。先抵台幣欠款，剩下的抵外幣欠款；外幣欠款要決定抵掉多少外幣：
 * 匯率先帶記的時候的預估值，可以改匯率或直接填外幣，兩格互相連動；抵掉的台幣固定。
 */
@Composable
private fun OffsetPage(vm: MoneyViewModel, who: String, onBack: () -> Unit, onDone: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val claims = claimsOf(d.bookTxns).filter { it.who == who && !it.item.closed && it.item.remaining > 0L }.sortedWith(compareBy({ it.txn.day }, { it.txn.id }))
    // 畫面上台幣欠款在前、外幣欠款在後
    val refs = d.debtGroups().filter { it.who == who }.flatMap { it.refs }
        .sortedWith(compareBy({ if (it.currency.isEmpty()) 0 else 1 }, { it.currency }, { it.t.day }, { it.t.id }, { it.index }))
    val selR = remember { mutableStateMapOf<String, Boolean>() }
    val selD = remember { mutableStateMapOf<Pair<Long, Int>, Boolean>() }
    val onR = claims.filter { selR[it.key] != false }
    // 分配照日期由舊到新（和 withOffset 一樣）
    val onD = refs.filter { selD[it.key] != false }.sortedWith(compareBy({ it.t.day }, { it.t.id }, { it.index }))
    val sumR = onR.sumOf { it.item.remaining }
    val sumD = onD.sumOf { it.item.remainingTwd }
    val x = minOf(sumR, sumD)
    val twdAlloc = offsetDebtTwd(x, onD)
    val fxTwd = offsetFxTwd(x, onD)
    val fxState = remember { HashMap<String, OffsetFx>() }
    // 勾選變了，匯率和外幣都回到預估
    val selKey = onR.joinToString(",") { it.key } + "|" + onD.joinToString(",") { "${it.key}" }
    LaunchedEffect(selKey) { fxState.values.forEach { it.rate = null; it.units = null } }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }

    /** 一種外幣這次的計算：抵掉的台幣、外幣（預設照預估匯率）、上限 */
    class FxCalc(val c: String, val twd: Long) {
        val dec = Currencies.of(c).decimals
        val mine = onD.filter { it.currency == c }
        val st = fxState.getOrPut(c) { OffsetFx() }
        val maxUnits = mine.sumOf { it.item.remainingUnits }
        val defUnits = offsetFxDefaultUnits(twd, mine)
        val units = st.units?.let { parseFx(it, dec) } ?: if (st.units == null) defUnits else 0L
        val estRate = tw.moneybook.app.impliedRate(mine.sumOf { it.item.remainingTwd }, maxUnits, dec)
        val rateShown = st.rate ?: (if (st.units == null) estRate else tw.moneybook.app.impliedRate(twd, units, dec))?.let { tw.moneybook.app.rateText(it) } ?: ""
        val unitsShown = st.units ?: fxPlain(defUnits, dec)
    }
    val fxCalcs = fxTwd.map { (c, t) -> FxCalc(c, t) }

    val tries = rememberNeedTries()
    val need = if (x <= 0L) Need("pick", "兩邊都要勾選還沒結清的帳") else fxCalcs.firstNotNullOfOrNull { f ->
        when {
            f.units <= 0L -> Need("fx${f.c}", "請輸入抵掉的${Currencies.of(f.c).name}")
            f.units > f.maxUnits -> Need("fx${f.c}", "不能超過勾選的還欠的 ${formatFx(f.maxUnits, f.c)}")
            else -> null
        }
    }
    val nv = NeedView(need, tries.count)

    SubPage("互相抵銷・${ownerLabel(who)}", onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("這次抵銷", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Text(formatMoney(x), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "取兩邊勾選的合計比較小的：${formatMoney(sumR)} 和 ${formatMoney(sumD)}" + (if (fxTwd.isNotEmpty() || onD.any { it.currency.isNotEmpty() }) "（外幣欠款用預估台幣）" else "") +
                            "。不動任何帳戶、不算收入或支出。",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub,
                    )
                    if (x > 0L) {
                        // 抵銷後還欠的：台幣一個數字，外幣各寫外幣
                        val twdLeft = refs.filter { it.currency.isEmpty() }.sumOf { it.item.remainingUnits } -
                            onD.indices.filter { onD[it].currency.isEmpty() }.sumOf { twdAlloc[it] }
                        val parts = buildList {
                            if (twdLeft > 0L) add(formatMoney(twdLeft))
                            refs.map { it.currency }.filter { it.isNotEmpty() }.distinct().forEach { c ->
                                val left = refs.filter { it.currency == c }.sumOf { it.item.remainingUnits } - (fxCalcs.firstOrNull { it.c == c }?.units ?: 0L)
                                if (left > 0L) add(formatFx(left, c))
                            }
                        }
                        Text(
                            "抵銷後：${ownerLabel(who)}還欠你 ${formatMoney(claims.sumOf { it.item.remaining } - x)}・你還欠${ownerLabel(who)} " + parts.joinToString("＋").ifEmpty { formatMoney(0L) },
                            style = MaterialTheme.typography.bodySmall, color = cute.ink,
                        )
                    }
                }
                CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                nv.Message("pick")
                Text("${ownerLabel(who)}欠我（勾選要抵的）", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                claims.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.card).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = selR[c.key] != false, onCheckedChange = { selR[c.key] = it })
                        Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("剩 ${formatMoney(c.item.remaining)}", style = MaterialTheme.typography.bodyMedium, color = cute.income)
                    }
                }
                Text("我欠${ownerLabel(who)}（勾選要抵的，先抵台幣欠款）", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                refs.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.card).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = selD[r.key] != false, onCheckedChange = { selD[r.key] = it })
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(billLabel(d, r.t), style = MaterialTheme.typography.bodyMedium)
                            if (r.currency.isNotEmpty()) {
                                Text("剩 ${formatFx(r.item.remainingUnits, r.currency)}（預估約 ${formatMoney(r.item.remainingTwd)}）", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                            }
                        }
                        Text(
                            if (r.currency.isEmpty()) "剩 ${formatMoney(r.item.remainingUnits)}" else formatFx(r.item.remainingUnits, r.currency),
                            style = MaterialTheme.typography.bodyMedium, color = cute.expense,
                        )
                    }
                }
                // 抵到外幣欠款：每種外幣一張，匯率和外幣兩格互相連動
                fxCalcs.forEach { f ->
                    val name = Currencies.of(f.c).name
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${name}欠款：這次抵掉多少", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text("抵 ${formatMoney(f.twd)}", style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("匯率（1 ${f.c} = 幾元台幣）", style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
                                Spacer(Modifier.height(4.dp))
                                CompactField(
                                    f.rateShown,
                                    { v ->
                                        val t = fxInput(v, 6)
                                        f.st.rate = t
                                        val r = t.toDoubleOrNull()
                                        if (r != null && r > 0.0) f.st.units = fxPlain(tw.moneybook.app.twdToFx(f.twd, f.dec, r), f.dec)
                                    },
                                    "匯率", Modifier.fillMaxWidth(), number = true, decimal = true,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("抵掉的$name", style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
                                Spacer(Modifier.height(4.dp))
                                CompactField(
                                    f.unitsShown,
                                    { v ->
                                        val t = fxInput(v, f.dec)
                                        f.st.units = t
                                        f.st.rate = parseFx(t, f.dec)?.let { u -> tw.moneybook.app.impliedRate(f.twd, u, f.dec) }?.let { tw.moneybook.app.rateText(it) } ?: ""
                                    },
                                    "金額", Modifier.fillMaxWidth().needInView(nv, "fx${f.c}"), number = true, decimal = f.dec > 0,
                                    prefix = Currencies.of(f.c).symbol.trim(), error = nv.on("fx${f.c}"),
                                )
                            }
                        }
                        nv.Message("fx${f.c}")
                        Spacer(Modifier.height(6.dp))
                        Text("匯率先帶記的時候的預估值；改匯率，${name}跟著重算；直接填${name}，匯率跟著變。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        Text("抵掉的台幣固定是 ${formatMoney(f.twd)}，那幾筆的支出就照這個算；${name}不能超過勾選的還欠的 ${formatFx(f.maxUnits, f.c)}。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = {
                        if (need != null) { tries.count++; return@Button }
                        val t = LocalTime.now()
                        val units = fxCalcs.associate { it.c to it.units }
                        if (vm.offsetDebts(who, onR.map { it.txn.id to it.index }.toSet(), onD.map { it.key }.toSet(), day, t.hour * 60 + t.minute, units)) onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("確認抵銷 ${formatMoney(x)}") }
            }
        }
    }
    if (pickDate) CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "抵銷的日期")
}

// ───────────────────────── 依分帳結論結清 ─────────────────────────

/** 分帳結論裡和我有關的一筆錢（畫面上的一列） */
private class SettleMoveRow(receive: Boolean, amt: String, acc: Long?) {
    var receive by mutableStateOf(receive)
    var who by mutableStateOf("")
    var amt by mutableStateOf(amt)
    var acc by mutableStateOf(acc)
}

/** 結清頁的一項：報銷（別人欠我）或欠款（我欠別人） */
private class SettleItem(val key: tw.moneybook.app.ItemKey, val debt: Boolean, val txn: Txn, val who: String, val twd: Long, val fx: String) {
    val id: String get() = (if (debt) "d" else "r") + "${key.txnId}_${key.index}"
}

/**
 * 依分帳結論結清（見 Settle.kt）：第一步勾要一起結清的帳（可以用標籤、期間篩選，整個人或某幾筆取消），
 * 第二步照分帳軟體的結論填實際收到／付出的錢；和照理說的不一樣時選「差的算我的」或「繼續欠」（勾差的留在哪幾筆）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettlePage(vm: MoneyViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    var step by remember { mutableStateOf(0) }
    // 篩選：標籤（多選，有其中一個就算）、期間（0 全部、1 近 3 個月、2 自訂）
    var tags by remember { mutableStateOf(setOf<String>()) }
    var period by remember { mutableStateOf(0) }
    var from by remember { mutableStateOf(today - 30) }
    var to by remember { mutableStateOf(today) }
    var pick by remember { mutableStateOf("") }
    // 取消勾選的項目（SettleItem.id）
    val off = remember { mutableStateListOf<String>() }

    val all = remember(d) {
        claimsOf(d.bookTxns).filter { !it.item.closed && it.item.remaining > 0L }
            .map { SettleItem(tw.moneybook.app.ItemKey(it.txn.id, it.index), false, it.txn, it.who, it.item.remaining, "") } +
            d.bookTxns.flatMap { t ->
                t.debtItems.mapIndexedNotNull { i, it ->
                    if (it.remainingUnits <= 0L) null
                    else SettleItem(
                        tw.moneybook.app.ItemKey(t.id, i), true, t, it.who.trim(), it.remainingTwd,
                        if (t.debtCurrency.isNotEmpty()) formatFx(it.remainingUnits, t.debtCurrency) else "",
                    )
                }
            }
    }
    val tagList = all.flatMap { it.txn.tags }.distinct().sorted()
    val start3 = LocalDate.now().minusMonths(3).toEpochDay()
    val shown = all.filter { s ->
        (tags.isEmpty() || s.txn.tags.any { it in tags }) && when (period) {
            1 -> s.txn.day >= start3
            2 -> s.txn.day in from..to
            else -> true
        }
    }
    val sel = shown.filter { it.id !in off }
    val oweMe = sel.filter { !it.debt }.sumOf { it.twd }
    val iOwe = sel.filter { it.debt }.sumOf { it.twd }
    val expected = oweMe - iOwe

    // 第二步
    val okAccs = d.visibleAccounts.filter { !it.isForeign }
    val defAcc = d.frequentAccounts(okAccs.size.coerceAtLeast(3)).firstOrNull { f -> okAccs.any { it.id == f.id } && f.type != AccountType.CARD && f.type != AccountType.LOAN }?.id
        ?: okAccs.firstOrNull { it.type != AccountType.CARD && it.type != AccountType.LOAN }?.id ?: okAccs.firstOrNull()?.id
    val moves = remember { mutableStateListOf<SettleMoveRow>() }
    var day by remember { mutableStateOf(today) }
    var timeMin by remember { mutableStateOf(java.time.LocalTime.now().let { it.hour * 60 + it.minute }) }
    var pickTime by remember { mutableStateOf(false) }
    var pickAcc by remember { mutableStateOf(-1) }
    // 正在挑第幾筆錢的「從誰／給誰」（-1 是沒有）
    var pickWho by remember { mutableStateOf(-1) }
    var keepMode by remember { mutableStateOf(false) }
    val keep = remember { mutableStateListOf<String>() }
    val actual = moves.sumOf { m -> (m.amt.toLongOrNull() ?: 0L).let { if (m.receive) it else -it } }
    val diff = actual - expected
    // 繼續欠：少收留在報銷（別人欠我）、多收留在欠款（我欠別人），從最新的排
    val keepChoices = sel.filter { it.debt == (diff > 0L) }.sortedWith(compareByDescending<SettleItem> { it.txn.day }.thenByDescending { it.txn.id })
    val keepSum = keepChoices.filter { it.id in keep }.sumOf { it.twd }
    val tries = rememberNeedTries()
    val need = if (step == 0) firstNeed(if (sel.isEmpty()) Need("pick", "請至少勾一筆要結清的帳") else null)
    else firstNeed(
        *moves.mapIndexed { i, m ->
            when {
                (m.amt.toLongOrNull() ?: 0L) <= 0L -> Need("amt$i", "請輸入金額")
                m.acc == null -> Need("acc$i", "請選錢進出哪個帳戶")
                else -> null
            }
        }.toTypedArray(),
        if (moves.isEmpty()) Need("moves", "請至少填一筆實際收到或付出的錢") else null,
        if (diff != 0L && keepMode && keepChoices.isEmpty()) Need("keep", if (diff < 0L) "沒有別人欠我的帳可以留，請選「差的算我的」" else "沒有我欠別人的帳可以留，請選「差的算我的」") else null,
        if (diff != 0L && keepMode && keepSum < kotlin.math.abs(diff)) Need("keep", "勾的帳加起來不夠留下 ${formatMoney(kotlin.math.abs(diff))}") else null,
    )
    val nv = NeedView(need, tries.count)

    SubPage(if (step == 0) "依分帳結論結清" else "分帳軟體的結論", { if (step == 1) { step = 0 } else onBack() }) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step == 0) {
                    if (tagList.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("標籤（可以多選，有其中一個就算）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            ChipRow {
                                tagList.forEach { tg -> CuteChip("#$tg", tg in tags, { tags = if (tg in tags) tags - tg else tags + tg }) }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("期間", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            ChipRow {
                                CuteChip("全部", period == 0, { period = 0 })
                                CuteChip("近 3 個月", period == 1, { period = 1 })
                                CuteChip(if (period == 2) "自訂 ${shortDate(from)}～${shortDate(to)}" else "自訂", period == 2, { period = 2; pick = "from" })
                            }
                        }
                    }
                    if (shown.isEmpty()) item { EmptyHint(d.prefs.mascot, "沒有符合的待報銷或欠款") }
                    // 依人分組：別人欠我一組、我欠他一組
                    shown.groupBy { it.debt to it.who }.forEach { (k, list) ->
                        item(key = "g${k.first}_${k.second}") {
                            val allOn = list.all { it.id !in off }
                            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(allOn, { on -> list.forEach { off.remove(it.id) }; if (!on) off.addAll(list.map { it.id }) })
                                    Text(
                                        if (k.first) "我欠${ownerLabel(k.second)}" else "${ownerLabel(k.second)}欠我",
                                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        formatMoney(list.filter { it.id !in off }.sumOf { it.twd }),
                                        style = MaterialTheme.typography.titleMedium, color = if (k.first) cute.expense else cute.income,
                                    )
                                }
                                list.forEach { s ->
                                    Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Checkbox(s.id !in off, { on -> if (on) off.remove(s.id) else off.add(s.id) })
                                        Text(billLabel(d, s.txn), style = MaterialTheme.typography.bodyMedium, color = if (s.id in off) cute.sub else cute.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            formatMoney(s.twd) + if (s.fx.isNotEmpty()) "（${s.fx}）" else "",
                                            style = MaterialTheme.typography.bodyMedium, color = cute.sub, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        CuteCard(Modifier.fillMaxWidth()) {
                            AmountLine("別人欠我", formatMoney(oweMe), cute.income)
                            AmountLine("我欠別人", formatMoney(iOwe), cute.expense)
                            AmountDivider()
                            AmountLine(if (expected >= 0L) "照理說你會收到" else "照理說你要付出", formatMoney(kotlin.math.abs(expected)), cute.ink, strong = true)
                        }
                        nv.Message("pick")
                    }
                } else {
                    item {
                        Text("填你實際收到／付出的錢（分帳結論裡和你有關的那幾筆就好）", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                    moves.forEachIndexed { i, m ->
                        item(key = "m$i") {
                            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ChipRow(Modifier.weight(1f)) {
                                        CuteChip("我收到", m.receive, { m.receive = true })
                                        CuteChip("我付出", !m.receive, { m.receive = false })
                                    }
                                    if (moves.size > 1) {
                                        Text(
                                            "✕", color = cute.sub, style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.clip(CircleShape).clickable { moves.removeAt(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PickerButton(m.who.ifBlank { if (m.receive) "從誰（選填）" else "給誰（選填）" }, { pickWho = i }, Modifier.weight(1f))
                                    CompactField(
                                        m.amt, { v -> m.amt = v.filter { it.isDigit() }.take(9) }, "金額", Modifier.width(120.dp),
                                        number = true, prefix = tw.moneybook.app.Money.twd, error = nv.on("amt$i"),
                                    )
                                }
                                nv.Message("amt$i")
                                Spacer(Modifier.height(8.dp))
                                Text(if (m.receive) "存進哪個帳戶" else "從哪個帳戶付", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                PickerButton(m.acc?.let { d.accMap[it]?.name } ?: "選帳戶", { pickAcc = i })
                                nv.Message("acc$i")
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CuteChip("＋ 再加一筆", false, { moves.add(SettleMoveRow(expected >= 0L, "", defAcc)) })
                            ChipRow {
                                CuteChip(dayLabel(day), false, { pick = "day" }, icon = "vec:calendar")
                                CuteChip(tw.moneybook.app.formatTime(timeMin), false, { pickTime = true }, icon = "vec:clock")
                            }
                        }
                    }
                    item {
                        CuteCard(Modifier.fillMaxWidth()) {
                            AmountLine(if (expected >= 0L) "照理說收到" else "照理說付出", formatMoney(kotlin.math.abs(expected)), cute.ink)
                            AmountLine("實際收到－付出", (if (actual < 0L) "-" else "") + formatMoney(kotlin.math.abs(actual)), cute.ink)
                            AmountDivider()
                            Text(
                                when {
                                    diff == 0L -> "剛好，勾的帳全部結清"
                                    diff < 0L -> "比照理說的少了 ${formatMoney(-diff)}，要怎麼算？"
                                    else -> "比照理說的多了 ${formatMoney(diff)}，要怎麼算？"
                                },
                                style = MaterialTheme.typography.bodyLarge, color = if (diff == 0L) cute.income else cute.expense,
                            )
                            if (diff != 0L) {
                                Spacer(Modifier.height(6.dp))
                                listOf(
                                    false to ("差的算我的" to (if (diff < 0L) "全部結清，少收的 ${formatMoney(-diff)} 算進我的支出" else "全部結清，多的 ${formatMoney(diff)} 從我的支出扣掉（或算收入）")),
                                    true to ("繼續欠" to (if (diff < 0L) "差的 ${formatMoney(-diff)} 留在勾的帳上，之後再收" else "差的 ${formatMoney(diff)} 留在勾的帳上，我之後再還")),
                                ).forEach { (k, txt) ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                            keepMode = k
                                            if (k && keep.isEmpty()) {
                                                // 預設勾最新的幾筆，剛好夠留下差額
                                                var left = kotlin.math.abs(diff)
                                                for (c in keepChoices) { if (left <= 0L) break; keep.add(c.id); left -= c.twd }
                                            }
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        androidx.compose.material3.RadioButton(keepMode == k, null)
                                        Column {
                                            Text(txt.first, style = MaterialTheme.typography.titleSmall)
                                            Text(txt.second, style = MaterialTheme.typography.bodySmall, color = cute.sub)
                                        }
                                    }
                                }
                                if (keepMode) {
                                    Text(if (diff < 0L) "差的留在哪幾筆（還沒收的）" else "差的留在哪幾筆（我還欠的）", style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(top = 6.dp))
                                    keepChoices.forEach { s ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Checkbox(s.id in keep, { on -> if (on) keep.add(s.id) else keep.remove(s.id) })
                                            Text("${ownerLabel(s.who)}・${billLabel(d, s.txn)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatMoney(s.twd), style = MaterialTheme.typography.bodyMedium, color = cute.sub)
                                        }
                                    }
                                    nv.Message("keep")
                                }
                            }
                        }
                        nv.Message("moves")
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        if (need != null) { tries.count++; return@Button }
                        if (step == 0) {
                            if (moves.isEmpty()) moves.add(SettleMoveRow(expected >= 0L, if (expected != 0L) kotlin.math.abs(expected).toString() else "", defAcc))
                            keep.clear()
                            keepMode = false
                            step = 1
                            return@Button
                        }
                        val ok = vm.settleUp(
                            sel.filter { !it.debt }.map { it.key }, sel.filter { it.debt }.map { it.key },
                            moves.map { tw.moneybook.app.SettleMove(it.receive, it.who.trim(), it.amt.toLongOrNull() ?: 0L, it.acc) },
                            day, timeMin,
                            if (keepMode) tw.moneybook.app.SettleDiff.KEEP else tw.moneybook.app.SettleDiff.SELF,
                            keepChoices.filter { it.id in keep }.map { it.key },
                        )
                        if (ok) onDone()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (step == 0) "下一步" else "確認結清", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
    when (pick) {
        "from" -> CuteDatePickerDialog(from, { from = it; pick = "to" }, { pick = "" }, "開始日期")
        "to" -> CuteDatePickerDialog(to, { to = it; pick = "" }, { pick = "" }, "結束日期")
        "day" -> CuteDatePickerDialog(day, { day = it; pick = "" }, { pick = "" }, "日期")
    }
    if (pickTime) TimePickerDialog(timeMin, { timeMin = it; pickTime = false }, { pickTime = false })
    if (pickWho >= 0 && pickWho < moves.size) {
        val m = moves[pickWho]
        // 這次勾的帳裡的人排前面，其他人照以前用過的次數
        val here = sel.map { it.who }.filter { it.isNotBlank() }.distinct()
        val others = (d.reimbNames() + d.debtNames()).distinct().filter { it !in here }
        SettleWhoDialog(
            if (m.receive) "從誰" else "給誰", m.who, here, others,
            onPick = { w -> m.who = w; pickWho = -1 }, onDismiss = { pickWho = -1 },
        )
    }
    if (pickAcc >= 0 && pickAcc < moves.size) {
        val m = moves[pickAcc]
        AccountPickDialog(
            vm = vm, accounts = okAccs, selectedId = m.acc,
            onPick = { a -> m.acc = a.id; pickAcc = -1 }, onDismiss = { pickAcc = -1 },
            title = if (m.receive) "存進哪個帳戶" else "從哪個帳戶付",
        )
    }
}

/**
 * 分帳結清的「從誰／給誰」：上面可以打字（篩選下面的名字，或直接用打的名字），
 * 下面是這次勾的帳裡的人、其他以前用過的人；最下面可以清掉不填。
 */
@Composable
private fun SettleWhoDialog(title: String, current: String, here: List<String>, others: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var q by remember { mutableStateOf("") }
    fun match(l: List<String>) = if (q.isBlank()) l else l.filter { it.contains(q.trim(), ignoreCase = true) }
    val hereF = match(here)
    val othersF = match(others)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactField(q, { q = it.take(12) }, "打字篩選，或輸入其他名字", Modifier.fillMaxWidth())
                val typed = q.trim()
                if (typed.isNotEmpty() && (here + others).none { it.equals(typed, true) }) {
                    WhoRow("用「$typed」", typed == current) { onPick(typed) }
                }
                if (hereF.isNotEmpty()) {
                    Text("這次結清的人", style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(top = 8.dp))
                    hereF.forEach { w -> WhoRow(w, w == current) { onPick(w) } }
                }
                if (othersF.isNotEmpty()) {
                    Text("其他人", style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(top = 8.dp))
                    othersF.forEach { w -> WhoRow(w, w == current) { onPick(w) } }
                }
                if (current.isNotBlank()) {
                    WhoRow("不填", false) { onPick("") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun WhoRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        name, style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else LocalCute.current.ink,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}
