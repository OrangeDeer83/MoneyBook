@file:OptIn(ExperimentalMaterial3Api::class)

package tw.moneybook.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import java.time.YearMonth

enum class Tab(val label: String, val title: String) {
    Records("明細", "記帳本"),
    Stats("統計", "月統計"),
    Settings("設定", "設定"),
}

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.Records -> AppIcons.ListAlt
    Tab.Stats -> AppIcons.PieChart
    Tab.Settings -> Icons.Filled.Settings
}

@Composable
fun MoneyApp(vm: MoneyViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Records) }
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Txn?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    if (showEditor) {
        EditScreen(
            initial = editTarget,
            onSave = { t ->
                vm.upsert(t)
                showEditor = false
                val m = YearMonth.from(t.date)
                vm.month = m
                val alert = if (t.isExpense) vm.budgetAlert(m) else null
                if (alert != null) toast(alert)
            },
            onDelete = { id ->
                vm.delete(id)
                showEditor = false
                toast("已刪除")
            },
            onClose = { showEditor = false },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(tabIcon(t), contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Records) {
                FloatingActionButton(onClick = {
                    editTarget = null
                    showEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "新增")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Records -> RecordsScreen(vm, onEdit = { t ->
                    editTarget = t
                    showEditor = true
                })
                Tab.Stats -> StatsScreen(vm)
                Tab.Settings -> SettingsScreen(vm, onMessage = { toast(it) })
            }
        }
    }
}
