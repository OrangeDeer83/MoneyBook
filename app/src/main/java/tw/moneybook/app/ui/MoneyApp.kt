package tw.moneybook.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.Saver
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
        /** 預設日期（日曆選了日期時，記一筆就預設那一天） */
        val presetDay: Long? = null,
    ) : Route
    data class Page(val name: String) : Route
}

/** 頁面堆疊要能撐過螢幕旋轉（Activity 重建）：每一頁存成一段文字 */
private val RouteStackSaver = Saver<List<Route>, ArrayList<String>>(
    save = { list ->
        ArrayList(list.map { r ->
            when (r) {
                is Route.Edit -> "E|${r.id ?: ""}|${r.presetTo ?: ""}|${r.presetAmount ?: ""}|${r.tplMode}|${r.tplId ?: ""}|${r.presetDay ?: ""}"
                is Route.Page -> "P|${r.name}"
            }
        })
    },
    restore = { saved ->
        saved.mapNotNull { s ->
            val p = s.split("|", limit = 7)
            when (p.firstOrNull()) {
                "E" -> if (p.size >= 6) Route.Edit(
                    id = p[1].toLongOrNull(),
                    presetTo = p[2].toLongOrNull(),
                    presetAmount = p[3].toLongOrNull(),
                    tplMode = p[4] == "true",
                    tplId = p[5].toLongOrNull(),
                    presetDay = p.getOrNull(6)?.toLongOrNull(),
                ) else null
                "P" -> if (p.size >= 2) Route.Page(s.substring(2)) else null
                else -> null
            }
        }
    },
)

private data class TabItem(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabItem("明細", AppIcons.ListAlt),
    TabItem("帳戶", AppIcons.Wallet),
    TabItem("統計", AppIcons.PieChart),
    TabItem("我的", AppIcons.Person),
)

@Composable
fun MoneyApp(vm: MoneyViewModel, openRequest: String? = null, onOpenHandled: () -> Unit = {}) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(listOf<Route>()) }
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

    // 桌面小工具點下去：直接開記一筆或報銷
    LaunchedEffect(openRequest) {
        when (openRequest) {
            "add" -> { stack = emptyList(); push(Route.Edit(null)) }
            "reimb" -> { stack = emptyList(); push(Route.Page("reimb")) }
        }
        if (openRequest != null) onOpenHandled()
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = stack.lastOrNull(),
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // 返回（原本那頁已不在堆疊裡）往右退場；進記一筆／編輯從下方滑上來；其他頁從右邊滑入
                val popping = initialState != null && initialState !in stack
                when {
                    popping -> (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { -it / 8 }) togetherWith
                        (fadeOut(tween(160)) + (if (initialState is Route.Edit) slideOutVertically(tween(220)) { it / 8 } else slideOutHorizontally(tween(220)) { it / 8 }))
                    targetState is Route.Edit -> (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 8 }) togetherWith fadeOut(tween(160))
                    else -> (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 8 }) togetherWith fadeOut(tween(160))
                }
            },
            label = "route",
        ) { top ->
        when (top) {
            null -> MainTabs(
                vm = vm,
                tab = tab,
                onTab = { tab = it },
                // 在日曆上選了日期再按記一筆，就預設那一天
                onAdd = { push(Route.Edit(null, presetDay = if (tab == 0 && vm.homeCalendar) vm.calSelected else null)) },
                onEdit = { push(Route.Edit(it)) },
                // 「我的 → 帳戶管理」直接切到帳戶分頁
                open = { if (it == "accounts") { tab = 1 } else { push(Route.Page(it)) } },
            )
            is Route.Edit -> EditScreen(vm, top.id, top.presetTo, top.presetAmount, top.tplMode, top.tplId, presetDay = top.presetDay, onClose = { pop() })
            is Route.Page -> when (top.name) {
                "books" -> BooksScreen(vm) { pop() }
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
                0 -> if (vm.homeCalendar) CalendarScreen(vm, onEdit) else HomeScreen(
                    vm, onEdit,
                    onManageBooks = { open("books") },
                    onReimb = { open("reimb") },
                )
                1 -> AccountsScreen(vm, onOpen = { open("account:$it") })
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
                // 選取時整顆（圖示加文字）一起變色
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) cute.soft else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onTab(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = if (on) primary else cute.sub, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
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
