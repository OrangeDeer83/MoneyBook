@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tw.moneybook.app.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tw.moneybook.app.Categories
import tw.moneybook.app.Txn
import java.time.LocalDate

@Composable
fun EditScreen(
    initial: Txn?,
    onSave: (Txn) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var isExpense by rememberSaveable { mutableStateOf(initial?.isExpense ?: true) }
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: Categories.expense.first().name) }
    var dateStr by rememberSaveable { mutableStateOf((initial?.date ?: LocalDate.now()).toString()) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    val date = LocalDate.parse(dateStr)
    val cats = if (isExpense) Categories.expense else Categories.income
    val amount = amountText.toLongOrNull() ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新增記錄" else "編輯記錄") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "關閉") }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isExpense,
                    onClick = {
                        if (!isExpense) {
                            isExpense = true
                            category = Categories.expense.first().name
                        }
                    },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = {
                        if (isExpense) {
                            isExpense = false
                            category = Categories.income.first().name
                        }
                    },
                    label = { Text("收入") },
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { s -> amountText = s.filter { it.isDigit() }.take(10) },
                label = { Text("金額") },
                prefix = { Text("$") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("分類", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                cats.forEach { c ->
                    FilterChip(
                        selected = category == c.name,
                        onClick = { category = c.name },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> dateStr = LocalDate.of(y, m + 1, d).toString() },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("📅  ${date.year} / ${date.monthValue} / ${date.dayOfMonth}")
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(100) },
                label = { Text("備註（選填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(
                        Txn(
                            id = initial?.id ?: System.currentTimeMillis(),
                            isExpense = isExpense,
                            amount = amount,
                            category = category,
                            date = date,
                            note = note.trim(),
                        )
                    )
                },
                enabled = amount > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("儲存")
            }
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆記錄？") },
            text = { Text("${initial.category} ${tw.moneybook.app.formatMoney(initial.amount)}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(initial.id)
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
