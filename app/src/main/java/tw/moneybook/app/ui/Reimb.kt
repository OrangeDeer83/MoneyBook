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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.ReimbCodec
import tw.moneybook.app.ReimbItem
import tw.moneybook.app.ReimbPay
import tw.moneybook.app.ClaimInput
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
) {
    val cute = LocalCute.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier,
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
private fun catPath(d: AppData, t: Txn): String {
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

// ───────────────────────── 記一筆：這筆的報銷（整頁） ─────────────────────────

/** 這一列可能是還能編輯的對象，也可能是已有收款、只能看的（locked） */
internal class ReimbRow(val locked: ReimbItem?, who: String, amt: String) {
    var who by mutableStateOf(who)
    var amt by mutableStateOf(amt)

    /** 「平分剩下的」模式下，這一列的金額由系統自動算（使用者手動改過就變固定） */
    var auto by mutableStateOf(false)
}

@Composable
fun ReimbEditPage(
    title: String,
    actual: Long,
    cap: Long,
    /** 外幣消費：幣別與這筆的外幣金額（最小單位）；分給多人時直接用外幣輸入每個人的金額，台幣用這筆的匯率換算。台幣消費不用填 */
    fxCur: String = "",
    fxActual: Long = 0L,
    origItems: List<ReimbItem>,
    initOn: Boolean,
    initFull: Boolean,
    initJson: String,
    initWho: String,
    names: List<String>,
    onDone: (on: Boolean, full: Boolean, json: String, who: String) -> Unit,
    onClose: () -> Unit,
) {
    val cute = LocalCute.current
    var on by remember { mutableStateOf(initOn) }
    val hasPays = origItems.any { it.pays.isNotEmpty() }
    // 外幣消費：每個人的金額用外幣輸入（r.amt 是外幣），換算台幣用「這一筆」的匯率（外幣金額 ÷ 台幣實付）
    val fxOn = fxCur.isNotEmpty() && fxActual > 0L && actual > 0L
    val fdec = Currencies.of(fxCur).decimals
    fun fxOf(twd: Long): Long = if (twd == actual) fxActual else scaleRound(twd, fxActual, actual)
    fun twdOf(fx: Long): Long = if (fx == fxActual) actual else scaleRound(fx, actual, fxActual)
    fun rowTwd(r: ReimbRow): Long = if (fxOn) twdOf(parseFx(r.amt, fdec) ?: 0L) else (r.amt.toLongOrNull() ?: 0L)
    fun amtText(twd: Long): String = if (fxOn) fxExpr(fxOf(twd), fdec) else twd.toString()
    val rows = remember {
        mutableStateListOf<ReimbRow>().apply {
            val src = if (initFull) origItems.map { if (it.pays.isEmpty() && !it.closed) it.copy(amount = actual) else it } else ReimbCodec.decode(initJson)
            // 新增、或原本是「一人・全額」：一列、預設全額給這個人（對象沿用原本填的）
            if (src.isEmpty()) add(ReimbRow(null, initWho, amtText(actual)))
            else src.forEach { add(if (it.pays.isNotEmpty() || it.closed) ReimbRow(it, it.who, it.amount.toString()) else ReimbRow(null, it.who, amtText(it.amount))) }
        }
    }
    val lockedAny = rows.any { it.locked != null }
    val total = rows.sumOf { it.locked?.effective ?: rowTwd(it) }

    fun finish() {
        if (!on) {
            onDone(false, true, "", "")
        } else if (rows.size == 1 && rows[0].locked == null && rowTwd(rows[0]) == actual) {
            // 只有一個人、而且是全額：存成「一人・全額」，之後帳目金額改了，報銷金額跟著走
            onDone(true, true, initJson, rows[0].who.trim())
        } else {
            val list = rows.mapNotNull { r ->
                r.locked ?: rowTwd(r).takeIf { it > 0L }?.let { a -> ReimbItem(r.who.trim(), a) }
            }
            if (list.isEmpty()) onDone(false, true, "", "") else onDone(true, false, ReimbCodec.encode(list), "")
        }
    }

    fun addName(name: String) {
        val blank = rows.firstOrNull { it.locked == null && it.who.isBlank() }
        if (blank != null) blank.who = name else rows.add(ReimbRow(null, name, ""))
    }

    var splitMenu by remember { mutableStateOf(false) }
    // 「平分剩下的」模式：沒填金額的人由系統自動分剩下的（含自己），手動改過金額的人就固定
    var restMode by remember { mutableStateOf(false) }
    val lockedSum = rows.sumOf { it.locked?.effective ?: 0L }

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
        val editable = rows.filter { it.locked == null }
        if (editable.isEmpty()) return
        restMode = false
        editable.forEach { it.auto = false }
        fillRows(editable, includeSelf, (actual - lockedSum).coerceAtLeast(0L))
    }

    fun startRest() {
        rows.filter { it.locked == null }.forEach { it.auto = rowTwd(it) <= 0L }
        restMode = true
    }

    // 平分剩下的：固定金額的人不動，自動那幾列每次都依「剩下的」重算
    val restAuto = if (restMode) rows.filter { it.locked == null && it.auto } else emptyList()
    val restFixed = rows.filter { it.locked == null && !it.auto }.sumOf { rowTwd(it) }
    val restPool = (actual - lockedSum - restFixed).coerceAtLeast(0L)
    LaunchedEffect(restMode, restAuto.size, restPool) {
        if (restMode) {
            if (restAuto.isEmpty()) restMode = false else fillRows(restAuto, true, restPool)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().navigationBarsPadding().imePadding()
        ) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(AppIcons.ChevronLeft, contentDescription = "返回") }
                Text("這筆的報銷", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { finish() }) { Text("完成") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("實付", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Text(formatMoney(actual), style = MaterialTheme.typography.titleLarge)
                            if (fxOn) Text(formatFx(fxActual, fxCur), style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("這筆可以報銷", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { on = it }, enabled = !hasPays)
                }
                if (on) {
                    rows.forEachIndexed { i, r ->
                        val lk = r.locked
                        if (lk != null) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(lk.who.ifBlank { "（沒填對象）" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(
                                        "已收 ${formatMoney(lk.received)}" + if (lk.closed) "・已結案" else "・還剩 ${formatMoney(lk.remaining)}",
                                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                                    )
                                }
                                Text(formatMoney(lk.amount), style = MaterialTheme.typography.bodyLarge, color = cute.sub)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactField(r.who, { r.who = it.take(12) }, "對象（選填）", Modifier.weight(1f))
                                CompactField(
                                    r.amt, { v -> r.auto = false; r.amt = if (fxOn) fxInput(v, fdec) else v.filter { c -> c.isDigit() }.take(9) },
                                    "金額", Modifier.width(if (fxOn) 132.dp else 112.dp), number = true, decimal = fxOn && fdec > 0,
                                    prefix = if (fxOn) Currencies.of(fxCur).symbol.trim() else tw.moneybook.app.Money.twd,
                                )
                                Text(
                                    "✕", color = cute.sub, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clip(CircleShape).clickable { rows.removeAt(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
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
                        CuteChip("＋ 新增對象", false, { rows.add(ReimbRow(null, "", "")) })
                        val n = rows.count { it.locked == null }
                        if (n >= 1) CuteChip("平分 ▾", restMode, { splitMenu = true })
                    }
                    val used = rows.map { it.who.trim() }.toSet()
                    val suggest = names.filter { it !in used }.take(8)
                    if (suggest.isNotEmpty()) {
                        Text("常用對象，點一下加入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggest.forEach { nm -> CuteChip(nm, false, { addName(nm) }) }
                        }
                    }
                    val frac = if (actual > 0L) (total.toFloat() / actual.toFloat()).coerceIn(0f, 1f) else 0f
                    CuteCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(cute.soft)) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("可報銷", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                Text(formatMoney(total), style = MaterialTheme.typography.titleLarge, color = cute.income)
                                if (fxOn) Text(formatFx(fxOf(total), fxCur), style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("自己負擔（算進支出）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                Text(formatMoney((actual - total).coerceAtLeast(0L)), style = MaterialTheme.typography.titleLarge)
                                if (fxOn) Text(formatFx(fxOf((actual - total).coerceAtLeast(0L)), fxCur), style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                        }
                        if (cap <= 0L) {
                            // 先填報銷、後填帳目金額：金額還沒輸入時不用提醒上限，儲存時才會依金額限制
                            Text("還沒輸入帳目金額，報銷金額之後不會超過實付。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                        } else if (total > cap) {
                            Text("合計超過上限 ${formatMoney(cap)}，超過的部分不會算。", style = MaterialTheme.typography.labelSmall, color = cute.expense)
                        } else if (total > actual) {
                            Text("比實付多 ${formatMoney(total - actual)}，多的部分收到後算成報銷回饋收入。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                        }
                    }
                    if (lockedAny) {
                        Text("已有收款的對象不能在這裡改，請到「我的 → 報銷」處理。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (splitMenu) {
            val editable = rows.filter { it.locked == null }
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
                            "平分給 $nE 人", "我只是幫忙付，不含我自己",
                            "每人 ${fmt(a1.amounts.firstOrNull() ?: 0L)}", true,
                        ) { splitAll(false); splitMenu = false }
                        SplitOption(
                            "平分給 ${nE + 1} 人（含我）", "我也分一份，除不盡的零頭算我自己",
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

// ───────────────────────── 我的 → 報銷 ─────────────────────────

private sealed class ReimbPage {
    object Home : ReimbPage()
    class Receive(val who: String) : ReimbPage()
    class Person(val who: String) : ReimbPage()
}

@Composable
fun ReimbScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    var page by remember { mutableStateOf<ReimbPage>(ReimbPage.Home) }
    when (val p = page) {
        is ReimbPage.Home -> ReimbHome(
            vm, onEdit, onBack,
            onReceive = { page = ReimbPage.Receive(it) },
            onPerson = { page = ReimbPage.Person(it) },
        )
        is ReimbPage.Receive -> ReimbReceivePage(vm, p.who, onBack = { page = ReimbPage.Home }, onDone = { page = ReimbPage.Home })
        is ReimbPage.Person -> ReimbPersonPage(
            vm, p.who, onBack = { page = ReimbPage.Home },
            onReceive = { page = ReimbPage.Receive(p.who) },
        )
    }
}

@Composable
private fun ReimbHome(
    vm: MoneyViewModel,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    onReceive: (String) -> Unit,
    onPerson: (String) -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    var tab by remember { mutableStateOf(0) }
    val pending = d.bookTxns.pendingReimb()
    val claims = claimsOf(d.bookTxns)
    val open = claims.filter { !it.item.closed }
    val outstanding = pending.sumOf { it.reimbOutstanding }
    val groups = open.groupBy { it.who }.entries.sortedBy { e -> e.value.minOf { it.txn.day } }

    SubPage("報銷", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("還沒收到的報銷款", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                            Text(formatMoney(outstanding), style = MaterialTheme.typography.headlineMedium)
                        }
                        Text("${groups.size} 位・${open.size} 筆", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    }
                }
            }
            item { PillSegment(listOf("依對象", "依帳單", "已收款"), tab, { tab = it }, Modifier.fillMaxWidth(), equal = true) }

            when (tab) {
                0 -> {
                    if (groups.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "沒有待報銷的項目\n記帳時點「報銷」就會出現在這裡") }
                    }
                    items(groups, key = { "g" + it.key }) { g ->
                        val who = g.key
                        val list = g.value.sortedWith(compareBy({ it.txn.day }, { it.txn.id }))
                        val days = daysSince(list.first().txn.day)
                        CuteCard(Modifier.fillMaxWidth().clickable { onReceive(who) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(cute.soft),
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (who.isBlank()) "?" else who.take(1), style = MaterialTheme.typography.titleMedium) }
                                Spacer(Modifier.width(12.dp))
                                Text(ownerLabel(who), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(formatMoney(list.sumOf { it.item.remaining }), style = MaterialTheme.typography.titleMedium, color = cute.expense)
                                Spacer(Modifier.width(4.dp))
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = cute.sub)
                            }
                            Spacer(Modifier.height(6.dp))
                            val oldest = list.first()
                            Text(
                                "欠最久：${billLabel(d, oldest.txn)} ${formatMoney(oldest.item.remaining)}" +
                                    if (list.size > 1) "（另有 ${list.size - 1} 筆）" else "",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub,
                            )
                            Text(
                                if (days >= 1L) "已經 $days 天" else "今天記的",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (days >= 30L) cute.expense else cute.sub,
                            )
                        }
                    }
                }
                1 -> {
                    if (pending.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "沒有待報銷的項目\n記帳時點「報銷」就會出現在這裡") }
                    }
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
                }
                else -> {
                    val pays = claims.flatMap { c -> c.item.pays.map { pay -> Triple(c, c.item.who.trim(), pay) } }.sortedByDescending { it.third.day * 1440L + maxOf(it.third.time, 0) }
                    if (pays.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "還沒有收款紀錄") }
                    }
                    items(pays.size, key = { "p$it" }) { n ->
                        val (c, who, pay) = pays[n]
                        val acc = pay.accountId?.let { d.accMap[it] }
                        CuteCard(Modifier.fillMaxWidth().clickable { onPerson(who) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${dayTimeLabel(pay.day, pay.time)}　${ownerLabel(who)}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${billLabel(d, c.txn)}・存入 ${acc?.name ?: "未指定帳戶"}" + if (acc != null && acc.isForeign && pay.fxAmount > 0L) "（${formatFx(pay.fxAmount, acc.currency)}）" else "",
                                        style = MaterialTheme.typography.labelMedium, color = cute.sub,
                                    )
                                }
                                Text("+" + formatMoney(pay.amount), style = MaterialTheme.typography.titleMedium, color = cute.income)
                            }
                        }
                    }
                }
            }
        }
    }
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
    val multi = lines.size > 1

    fun accOf(l: RLine): Account? = l.accId?.let { d.accMap[it] }
    fun curOf(l: RLine): String = accOf(l)?.takeIf { it.isForeign }?.currency ?: ""
    fun decOf(l: RLine): Int = accOf(l)?.takeIf { it.isForeign }?.cur?.decimals ?: 0
    fun parseLine(l: RLine): Long = (if (curOf(l).isNotEmpty()) parseFx(l.text, decOf(l)) else l.text.toLongOrNull()) ?: 0L
    fun claimCur(t: Txn): String = d.fxAccountOf(t)?.currency ?: ""
    fun unitText(cur: String, u: Long) = if (cur.isNotEmpty()) formatFx(u, cur) else formatMoney(u)
    fun defaultText(a: Account?): String =
        if (a != null && a.isForeign) fxExpr(claims.filter { claimCur(it.txn) == a.currency }.sumOf { it.txn.twdToFxAt(it.item.remaining) }, a.cur.decimals)
        else totalRemaining.toString()

    val parsed = lines.map { ReceiptLine(it.accId, curOf(it), parseLine(it)) }
    // 只有一種幣別時才開放逐筆改金額；多種幣別一起收時由系統自動分配
    val overrideAmounts: Map<String, Long> = if (multi) emptyMap() else {
        val l = lines[0]
        overrides.mapNotNull { (k, v) -> (if (curOf(l).isNotEmpty()) parseFx(v, decOf(l)) else v.toLongOrNull())?.let { k to it } }.toMap()
    }
    val outcomes = allocateReceipt(claims.map { ClaimInput(it.key, it.txn, it.item.remaining) }, parsed, ::claimCur, overrideAmounts)
    val sumCredit = outcomes.sumOf { it.credit }
    // 必填：什麼都沒收到不能確認；標出第一個還沒填金額的那一種幣別
    val tries = rememberNeedTries()
    val need = if (sumCredit > 0L) null else Need("line${parsed.indexOfFirst { it.amount <= 0L }.coerceAtLeast(0)}", "請輸入這次收到的金額")
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
    }

    SubPage("收款・${ownerLabel(who)}", onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ownerLabel(who)}還欠你（${claims.size} 筆）", style = MaterialTheme.typography.bodyLarge, color = cute.sub, modifier = Modifier.weight(1f))
                        Text(formatMoney(totalRemaining), style = MaterialTheme.typography.titleLarge, color = cute.expense)
                    }
                    val l0 = lines[0]
                    if (!multi && curOf(l0).isNotEmpty()) {
                        val tot = claims.filter { claimCur(it.txn) == curOf(l0) }.sumOf { it.txn.twdToFxAt(it.item.remaining) }
                        Text("其中可以用 ${curOf(l0)} 收的：${formatFx(tot, curOf(l0))}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
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
                            l.text, { v -> l.text = if (cur.isNotEmpty()) fxInput(v, dec) else v.filter { c -> c.isDigit() }.take(9); overrides.clear() },
                            "金額", Modifier.fillMaxWidth().needInView(nv, "line$li"), number = true, decimal = cur.isNotEmpty() && dec > 0,
                            prefix = accOf(l)?.takeIf { it.isForeign }?.cur?.symbol?.trim() ?: tw.moneybook.app.Money.twd,
                            error = nv.on("line$li"),
                        )
                        nv.Message("line$li")
                        Spacer(Modifier.height(8.dp))
                        Text("存進哪個帳戶", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 同一次收款每種幣別只能有一列：已經被別列用掉的幣別不列出（自己這一列的幣別除外）
                            allChoices.filter { a -> currencyOfAcc(a) == cur || lines.none { o -> o !== l && curOf(o) == currencyOfAcc(a) } }
                                .forEach { a -> CuteChip(accLabel(a), l.accId == a.id, { pickAccount(l, a) }) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip(dayLabel(day), false, { pickDate = true }, icon = "vec:calendar")
                    CuteChip(tw.moneybook.app.formatTime(timeMin), false, { pickTime = true }, icon = "vec:clock")
                }

                Text(
                    if (multi) "分配到這幾筆（外幣先沖同幣別的帳，台幣再沖剩下的，由舊到新）" else "分配到這幾筆（由舊到新自動分配，可以直接改金額）",
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
                    CuteCard(Modifier.fillMaxWidth()) {
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
                                if (single) {
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
                val over = sumCredit - totalRemaining
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

// ───────────────────────── 某人的明細與收款紀錄 ─────────────────────────

@Composable
private fun ReimbPersonPage(vm: MoneyViewModel, who: String, onBack: () -> Unit, onReceive: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val claims = claimsOf(d.bookTxns).filter { it.who == who }.sortedWith(compareByDescending<Claim> { it.txn.day }.thenByDescending { it.txn.id })
    val owed = claims.filter { !it.item.closed }.sumOf { it.item.remaining }
    val got = claims.sumOf { it.item.received }
    val pays = claims.flatMap { c -> c.item.pays.mapIndexed { n, pay -> Triple(c, n, pay) } }.sortedByDescending { it.third.day * 1440L + maxOf(it.third.time, 0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Triple<Claim, Int, ReimbPay>?>(null) }

    SubPage(ownerLabel(who), onBack) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CuteCard(Modifier.weight(1f)) {
                        Text("還欠你", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Text(formatMoney(owed), style = MaterialTheme.typography.titleLarge, color = cute.expense)
                    }
                    CuteCard(Modifier.weight(1f)) {
                        Text("已收", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        Text(formatMoney(got), style = MaterialTheme.typography.titleLarge, color = cute.income)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("收款紀錄", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("點右邊的 ⋯ 可以改或刪除", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
                if (pays.isEmpty()) {
                    Text("還沒有收款紀錄", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
                pays.forEach { entry ->
                    val (c, n, pay) = entry
                    val id = "${c.key}#$n"
                    val acc = pay.accountId?.let { d.accMap[it] }
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${dayTimeLabel(pay.day, pay.time)}　收到 ${payText(d, pay)}", style = MaterialTheme.typography.bodyLarge)
                                Text("${billLabel(d, c.txn)}・存入 ${acc?.name ?: "未指定帳戶"}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                            Box {
                                TextButton(onClick = { menuFor = id }) { Text("⋯", style = MaterialTheme.typography.titleLarge) }
                                DropdownMenu(expanded = menuFor == id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("修改") }, onClick = { menuFor = null; editing = entry })
                                    DropdownMenuItem(text = { Text("刪除") }, onClick = { menuFor = null; vm.deleteReimbPay(c.txn.id, c.index, n) })
                                }
                            }
                        }
                    }
                }
                Text("帳單", style = MaterialTheme.typography.titleMedium)
                claims.forEach { c ->
                    val item = c.item
                    val status = when {
                        !item.closed -> "待收 ${formatMoney(item.remaining)}"
                        item.received < item.amount -> "不追了，少收 ${formatMoney(item.amount - item.received)}"
                        else -> "已收齊"
                    }
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyLarge)
                                Text("應收 ${formatMoney(item.amount)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                            Text(status, style = MaterialTheme.typography.labelLarge, color = if (!item.closed) cute.expense else cute.income)
                        }
                    }
                }
            }
            if (owed > 0L) {
                Box(Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Button(onClick = onReceive, modifier = Modifier.fillMaxWidth()) { Text("向${ownerLabel(who)}收款") }
                }
            }
        }
    }

    editing?.let { entry ->
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
        val payTries = rememberNeedTries()
        val payAmt = if (editFx != null) parseFx(amt, editDec) ?: 0L else amt.toLongOrNull() ?: 0L
        val payNeed = firstNeed(if (payAmt <= 0L) Need("amt", "請輸入這次收到的金額") else null)
        val payView = NeedView(payNeed, payTries.count)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改收款") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactField(
                        amt, { v -> amt = if (editFx != null) fxInput(v, editDec) else v.filter { ch -> ch.isDigit() }.take(9) },
                        "金額", Modifier.fillMaxWidth(), number = true, decimal = editFx != null && editDec > 0, prefix = editFx?.cur?.symbol?.trim() ?: tw.moneybook.app.Money.twd,
                        error = payView.on("amt"),
                    )
                    payView.Message("amt")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.visibleAccounts.filter { !it.isForeign }.forEach { a -> CuteChip(accLabel(a), accId == a.id, { switchAccount(a) }) }
                        fxOptions.forEach { a -> CuteChip(accLabel(a), accId == a.id, { switchAccount(a) }) }
                    }
                    if (editFx != null) {
                        Text("收外幣：沖掉的台幣用這筆消費當時的匯率換算。", style = MaterialTheme.typography.labelSmall, color = LocalCute.current.sub)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    editing = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
        if (pickDate) {
            CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "收到報銷款的日期")
        }
        if (pickTime) {
            TimePickerDialog(timeMin, { timeMin = it; pickTime = false }, { pickTime = false })
        }
    }
}
