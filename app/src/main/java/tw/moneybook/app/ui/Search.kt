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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.Txn
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.incomeSum

private fun matches(d: AppData, t: Txn, kw: String): Boolean {
    if (kw.isEmpty()) return true
    val c = t.categoryId?.let { d.catMap[it] }
    val parent = c?.let { d.topOf(it) }
    val fields = listOfNotNull(
        t.note,
        c?.name,
        parent?.name,
        t.accountId?.let { d.accMap[it]?.name },
        t.toAccountId?.let { d.accMap[it]?.name },
        t.amount.toString(),
        t.paid.toString(),
        if (t.type == TxType.TRANSFER) "轉帳" else null,
    ) + t.tags + t.tags.map { "#$it" }
    return fields.any { it.lowercase().contains(kw) }
}

@Composable
fun SearchScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    var keyword by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(if (vm.statMode in 0..2) vm.statMode else 0) } // 0 月 1 年 2 區間 3 全部
    var type by rememberSaveable { mutableIntStateOf(0) } // 0 全部 1 支出 2 收入 3 轉帳
    var catIds by remember { mutableStateOf(setOf<Long>()) }
    var catDialog by remember { mutableStateOf(false) }
    var tagSel by remember { mutableStateOf(setOf<String>()) }
    var tagDialog by remember { mutableStateOf(false) }

    val catFilter = catIds + catIds.flatMap { id -> d.childrenOf(id).map { it.id } }
    val kw = keyword.trim().lowercase()
    val results = periodFilter(d.bookTxns, mode, vm).filter { t ->
        val typeOk = when (type) {
            1 -> t.type == TxType.EXPENSE
            2 -> t.type == TxType.INCOME
            3 -> t.type == TxType.TRANSFER
            else -> true
        }
        val catOk = catFilter.isEmpty() || (t.categoryId != null && t.categoryId in catFilter)
        val tagOk = tagSel.isEmpty() || t.tags.any { it in tagSel }
        typeOk && catOk && tagOk && matches(d, t, kw)
    }
    val catLabel = when {
        catIds.isEmpty() -> "全部分類"
        catIds.size == 1 -> d.catMap[catIds.first()]?.let { "${it.emoji} ${it.name}" } ?: "1 個分類"
        else -> "${catIds.size} 個分類"
    }

    SubPage("搜尋", onBack) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.take(40) },
                    placeholder = { Text("備註、分類、標籤、帳戶、金額") },
                    leadingIcon = { Text("🔍") },
                    trailingIcon = if (keyword.isNotEmpty()) {
                        { TextButton(onClick = { keyword = "" }) { Text("清除") } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillSegment(listOf("月", "年", "區間", "全部"), mode, { mode = it })
                    PeriodBar(vm, mode)
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("全部", "支出", "收入", "轉帳").forEachIndexed { i, label ->
                        CuteChip(label, type == i, { type = i })
                    }
                    Spacer(Modifier.width(6.dp))
                    CuteChip("🗂️ $catLabel ▾", catIds.isNotEmpty(), { catDialog = true })
                    Spacer(Modifier.width(6.dp))
                    val tagLabel = when {
                        tagSel.isEmpty() -> "全部標籤"
                        tagSel.size == 1 -> "#" + tagSel.first()
                        else -> "${tagSel.size} 個標籤"
                    }
                    CuteChip("🏷️ $tagLabel ▾", tagSel.isNotEmpty(), { tagDialog = true })
                }
            }
            item {
                CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("找到 ${results.size} 筆", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("支出 ${formatMoney(results.expenseSum())}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
                        Text("收入 ${formatMoney(results.incomeSum())}", color = cute.income, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (results.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "找不到符合的記錄\n換個關鍵字或放寬篩選看看") }
            }
            results.groupBy { it.day }.forEach { (day, list) ->
                item(key = "d$day") { DayHeader(day, list) }
                items(list, key = { it.id }) { t ->
                    SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t) { onEdit(t.id) } }
                }
            }
        }
    }

    if (tagDialog) {
        val all = d.allTags()
        var temp by remember { mutableStateOf(tagSel) }
        AlertDialog(
            onDismissRequest = { tagDialog = false },
            title = { Text("篩選標籤") },
            text = {
                if (all.isEmpty()) {
                    Text("還沒有用過任何標籤。記帳時點「🏷️ 新增標籤」就能加上。", color = cute.sub)
                } else {
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        Text("符合任一個選到的標籤就會列出", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        all.forEach { tag ->
                            CheckLine("", "#$tag", tag in temp, false) {
                                temp = if (tag in temp) temp - tag else temp + tag
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tagSel = temp; tagDialog = false }) { Text("套用") } },
            dismissButton = { TextButton(onClick = { tagSel = emptySet(); tagDialog = false }) { Text("全部標籤") } },
        )
    }

    if (catDialog) {
        var kindIdx by remember { mutableIntStateOf(if (type == 2) 1 else 0) }
        var temp by remember { mutableStateOf(catIds) }
        val kind = if (kindIdx == 0) TxType.EXPENSE else TxType.INCOME
        AlertDialog(
            onDismissRequest = { catDialog = false },
            title = { Text("篩選分類") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillSegment(listOf("支出", "收入"), kindIdx, { kindIdx = it })
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        d.topCategories(kind).forEach { p ->
                            CheckLine(p.emoji, p.name, p.id in temp, false) {
                                temp = if (p.id in temp) temp - p.id else temp + p.id
                            }
                            d.childrenOf(p.id).forEach { k ->
                                CheckLine(k.emoji, k.name, k.id in temp || p.id in temp, true) {
                                    if (p.id !in temp) temp = if (k.id in temp) temp - k.id else temp + k.id
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { catIds = temp; catDialog = false }) { Text("套用") } },
            dismissButton = { TextButton(onClick = { catIds = emptySet(); catDialog = false }) { Text("全部分類") } },
        )
    }
}

@Composable
private fun CheckLine(emoji: String, name: String, on: Boolean, child: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (child) 28.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (on) primary else LocalCute.current.soft),
            contentAlignment = Alignment.Center,
        ) { if (on) Text("✓", color = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.width(10.dp))
        Text("$emoji $name", style = if (child) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
    }
}

/** 從統計點進某個分類或標籤：列出這段期間的每一筆，點了可以直接修改 */
@Composable
fun DrillScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val dr = vm.drill
    if (dr == null) {
        SubPage("明細", onBack) { EmptyHint(d.prefs.mascot, "沒有資料") }
        return
    }
    val list = periodFilter(d.bookTxns, dr.mode, vm).filter { dr.pred(it) }
    val total = list.sumOf { dr.amt(it) }
    val period = when (dr.mode) {
        0 -> "${vm.month.year} 年 ${vm.month.monthValue} 月"
        1 -> "${vm.statYear} 年"
        2 -> "${shortDate(vm.rangeStart)} ～ ${shortDate(vm.rangeEnd)}"
        else -> "全部"
    }
    SubPage(dr.title, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(period, style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Text(formatMoney(total), style = MaterialTheme.typography.headlineSmall)
                    Text("共 ${list.size} 筆・點一筆可以修改，左滑可以刪除", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
            }
            if (list.isEmpty()) {
                item { EmptyHint(d.prefs.mascot, "這段期間沒有記錄了") }
            }
            list.groupBy { it.day }.forEach { (day, dl) ->
                item(key = "d$day") { DayHeader(day, dl) }
                items(dl, key = { it.id }) { t ->
                    SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t) { onEdit(t.id) } }
                }
            }
        }
    }
}
