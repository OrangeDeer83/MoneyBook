package tw.moneybook.app.ui

import androidx.compose.foundation.background
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
import tw.moneybook.app.ReimbReceipt
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.formatMoney
import tw.moneybook.app.pendingReimb
import java.time.LocalDate
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
) {
    val cute = LocalCute.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(cute.soft).padding(horizontal = 12.dp, vertical = 12.dp),
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

private fun billLabel(d: AppData, t: Txn): String {
    val c = t.categoryId?.let { d.catMap[it] }
    val date = LocalDate.ofEpochDay(t.day)
    val md = if (date.year == LocalDate.now().year) "${date.monthValue}/${date.dayOfMonth}" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"
    return (c?.name ?: "未分類") + " " + md
}

private fun daysSince(day: Long): Long = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(day), LocalDate.now()).coerceAtLeast(0L)

// ───────────────────────── 記一筆：這筆的報銷（整頁） ─────────────────────────

/** 這一列可能是還能編輯的對象，也可能是已有收款、只能看的（locked） */
internal class ReimbRow(val locked: ReimbItem?, who: String, amt: String) {
    var who by mutableStateOf(who)
    var amt by mutableStateOf(amt)
}

@Composable
fun ReimbEditPage(
    title: String,
    actual: Long,
    cap: Long,
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
    var full by remember { mutableStateOf(initFull) }
    var fullWho by remember { mutableStateOf(initWho) }
    val hasPays = origItems.any { it.pays.isNotEmpty() }
    val rows = remember {
        mutableStateListOf<ReimbRow>().apply {
            val src = if (initFull) origItems.map { if (it.pays.isEmpty() && !it.closed) it.copy(amount = actual) else it } else ReimbCodec.decode(initJson)
            if (src.isEmpty()) add(ReimbRow(null, "", actual.toString()))
            else src.forEach { add(if (it.pays.isNotEmpty() || it.closed) ReimbRow(it, it.who, it.amount.toString()) else ReimbRow(null, it.who, it.amount.toString())) }
        }
    }
    val lockedAny = rows.any { it.locked != null }
    val total = rows.sumOf { it.locked?.effective ?: (it.amt.toLongOrNull() ?: 0L) }

    fun finish() {
        if (!on) {
            onDone(false, true, "", "")
        } else if (full) {
            onDone(true, true, initJson, fullWho.trim())
        } else {
            val list = rows.mapNotNull { r ->
                r.locked ?: (r.amt.toLongOrNull() ?: 0L).takeIf { it > 0L }?.let { a -> ReimbItem(r.who.trim(), a.coerceAtMost(cap)) }
            }
            if (list.isEmpty()) onDone(false, true, "", "") else onDone(true, false, ReimbCodec.encode(list), "")
        }
    }

    fun addName(name: String) {
        val blank = rows.firstOrNull { it.locked == null && it.who.isBlank() }
        if (blank != null) blank.who = name else rows.add(ReimbRow(null, name, ""))
    }

    fun splitEvenly() {
        val editable = rows.filter { it.locked == null }
        if (editable.isEmpty()) return
        val pool = (actual - rows.sumOf { it.locked?.effective ?: 0L }).coerceAtLeast(0L)
        val n = editable.size
        editable.forEachIndexed { i, r -> r.amt = (pool / n + if (i == 0) pool % n else 0L).toString() }
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
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("這筆可以報銷", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = { on = it }, enabled = !hasPays)
                }
                if (on) {
                    if (!lockedAny) {
                        PillSegment(
                            listOf("一人・全額", "分給多人"), if (full) 0 else 1,
                            { pick ->
                                full = pick == 0
                                if (!full) rows.firstOrNull { it.locked == null && it.who.isBlank() }?.let { r -> r.who = fullWho }
                            },
                            Modifier.fillMaxWidth(), equal = true,
                        )
                    }
                    if (full) {
                        CompactField(fullWho, { fullWho = it.take(12) }, "對象（選填，例如小明）", Modifier.fillMaxWidth())
                        val fullSuggest = names.filter { it != fullWho.trim() }.take(8)
                        if (fullSuggest.isNotEmpty()) {
                            Text("常用對象，點一下加入", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                fullSuggest.forEach { nm -> CuteChip(nm, false, { fullWho = nm }) }
                            }
                        }
                        Text("全額 ${formatMoney(actual)}，收到後這筆就不算你的支出。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    } else {
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
                                    CompactField(r.amt, { r.amt = it.filter { c -> c.isDigit() }.take(9) }, "金額", Modifier.width(112.dp), number = true, prefix = "$")
                                    Text(
                                        "✕", color = cute.sub, style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.clip(CircleShape).clickable { rows.removeAt(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CuteChip("＋ 新增對象", false, { rows.add(ReimbRow(null, "", "")) })
                            val n = rows.count { it.locked == null }
                            if (n > 1) CuteChip("平均分給 $n 人", false, { splitEvenly() })
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
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("自己負擔（算進支出）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                    Text(formatMoney((actual - total).coerceAtLeast(0L)), style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            if (total > cap) {
                                Text("合計超過上限 ${formatMoney(cap)}，超過的部分不會算。", style = MaterialTheme.typography.labelSmall, color = cute.expense)
                            } else if (total > actual) {
                                Text("比實付多 ${formatMoney(total - actual)}，多的部分收到後算成報銷回饋收入。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                            }
                        }
                    }
                    if (lockedAny) {
                        Text("已有收款的對象不能在這裡改，請到「我的 → 報銷」處理。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
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
                    val pays = claims.flatMap { c -> c.item.pays.map { pay -> Triple(c, c.item.who.trim(), pay) } }.sortedByDescending { it.third.day }
                    if (pays.isEmpty()) {
                        item { EmptyHint(d.prefs.mascot, "還沒有收款紀錄") }
                    }
                    items(pays.size, key = { "p$it" }) { n ->
                        val (c, who, pay) = pays[n]
                        val acc = pay.accountId?.let { d.accMap[it] }
                        CuteCard(Modifier.fillMaxWidth().clickable { onPerson(who) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${dayLabel(pay.day)}　${ownerLabel(who)}", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${billLabel(d, c.txn)}・存入 ${acc?.name ?: "未指定帳戶"}",
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

@Composable
private fun ReimbReceivePage(vm: MoneyViewModel, who: String, onBack: () -> Unit, onDone: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val claims = claimsOf(d.bookTxns).filter { !it.item.closed && it.who == who }.sortedWith(compareBy({ it.txn.day }, { it.txn.id }))
    val totalRemaining = claims.sumOf { it.item.remaining }
    var amountText by remember { mutableStateOf(totalRemaining.toString()) }
    var accId by remember { mutableStateOf(d.visibleAccounts.firstOrNull()?.id) }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }
    val overrides = remember { mutableStateMapOf<String, String>() }
    val chase = remember { mutableStateMapOf<String, Boolean>() }
    var askChase by remember { mutableStateOf(false) }

    // 由舊到新自動分配；多收的算在最後一筆
    val amount = amountText.toLongOrNull() ?: 0L
    var left = amount
    val auto = ArrayList<Long>()
    for (c in claims) {
        val a = minOf(c.item.remaining, left)
        auto.add(a)
        left -= a
    }
    if (left > 0L && auto.isNotEmpty()) auto[auto.lastIndex] = auto.last() + left
    val alloc = claims.mapIndexed { i, c -> overrides[c.key]?.toLongOrNull() ?: auto[i] }
    val sum = alloc.sum()
    // 收得比剩下的少、又還沒選要不要追的
    val shortIdx = claims.indices.filter { alloc[it] in 1 until claims[it].item.remaining }
    val undecided = shortIdx.filter { chase[claims[it].key] == null }

    fun submit() {
        val list = claims.indices.filter { alloc[it] > 0L }.map { i ->
            ReimbReceipt(claims[i].txn.id, claims[i].index, alloc[i], chase[claims[i].key] != false)
        }
        vm.receiveReimb(list, accId, day)
        onDone()
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
                }
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("這次收到多少", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    Spacer(Modifier.height(6.dp))
                    CompactField(
                        amountText, { v -> amountText = v.filter { c -> c.isDigit() }.take(9); overrides.clear() },
                        "金額", Modifier.fillMaxWidth(), number = true, prefix = "$",
                    )
                }
                Text("存進哪個帳戶", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.visibleAccounts.forEach { a -> CuteChip(accLabel(a), accId == a.id, { accId = a.id }) }
                }
                CuteChip("📅 ${dayLabel(day)}", false, { pickDate = true })

                Text("分配到這幾筆（由舊到新自動分配，可以直接改金額）", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                claims.forEachIndexed { i, c ->
                    val a = alloc[i]
                    val short = a in 1 until c.item.remaining
                    CuteCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(billLabel(d, c.txn), style = MaterialTheme.typography.bodyLarge)
                                Text("還剩 ${formatMoney(c.item.remaining)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            }
                            CompactField(
                                overrides[c.key] ?: a.toString(),
                                { v -> overrides[c.key] = v.filter { ch -> ch.isDigit() }.take(9) },
                                "收", Modifier.width(120.dp), number = true, prefix = "$",
                            )
                        }
                        if (short) {
                            Spacer(Modifier.height(8.dp))
                            Text("還差 ${formatMoney(c.item.remaining - a)}，要繼續追嗎？", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CuteChip("繼續追剩下的", chase[c.key] == true, { chase[c.key] = true })
                                CuteChip("不追了（自己負擔）", chase[c.key] == false, { chase[c.key] = false })
                            }
                        }
                    }
                }
                if (sum > totalRemaining) {
                    Text("多收 ${formatMoney(sum - totalRemaining)}，多的部分會算成報銷回饋收入。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            }
            Box(Modifier.fillMaxWidth().background(cute.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = { if (undecided.isNotEmpty()) askChase = true else submit() },
                    enabled = sum > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("確認收款 ${formatMoney(sum)}") }
            }
        }
    }
    if (pickDate) {
        CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "收到報銷款的日期")
    }
    if (askChase) {
        AlertDialog(
            onDismissRequest = { askChase = false },
            title = { Text("還有沒收到的款項") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    undecided.forEach { i ->
                        Text("${billLabel(d, claims[i].txn)} 還差 ${formatMoney(claims[i].item.remaining - alloc[i])}")
                    }
                    Text("要繼續追剩下的嗎？選「不追了」的差額會算成你自己的支出。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    undecided.forEach { chase[claims[it].key] = true }
                    askChase = false
                    submit()
                }) { Text("繼續追") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askChase = false }) { Text("取消") }
                    TextButton(onClick = {
                        undecided.forEach { chase[claims[it].key] = false }
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
    val pays = claims.flatMap { c -> c.item.pays.mapIndexed { n, pay -> Triple(c, n, pay) } }.sortedByDescending { it.third.day }
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
                                Text("${dayLabel(pay.day)}　收到 ${formatMoney(pay.amount)}", style = MaterialTheme.typography.bodyLarge)
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
        var amt by remember { mutableStateOf(pay.amount.toString()) }
        var accId by remember { mutableStateOf(pay.accountId) }
        var day by remember { mutableStateOf(pay.day) }
        var pickDate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改收款") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactField(amt, { v -> amt = v.filter { ch -> ch.isDigit() }.take(9) }, "金額", Modifier.fillMaxWidth(), number = true, prefix = "$")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.visibleAccounts.forEach { a -> CuteChip(accLabel(a), accId == a.id, { accId = a.id }) }
                    }
                    CuteChip("📅 ${dayLabel(day)}", false, { pickDate = true })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editReimbPay(c.txn.id, c.index, n, ReimbPay(day, accId, amt.toLongOrNull() ?: 0L))
                    editing = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
        if (pickDate) {
            CuteDatePickerDialog(day, { day = it; pickDate = false }, { pickDate = false }, "收到報銷款的日期")
        }
    }
}
