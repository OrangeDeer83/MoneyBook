package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.BadgePreset
import tw.moneybook.app.BadgePresets
import tw.moneybook.app.Book
import tw.moneybook.app.Category
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.formatMoney
import tw.moneybook.app.limitInfo
import tw.moneybook.app.pendingReimb
import tw.moneybook.app.ReimbItem
import tw.moneybook.app.ReimbReceipt
import tw.moneybook.app.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 子頁面的共用外框 */
@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
    ) {
        SubTopBar(title, onBack)
        Box(Modifier.weight(1f)) { content() }
    }
}

// ───────────────────────── 我的 ─────────────────────────

@Composable
fun MeScreen(vm: MoneyViewModel, open: (String) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    var budgetDialog by remember { mutableStateOf(false) }
    val mascot = d.prefs.mascot
    LazyColumn(
        Modifier.fillMaxSize(),
        state = vm.meListState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mascot != "none") Mascot(mascot, moodOf(d), Modifier.size(84.dp)) else IconGlyph("img:ui_ledger", 48.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (mascot != "none") d.prefs.mascotName.ifBlank { mascotDefaultName(mascot) } else "記帳本",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "已經一起記了 ${d.txns.size} 筆・${d.books.size} 本帳本",
                            style = MaterialTheme.typography.bodySmall,
                            color = cute.sub,
                        )
                    }
                }
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(8.dp)) {
                MenuRow("vec:book", "帳本管理", "目前：${d.currentBook.name}") { open("books") }
                MenuRow("vec:wallet", "帳戶管理", "${d.visibleAccounts.size} 個帳戶") { open("accounts") }
                MenuRow("vec:folder", "分類管理", "新增、改圖示、子分類、排序") { open("categories") }
                MenuRow("vec:receipt", "報銷", d.bookTxns.pendingReimb().let { p -> if (p.isEmpty()) "沒有待報銷的項目" else "待報銷 ${p.size} 筆・${formatMoney(p.sumOf { it.reimbOutstanding })}" }) { open("reimb") }
                MenuRow("vec:star", "常用記帳", if (d.templates.isEmpty()) "在記一筆畫面按「存為常用」" else "${d.templates.size} 個") { open("templates") }
                MenuRow(
                    "vec:target", "每月預算",
                    d.currentBook.budgetFor(vm.month).let { b -> if (b > 0) "${vm.month.monthValue} 月：${formatMoney(b)}" + (if (d.currentBook.monthBudgets.isNotEmpty()) "・有個別月份設定" else "") else "尚未設定" },
                ) { budgetDialog = true }
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(8.dp)) {
                MenuRow("vec:palette", "外觀與吉祥物", "${palOf(d.prefs.palette).name}・" + (if (mascot == "none") "不顯示吉祥物" else mascotDefaultName(mascot))) { open("appearance") }
                MenuRow("vec:save", "備份與匯入匯出", "備份檔、CSV") { open("data") }
            }
        }
        item {
            Text(
                "記帳本 v${tw.moneybook.app.BuildConfig.VERSION_NAME}・資料只存在這支手機裡，記得定期備份喔",
                style = MaterialTheme.typography.bodySmall,
                color = cute.sub,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }

    if (budgetDialog) BudgetDialog(vm, vm.month) { budgetDialog = false }
}

