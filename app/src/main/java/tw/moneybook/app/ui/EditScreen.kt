package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.Calc
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.ReimbCodec
import tw.moneybook.app.ReimbItem
import tw.moneybook.app.TxnDraft
import tw.moneybook.app.formatMoney
import java.time.LocalDate

private fun defaultCat(d: AppData, type: TxType): Long? =
    if (type == TxType.TRANSFER) null else d.topCategories(type).firstOrNull()?.id

@Composable
fun EditScreen(
    vm: MoneyViewModel,
    editId: Long?,
    presetTo: Long? = null,
    presetAmount: Long? = null,
    tplMode: Boolean = false,
    tplId: Long? = null,
    onClose: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val context = LocalContext.current
    val orig = remember(editId) { editId?.let { id -> d.txns.firstOrNull { it.id == id } } }
    val accs = d.visibleAccounts
    val tpl = remember(tplId) { tplId?.let { id -> d.templates.firstOrNull { it.id == id } } }
    var tplName by rememberSaveable { mutableStateOf(tpl?.name ?: "") }
    var showTpl by remember { mutableStateOf(false) }

    var type by rememberSaveable {
        mutableStateOf(orig?.type ?: tpl?.type ?: if (presetTo != null) TxType.TRANSFER else TxType.EXPENSE)
    }
    var expr by rememberSaveable {
        mutableStateOf(orig?.amount?.toString() ?: tpl?.amount?.takeIf { it > 0 }?.toString() ?: presetAmount?.takeIf { it > 0 }?.toString() ?: "")
    }
    var catId by rememberSaveable { mutableStateOf(orig?.categoryId ?: tpl?.categoryId ?: defaultCat(d, type)) }
    var accId by rememberSaveable {
        mutableStateOf(
            orig?.accountId
                ?: tpl?.accountId
                ?: (if (presetTo != null) accs.firstOrNull { it.id != presetTo && it.type != tw.moneybook.app.AccountType.CARD }?.id else null)
                ?: accs.firstOrNull()?.id
        )
    }
    var toAccId by rememberSaveable { mutableStateOf(orig?.toAccountId ?: presetTo ?: accs.getOrNull(1)?.id) }
    var day by rememberSaveable { mutableLongStateOf(orig?.day ?: LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(orig?.note ?: tpl?.note ?: "") }
    var tagsText by rememberSaveable { mutableStateOf((orig?.tags ?: tpl?.tags)?.joinToString("\n") ?: "") }
    var inst by rememberSaveable { mutableIntStateOf(1) }
    var fee by rememberSaveable { mutableLongStateOf(orig?.fee ?: 0L) }
    var discount by rememberSaveable { mutableLongStateOf(orig?.discount ?: 0L) }
    // 報銷：reimbFull = 一個人、全額（金額跟著實付走）；否則用 reimbJson 存多個對象
    val origItems = remember { orig?.items ?: emptyList() }
    var reimbOn by rememberSaveable { mutableStateOf(origItems.isNotEmpty()) }
    var reimbFull by rememberSaveable {
        mutableStateOf(origItems.isEmpty() || (origItems.size == 1 && origItems[0].amount >= (orig?.paid ?: 0L)))
    }
    var reimbWho by rememberSaveable { mutableStateOf(origItems.singleOrNull()?.who ?: "") }
    var reimbJson by rememberSaveable { mutableStateOf(ReimbCodec.encode(origItems)) }
    var noteFocused by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    // 系統鍵盤直接蓋在畫面上，不要把畫面往上推（備註在最上面，不會被蓋到）
    val hostContext = LocalContext.current
    DisposableEffect(Unit) {
        var c = hostContext
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window
        val old = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose { if (window != null && old != null) window.setSoftInputMode(old) }
    }


    val amount = Calc.eval(expr)
    val pending = Calc.hasOp(expr)
    val tags = tagsText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val effDiscount = if (type == TxType.EXPENSE) discount else 0L
    val actual = when (type) {
        TxType.EXPENSE -> (amount - effDiscount + fee).coerceAtLeast(0L)
        TxType.INCOME -> (amount - fee).coerceAtLeast(0L)
        TxType.TRANSFER -> amount + fee
    }
    val reimbItems: List<ReimbItem> = when {
        type != TxType.EXPENSE || !reimbOn -> emptyList()
        reimbFull -> listOf((origItems.singleOrNull() ?: ReimbItem("", 0L)).copy(who = reimbWho.trim(), amount = actual))
        // 上限跟著目前的金額走：先填報銷、後填金額時，金額還是 0，不能先把報銷金額截成 0
        else -> ReimbCodec.decode(reimbJson).map { if (amount + fee > 0L && it.pays.isEmpty() && !it.closed) it.copy(amount = it.amount.coerceAtMost(amount + fee)) else it }
    }
    val cat = catId?.let { d.catMap[it] }
    val parent = cat?.let { d.topOf(it) }
    val targetOk = when (type) {
        TxType.TRANSFER -> accId != null && toAccId != null && accId != toAccId
        else -> catId != null
    }
    val canSave = if (tplMode) targetOk else amount > 0 && targetOk

    fun draft() = TxnDraft(
        type = type, amount = amount,
        categoryId = if (type == TxType.TRANSFER) null else catId,
        accountId = accId,
        toAccountId = if (type == TxType.TRANSFER) toAccId else null,
        day = day, note = note.trim(), tags = tags,
        installments = if (orig == null && type == TxType.EXPENSE) inst else 1,
        fee = fee,
        discount = effDiscount,
        reimbItems = reimbItems,
    )

    val initialDraft = remember { draft() }
    val initialName = remember { tplName }
    val dirty = draft() != initialDraft || (tplMode && tplName != initialName)

    fun applyTemplate(tp: tw.moneybook.app.Template) {
        if (tp.amount > 0) expr = tp.amount.toString()
        tp.categoryId?.let { c -> if (d.catMap[c] != null) catId = c }
        tp.accountId?.let { a -> if (d.accMap[a]?.hidden == false) accId = a }
        if (tp.note.isNotBlank()) note = tp.note
        if (tp.tags.isNotEmpty()) tagsText = tp.tags.joinToString("\n")
        showTpl = false
    }

    /** 儲存；成功回傳 true */
    fun doSave(): Boolean {
        if (!canSave) {
            vm.toast(
                when {
                    !tplMode && amount <= 0 -> "請先輸入金額"
                    type == TxType.TRANSFER -> "請選擇兩個不同的帳戶"
                    else -> "請選擇分類"
                }
            )
            return false
        }
        if (tplMode) {
            if (tplName.isBlank()) {
                dialog = "tplname"
                return false
            }
            vm.saveTemplate(tplId, tplName.trim(), draft())
            vm.toast("常用記帳已儲存")
        } else {
            vm.saveTxn(editId, draft())
        }
        onClose()
        return true
    }

    fun tryClose() {
        when {
            noteFocused -> focus.clearFocus()
            dirty -> dialog = "unsaved"
            else -> onClose()
        }
    }

    BackHandler { tryClose() }

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
            IconButton(onClick = { tryClose() }) { Icon(Icons.Filled.Close, contentDescription = "關閉") }
            Spacer(Modifier.weight(1f))
            PillSegment(
                listOf("支出", "收入", "轉帳"),
                when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.TRANSFER -> 2 },
                { i -> setType(listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)[i]) },
            )
            Spacer(Modifier.weight(1f))
            if (orig != null || (tplMode && tplId != null)) {
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // 編輯既有的帳目或常用記帳時，標示正在改的是哪一筆（內容固定為開啟時的樣子，不隨輸入變動）
        if (orig != null || (tplMode && tplId != null)) {
            val oc = orig?.categoryId?.let { d.catMap[it] }
            val op = oc?.parentId?.let { d.catMap[it] }
            val what = when {
                tplMode -> "常用記帳「${initialName.ifBlank { "未命名" }}」"
                orig == null -> ""
                orig.type == TxType.TRANSFER -> "${dayLabel(orig.day)}　轉帳　${formatMoney(orig.paid)}"
                else -> "${dayLabel(orig.day)}　" + listOfNotNull(op?.name, oc?.name ?: "未分類").joinToString(" › ") + "　${formatMoney(orig.paid)}"
            }
            val onBanner = MaterialTheme.colorScheme.onPrimaryContainer
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides onBanner) { IconGlyph("vec:pencil", 16.sp) }
                Spacer(Modifier.width(8.dp))
                Text("正在編輯", style = MaterialTheme.typography.labelLarge, color = onBanner, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(what, style = MaterialTheme.typography.bodyMedium, color = onBanner, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        // 備註：放在最上面，鍵盤開關時不會跟著移動
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            placeholder = { Text("備註（選填）") },
            leadingIcon = { IconGlyph("vec:pencil", 18.sp) },
            minLines = 1,
            maxLines = if (noteFocused) 3 else 1,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            trailingIcon = if (noteFocused) {
                { TextButton(onClick = { focus.clearFocus() }) { Text("完成") } }
            } else null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).onFocusChanged { noteFocused = it.isFocused },
        )

        // 分類或轉帳帳戶
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (type == TxType.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccountPick("從", accId?.let { d.accMap[it] }?.let { accLabel(it) } ?: "選擇帳戶") { dialog = "from" }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides cute.sub) { IconGlyph("vec:down", 22.sp) }
                    }
                    AccountPick("轉到", toAccId?.let { d.accMap[it] }?.let { accLabel(it) } ?: "選擇帳戶") { dialog = "to" }
                    if (accs.size < 2) {
                        Text("轉帳需要至少兩個帳戶，可以到「我的 → 帳戶管理」新增。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
            } else {
                Column {
                    // 大分類：上方的小膠囊；「⭐ 常用」也是一個大分類
                    val tops = d.topCategories(type)
                    val tpls = if (!tplMode && orig == null) d.templates.filter { it.type == type } else emptyList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        if (tpls.isNotEmpty()) {
                            item { CuteChip("常用", showTpl, { showTpl = true }, icon = "vec:star") }
                        }
                        items(tops, key = { it.id }) { c ->
                            CuteChip(iconLabel(c.emoji, c.name), !showTpl && parent?.id == c.id, { catId = c.id; showTpl = false })
                        }
                    }
                    if (showTpl) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(tpls, key = { it.id }) { tp ->
                                val tc = tp.categoryId?.let { d.catMap[it] }
                                Column(
                                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { applyTemplate(tp) }.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CatBubble(tc?.emoji ?: "img:ui_favorite", tc?.color ?: 1, 46.dp)
                                    Text(tp.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                    if (tp.amount > 0) Text(formatMoney(tp.amount), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                }
                            }
                        }
                    } else {
                    // 子分類：下方的大圖示（第一格代表大分類本身）
                    val kids = parent?.let { d.childrenOf(it.id) } ?: emptyList()
                    val tiles = if (parent != null) listOf(parent) + kids else emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(tiles, key = { it.id }) { c ->
                            val on = catId == c.id
                            val isParent = c.id == parent?.id
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { catId = c.id }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CatBubble(c.emoji, c.color, 46.dp)
                                Text(
                                    if (isParent && kids.isNotEmpty()) "不細分" else c.name,
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
        }

        // 附加資訊
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tplMode) CuteChip("名稱：" + tplName.ifBlank { "未命名" }, tplName.isNotBlank(), { dialog = "tplname" }, icon = "vec:pencil")
            if (!tplMode) CuteChip(dayLabel(day), false, { dialog = "date" }, icon = "vec:calendar")
            if (type != TxType.TRANSFER && accs.isNotEmpty()) {
                val a = accId?.let { d.accMap[it] }
                CuteChip(a?.let { accLabel(it) } ?: "帳戶", false, { dialog = "from" }, icon = "vec:wallet")
            }
            CuteChip(if (tags.isEmpty()) "新增標籤" else tags.joinToString(" ") { "#$it" }.take(16), tags.isNotEmpty(), { dialog = "tags" }, icon = "vec:tag")
            val feeLabel = when {
                fee > 0 && effDiscount > 0 -> "手續費・優惠"
                fee > 0 -> "手續費 ${formatMoney(fee)}"
                effDiscount > 0 -> "優惠 ${formatMoney(effDiscount)}"
                type == TxType.EXPENSE -> "手續費／優惠"
                else -> "手續費"
            }
            CuteChip(feeLabel, fee > 0 || effDiscount > 0, { dialog = "fee" }, icon = if (fee == 0L && effDiscount > 0) "vec:ticket" else "vec:coin")
            if (type == TxType.EXPENSE && !tplMode) {
                val totalReimb = reimbItems.sumOf { it.effective }
                val part = if (reimbItems.isNotEmpty() && totalReimb != actual) " ${formatMoney(totalReimb)}" else ""
                val people = if (reimbItems.size > 1) "・${reimbItems.size} 人" else ""
                CuteChip(
                    when {
                        reimbItems.isEmpty() -> "報銷"
                        reimbItems.all { it.closed } -> "已報銷$part"
                        else -> "待報銷$part$people"
                    },
                    reimbItems.isNotEmpty(),
                    { dialog = "reimb" },
                    icon = if (reimbItems.isNotEmpty() && reimbItems.all { it.closed }) "vec:check" else "vec:receipt",
                )
            }
            if (orig == null && type == TxType.EXPENSE && !tplMode) {
                CuteChip(if (inst > 1) "分 $inst 期" else "分期", inst > 1, { dialog = "inst" }, icon = "vec:repeat")
            }
            if (orig == null && !tplMode) CuteChip("存為常用", false, { dialog = "tpl" }, icon = "vec:star")
            if (orig != null && orig.instTotal > 1) CuteChip("分期 ${orig.instIndex}/${orig.instTotal}", false, {})
        }

        // 金額（貼在數字鍵盤正上方）
        Spacer(Modifier.height(8.dp))
        CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            if (pending || (cat == null && type != TxType.TRANSFER)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (cat == null && type != TxType.TRANSFER) "請先選分類" else "",
                        style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f),
                    )
                    if (pending) Text("= ${formatMoney(amount)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
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
            if (fee > 0 || effDiscount > 0) {
                val parts = ArrayList<String>()
                parts.add(if (type == TxType.TRANSFER) "轉帳 ${formatMoney(amount)}" else "金額 ${formatMoney(amount)}")
                if (effDiscount > 0) parts.add("− 優惠 ${formatMoney(effDiscount)}")
                if (fee > 0) parts.add((if (type == TxType.INCOME) "− " else "+ ") + "手續費 ${formatMoney(fee)}")
                val label = when (type) {
                    TxType.EXPENSE -> "實付"
                    TxType.INCOME -> "實收"
                    TxType.TRANSFER -> "共扣"
                }
                Text(
                    parts.joinToString(" ") + " ＝ $label ${formatMoney(actual)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cute.sub,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Keypad(
            onKey = { k -> expr = Calc.press(expr, k) },
            doneLabel = if (pending) "=" else "完成",
            doneEnabled = pending || canSave,
            onDone = {
                if (pending) {
                    expr = if (amount > 0) amount.toString() else ""
                } else {
                    doSave()
                }
            },
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    // ───── 對話框 ─────
    when (dialog) {
        "reimb" -> ReimbEditPage(
            title = "${cat?.name ?: "未分類"}・${shortDate(day)}",
            actual = actual,
            cap = amount + fee,
            origItems = origItems,
            initOn = reimbOn,
            initFull = reimbFull,
            initJson = reimbJson,
            initWho = reimbWho,
            names = d.reimbNames(),
            onDone = { on2, full2, json2, who2 ->
                reimbOn = on2
                reimbFull = full2
                reimbJson = json2
                reimbWho = who2
                dialog = ""
            },
            onClose = { dialog = "" },
        )
        "unsaved" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("要儲存修改嗎？") },
            text = { Text(if (canSave) "你有還沒儲存的修改。" else "你有還沒儲存的修改，但目前的內容還不能儲存（${if (!tplMode && amount <= 0) "沒有金額" else "資料不完整"}）。") },
            confirmButton = {
                Row {
                    TextButton(onClick = { dialog = "" }) { Text("繼續編輯") }
                    if (canSave) TextButton(onClick = { dialog = ""; doSave() }) { Text("儲存") }
                }
            },
            dismissButton = { TextButton(onClick = { dialog = ""; onClose() }) { Text("不儲存", color = cute.expense) } },
        )
        "tplname" -> {
            var text by remember { mutableStateOf(tplName.ifBlank { cat?.name ?: "" }) }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("常用記帳的名稱") },
                text = {
                    OutlinedTextField(text, { text = it.take(12) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = { TextButton(onClick = { tplName = text.trim(); dialog = "" }) { Text("好") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "date" -> CuteDatePickerDialog(
            initial = day,
            onPick = { day = it; dialog = "" },
            onDismiss = { dialog = "" },
        )
        "tags" -> {
            var list by remember { mutableStateOf(tags) }
            var input by remember { mutableStateOf("") }
            val known = d.allTags().filter { it !in list }.take(15)
            fun addTag() {
                val t = input.trim().trimStart('#', '＃').trim()
                if (t.isNotEmpty() && t !in list && list.size < 10) list = list + t
                input = ""
            }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("標籤") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (list.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { t -> CuteChip("#$t  ✕", true, { list = list - t }) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                input, { input = it.take(12) },
                                placeholder = { Text("輸入一個標籤") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addTag() }),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { addTag() }, enabled = input.isNotBlank()) { Text("新增") }
                        }
                        if (known.isNotEmpty()) {
                            Text("用過的標籤", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                known.forEach { k -> CuteChip("#$k", false, { if (list.size < 10) list = list + k }) }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = input.trim().trimStart('#', '＃').trim()
                        val final = if (t.isNotEmpty() && t !in list) list + t else list
                        tagsText = final.joinToString("\n")
                        dialog = ""
                    }) { Text("完成") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "fee" -> {
            var feeText by remember { mutableStateOf(if (fee > 0) fee.toString() else "") }
            var discText by remember { mutableStateOf(if (discount > 0) discount.toString() else "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(if (type == TxType.EXPENSE) "手續費與優惠" else "手續費") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when (type) {
                                TxType.EXPENSE -> "上面輸入的是原價。優惠會從原價扣掉，手續費會加上去，帳戶扣的是實付金額。"
                                TxType.INCOME -> "手續費會從收到的金額扣掉，例如匯款手續費。"
                                TxType.TRANSFER -> "手續費從轉出帳戶多扣，例如跨行轉帳 $15，會算進支出統計。"
                            },
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        OutlinedTextField(
                            feeText, { feeText = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("手續費") }, prefix = { Text("$") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (type == TxType.EXPENSE) {
                            OutlinedTextField(
                                discText, { discText = it.filter { c -> c.isDigit() }.take(8) },
                                label = { Text("優惠／折扣") }, prefix = { Text("$") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        fee = feeText.toLongOrNull() ?: 0L
                        discount = discText.toLongOrNull() ?: 0L
                        dialog = ""
                    }) { Text("好") }
                },
                dismissButton = {
                    TextButton(onClick = { fee = 0L; discount = 0L; dialog = "" }) { Text("清除") }
                },
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
                            AccountLine(a, bal[a.id] ?: 0L, on) {
                                if (isTo) toAccId = a.id else accId = a.id
                                dialog = ""
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = "" }) { Text("關閉") } },
            )
        }
        "delete" -> {
            if (tplMode && tplId != null) {
                ConfirmDialog(
                    title = "刪除這個常用記帳？",
                    text = "刪除後不會影響已經記好的帳。",
                    confirm = "刪除",
                    onConfirm = { vm.deleteTemplate(tplId); dialog = ""; onClose() },
                    onDismiss = { dialog = "" },
                )
            }
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
