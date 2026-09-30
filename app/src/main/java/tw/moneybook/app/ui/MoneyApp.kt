package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.MoneyViewModel

/** 疊在主畫面上的頁面 */
sealed interface Route {
    data class Edit(
        val id: Long?,
        val presetTo: Long? = null,
        val presetAmount: Long? = null,
        val tplMode: Boolean = false,
        val tplId: Long? = null,
    ) : Route
    data class Page(val name: String) : Route
}

private data class TabItem(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabItem("明細", AppIcons.ListAlt),
    TabItem("日曆", AppIcons.Calendar),
    TabItem("統計", AppIcons.PieChart),
    TabItem("我的", AppIcons.Person),
)

@Composable
fun MoneyApp(vm: MoneyViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Route>()) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collectLatest { m ->
            // 有「復原」的訊息也只停 4 秒，避免擋住最下面的項目；新訊息會直接取代舊的
            val r = withTimeoutOrNull(4000L) {
                snackbar.showSnackbar(
                    message = m.text,
                    actionLabel = m.action,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }

    fun push(r: Route) {
        stack = stack + r
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val top = stack.lastOrNull()) {
            null -> MainTabs(
                vm = vm,
                tab = tab,
                onTab = { tab = it },
                onAdd = { push(Route.Edit(null)) },
                onEdit = { push(Route.Edit(it)) },
                open = { push(Route.Page(it)) },
            )
            is Route.Edit -> EditScreen(vm, top.id, top.presetTo, top.presetAmount, top.tplMode, top.tplId, onClose = { pop() })
            is Route.Page -> when (top.name) {
                "books" -> BooksScreen(vm) { pop() }
                "accounts" -> AccountsScreen(vm, onOpen = { push(Route.Page("account:$it")) }) { pop() }
                "categories" -> CategoriesScreen(vm) { pop() }
                "templates" -> TemplatesScreen(vm, onOpen = { push(Route.Edit(null, tplMode = true, tplId = it)) }) { pop() }
                "appearance" -> AppearanceScreen(vm) { pop() }
                "data" -> DataScreen(vm) { pop() }
                "reimb" -> ReimbScreen(vm, onEdit = { push(Route.Edit(it)) }) { pop() }
                "search" -> SearchScreen(vm, onEdit = { push(Route.Edit(it)) }) { pop() }
                "drill" -> DrillScreen(vm, onEdit = { push(Route.Edit(it)) }) { pop() }
                else -> {
                    val accId = top.name.removePrefix("account:").toLongOrNull()
                    if (top.name.startsWith("account:") && accId != null) {
                        AccountDetailScreen(
                            vm, accId,
                            onEdit = { push(Route.Edit(it)) },
                            onPayCard = { acc, amt -> push(Route.Edit(null, presetTo = acc, presetAmount = amt)) },
                        ) { pop() }
                    }
                }
            }
        }
        ConfettiOverlay(vm)
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp),
        )
    }
}

@Composable
private fun MainTabs(
    vm: MoneyViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    open: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            when (tab) {
                0 -> HomeScreen(
                    vm, onEdit,
                    onManageBooks = { open("books") },
                    onManageAccounts = { open("accounts") },
                    onReimb = { open("reimb") },
                    onAccount = { open("account:$it") },
                )
                1 -> CalendarScreen(vm, onEdit)
                2 -> StatsScreen(vm, onSearch = { open("search") }, onDrill = { open("drill") })
                else -> MeScreen(vm, open)
            }
        }
        BottomBar(tab, onTab, onAdd)
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), clip = false)
            .background(cute.card, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tabs.forEachIndexed { i, t ->
                if (i == 2) Spacer(Modifier.size(64.dp))
                val on = i == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onTab(i) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (on) cute.soft else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Icon(t.icon, contentDescription = t.label, tint = if (on) primary else cute.sub, modifier = Modifier.size(22.dp))
                    }
                    Text(t.label, fontSize = 11.sp, color = if (on) primary else cute.sub)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .size(62.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp), clip = false)
                .clip(RoundedCornerShape(22.dp))
                .background(primary)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = "記一筆",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
