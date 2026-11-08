package tw.moneybook.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import tw.moneybook.app.AccountType
import tw.moneybook.app.AppData
import tw.moneybook.app.Currencies
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Sub
import tw.moneybook.app.TxType
import tw.moneybook.app.bookSubs
import tw.moneybook.app.cur
import tw.moneybook.app.formatFx
import tw.moneybook.app.formatMoney
import tw.moneybook.app.fxPlain
import tw.moneybook.app.isForeign
import tw.moneybook.app.next
import tw.moneybook.app.parseFx
import tw.moneybook.app.ruleText
import tw.moneybook.app.subTwd
import tw.moneybook.app.subsMonthly

/** 訂閱一次扣多少：台幣「$149」、外幣「US$15.49」 */
fun subAmountText(s: Sub): String = if (s.isForeign) formatFx(s.amount, s.cur) else formatMoney(s.amount)

/** 「今天」「昨天」「11/5」「2027/3/1」 */
private fun shortDay(day: Long, today: Long): String {
    val dt = LocalDate.ofEpochDay(day)
    return when (day) {
        today -> "今天"
        today - 1 -> "昨天"
        today + 1 -> "明天"
        else -> if (dt.year != LocalDate.ofEpochDay(today).year) "${dt.year}/${dt.monthValue}/${dt.dayOfMonth}" else "${dt.monthValue}/${dt.dayOfMonth}"
    }
}

// ───────────────────────── 訂閱清單 ─────────────────────────

/** 我的 → 訂閱／固定扣款：上面每月合計、下一筆；照下一次扣款日排，暫停的（和已經結束的）放最下面 */
@Composable
fun SubsScreen(vm: MoneyViewModel, onOpen: (Long?) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    val all = d.bookSubs
    val nexts = remember(d) { all.associate { it.id to it.next(today) } }
    val list = all.sortedWith(compareBy({ it.paused || nexts[it.id] == null }, { nexts[it.id] ?: Long.MAX_VALUE }, { it.id }))
    SubPage("訂閱／固定扣款", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("每月固定扣款", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Text(formatMoney(remember(d) { d.subsMonthly(today) }), style = MaterialTheme.typography.headlineMedium)
                    val first = list.firstOrNull { !it.paused && nexts[it.id] != null }
                    Text(
                        if (first != null) "下一筆：${shortDay(nexts[first.id]!!, today)} ${first.name} ${subAmountText(first)}"
                        else "到了扣款日，首頁最上面會提醒你記一筆",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub,
                    )
                }
            }
            if (list.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "還沒有訂閱\n像 Netflix、手機月租、保險，設定一次就會按時提醒") }
            }
            items(list, key = { it.id }) { s ->
                SwipeRow(onDelete = { vm.deleteSubWithUndo(s.id) }) { SubLine(d, s, nexts[s.id], today) { onOpen(s.id) } }
            }
            item { OutlinedButton(onClick = { onOpen(null) }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增訂閱") } }
            item {
                Text(
                    "合計是每月的加起來，每週的乘 52 除以 12、每年的除以 12；外幣用目前匯率換成台幣，暫停的不算。左滑可以刪除，刪掉訂閱不會刪掉已經記好的記錄。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }
}

@Composable
private fun SubLine(d: AppData, s: Sub, next: Long?, today: Long, onClick: () -> Unit) {
    val cute = LocalCute.current
    val c = s.categoryId?.let { d.catMap[it] }
    val a = s.accountId?.let { d.accMap[it] }
    val off = s.paused || next == null
    CuteCard(Modifier.fillMaxWidth(), color = if (off) cute.soft.copy(alpha = 0.5f) else cute.card, onClick = onClick, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = if (off) cute.sub else cute.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(s.ruleText(), a?.name).joinToString("・"), style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(subAmountText(s), fontWeight = FontWeight.SemiBold, color = if (off) cute.sub else cute.expense)
                Text(
                    when {
                        s.paused -> "已暫停"
                        next == null -> "已結束"
                        next <= today -> "${shortDay(next, today)}到期"
                        else -> "下次 ${shortDay(next, today)}"
                    },
                    style = MaterialTheme.typography.labelSmall, color = if (!off && next != null && next <= today) cute.expense else cute.sub,
                )
            }
        }
    }
}

// ───────────────────────── 新增／修改訂閱 ─────────────────────────

/** 星期的開關：從星期日開始排（照使用者給的圖） */
private val WEEK_NAMES = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

private fun ints(s: String): List<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }
private fun intsText(l: Collection<Int>): String = l.distinct().sorted().joinToString(",")

