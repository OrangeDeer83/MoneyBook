package tw.moneybook.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.Calc
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.TxnDraft
import tw.moneybook.app.formatMoney
import tw.moneybook.app.parseTags
import java.time.LocalDate

private fun defaultCat(d: AppData, type: TxType): Long? =
    if (type == TxType.TRANSFER) null else d.topCategories(type).firstOrNull()?.id

@Composable
fun EditScreen(vm: MoneyViewModel, editId: Long?, onClose: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val context = LocalContext.current
    val orig = remember(editId) { editId?.let { id -> d.txns.firstOrNull { it.id == id } } }
    val accs = d.visibleAccounts

    var type by rememberSaveable { mutableStateOf(orig?.type ?: TxType.EXPENSE) }
    var expr by rememberSaveable { mutableStateOf(orig?.amount?.toString() ?: "") }
    var catId by rememberSaveable { mutableStateOf(orig?.categoryId ?: defaultCat(d, TxType.EXPENSE)) }
    var accId by rememberSaveable { mutableStateOf(orig?.accountId ?: accs.firstOrNull()?.id) }
    var toAccId by rememberSaveable { mutableStateOf(orig?.toAccountId ?: accs.getOrNull(1)?.id) }
    var day by rememberSaveable { mutableLongStateOf(orig?.day ?: LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(orig?.note ?: "") }
    var tagsText by rememberSaveable { mutableStateOf(orig?.tags?.joinToString(" ") ?: "") }
    var inst by rememberSaveable { mutableIntStateOf(1) }
    var dialog by remember { mutableStateOf("") }

    BackHandler { onClose() }

    val amount = Calc.eval(expr)
    val pending = Calc.hasOp(expr)
    val tags = parseTags(tagsText)
    val cat = catId?.let { d.catMap[it] }
    val parent = cat?.let { d.topOf(it) }
    val canSave = amount > 0 && when (type) {
        TxType.TRANSFER -> accId != null && toAccId != null && accId != toAccId
        else -> catId != null
    }

    fun draft() = TxnDraft(
        type = type, amount = amount,
        categoryId = if (type == TxType.TRANSFER) null else catId,
        accountId = accId,
        toAccountId = if (type == TxType.TRANSFER) toAccId else null,
        day = day, note = note.trim(), tags = tags,
        installments = if (orig == null && type == TxType.EXPENSE) inst else 1,
    )

    fun setType(t: TxType) {
        if (t == type) return
        type = t
        catId = defaultCat(d, t)
        if (t != TxType.EXPENSE) inst = 1
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        // 標題列
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "關閉") }
            Spacer(Modifier.weight(1f))
            PillSegment(
                listOf("支出", "收入", "轉帳"),
                when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.TRANSFER -> 2 },
                { i -> setType(listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)[i]) },
            )
            Spacer(Modifier.weight(1f))
            if (orig != null) {
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // 常用記帳
        val tpls = d.templates.filter { it.type == type }
        if (orig == null && tpls.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(tpls, key = { it.id }) { tp ->
                    val tc = tp.categoryId?.let { d.catMap[it] }
                    CuteChip(
                        "⭐ ${tp.name}" + if (tp.amount > 0) " ${formatMoney(tp.amount)}" else "",
                        false,
                        {
                            if (tp.amount > 0) expr = tp.amount.toString()
                            if (tc != null) catId = tc.id
                            tp.accountId?.let { a -> if (d.accMap[a]?.hidden == false) accId = a }
                            if (tp.note.isNotBlank()) note = tp.note
                            if (tp.tags.isNotEmpty()) tagsText = tp.tags.joinToString(" ")
                        },
                    )
                }
            }
        }

        // 金額
        CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    type == TxType.TRANSFER -> "🔁 轉帳"
                    cat != null && parent != null && parent.id != cat.id -> "${cat.emoji} ${parent.name}・${cat.name}"
                    cat != null -> "${cat.emoji} ${cat.name}"
                    else -> "請選分類"
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f), maxLines = 1)
                if (pending) Text("= ${formatMoney(amount)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (expr.isEmpty()) "$0" else "$" + Calc.pretty(expr),
                style = MaterialTheme.typography.displaySmall,
                color = when (type) {
                    TxType.EXPENSE -> cute.expense
                    TxType.INCOME -> cute.income
                    TxType.TRANSFER -> cute.ink
                },
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))

        // 分類或轉帳帳戶
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (type == TxType.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountPick("從", accId?.let { d.accMap[it] }?.let { "${it.emoji} ${it.name}" } ?: "選擇帳戶") { dialog = "from" }
                    Text("⬇", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = cute.sub, fontSize = 20.sp)
                    AccountPick("轉到", toAccId?.let { d.accMap[it] }?.let { "${it.emoji} ${it.name}" } ?: "選擇帳戶") { dialog = "to" }
                    if (accs.size < 2) {
                        Text("轉帳需要至少兩個帳戶，可以到「我的 → 帳戶管理」新增。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
            } else {
                Column {
                    val kids = parent?.let { d.childrenOf(it.id) } ?: emptyList()
                    if (kids.isNotEmpty() && parent != null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            item { CuteChip("${parent.emoji} 全部${parent.name}", catId == parent.id, { catId = parent.id }) }
                            items(kids, key = { it.id }) { k -> CuteChip("${k.emoji} ${k.name}", catId == k.id, { catId = k.id }) }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(d.topCategories(type), key = { it.id }) { c ->
                            val on = parent?.id == c.id
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { catId = c.id }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CatBubble(c.emoji, c.color, 42.dp)
                                Text(
                                    c.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (on) cute.ink else cute.sub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 附加資訊
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CuteChip("📅 ${dayLabel(day)}", false, { showDatePicker(context, day) { day = it } })
            if (type != TxType.TRANSFER && accs.isNotEmpty()) {
                val a = accId?.let { d.accMap[it] }
                CuteChip("${a?.emoji ?: "👛"} ${a?.name ?: "帳戶"}", false, { dialog = "from" })
            }
            CuteChip(if (note.isBlank()) "✏️ 備註" else "✏️ ${note.take(8)}", note.isNotBlank(), { dialog = "note" })
            CuteChip(if (tags.isEmpty()) "🏷️ 標籤" else "🏷️ " + tags.joinToString(" ") { "#$it" }.take(14), tags.isNotEmpty(), { dialog = "tags" })
            if (orig == null && type == TxType.EXPENSE) {
                CuteChip(if (inst > 1) "🧾 分 $inst 期" else "🧾 分期", inst > 1, { dialog = "inst" })
            }
            if (orig == null) CuteChip("⭐ 存為常用", false, { dialog = "tpl" })
            if (orig != null && orig.instTotal > 1) CuteChip("分期 ${orig.instIndex}/${orig.instTotal}", false, {})
        }

        Keypad(
            onKey = { k -> expr = Calc.press(expr, k) },
            doneLabel = if (pending) "=" else "完成",
            doneEnabled = pending || canSave,
            onDone = {
                if (pending) {
                    expr = if (amount > 0) amount.toString() else ""
                } else if (canSave) {
                    vm.saveTxn(editId, draft())
                    onClose()
                } else if (amount <= 0) {
                    vm.toast("請先輸入金額")
                } else if (type == TxType.TRANSFER) {
                    vm.toast("請選擇兩個不同的帳戶")
                }
            },
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    // ───── 對話框 ─────
    when (dialog) {
        "note" -> {
            var text by remember { mutableStateOf(note) }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("備註") },
                text = {
                    OutlinedTextField(text, { text = it.take(80) }, placeholder = { Text("例如：和朋友聚餐") }, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = { TextButton(onClick = { note = text; dialog = "" }) { Text("好") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "tags" -> {
            var text by remember { mutableStateOf(tagsText) }
            val known = d.allTags().take(12)
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("標籤") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            text, { text = it.take(80) },
                            placeholder = { Text("用空白分開，例如：旅行 約會") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (known.isNotEmpty()) {
                            Text("常用標籤", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val cur = parseTags(text)
                                known.forEach { k ->
                                    CuteChip("#$k", k in cur, {
                                        text = if (k in cur) (cur - k).joinToString(" ") else (cur + k).joinToString(" ")
                                    })
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { tagsText = text; dialog = "" }) { Text("好") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "inst" -> {
            var text by remember { mutableStateOf(if (inst > 1) inst.toString() else "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("分期付款") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("金額會平均分成每月一筆，從選擇的日期開始。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 6, 12, 24).forEach { n -> CuteChip("$n 期", text == n.toString(), { text = n.toString() }) }
                        }
                        OutlinedTextField(
                            text, { text = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("期數") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val n = text.toIntOrNull() ?: 1
                        if (n > 1 && amount > 0) Text("每期約 ${formatMoney(amount / n)}", color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = { TextButton(onClick = { inst = (text.toIntOrNull() ?: 1).coerceIn(1, 60); dialog = "" }) { Text("好") } },
                dismissButton = { TextButton(onClick = { inst = 1; dialog = "" }) { Text("不分期") } },
            )
        }
        "tpl" -> {
            var text by remember { mutableStateOf(cat?.name ?: "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("存為常用記帳") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "會記住目前的分類、帳戶、金額、備註和標籤，下次在上方一點就帶入。金額留 0 代表每次自己輸入。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        OutlinedTextField(text, { text = it.take(12) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) vm.addTemplate(text.trim(), draft())
                        dialog = ""
                    }) { Text("儲存") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "from", "to" -> {
            val isTo = dialog == "to"
            val bal = remember(d) { d.balances() }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(if (isTo) "轉入帳戶" else "選擇帳戶") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        accs.forEach { a ->
                            val on = if (isTo) toAccId == a.id else accId == a.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        if (isTo) toAccId = a.id else accId = a.id
                                        dialog = ""
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(a.emoji, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(a.name, modifier = Modifier.weight(1f))
                                Text(formatMoney(bal[a.id] ?: 0L), color = cute.sub, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = "" }) { Text("關閉") } },
            )
        }
        "delete" -> {
            val o = orig
            if (o != null && o.instGroup != null && o.instTotal > 1) {
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("刪除分期記錄") },
                    text = { Text("這筆是分期 ${o.instIndex}/${o.instTotal}，要只刪這一期，還是整組分期一起刪？") },
                    confirmButton = {
                        TextButton(onClick = { vm.deleteTxn(o.id, true); dialog = ""; onClose() }) { Text("全部刪除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.deleteTxn(o.id, false); dialog = ""; onClose() }) { Text("只刪這期") }
                    },
                )
            } else if (o != null) {
                ConfirmDialog(
                    title = "刪除這筆記錄？",
                    text = "刪除後無法復原。",
                    confirm = "刪除",
                    onConfirm = { vm.deleteTxn(o.id, false); dialog = ""; onClose() },
                    onDismiss = { dialog = "" },
                )
            }
        }
    }
}

@Composable
private fun AccountPick(label: String, value: String, onClick: () -> Unit) {
    val cute = LocalCute.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = cute.sub, modifier = Modifier.width(48.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}
