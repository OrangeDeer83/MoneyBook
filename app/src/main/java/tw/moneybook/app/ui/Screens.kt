@file:OptIn(ExperimentalMaterial3Api::class)

package tw.moneybook.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Categories
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.buildCsv
import tw.moneybook.app.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun MonthBar(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(AppIcons.ChevronLeft, contentDescription = "上個月")
        }
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onChange(YearMonth.now()) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(AppIcons.ChevronRight, contentDescription = "下個月")
        }
    }
}

// ───────────────────────── 明細 ─────────────────────────

@Composable
fun RecordsScreen(vm: MoneyViewModel, onEdit: (Txn) -> Unit) {
    val month = vm.month
    val list = vm.txnsOf(month)
    val income = list.filter { !it.isExpense }.sumOf { it.amount }
    val expense = list.filter { it.isExpense }.sumOf { it.amount }
    val groups = list.groupBy { it.date }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        item { MonthBar(month) { vm.month = it } }
        item { SummaryCard(income, expense, vm.budget) }
        if (list.isEmpty()) {
            item {
                Text(
                    text = "這個月還沒有記錄\n按右下角 ＋ 開始記帳",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }
        groups.forEach { (date, dayList) ->
            item(key = "day-$date") { DayHeader(date, dayList) }
            items(dayList, key = { it.id }) { t ->
                TxnRow(t) { onEdit(t) }
            }
        }
    }
}

@Composable
private fun SummaryCard(income: Long, expense: Long, budget: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell("收入", formatMoney(income), IncomeColor, Modifier.weight(1f))
                StatCell("支出", formatMoney(expense), ExpenseColor, Modifier.weight(1f))
                val bal = income - expense
                StatCell(
                    "結餘",
                    (if (bal < 0) "-" else "") + formatMoney(kotlin.math.abs(bal)),
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
            }
            if (budget > 0) {
                Spacer(Modifier.height(14.dp))
                val over = expense > budget
                val frac = (expense.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                val barColor = when {
                    over -> ExpenseColor
                    frac >= 0.8f -> WarnColor
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = barColor,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (over) "已超支 ${formatMoney(expense - budget)}（預算 ${formatMoney(budget)}）"
                    else "預算 ${formatMoney(budget)}，還剩 ${formatMoney(budget - expense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DayHeader(date: LocalDate, dayList: List<Txn>) {
    val dayExpense = dayList.filter { it.isExpense }.sumOf { it.amount }
    Column {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 週${WEEKDAYS[date.dayOfWeek.value - 1]}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (dayExpense > 0) {
                Text(
                    text = "支出 ${formatMoney(dayExpense)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TxnRow(t: Txn, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(Categories.emojiOf(t.category, t.isExpense), fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.category, style = MaterialTheme.typography.bodyLarge)
            if (t.note.isNotBlank()) {
                Text(
                    t.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = (if (t.isExpense) "-" else "+") + formatMoney(t.amount),
            color = if (t.isExpense) ExpenseColor else IncomeColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ───────────────────────── 統計 ─────────────────────────

@Composable
fun StatsScreen(vm: MoneyViewModel) {
    var showExpense by rememberSaveable { mutableStateOf(true) }
    val list = vm.txnsOf(vm.month).filter { it.isExpense == showExpense }
    val total = list.sumOf { it.amount }
    val byCat: List<Pair<String, Long>> = list.groupBy { it.category }
        .map { (k, v) -> Pair(k, v.sumOf { it.amount }) }
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item { MonthBar(vm.month) { vm.month = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showExpense, onClick = { showExpense = true }, label = { Text("支出") })
                FilterChip(selected = !showExpense, onClick = { showExpense = false }, label = { Text("收入") })
            }
        }
        if (total == 0L) {
            item {
                Text(
                    text = "這個月沒有${if (showExpense) "支出" else "收入"}記錄",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DonutChart(values = byCat.map { it.second }, modifier = Modifier.size(220.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (showExpense) "總支出" else "總收入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatMoney(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            itemsIndexed(byCat) { i, entry ->
                val (cat, amt) = entry
                val pct = amt * 100.0 / total
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(ChartColors[i % ChartColors.size])
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${Categories.emojiOf(cat, showExpense)} $cat",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        String.format("%.1f%%", pct),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(formatMoney(amt), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DonutChart(values: List<Long>, modifier: Modifier) {
    val total = values.sum().toFloat()
    Canvas(modifier) {
        if (total <= 0f) return@Canvas
        val stroke = size.minDimension * 0.18f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = v.toFloat() / total * 360f
            drawArc(
                color = ChartColors[i % ChartColors.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(d, d),
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

// ───────────────────────── 設定 ─────────────────────────

@Composable
fun SettingsScreen(vm: MoneyViewModel, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var budgetText by rememberSaveable {
        mutableStateOf(if (vm.budget > 0) vm.budget.toString() else "")
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(buildCsv(vm.txns)) }
                onMessage("已匯出 ${vm.txns.size} 筆記錄")
            } catch (e: Exception) {
                onMessage("匯出失敗：${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("每月預算", style = MaterialTheme.typography.titleMedium)
                Text(
                    "設定後首頁會顯示剩餘額度；記一筆支出後若用掉 80% 以上或超支，會跳出提醒。留空或 0 代表不使用預算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { s -> budgetText = s.filter { it.isDigit() }.take(10) },
                    label = { Text("預算金額") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    vm.updateBudget(budgetText.toLongOrNull() ?: 0L)
                    focus.clearFocus()
                    onMessage("預算已儲存")
                }) { Text("儲存預算") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("匯出備份", style = MaterialTheme.typography.titleMedium)
                Text(
                    "把全部 ${vm.txns.size} 筆記錄匯出成 CSV 檔，可以用 Excel 或 Google 試算表開啟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val name = "記帳本_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv"
                        exporter.launch(name)
                    },
                    enabled = vm.txns.isNotEmpty(),
                ) { Text("匯出 CSV") }
            }
        }

        Text(
            "記帳本 v1.0 · 資料只存在這支手機裡，換手機前記得先匯出備份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
