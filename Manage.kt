package tw.moneybook.app.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.Book
import tw.moneybook.app.Category
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.formatMoney
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mascot != "none") Mascot(mascot, moodOf(d), Modifier.size(84.dp)) else Text("📒", fontSize = 48.sp)
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
                MenuRow("📒", "帳本管理", "目前：${d.currentBook.name}") { open("books") }
                MenuRow("👛", "帳戶管理", "${d.visibleAccounts.size} 個帳戶") { open("accounts") }
                MenuRow("🗂️", "分類管理", "新增、改圖示、子分類、排序") { open("categories") }
                MenuRow("⭐", "常用記帳", if (d.templates.isEmpty()) "在記一筆畫面按「存為常用」" else "${d.templates.size} 個") { open("templates") }
                MenuRow(
                    "🎯", "每月預算",
                    if (d.currentBook.budget > 0) "${d.currentBook.name}：${formatMoney(d.currentBook.budget)}" else "尚未設定",
                ) { budgetDialog = true }
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(8.dp)) {
                MenuRow("🎨", "外觀與吉祥物", "${palOf(d.prefs.palette).name}・" + (if (mascot == "none") "不顯示吉祥物" else mascotDefaultName(mascot))) { open("appearance") }
                MenuRow("💾", "備份與匯入匯出", "備份檔、CSV") { open("data") }
            }
        }
        item {
            Text(
                "記帳本 v2・資料只存在這支手機裡，記得定期備份喔",
                style = MaterialTheme.typography.bodySmall,
                color = cute.sub,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }

    if (budgetDialog) {
        var text by remember { mutableStateOf(if (d.currentBook.budget > 0) d.currentBook.budget.toString() else "") }
        AlertDialog(
            onDismissRequest = { budgetDialog = false },
            title = { Text("每月預算") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("「${d.currentBook.name}」每個月的支出預算。留空代表不設定。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    OutlinedTextField(
                        text, { text = it.filter { c -> c.isDigit() }.take(10) },
                        prefix = { Text("$") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBudget(text.toLongOrNull() ?: 0L)
                    budgetDialog = false
                    vm.toast("預算已更新")
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { budgetDialog = false }) { Text("取消") } },
        )
    }
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
                        Text(b.emoji, fontSize = 26.sp)
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
    var emoji by remember { mutableStateOf(book?.emoji ?: "✈️") }
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
    ) { Text(emoji, fontSize = 26.sp) }
}

// ───────────────────────── 帳戶 ─────────────────────────