/** 預算設定：每個月都一樣，或只設定某個月 */
@Composable
fun BudgetDialog(vm: MoneyViewModel, month: java.time.YearMonth, onDismiss: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val book = d.currentBook
    val hasOverride = book.monthBudgets.containsKey(month.toString())
    var mode by remember { mutableStateOf(if (hasOverride) 1 else 0) } // 0 每月, 1 只有這個月
    var text by remember {
        mutableStateOf(
            (if (hasOverride) book.monthBudgets[month.toString()] ?: 0L else book.budget).takeIf { it > 0 }?.toString() ?: ""
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("預算設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillSegment(listOf("每個月", "只有 ${month.monthValue} 月"), mode, { m ->
                    mode = m
                    val v = if (m == 1) book.budgetFor(month) else book.budget
                    text = if (v > 0) v.toString() else ""
                })
                Text(
                    if (mode == 0) "「${book.name}」每個月的預設預算，留空代表不設定。"
                    else "只套用在 ${month.year} 年 ${month.monthValue} 月，其他月份還是用每月預設（${formatMoney(book.budget)}）。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() }.take(10) },
                    prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val others = book.monthBudgets.entries.sortedByDescending { it.key }
                if (others.isNotEmpty()) {
                    Text("個別設定的月份", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    others.take(6).forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${k.replace("-", " 年 ")} 月", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMoney(v), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.setBudget(-1L, java.time.YearMonth.parse(k)) }) { Text("清除") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toLongOrNull() ?: 0L
                vm.setBudget(v, if (mode == 0) null else month)
                vm.toast("預算已更新")
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ───────────────────────── 帳本 ─────────────────────────

@Composable
fun BooksScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val d = vm.data
    var editing by remember { mutableStateOf<Book?>(null) }
    var adding by remember { mutableStateOf(false) }
    SubPage("帳本管理", onBack) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "不同帳本的記錄分開計算，例如「個人」、「旅行」、「家庭」。帳戶是所有帳本共用的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalCute.current.sub,
                )
            }
            items(d.books, key = { it.id }) { b ->
                val count = d.txns.count { it.bookId == b.id }
                CuteCard(Modifier.fillMaxWidth(), onClick = { editing = b }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconGlyph(b.emoji, 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$count 筆" + if (b.budget > 0) "・預算 ${formatMoney(b.budget)}" else "",
                                style = MaterialTheme.typography.bodySmall, color = LocalCute.current.sub,
                            )
                        }
                        if (b.id == d.currentBook.id) {
                            Text("使用中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        } else {
                            TextButton(onClick = { vm.switchBook(b.id) }) { Text("切換") }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增帳本") }
            }
        }
    }
    val target = editing
    if (adding || target != null) {
        BookDialog(
            book = target,
            onSave = { name, emoji, budget ->
                vm.saveBook(target?.id, name, emoji, budget)
                adding = false; editing = null
            },
            onDelete = if (target != null && d.books.size > 1) ({ vm.deleteBook(target.id); editing = null }) else null,
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun BookDialog(book: Book?, onSave: (String, String, Long) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(book?.name ?: "") }
    var emoji by remember { mutableStateOf(book?.emoji ?: "img:extra_passport") }
    var budget by remember { mutableStateOf(if ((book?.budget ?: 0L) > 0) book?.budget.toString() else "") }
    var pick by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (book == null) "新增帳本" else "編輯帳本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiButton(emoji) { pick = true }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(name, { name = it.take(12) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    budget, { budget = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("每月預算（選填）") }, prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDel = true }) { Text("刪除這本帳本", color = LocalCute.current.expense) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), emoji, budget.toLongOrNull() ?: 0L) }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pick) EmojiPickerDialog(emoji, { emoji = it; pick = false }, { pick = false })
    if (confirmDel && onDelete != null) {
        ConfirmDialog("刪除帳本？", "帳本裡的所有記錄都會一起刪除，無法復原。", "刪除", { confirmDel = false; onDelete() }, { confirmDel = false })
    }
}

@Composable
fun EmojiButton(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(LocalCute.current.soft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IconGlyph(emoji, 26.sp) }
}

// ───────────────────────── 帳戶 ─────────────────────────

@Composable
fun AccountsScreen(vm: MoneyViewModel, onOpen: (Long) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val bal = remember(d) { d.balances() }
    var adding by remember { mutableStateOf(false) }
    // 編輯排序模式：右上角按鈕切換，開啟後每個帳戶右邊才出現拖曳把手
    var editing by remember { mutableStateOf(false) }
    val sorted = d.accounts.sortedBy { it.order }
    // 隱藏的帳戶預設不顯示（總資產本來就不含），底下有開關可以打開
    val showHidden = vm.accShowHidden
    val shown = sorted.filter { showHidden || !it.hidden }
    val hiddenCount = sorted.count { it.hidden }
    LazyColumn(
        state = vm.accListState,
        modifier = Modifier.fillMaxSize(),
        // 底部多留一點空間：記一筆的 + 按鈕會凸出來蓋到清單最底下（約 20dp），再留一點縫隙
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("帳戶", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                CuteChip(if (editing) "完成" else "編輯排序", editing, { editing = !editing }, icon = if (editing) null else "vec:pencil")
            }
        }
        // 隱藏帳戶的開關放在最上方，比較好按
        if (hiddenCount > 0) {
            item {
                TextButton(onClick = { vm.accShowHidden = !showHidden }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showHidden) "收起已隱藏的帳戶" else "顯示已隱藏的帳戶（$hiddenCount）")
                }
            }
        }
        item {
            val total = d.visibleAccounts.sumOf { bal[it.id] ?: 0L }
            CuteCard(Modifier.fillMaxWidth()) {
                Text("總資產", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                Text(formatMoney(total), style = MaterialTheme.typography.headlineMedium, color = if (total < 0) cute.expense else cute.ink)
            }
        }
        // 依帳戶類型分組（現金、銀行、信用卡…），每組有小計；拖曳排序只在同一組裡進行
        for (type in AccountType.values()) {
            val group = shown.filter { it.type == type }
            if (group.isEmpty()) continue
            val collapsed = type.name in d.prefs.collapsedAccTypes && !editing   // 編輯排序時全部展開
            item(key = "head-${type.name}") {
                val sub = group.filter { !it.hidden }.sumOf { bal[it.id] ?: 0L }
                // 點標題收折／展開這一組
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { vm.toggleAccGroup(type) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        if (collapsed) AppIcons.ChevronRight else AppIcons.ChevronDown,
                        contentDescription = if (collapsed) "展開" else "收合", tint = cute.sub, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (collapsed) {
                        Text("${group.size} 個帳戶", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(formatMoney(sub), style = MaterialTheme.typography.labelLarge, color = if (sub < 0) cute.expense else cute.sub)
                }
            }
            if (collapsed) continue
            item(key = "group-${type.name}") {
                // 切換編輯模式時重新建立，才不會留著上一個模式的把手位置
                androidx.compose.runtime.key(editing) {
                    ReorderColumn(
                        group, { it.id },
                        { newGroupOrder ->
                            // 把這一組的新順序放回全部帳戶的順序裡，其他組不動
                            // （隱藏的帳戶不在這一組裡，保持原本的位置）
                            val inGroup = newGroupOrder.toSet()
                            val queue = newGroupOrder.iterator()
                            vm.reorderAccounts(sorted.map { acc -> if (acc.id in inGroup) queue.next() else acc.id })
                        },
                    ) { a, handle, _ ->
                        val b = bal[a.id] ?: 0L
                        CuteCard(
                            Modifier.fillMaxWidth(),
                            onClick = if (editing) null else ({ onOpen(a.id) }),
                            padding = PaddingValues(start = 14.dp, end = if (editing) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AccountIcon(a, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.name + if (a.hidden) "（已隱藏）" else "", style = MaterialTheme.typography.titleMedium, color = if (a.hidden) cute.sub else cute.ink)
                                    d.limitInfo(a, bal)?.let { info ->
                                        Text("可用 ${formatMoney(info.available)}", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                                    }
                                }
                                Text(formatMoney(b), color = if (b < 0) cute.expense else cute.ink, style = MaterialTheme.typography.titleMedium)
                                FavoriteStar(a.favorite) { vm.setFavorite(a.id, !a.favorite) }
                                if (editing) DragHandle(handle)
                            }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增帳戶") } }
        item {
            Text(
                if (editing) "按住右邊的 ≡ 拖曳排序，排好按「完成」。"
                else "點帳戶可以看它的明細。餘額 = 初始金額 + 收入 − 支出 ± 轉帳。",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
        }
    }
    if (adding) {
        AccountDialog(
            acc = null,
            cards = d.accounts.filter { it.type == AccountType.CARD },
            onSave = { na -> vm.saveAccount(null, na); adding = false },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountDialog(
    acc: Account?,
    /** 現有的信用卡（挑「共用額度」的卡用） */
    cards: List<Account> = emptyList(),
    onSave: (Account) -> Unit,
    /** 共用額度連帶要改的其他卡：帳戶 id → 主卡 id（0 = 不共用） */
    onShares: (Map<Long, Long>) -> Unit = {},
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cute = LocalCute.current
    var name by remember { mutableStateOf(acc?.name ?: "") }
    var type by remember { mutableStateOf(acc?.type ?: AccountType.BANK) }
    var emoji by remember { mutableStateOf(acc?.emoji ?: AccountType.BANK.emoji) }
    var useBadge by remember { mutableStateOf((acc?.badge ?: "").isNotBlank()) }
    var badge by remember { mutableStateOf(acc?.badge ?: "") }
    var badgeCol by remember { mutableStateOf(acc?.badgeColor ?: 0) }
    // 雙色漸層的起點／終點（0 = 沒有漸層，用 badgeCol 的單色）
    var gradFrom by remember { mutableStateOf(acc?.badgeFrom ?: 0L) }
    var gradTo by remember { mutableStateOf(acc?.badgeTo ?: 0L) }
    var initial by remember { mutableStateOf(acc?.initial?.takeIf { it != 0L }?.toString() ?: "") }
    var hidden by remember { mutableStateOf(acc?.hidden ?: false) }
    var favorite by remember { mutableStateOf(acc?.favorite ?: false) }
    var limit by remember { mutableStateOf(acc?.creditLimit?.takeIf { it > 0 }?.toString() ?: "") }
    var stmt by remember { mutableStateOf(acc?.statementDay?.takeIf { it > 0 }?.toString() ?: "") }
    var due by remember { mutableStateOf(acc?.dueDay?.takeIf { it > 0 }?.toString() ?: "") }
    var sharedOf by remember { mutableStateOf(acc?.sharedLimitOf ?: 0L) }
    // 和這張卡同一組共用額度的其他卡（可以多選）：這張是主卡時就是掛在它底下的卡；這張是副卡時是同組的其他副卡
    var members by remember {
        mutableStateOf(
            if (acc == null) emptySet<Long>()
            else {
                val ownerId = if (acc.sharedLimitOf != 0L) acc.sharedLimitOf else acc.id
                cards.filter { it.id != acc.id && it.id != ownerId && it.sharedLimitOf == ownerId }.map { it.id }.toSet()
            },
        )
    }
    var pick by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    // 按「離開共用」這類操作要馬上反映在可以選的卡上，所以各卡的關係用「畫面上現在的狀態」算，不是存檔裡的
    val vCards = cards.map { if (it.id == acc?.id) it.copy(sharedLimitOf = sharedOf) else it }
    fun groupMembers(o: Account) = vCards.filter { it.sharedLimitOf == o.id && it.id != o.id }
    /** 有組員的主卡，代表一整個共用組 */
    fun isGroupOwner(o: Account) = o.sharedLimitOf == 0L && groupMembers(o).isNotEmpty()
    fun groupLabel(o: Account) = (listOf(o) + groupMembers(o)).joinToString("＋") { it.name } + "（共用）"
    // 單獨一張卡選了一整個共用組：加入那一組，額度用那一組主卡的
    val selGroup: Account? =
        if (acc != null && type == AccountType.CARD && sharedOf == 0L && cards.none { it.sharedLimitOf == acc.id })
            vCards.firstOrNull { it.id in members && it.id != acc.id && isGroupOwner(it) }
        else null
    // 這張卡自己沒額度、也沒有帶別的卡，卻選了有額度的卡一起共用：那張有額度的卡就是主卡（額度用它的）
    val joinOwner: Account? = selGroup ?:
        if (acc != null && type == AccountType.CARD && sharedOf == 0L && members.isNotEmpty() &&
            (limit.toLongOrNull() ?: 0L) <= 0L && cards.none { it.sharedLimitOf == acc.id }
        ) cards.firstOrNull { it.id in members && it.creditLimit > 0L } else null

    fun build(): Account = Account(
        id = acc?.id ?: 0L,
        name = name.trim(),
        emoji = emoji,
        type = type,
        initial = initial.toLongOrNull() ?: 0L,
        order = acc?.order ?: 0,
        hidden = hidden,
        favorite = favorite,
        badge = if (useBadge) badge.trim().take(4) else "",
        badgeColor = badgeCol,
        badgeFrom = if (useBadge) gradFrom else 0L,
        badgeTo = if (useBadge) gradTo else 0L,
        creditLimit = if (type == AccountType.CARD) limit.toLongOrNull() ?: 0L else 0L,
        statementDay = if (type == AccountType.CARD) (stmt.toIntOrNull() ?: 0).coerceIn(0, 31) else 0,
        dueDay = if (type == AccountType.CARD) (due.toIntOrNull() ?: 0).coerceIn(0, 31) else 0,
        sharedLimitOf = if (type == AccountType.CARD) (joinOwner?.id ?: sharedOf) else 0L,
    )

    /** 按儲存時，其他卡的共用額度要怎麼改 */
    fun shareUpdates(): Map<Long, Long> {
        if (acc == null || type != AccountType.CARD) return emptyMap()
        val ownerId = joinOwner?.id ?: (if (sharedOf != 0L) sharedOf else acc.id)
        val out = HashMap<Long, Long>()
        if (selGroup == null) {
            cards.forEach { c -> if (c.id != acc.id && c.id != ownerId && c.sharedLimitOf == ownerId && c.id !in members) out[c.id] = 0L }
        }
        members.forEach { id -> if (id != ownerId && cards.firstOrNull { it.id == id }?.sharedLimitOf != ownerId) out[id] = ownerId }
        return out
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (acc == null) "新增帳戶" else "編輯帳戶") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { if (!useBadge) pick = true }) { AccountIcon(build(), 52.dp) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        name,
                        { v ->
                            if (useBadge && (badge.isBlank() || badge == name.take(2))) badge = v.take(2)
                            name = v.take(12)
                        },
                        label = { Text("名稱") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("設為常用帳戶（記一筆選帳戶時固定放最上面）", modifier = Modifier.weight(1f))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CuteChip("表情符號", !useBadge, { useBadge = false })
                    CuteChip("文字徽章", useBadge, {
                        useBadge = true
                        if (badge.isBlank()) badge = name.take(2)
                    })
                }
                if (useBadge) {
                    // 銀行、行動支付的預設：點一下就帶入簡稱和雙色漸層（名稱不會被改）
                    fun usePreset(p: BadgePreset) { badge = p.badge; gradFrom = p.from; gradTo = p.to }
                    Text("常見銀行", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    PresetRow(BadgePresets.banks) { usePreset(it) }
                    Text("常見行動支付", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    PresetRow(BadgePresets.pays) { usePreset(it) }
                    OutlinedTextField(
                        badge, { badge = it.take(4) },
                        label = { Text("徽章文字（最多 4 個字）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("雙色漸層", modifier = Modifier.weight(1f))
                        Switch(
                            checked = gradFrom != 0L,
                            onCheckedChange = { on ->
                                if (on) {
                                    val (f, t) = BadgePresets.autoGradient(badgeColor(badgeCol).toArgb().toLong() and 0xFFFFFFFFL)
                                    gradFrom = f; gradTo = t
                                } else { gradFrom = 0L; gradTo = 0L }
                            },
                        )
                    }
                    Text("自選顏色：", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    ColorDots(BadgeColors, if (gradFrom == 0L) badgeCol else -1) { i ->
                        badgeCol = i
                        if (gradFrom != 0L) {
                            val (f, t) = BadgePresets.autoGradient(badgeColor(i).toArgb().toLong() and 0xFFFFFFFFL)
                            gradFrom = f; gradTo = t
                        }
                    }
                    Text(
                        "可以點上面的銀行或行動支付，也可以自己打字（例如「悠遊」）再選顏色；開「雙色漸層」會用你選的顏色自動配漸層。",
                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                    )
                } else {
                    TextButton(onClick = { pick = true }) { Text("換一個表情符號") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.values().forEach { t ->
                        CuteChip(iconLabel(t.emoji, t.label), type == t, {
                            if (emoji == type.emoji) emoji = t.emoji
                            type = t
                        })
                    }
                }
                OutlinedTextField(
                    initial,
                    { s -> initial = s.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }.take(11) },
                    label = { Text(if (type == AccountType.CARD || type == AccountType.LOAN) "初始金額（欠款請填負數）" else "初始金額") },
                    prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == AccountType.CARD) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconGlyph("img:acc_card", 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("信用卡設定", style = MaterialTheme.typography.labelLarge)
                    }
                    // 共用額度：幾張卡共用同一個額度（額度取主卡的，已用金額一起算）。
                    // 新增的卡先挑一張卡或一整個共用組；已經存在的卡可以多選，一次選好所有一起共用的卡
                    val others = vCards.filter { it.id != acc?.id && it.type == AccountType.CARD && !it.hidden }
                    val owner = vCards.firstOrNull { it.id == sharedOf }
                    if (acc == null) {
                        val choices = others.filter { it.sharedLimitOf == 0L }      // 單張卡，或有組員的主卡（代表整個共用組）
                        if (choices.isNotEmpty()) {
                            Text("和其他信用卡共用額度", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CuteChip("不共用", sharedOf == 0L, { sharedOf = 0L })
                                choices.forEach { o -> CuteChip(if (isGroupOwner(o)) groupLabel(o) else o.name, sharedOf == o.id, { sharedOf = o.id }) }
                            }
                        }
                    } else {
                        val myOwnerId = if (sharedOf != 0L) sharedOf else if (cards.any { it.sharedLimitOf == acc.id }) acc.id else 0L
                        val candidates = if (myOwnerId != 0L) {
                            // 已經在一組裡：可以加入沒有在共用的卡，或取消同組的卡
                            others.filter { c -> c.id != myOwnerId && (c.sharedLimitOf == myOwnerId || (c.sharedLimitOf == 0L && groupMembers(c).isEmpty())) }
                        } else {
                            // 單獨一張：可以選沒有在共用的卡，或整個共用組
                            others.filter { c -> c.sharedLimitOf == 0L }
                        }
                        if (owner != null && sharedOf != 0L) {
                            Text("這張卡和「${owner.name}」共用額度", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            CuteChip("離開共用", false, { sharedOf = 0L; members = emptySet() })
                        }
                        if (candidates.isNotEmpty()) {
                            Text(
                                if (sharedOf != 0L) "也一起共用這組額度的卡（可以多選）" else "和這張卡共用額度的信用卡（可以多選）",
                                style = MaterialTheme.typography.labelMedium, color = cute.sub,
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                candidates.forEach { c ->
                                    val group = myOwnerId == 0L && isGroupOwner(c)
                                    CuteChip(if (group) groupLabel(c) else c.name, c.id in members, {
                                        members = when {
                                            c.id in members -> members - c.id
                                            // 同時只能加入一個共用組：選了這一組，就取消另一組
                                            group -> members.filter { x -> vCards.firstOrNull { it.id == x }?.let { y -> !isGroupOwner(y) } ?: true }.toSet() + c.id
                                            else -> members + c.id
                                        }
                                    })
                                }
                            }
                        }
                        if (selGroup != null) {
                            Text("加入「${groupLabel(selGroup)}」，額度和已用金額一起算，額度請到「${selGroup.name}」設定。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                        } else if (joinOwner != null) {
                            Text("額度和已用金額跟「${joinOwner.name}」一起算，額度請到那張卡設定。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                        } else if (sharedOf == 0L && members.isNotEmpty()) {
                            Text("有 ${members.size} 張卡跟這張共用額度，額度請在這張設定。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                        }
                    }
                    if (sharedOf != 0L && owner != null) {
                        Text("額度和已用金額跟「${owner.name}」一起算，額度請到那張卡設定。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    } else {
                        OutlinedTextField(
                            limit, { limit = it.filter { c -> c.isDigit() }.take(9) },
                            label = { Text("信用額度") }, prefix = { Text("$") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            stmt, { stmt = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("結帳日") }, suffix = { Text("號") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            due, { due = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("繳款日") }, suffix = { Text("號") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (acc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("隱藏這個帳戶", modifier = Modifier.weight(1f))
                        Switch(checked = hidden, onCheckedChange = { hidden = it })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDel = true }) { Text("刪除帳戶", color = cute.expense) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { onSave(build()); onShares(shareUpdates()) } }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pick) EmojiPickerDialog(emoji, { emoji = it; pick = false }, { pick = false })
    if (confirmDel && onDelete != null) {
        ConfirmDialog(
            "刪除帳戶？", "如果這個帳戶還有記錄，會改成隱藏，不會真的刪掉。", "刪除",
            { confirmDel = false; onDelete() }, { confirmDel = false },
        )
    }
}

// ───────────────────────── 分類 ─────────────────────────

private class CatEdit(val cat: Category?, val kind: TxType, val parentId: Long?)

@Composable
fun CategoriesScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    var kindIdx by rememberSaveable { mutableStateOf(0) }
    val kind = if (kindIdx == 0) TxType.EXPENSE else TxType.INCOME
    var edit by remember { mutableStateOf<CatEdit?>(null) }
    SubPage("分類管理", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PillSegment(listOf("支出", "收入"), kindIdx, { kindIdx = it }) }
            item {
                ReorderColumn(d.topCategories(kind), { it.id }, { vm.reorderCategories(it) }) { c, handle, _ ->
                    val kids = d.childrenOf(c.id)
                    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        CatLine(c, onClick = { edit = CatEdit(c, kind, null) }, handle = handle)
                        if (kids.isNotEmpty()) {
                            Box(Modifier.padding(start = 28.dp)) {
                                ReorderColumn(kids, { it.id }, { vm.reorderCategories(it) }, spacing = 2.dp) { k, kHandle, _ ->
                                    CatLine(k, onClick = { edit = CatEdit(k, kind, c.id) }, handle = kHandle, small = true)
                                }
                            }
                        }
                        Text(
                            "＋ 子分類",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 36.dp, top = 2.dp).clip(CircleShape)
                                .clickable { edit = CatEdit(null, kind, c.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { edit = CatEdit(null, kind, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ 新增${if (kind == TxType.EXPENSE) "支出" else "收入"}分類")
                }
            }
            item {
                Text(
                    "按住右邊的 ≡ 可以拖曳排序。刪除子分類時，原本的記錄會移到上層分類。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }
    val e = edit
    if (e != null) {
        val parent = e.parentId?.let { d.catMap[it] }
        CategoryDialog(
            cat = e.cat,
            parent = parent,
            onSave = { name, emoji, color ->
                vm.saveCategory(e.cat?.id, name, emoji, color, e.kind, e.parentId)
                edit = null
            },
            onDelete = if (e.cat != null) ({ if (vm.deleteCategory(e.cat.id)) edit = null }) else null,
            onDismiss = { edit = null },
        )
    }
}

@Composable
private fun CatLine(c: Category, onClick: () -> Unit, handle: Modifier, small: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalCute.current.card)
            .clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble(c.emoji, c.color, if (small) 32.dp else 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(c.name, modifier = Modifier.weight(1f), style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        DragHandle(handle)
    }
}

@Composable
private fun CategoryDialog(
    cat: Category?,
    parent: Category?,
    onSave: (String, String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(cat?.name ?: "") }
    var emoji by remember { mutableStateOf(cat?.emoji ?: parent?.emoji ?: "img:cat_box") }
    var color by remember { mutableStateOf(cat?.color ?: parent?.color ?: 0) }
    var pick by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    cat == null && parent != null -> "新增「${parent.name}」的子分類"
                    cat == null -> "新增分類"
                    else -> "編輯分類"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(catColor(color).copy(alpha = 0.35f)).clickable { pick = true },
                        contentAlignment = Alignment.Center,
                    ) { IconGlyph(emoji, 26.sp) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(name, { name = it.take(10) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (parent == null) {
                    Text("顏色", style = MaterialTheme.typography.labelMedium, color = LocalCute.current.sub)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatColors.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(c)
                                    .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { color = i }
                            )
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDel = true }) { Text("刪除分類", color = LocalCute.current.expense) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), emoji, color) }) { Text("儲存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pick) EmojiPickerDialog(emoji, { emoji = it; pick = false }, { pick = false })
    if (confirmDel && onDelete != null) {
        ConfirmDialog(
            "刪除「${cat?.name ?: ""}」？",
            if (cat?.parentId != null) "這個子分類的記錄會移到上層分類。" else "連同底下的子分類一起刪除。",
            "刪除",
            { confirmDel = false; onDelete() },
            { confirmDel = false },
        )
    }
}

// ───────────────────────── 常用記帳 ─────────────────────────

@Composable
fun TemplatesScreen(vm: MoneyViewModel, onOpen: (Long?) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    SubPage("常用記帳", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "常用記帳會出現在記一筆畫面的「常用」分類裡，點一下就帶入。點下面的項目可以修改分類、帳戶、金額、備註和標籤。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
            if (d.templates.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "還沒有常用記帳\n按下面的按鈕新增一個吧") }
            }
            items(d.templates, key = { it.id }) { t ->
                val c = t.categoryId?.let { d.catMap[it] }
                val a = t.accountId?.let { d.accMap[it] }
                CuteCard(Modifier.fillMaxWidth(), onClick = { onOpen(t.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBubble(c?.emoji ?: "img:ui_transfer", c?.color ?: 5, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    when (t.type) { TxType.EXPENSE -> "支出"; TxType.INCOME -> "收入"; TxType.TRANSFER -> "轉帳" },
                                    c?.let { cc -> d.topOf(cc).let { p -> if (p.id != cc.id) "${p.name}・${cc.name}" else cc.name } },
                                    a?.name,
                                    if (t.amount > 0) formatMoney(t.amount) else "金額自訂",
                                ).joinToString("・"),
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1,
                            )
                            if (t.note.isNotBlank() || t.tags.isNotEmpty()) {
                                Text(
                                    (listOf(t.note.lineSequence().first()).filter { it.isNotBlank() } + t.tags.map { "#$it" }).joinToString(" "),
                                    style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1,
                                )
                            }
                        }
                        Icon(AppIcons.ChevronRight, contentDescription = null, tint = cute.sub)
                    }
                }
            }
            item { OutlinedButton(onClick = { onOpen(null) }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增常用記帳") } }
        }
    }
}

// ───────────────────────── 外觀 ─────────────────────────

@Composable
fun AppearanceScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val p = vm.data.prefs
    val cute = LocalCute.current
    var name by remember { mutableStateOf(p.mascotName) }
    SubPage("外觀與吉祥物", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle("配色") }
            items(Palettes, key = { it.key }) { pal ->
                val on = pal.key == p.palette
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card)
                        .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable { vm.setPalette(pal.key) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(pal.primary, pal.accent2, pal.soft, pal.bg).forEach { c ->
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c)).border(1.dp, cute.soft, CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(pal.name, modifier = Modifier.weight(1f))
                    if (on) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                }
            }
            item {
                Column {
                    SectionTitle("深色模式")
                    PillSegment(listOf("跟隨系統", "淺色", "深色"), p.dark, { vm.setDark(it) })
                }
            }
            item { SectionTitle("吉祥物") }
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("顯示吉祥物", style = MaterialTheme.typography.bodyLarge)
                            Text("首頁和空白畫面會出現，並依預算換表情", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        }
                        androidx.compose.material3.Switch(checked = p.mascot != "none", onCheckedChange = { vm.setMascotOn(it) })
                    }
                }
            }
            if (p.mascot != "none") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MascotKinds.map { it.first }.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { k ->
                                    val on = p.mascot == k
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(cute.card)
                                            .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
                                            .clickable { vm.setMascot(k) }.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Mascot(k, if (on) Mood.HAPPY else Mood.NORMAL, Modifier.size(64.dp), animate = on)
                                        Text(mascotDefaultName(k), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (p.mascot != "none") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name, { name = it.take(12) },
                            label = { Text("幫吉祥物取名字") },
                            placeholder = { Text(mascotDefaultName(p.mascot)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (name != p.mascotName) {
                            Button(onClick = { vm.setMascotName(name.trim()); vm.toast("名字改好了！") }) { Text("儲存名字") }
                        }
                        Text(
                            "吉祥物會看你的預算狀況換表情：有記帳時開心、花太快時會擔心。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                    }
                }
            }
            item {
                Column {
                SectionTitle("記帳小動畫")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("記完一筆撒花")
                        Text("新增記錄後會有彩色紙花", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                    Switch(checked = p.celebrate, onCheckedChange = { vm.setCelebrate(it) })
                }
                }
            }
        }
    }
}

// ───────────────────────── 備份與匯入匯出 ─────────────────────────

@Composable
fun DataScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val cute = LocalCute.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var pendingLegacy by remember { mutableStateOf<tw.moneybook.app.LegacyImport.Result?>(null) }
    val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    fun write(uri: android.net.Uri, bytes: ByteArray, okMsg: String) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            vm.toast(okMsg)
        } catch (e: Exception) {
            vm.toast("存檔失敗：${e.message}")
        }
    }

    fun read(uri: android.net.Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // 限制檔案大小，避免選到超大檔案把 App 記憶體吃光
            val limit = 20 * 1024 * 1024
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > limit) throw IllegalStateException("檔案太大")
                buf.write(chunk, 0, n)
            }
            buf.toString("UTF-8")
        }
    } catch (_: Exception) {
        null
    }

    val backupOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) write(uri, vm.exportBackup(), "備份完成")
    }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) write(uri, vm.exportCsv(), "已匯出 ${vm.data.txns.size} 筆記錄")
    }
    val backupIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = read(uri)
            if (text == null) vm.toast("讀取檔案失敗") else pendingRestore = text
        }
    }
    val csvIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = read(uri)
            when {
                text == null -> vm.toast("讀取檔案失敗")
                else -> {
                    // 認得是別的記帳 App 匯出的格式：先轉換、讓使用者預覽，確認才寫入
                    val legacy = vm.previewLegacy(text)
                    if (legacy != null) {
                        pendingLegacy = legacy
                        return@rememberLauncherForActivityResult
                    }
                    val n = vm.importCsv(text)
                    vm.toast(
                        when {
                            n > 0 -> "匯入了 $n 筆記錄到「${vm.data.currentBook.name}」"
                            n == 0 -> "沒有找到可以匯入的資料，請確認欄位有「日期」和「金額」"
                            else -> "匯入失敗，檔案格式不正確"
                        }
                    )
                }
            }
        }
    }

    SubPage("備份與匯入匯出", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DataCard(
                    "vec:save", "備份", "把所有帳本、帳戶、分類和設定存成一個檔案。建議存到雲端硬碟，換手機時就能還原。",
                    "建立備份",
                ) { backupOut.launch("記帳本備份_$stamp.json") }
            }
            item {
                DataCard(
                    "vec:restore", "還原", "從備份檔還原。目前手機上的資料會被備份檔的內容取代。",
                    "選擇備份檔",
                ) { backupIn.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
            }
            item {
                DataCard(
                    "vec:export", "匯出 CSV", "所有記錄匯出成 CSV，可以用 Excel 或 Google 試算表開啟。",
                    "匯出",
                ) { csvOut.launch("記帳本_$stamp.csv") }
            }
            item {
                DataCard(
                    "vec:import", "匯入 CSV",
                    "把其他記帳 App 或 Excel 的資料搬進來。有「日期」和「金額」欄位就能匯入，也可以有「類型、分類、子分類、帳戶、備註、標籤」；找不到的分類和帳戶會自動建立。" +
                        "如果是舊記帳 App 匯出的完整格式（有「記帳時間、交易帳戶、一級分類」），會自動轉換：配對轉帳、更新餘額、報銷、外幣，匯入前先給你預覽。",
                    "選擇 CSV 檔",
                ) { csvIn.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
            }
            item {
                Text(
                    "小提醒：解除安裝 App 會把資料一起刪掉，請先備份。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }

    pendingLegacy?.let { r ->
        LegacyPreviewDialog(
            r = r,
            existing = vm.data.txns.size,
            onConfirm = { vm.applyLegacy(r); pendingLegacy = null },
            onDismiss = { pendingLegacy = null },
        )
    }

    val text = pendingRestore
    if (text != null) {
        ConfirmDialog(
            "要還原備份嗎？",
            "目前手機上的所有記帳資料會被取代，建議先建立一份備份。",
            "還原",
            {
                pendingRestore = null
                vm.toast(if (vm.restoreBackup(text)) "還原完成！" else "這不是有效的備份檔")
            },
            { pendingRestore = null },
        )
    }
}

@Composable
private fun DataCard(icon: String, title: String, desc: String, action: String, onClick: () -> Unit) {
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { IconGlyph(icon, 20.sp) }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = LocalCute.current.sub)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

/** 匯入舊記帳 App 資料前的預覽：轉出來有多少筆、怎麼處理、各帳戶匯入後的餘額 */
@Composable
private fun LegacyPreviewDialog(r: tw.moneybook.app.LegacyImport.Result, existing: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    val s = r.summary
    val range = "${LocalDate.ofEpochDay(s.from)} ～ ${LocalDate.ofEpochDay(s.to)}"
    val lines = ArrayList<String>()
    lines.add("認得這是別的記帳 App 匯出的檔案：$range，共 ${s.rows} 筆。")
    lines.add("一般收支 ${s.expense + s.income} 筆（支出 ${s.expense}、收入 ${s.income}）")
    lines.add("轉帳 ${s.transfers} 筆" + if (s.stockTransfers > 0) "，其中 ${s.stockTransfers} 筆是股票基金買賣，轉成轉帳到證券帳戶，不算支出或收入" else "")
    if (s.adjusts > 0) lines.add("餘額調整 ${s.adjusts} 筆：更新餘額、報銷入帳、收益／虧損、借出收回，只改帳戶餘額，不算收入或支出")
    if (s.loanPayments > 0) lines.add("還貸款 ${s.loanPayments} 筆：轉成轉帳到「${s.loanAccounts.joinToString("、")}」貸款帳戶，不算支出。舊檔沒有貸款金額，匯入後請到帳戶頁用「更新餘額」填目前的欠款（負數）")
    if (s.foreign > 0) lines.add("外幣 ${s.foreign} 筆：用檔案裡的匯率換成 NT$，原幣金額寫在備註")
    if (s.unpaired > 0) lines.add("找不到另一半的轉帳／還款 ${s.unpaired} 筆，當成一般收支")
    if (s.skipped > 0) lines.add("看不懂而略過 ${s.skipped} 筆")
    val created = ArrayList<String>()
    if (s.newAccounts.isNotEmpty()) created.add("${s.newAccounts.size} 個帳戶")
    if (s.newBooks.isNotEmpty()) created.add("${s.newBooks.size} 個帳本（${s.newBooks.joinToString("、")}）")
    if (s.newCategories > 0) created.add("${s.newCategories} 個分類")
    if (created.isNotEmpty()) lines.add("會新建：" + created.joinToString("、"))
    if (s.offsetAccounts.isNotEmpty()) {
        lines.add("${s.offsetAccounts.joinToString("、")} 最後用一筆餘額調整沖回 0：舊資料的持股市值已經記在「台股／美股／幣安」，不然資產會重複計算。")
    }
    if (existing > 0) lines.add("目前已經有 $existing 筆記錄，匯入會附加上去，不會比對重複。")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("匯入預覽") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (s.newAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("新帳戶匯入後的餘額（舊檔沒有初始餘額，和實際不同的話，到帳戶頁用「更新餘額」修正）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                    s.newAccounts.forEach { (name, bal) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(formatMoney(bal), style = MaterialTheme.typography.bodySmall, color = if (bal < 0) cute.expense else cute.ink)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("匯入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