/**
 * 新增（id = null）或修改訂閱；fromTxn 不是 null 時，用那一筆記錄的內容當新的訂閱（修改記錄的 ••• →「設成固定扣款」）。
 */
@Composable
fun SubEditScreen(vm: MoneyViewModel, id: Long?, fromTxn: Long?, onClose: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now()
    val orig = remember(id) { id?.let { i -> d.subs.firstOrNull { it.id == i } } }
    val txn = remember(fromTxn) { fromTxn?.let { i -> d.txns.firstOrNull { it.id == i } } }
    val txnAcc = txn?.accountId?.let { d.accMap[it] }
    val accs = d.visibleAccounts.filter { it.type != AccountType.INVEST || it.id == orig?.accountId }
    val startDate = txn?.date ?: today

    var name by rememberSaveable {
        mutableStateOf(orig?.name ?: txn?.let { t -> t.note.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.take(30) ?: t.categoryId?.let { d.catMap[it]?.name } } ?: "")
    }
    var cur by rememberSaveable {
        mutableStateOf(orig?.cur ?: when {
            txn == null -> ""
            txn.fxSpendCur.isNotEmpty() -> txn.fxSpendCur
            txnAcc?.isForeign == true -> txnAcc.currency
            else -> ""
        })
    }
    var amountText by rememberSaveable {
        mutableStateOf(
            when {
                orig != null -> if (orig.isForeign) fxPlain(orig.amount, Currencies.of(orig.cur).decimals) else orig.amount.toString()
                txn == null -> ""
                txn.fxSpendCur.isNotEmpty() -> fxPlain(txn.fxSpendAmount, Currencies.of(txn.fxSpendCur).decimals)
                txnAcc?.isForeign == true -> fxPlain(txn.fxAmount, txnAcc.cur.decimals)
                else -> txn.amount.toString()
            }
        )
    }
    var catId by rememberSaveable { mutableStateOf(orig?.categoryId ?: txn?.categoryId) }
    var accId by rememberSaveable { mutableStateOf(orig?.accountId ?: txn?.accountId ?: d.defaultAccount(accs)) }
    var note by rememberSaveable { mutableStateOf(orig?.note ?: txn?.note ?: "") }
    var tagsText by rememberSaveable { mutableStateOf((orig?.tags ?: txn?.tags?.filter { it != Sub.TAG } ?: emptyList()).joinToString(" ")) }
    var freq by rememberSaveable { mutableIntStateOf(orig?.freq ?: Sub.FREQ_MONTH) }
    var every by rememberSaveable { mutableIntStateOf(orig?.every ?: 1) }
    // 三種頻率各自記住選的日子，切來切去不會不見
    var wdays by rememberSaveable { mutableStateOf(if (orig?.freq == Sub.FREQ_WEEK) intsText(orig.days) else "${startDate.dayOfWeek.value % 7}") }
    var mdays by rememberSaveable { mutableStateOf(if (orig?.freq == Sub.FREQ_MONTH) intsText(orig.days) else "${startDate.dayOfMonth}") }
    var ymd by rememberSaveable { mutableIntStateOf(if (orig?.freq == Sub.FREQ_YEAR) orig.days.firstOrNull() ?: 101 else startDate.monthValue * 100 + startDate.dayOfMonth) }
    var start by rememberSaveable { mutableLongStateOf(orig?.start ?: startDate.toEpochDay()) }
    var endMode by rememberSaveable { mutableIntStateOf(orig?.endMode ?: Sub.END_NONE) }
    var endCount by rememberSaveable { mutableIntStateOf(orig?.endCount ?: 12) }
    var endDay by rememberSaveable { mutableLongStateOf(orig?.endDay?.takeIf { it > 0L } ?: startDate.plusYears(1).toEpochDay()) }
    var paused by rememberSaveable { mutableStateOf(orig?.paused ?: false) }
    var dialog by remember { mutableStateOf("") }

    val acc = accId?.let { d.accMap[it] }
    // 扣外幣帳戶：幣別就是那個帳戶的
    val effCur = if (acc?.isForeign == true) acc.currency else cur
    val dec = if (effCur.isEmpty()) 0 else Currencies.of(effCur).decimals
    val amount = if (effCur.isEmpty()) amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L else parseFx(amountText, dec) ?: 0L
    val days = when (freq) {
        Sub.FREQ_WEEK -> ints(wdays)
        Sub.FREQ_YEAR -> listOf(ymd)
        else -> ints(mdays)
    }
    val tags = tagsText.split(' ', '　', ',', '，', '#').map { it.trim() }.filter { it.isNotEmpty() && it != Sub.TAG }.distinct()
    fun draft() = Sub(
        id = orig?.id ?: 0L, bookId = orig?.bookId ?: d.currentBook.id, name = name.trim(), amount = amount, cur = effCur,
        categoryId = catId, accountId = accId, note = note.trim(), tags = tags,
        freq = freq, every = every, days = days, start = start,
        endMode = endMode, endCount = endCount, endDay = endDay, paused = paused,
        doneUntil = orig?.doneUntil ?: 0L, doneDays = orig?.doneDays ?: emptyList(),
    )
    val initial = remember { draft() }
    val dirty = draft() != initial

    val tries = rememberNeedTries()
    val need = firstNeed(
        if (name.isBlank()) Need("name", "請輸入名稱，例如 Netflix") else null,
        if (amount <= 0L) Need("amount", "請輸入每次扣多少") else null,
        if (catId == null || d.catMap[catId!!] == null) Need("cat", "請選擇分類") else null,
        if (acc == null) Need("acc", "請選擇從哪個帳戶扣") else null,
        if (days.isEmpty()) Need("days", if (freq == Sub.FREQ_WEEK) "請至少開一天" else "請至少選一天") else null,
        if (endMode == Sub.END_UNTIL && endDay < start) Need("end", "結束日要在開始日之後") else null,
    )
    val nv = NeedView(need, tries.count)

    fun save() {
        if (need != null) { tries.count++; return }
        vm.saveSub(draft(), if (orig == null) txn?.day else null)
        onClose()
    }
    fun tryClose() { if (dirty) dialog = "unsaved" else onClose() }

    SubPage(
        if (orig == null) "新增訂閱" else "修改訂閱", { tryClose() },
        actions = if (orig != null) ({ IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") } }) else null,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (txn != null && orig == null) {
                Text("用這筆記錄的內容新增，之後到了扣款日首頁會提醒你記。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            }
            Field("名稱") {
                OutlinedTextField(
                    name, { name = it.take(30) }, placeholder = { Text("例如 Netflix、手機月租") }, singleLine = true,
                    isError = nv.on("name"), supportingText = nv.supporting("name"), shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().needInView(nv, "name"),
                )
            }
            Field("帳戶") {
                PickerButton(acc?.let { accLabel(it) } ?: "選擇帳戶", { dialog = "acc" }, Modifier.needFrame(nv, "acc", RoundedCornerShape(14.dp)).needInView(nv, "acc"))
                nv.Message("acc")
            }
            Field("每次扣多少") {
                // 台幣帳戶可以選外幣（例如美金計價的訂閱刷台幣卡），扣款日再確認匯率；外幣帳戶就是那個幣別
                if (acc?.isForeign != true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        CuteChip("台幣", cur.isEmpty(), { if (cur.isNotEmpty()) { cur = ""; amountText = "" } })
                        CuteChip(if (cur.isEmpty()) "外幣…" else currencyLabel(cur), cur.isNotEmpty(), { dialog = "cur" }, icon = fxCoinIcon(cur))
                    }
                }
                AmountField(
                    amountText, { amountText = it }, "0", "每次扣多少", Modifier.fillMaxWidth().needInView(nv, "amount"),
                    prefix = if (effCur.isEmpty()) tw.moneybook.app.Money.twd else Currencies.of(effCur).symbol.trim(),
                    dec = dec, error = nv.on("amount"), maxDigits = if (effCur.isEmpty()) 10 else 11,
                )
                nv.Message("amount")
                if (effCur.isNotEmpty()) {
                    val r = d.subTwd(draft().copy(cur = effCur))
                    Text(
                        (if (r != null && amount > 0L) "目前約 ${formatMoney(r)}。" else "") +
                            if (acc?.isForeign == true) "直接從${acc.name}扣 $effCur。" else "扣款日按「記」時再確認匯率，記成外幣消費（待請款），帳單出來再填實際台幣。",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Field("分類") {
                val c = catId?.let { d.catMap[it] }
                PickerButton(c?.let { cc -> d.topOf(cc).let { p -> if (p.id != cc.id) "${p.name} › ${cc.name}" else cc.name } } ?: "選擇分類", { dialog = "cat" },
                    Modifier.needFrame(nv, "cat", RoundedCornerShape(14.dp)).needInView(nv, "cat"))
                nv.Message("cat")
            }

            // 多久扣一次（照使用者給的圖：先選週／月／年，再選哪幾天，最後設結束）
            Field("多久扣一次") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("每週" to Sub.FREQ_WEEK, "每月" to Sub.FREQ_MONTH, "每年" to Sub.FREQ_YEAR).forEach { (t, f) ->
                        CuteChip(t, freq == f, { freq = f })
                    }
                }
                Spacer(Modifier.height(8.dp))
                CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("間隔", modifier = Modifier.weight(1f))
                        val unit = when (freq) { Sub.FREQ_WEEK -> "週"; Sub.FREQ_YEAR -> "年"; else -> "個月" }
                        Stepper("每 $every $unit", every > 1, every < 24, { every-- }, { every++ })
                    }
                }
            }
            Box(Modifier.needFrame(nv, "days", RoundedCornerShape(24.dp)).needInView(nv, "days")) {
                when (freq) {
                    Sub.FREQ_WEEK -> CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        val on = ints(wdays).toSet()
                        WEEK_NAMES.forEachIndexed { i, w ->
                            Row(Modifier.fillMaxWidth().clickable { wdays = intsText(if (i in on) on - i else on + i) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(w, modifier = Modifier.weight(1f))
                                Switch(checked = i in on, onCheckedChange = { wdays = intsText(if (it) on + i else on - i) }, modifier = Modifier.semantics { contentDescription = w })
                            }
                        }
                    }
                    Sub.FREQ_YEAR -> CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("每年幾月幾號", modifier = Modifier.weight(1f))
                            SoftButton("${ymd / 100} 月 ${ymd % 100} 號", { dialog = "ymd" }, compact = true)
                        }
                    }
                    else -> MonthDayGrid(ints(mdays).toSet()) { mdays = intsText(it) }
                }
            }
            nv.Message("days")
            Field("從哪天開始") {
                PickerButton(dayLabel(start), { dialog = "start" })
                if (orig == null && start < today.toEpochDay()) {
                    Text("今天以前的不會提醒，要補記的請自己記。", style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Field("結束") {
                CuteCard(Modifier.fillMaxWidth().needFrame(nv, "end", RoundedCornerShape(24.dp)).needInView(nv, "end"), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("設定結束", modifier = Modifier.weight(1f))
                        Switch(checked = endMode != Sub.END_NONE, onCheckedChange = { endMode = if (it) Sub.END_COUNT else Sub.END_NONE }, modifier = Modifier.semantics { contentDescription = "設定結束" })
                    }
                    if (endMode != Sub.END_NONE) {
                        Row(Modifier.fillMaxWidth().clickable { endMode = Sub.END_COUNT }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(endMode == Sub.END_COUNT, { endMode = Sub.END_COUNT })
                            Text("次數", modifier = Modifier.weight(1f))
                            Stepper("$endCount 次", endCount > 1, endCount < 999, { endMode = Sub.END_COUNT; endCount-- }, { endMode = Sub.END_COUNT; endCount++ })
                        }
                        Row(Modifier.fillMaxWidth().clickable { endMode = Sub.END_UNTIL }.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(endMode == Sub.END_UNTIL, { endMode = Sub.END_UNTIL })
                            Text("直到", modifier = Modifier.weight(1f))
                            SoftButton(dayLabel(endDay), { endMode = Sub.END_UNTIL; dialog = "end" }, compact = true)
                        }
                    }
                }
                nv.Message("end")
            }
            Field("備註（選填，記錄會帶上）") {
                OutlinedTextField(note, { note = it.take(300) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            Field("標籤（選填，用空白分開；記錄會自動加 #訂閱）") {
                OutlinedTextField(tagsText, { tagsText = it.take(100) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            if (orig != null) {
                CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("暫停")
                            Text("暫停時不提醒、不算進每月合計", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                        Switch(checked = paused, onCheckedChange = { paused = it }, modifier = Modifier.semantics { contentDescription = "暫停" })
                    }
                }
                Text("改金額、帳戶只影響之後的扣款，已經記好的記錄不會變。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("儲存") }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "acc" -> AccountPickDialog(vm, accs, accId, { a ->
            accId = a.id
            // 換成外幣帳戶：幣別跟著帳戶；從外幣帳戶換回台幣帳戶：回到台幣
            if (a.isForeign) cur = a.currency else if (acc?.isForeign == true) { cur = ""; amountText = "" }
            dialog = ""
        }, { dialog = "" }, title = "從哪個帳戶扣")
        "cur" -> CurrencyPickDialog(cur, { c -> if (c != cur) amountText = ""; cur = c; dialog = "" }, { dialog = "" })
        "cat" -> SubCatPickDialog(d, catId, { catId = it; dialog = "" }, { dialog = "" })
        "start" -> CuteDatePickerDialog(start, { start = it; dialog = "" }, { dialog = "" }, title = "從哪天開始")
        "end" -> CuteDatePickerDialog(endDay, { endDay = it; dialog = "" }, { dialog = "" }, title = "直到哪一天")
        "ymd" -> {
            // 每年幾月幾號：用日期選擇器挑一天，只看月和日
            val y = today.year
            val init = runCatching { LocalDate.of(y, ymd / 100, minOf(ymd % 100, java.time.YearMonth.of(y, ymd / 100).lengthOfMonth())) }.getOrDefault(today)
            CuteDatePickerDialog(init.toEpochDay(), { e -> val dt = LocalDate.ofEpochDay(e); ymd = dt.monthValue * 100 + dt.dayOfMonth; dialog = "" }, { dialog = "" }, title = "每年幾月幾號")
        }
        "unsaved" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("還沒儲存") },
            text = { Text("你有還沒儲存的修改，要離開嗎？") },
            confirmButton = { TextButton(onClick = { dialog = ""; onClose() }) { Text("不儲存") } },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("繼續編輯") } },
        )
        "delete" -> if (orig != null) ConfirmDialog(
            title = "刪除這個訂閱？",
            text = "已經記好的記錄不會刪掉。",
            confirm = "刪除",
            onConfirm = { dialog = ""; vm.deleteSubWithUndo(orig.id); onClose() },
            onDismiss = { dialog = "" },
        )
    }
}

/** 欄位：上面小標題、下面內容 */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalCute.current.sub, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        content()
    }
}