@Composable
fun AccountsScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val bal = remember(d) { d.balances() }
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sorted = d.accounts.sortedBy { it.order }
    SubPage("帳戶管理", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val total = d.visibleAccounts.sumOf { bal[it.id] ?: 0L }
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("總資產", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Text(formatMoney(total), style = MaterialTheme.typography.headlineMedium, color = if (total < 0) cute.expense else cute.ink)
                }
            }
            items(sorted, key = { it.id }) { a ->
                val b = bal[a.id] ?: 0L
                CuteCard(Modifier.fillMaxWidth(), onClick = { editing = a }, padding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name + if (a.hidden) "（已隱藏）" else "", style = MaterialTheme.typography.titleMedium, color = if (a.hidden) cute.sub else cute.ink)
                            Text(a.type.label, style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        }
                        Text(formatMoney(b), color = if (b < 0) cute.expense else cute.ink, style = MaterialTheme.typography.titleMedium)
                        Column {
                            IconButton(onClick = { vm.moveAccount(a.id, true) }, modifier = Modifier.size(28.dp)) { Icon(AppIcons.ChevronUp, "上移", tint = cute.sub) }
                            IconButton(onClick = { vm.moveAccount(a.id, false) }, modifier = Modifier.size(28.dp)) { Icon(AppIcons.ChevronDown, "下移", tint = cute.sub) }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增帳戶") } }
            item {
                Text(
                    "餘額 = 初始金額 + 收入 − 支出 ± 轉帳。信用卡可以把初始金額設成負數代表欠款。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }
    val target = editing
    if (adding || target != null) {
        AccountDialog(
            acc = target,
            onSave = { name, emoji, type, initial, hidden ->
                vm.saveAccount(target?.id, name, emoji, type, initial, hidden)
                adding = false; editing = null
            },
            onDelete = if (target != null) ({ vm.deleteAccount(target.id); editing = null }) else null,
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun AccountDialog(
    acc: Account?,
    onSave: (String, String, AccountType, Long, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(acc?.name ?: "") }
    var type by remember { mutableStateOf(acc?.type ?: AccountType.BANK) }
    var emoji by remember { mutableStateOf(acc?.emoji ?: AccountType.BANK.emoji) }
    var initial by remember { mutableStateOf(acc?.initial?.takeIf { it != 0L }?.toString() ?: "") }
    var hidden by remember { mutableStateOf(acc?.hidden ?: false) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (acc == null) "新增帳戶" else "編輯帳戶") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiButton(emoji) { pick = true }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(name, { name = it.take(12) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.values().forEach { t ->
                        CuteChip("${t.emoji} ${t.label}", type == t, {
                            if (emoji == type.emoji) emoji = t.emoji
                            type = t
                        })
                    }
                }
                OutlinedTextField(
                    initial,
                    { s -> initial = s.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }.take(11) },
                    label = { Text("初始金額") }, prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (acc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("隱藏這個帳戶", modifier = Modifier.weight(1f))
                        Switch(checked = hidden, onCheckedChange = { hidden = it })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("刪除帳戶", color = LocalCute.current.expense) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(name.trim(), emoji, type, initial.toLongOrNull() ?: 0L, hidden)
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (pick) EmojiPickerDialog(emoji, { emoji = it; pick = false }, { pick = false })
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
            items(d.topCategories(kind), key = { it.id }) { c ->
                val kids = d.childrenOf(c.id)
                CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    CatLine(c, onClick = { edit = CatEdit(c, kind, null) }, onUp = { vm.moveCategory(c.id, true) }, onDown = { vm.moveCategory(c.id, false) })
                    kids.forEach { k ->
                        Box(Modifier.padding(start = 28.dp)) {
                            CatLine(k, onClick = { edit = CatEdit(k, kind, c.id) }, onUp = { vm.moveCategory(k.id, true) }, onDown = { vm.moveCategory(k.id, false) }, small = true)
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
            item {
                OutlinedButton(onClick = { edit = CatEdit(null, kind, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ 新增${if (kind == TxType.EXPENSE) "支出" else "收入"}分類")
                }
            }
            item {
                Text(
                    "刪除子分類時，原本的記錄會移到上層分類。",
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
private fun CatLine(c: Category, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, small: Boolean = false) {
    val cute = LocalCute.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble(c.emoji, c.color, if (small) 32.dp else 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(c.name, modifier = Modifier.weight(1f), style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onUp, modifier = Modifier.size(32.dp)) { Icon(AppIcons.ChevronUp, "上移", tint = cute.sub) }
        IconButton(onClick = onDown, modifier = Modifier.size(32.dp)) { Icon(AppIcons.ChevronDown, "下移", tint = cute.sub) }
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
    var emoji by remember { mutableStateOf(cat?.emoji ?: parent?.emoji ?: "📦") }
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
                    ) { Text(emoji, fontSize = 26.sp) }
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
fun TemplatesScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    var editing by remember { mutableStateOf<tw.moneybook.app.Template?>(null) }
    SubPage("常用記帳", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.templates.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "還沒有常用記帳\n在「記一筆」畫面設定好分類和金額後，\n按「⭐ 存為常用」就會出現在這裡") }
            }
            items(d.templates, key = { it.id }) { t ->
                val c = t.categoryId?.let { d.catMap[it] }
                CuteCard(Modifier.fillMaxWidth(), onClick = { editing = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBubble(c?.emoji ?: "🔁", c?.color ?: 5, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    c?.name,
                                    if (t.amount > 0) formatMoney(t.amount) else "金額自訂",
                                    t.note.takeIf { it.isNotBlank() },
                                ).joinToString("・"),
                                style = MaterialTheme.typography.bodySmall, color = cute.sub,
                            )
                        }
                    }
                }
            }
        }
    }
    val t = editing
    if (t != null) {
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("編輯常用記帳") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(12) }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { vm.deleteTemplate(t.id); editing = null }) { Text("刪除", color = cute.expense) }
                }
            },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.renameTemplate(t.id, name.trim()); editing = null }) { Text("儲存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
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
                val options = MascotKinds.map { it.first } + "none"
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                val on = p.mascot == k
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(cute.card)
                                        .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
                                        .clickable { vm.setMascot(k) }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (k == "none") {
                                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Text("🙈", fontSize = 34.sp) }
                                        Text("不需要", style = MaterialTheme.typography.labelLarge)
                                    } else {
                                        Mascot(k, if (on) Mood.HAPPY else Mood.NORMAL, Modifier.size(64.dp), animate = on)
                                        Text(mascotDefaultName(k), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
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
                    "💾 備份", "把所有帳本、帳戶、分類和設定存成一個檔案。建議存到雲端硬碟，換手機時就能還原。",
                    "建立備份",
                ) { backupOut.launch("記帳本備份_$stamp.json") }
            }
            item {
                DataCard(
                    "♻️ 還原", "從備份檔還原。目前手機上的資料會被備份檔的內容取代。",
                    "選擇備份檔",
                ) { backupIn.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
            }
            item {
                DataCard(
                    "📤 匯出 CSV", "所有記錄匯出成 CSV，可以用 Excel 或 Google 試算表開啟。",
                    "匯出",
                ) { csvOut.launch("記帳本_$stamp.csv") }
            }
            item {
                DataCard(
                    "📥 匯入 CSV",
                    "把其他記帳 App 或 Excel 的資料搬進目前的帳本。需要有「日期」和「金額」欄位，也可以有「類型、分類、子分類、帳戶、備註、標籤」。找不到的分類和帳戶會自動建立。",
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
private fun DataCard(title: String, desc: String, action: String, onClick: () -> Unit) {
    CuteCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = LocalCute.current.sub)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClick) { Text(action) }
    }
}