/** －　每 2 週　＋ */
@Composable
private fun Stepper(text: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.clip(CircleShape).background(cute.soft), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canMinus, onClick = onMinus).semantics { contentDescription = "減少" }, contentAlignment = Alignment.Center) {
            Text("－", color = if (canMinus) primary else cute.sub.copy(alpha = 0.4f))
        }
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = canPlus, onClick = onPlus).semantics { contentDescription = "增加" }, contentAlignment = Alignment.Center) {
            Text("＋", color = if (canPlus) primary else cute.sub.copy(alpha = 0.4f))
        }
    }
}

/** 每月幾號：1～31 七個一排可以複選，最後一個是「月底」 */
@Composable
private fun MonthDayGrid(on: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
        Text("每月幾號（可以選好幾天；遇到小月沒有的日子算那個月最後一天）", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        val cells = (1..31).toList() + Sub.MONTH_END
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { n ->
                    val sel = n in on
                    Box(Modifier.weight(if (n == Sub.MONTH_END) 2f else 1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.height(36.dp).then(if (n == Sub.MONTH_END) Modifier.fillMaxWidth() else Modifier.size(36.dp))
                                .clip(CircleShape).background(if (sel) primary else Color.Transparent)
                                .clickable { onChange(if (sel) on - n else on + n) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (n == Sub.MONTH_END) "月底" else "$n", color = if (sel) MaterialTheme.colorScheme.onPrimary else cute.ink)
                        }
                    }
                }
                // 最後一排補空位，格子才對齊
                repeat(7 - row.size - (if (Sub.MONTH_END in row) 1 else 0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 選支出分類：大分類可以收折，打開時只展開選到的那個大分類 */
@Composable
private fun SubCatPickDialog(d: AppData, selected: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    val tops = remember(d) { d.topCategories(TxType.EXPENSE) }
    val open = remember { mutableStateListOf<Long>().apply { selected?.let { s -> d.catMap[s]?.let { c -> add(d.topOf(c).id) } } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇分類") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tops.forEach { t ->
                    val kids = d.childrenOf(t.id)
                    val isOpen = t.id in open
                    CatPickRow(t.emoji, t.color, t.name, selected == t.id, false, kids.isNotEmpty(), isOpen, { onPick(t.id) }) {
                        if (isOpen) open.remove(t.id) else open.add(t.id)
                    }
                    if (isOpen) kids.forEach { k -> CatPickRow(k.emoji, k.color, k.name, selected == k.id, true, false, false, { onPick(k.id) }) {} }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

@Composable
private fun CatPickRow(emoji: String, color: Int, name: String, on: Boolean, child: Boolean, hasKids: Boolean, isOpen: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    val cute = LocalCute.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(start = if (child) 28.dp else 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble(emoji, color, if (child) 28.dp else 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, modifier = Modifier.weight(1f), style = if (child) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        if (hasKids) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (isOpen) AppIcons.ChevronDown else AppIcons.ChevronRight, contentDescription = if (isOpen) "收起$name" else "展開$name", tint = cute.sub)
            }
        } else Spacer(Modifier.size(36.dp))
    }
}

// ───────────────────────── 首頁提醒卡 ─────────────────────────

/** 首頁最上面：到期還沒記的固定扣款，每一筆「略過」「記」，好幾筆時有「全部記」 */
@Composable
fun SubDueCard(vm: MoneyViewModel, due: List<Pair<Sub, Long>>) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    var fx by remember { mutableStateOf<Pair<Sub, Long>?>(null) }
    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (due.size == 1) "固定扣款到期了" else "有 ${due.size} 筆固定扣款還沒記",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            if (due.size > 1) Button(onClick = { vm.recordAllSubs(due.map { it.first.id to it.second }) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
                Text("全部記", style = MaterialTheme.typography.labelLarge)
            }
        }
        due.take(5).forEach { (s, day) ->
            val c = s.categoryId?.let { d.catMap[it] }
            val a = s.accountId?.let { d.accMap[it] }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(shortDay(day, today), a?.name).joinToString("・"), style = MaterialTheme.typography.labelSmall, color = if (day < today) cute.expense else cute.sub, maxLines = 1)
                }
                Text(subAmountText(s), color = cute.expense, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
                SoftButton("略過", { vm.skipSub(s.id, day) }, compact = true)
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        // 外幣扣台幣帳戶：先確認匯率（記成外幣消費）；其他直接記
                        if (s.isForeign && a?.isForeign != true) fx = s to day else vm.recordSub(s.id, day)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp).semantics { contentDescription = "記 ${s.name}" },
                ) { Text("記", style = MaterialTheme.typography.labelLarge) }
            }
        }
        if (due.size > 5) Text("還有 ${due.size - 5} 筆，記完上面的就會出現", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(top = 6.dp))
    }
    fx?.let { (s, day) ->
        FxSpendDialog(
            vm = vm,
            init = FxSpendInit(cur = s.cur, amount = fxPlain(s.amount, Currencies.of(s.cur).decimals), pending = true, twd = 0L),
            hasValue = false,
            onConfirm = { _, minor, twd, pending -> fx = null; vm.recordSub(s.id, day, twd, pending, minor) },
            onClear = { fx = null },
            onDismiss = { fx = null },
            title = s.name,
        )
    }
}
