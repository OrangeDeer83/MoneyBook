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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
        /** 預設轉出帳戶（賣外幣：從外幣帳戶轉回台幣） */
        val presetFrom: Long? = null,
        /** 預設記在哪個帳戶（從帳戶明細按記一筆：一般的支出／收入，帳戶自動帶入） */
        val presetAcc: Long? = null,
        /** 刷卡待確認按「改一下」：那一筆的 id */
        val pendId: Long? = null,
        /** 預購（見 Preorder.kt）：new＝新增預購、edit:<id>＝修改那筆預購、buy:<id>＝買了，照那筆帶好記一筆 */
        val preorder: String? = null,
        /** 一開始的類型（自組小工具的「＋ 記收入／記轉帳」） */
        val presetType: tw.moneybook.app.TxType? = null,
    ) : Route
    data class Page(val name: String) : Route
}

/** 一頁存成一段文字（頁面堆疊存檔用，也當成記住那一頁狀態的 key） */
private fun encodeRoute(r: Route): String = when (r) {
    is Route.Edit -> "E|${r.id ?: ""}|${r.presetTo ?: ""}|${r.presetAmount ?: ""}|${r.tplMode}|${r.tplId ?: ""}|${r.presetDay ?: ""}|${r.presetFrom ?: ""}|${r.presetAcc ?: ""}|${r.pendId ?: ""}|${r.preorder ?: ""}|${r.presetType ?: ""}"
    is Route.Page -> "P|${r.name}"
}

/** 頁面堆疊要能撐過螢幕旋轉（Activity 重建）：每一頁存成一段文字 */
private val RouteStackSaver = Saver<List<Route>, ArrayList<String>>(
    save = { list -> ArrayList(list.map { encodeRoute(it) }) },
    restore = { saved ->
        saved.mapNotNull { s ->
            val p = s.split("|", limit = 12)
            when (p.firstOrNull()) {
                "E" -> if (p.size >= 6) Route.Edit(
                    id = p[1].toLongOrNull(),
                    presetTo = p[2].toLongOrNull(),
                    presetAmount = p[3].toLongOrNull(),
                    tplMode = p[4] == "true",
                    tplId = p[5].toLongOrNull(),
                    presetDay = p.getOrNull(6)?.toLongOrNull(),
                    presetFrom = p.getOrNull(7)?.toLongOrNull(),
                    presetAcc = p.getOrNull(8)?.toLongOrNull(),
                    pendId = p.getOrNull(9)?.toLongOrNull(),
                    preorder = p.getOrNull(10)?.takeIf { it.isNotEmpty() },
                    presetType = p.getOrNull(11)?.let { t -> runCatching { tw.moneybook.app.TxType.valueOf(t) }.getOrNull() },
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var stack by rememberSaveable(stateSaver = RouteStackSaver) { mutableStateOf(listOf<Route>()) }
    val snackbar = remember { SnackbarHostState() }

    // 打開 App：今天還沒抓過價就抓一次（有同意上網抓價才會）
    LaunchedEffect(Unit) { vm.autoFetchOnOpen() }

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

    // 桌面小工具點下去：直接開記一筆、報銷、常用記帳
    LaunchedEffect(openRequest) {
        when (openRequest) {
            "add" -> { stack = emptyList(); push(Route.Edit(null)) }
            // 訂閱到期通知：回到首頁（最上面是到期提醒卡）
            "home" -> { stack = emptyList(); tab = 0 }
            // 預估放太久的通知：回到首頁、展開「試刷」那張提醒
            "est" -> { stack = emptyList(); tab = 0; vm.remindAsk = "est" }
            "reimb" -> { stack = emptyList(); push(Route.Page("reimb")) }
            "templates" -> { stack = emptyList(); push(Route.Page("templates")) }
            // 預購的「買了嗎？」通知：點通知本身打開預購清單
            "preorders" -> { stack = emptyList(); vm.preorderMonth = null; push(Route.Page("preorders")) }
            // 自組小工具的區塊（見 widgetOpen）
            "add:INCOME" -> { stack = emptyList(); push(Route.Edit(null, presetType = tw.moneybook.app.TxType.INCOME)) }
            "add:TRANSFER" -> { stack = emptyList(); push(Route.Edit(null, presetType = tw.moneybook.app.TxType.TRANSFER)) }
            "rank", "budget" -> { stack = emptyList(); tab = 0; vm.rankAsk = true }
            "pend-home" -> { stack = emptyList(); tab = 0; vm.remindAsk = "pend" }
            "accounts" -> { stack = emptyList(); tab = 1 }
            "stats" -> { stack = emptyList(); tab = 2 }
            "subs" -> { stack = emptyList(); push(Route.Page("subs")) }
            "search" -> { stack = emptyList(); push(Route.Page("search")) }
            "widgets" -> { stack = emptyList(); tab = 3; push(Route.Page("widgets")) }
            else -> {
                // 自組小工具的預購預留：這個月的預購清單
                openRequest?.takeIf { it.startsWith("po-month:") }?.let {
                    stack = emptyList()
                    vm.preorderMonth = runCatching { java.time.YearMonth.parse(it.removePrefix("po-month:")) }.getOrNull()
                    push(Route.Page("preorders"))
                }
                // 放自組小工具時還沒有排版、或選了「＋ 新增一份」：wnew:<小工具 id>[:寬:高] → 新增範例排版、綁好、打開排版頁
                openRequest?.takeIf { it.startsWith("wnew:") }?.let {
                    val parts = it.split(":")
                    val wid = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val lid = vm.newWidgetLayout()
                    if (wid != 0) {
                        tw.moneybook.app.widget.WidgetBinds.bind(ctx, wid, lid)
                        tw.moneybook.app.widget.WidgetConfigActivity.refresh(ctx.applicationContext)
                    }
                    // 返回先回到排版清單
                    stack = listOf(Route.Page("widgets")); tab = 3
                    push(Route.Page("wlayout:$lid" + if (parts.size >= 4) ":${parts[2]}:${parts[3]}" else ""))
                }
                // 放到桌面時選了「改這份排版」、或長按小工具 → 設定：打開那一份的排版頁（帶小工具的大小）
                openRequest?.takeIf { it.startsWith("wlayout:") }?.let { stack = listOf(Route.Page("widgets")); tab = 3; push(Route.Page(it)) }
                // 通知上的「買了」：記一筆帶好那筆預購；「延後」：預購清單先問延後到哪天
                openRequest?.takeIf { it.startsWith("po-buy:") }?.removePrefix("po-buy:")?.toLongOrNull()?.let {
                    stack = emptyList(); push(Route.Edit(null, preorder = "buy:$it"))
                }
                openRequest?.takeIf { it.startsWith("po-later:") }?.removePrefix("po-later:")?.toLongOrNull()?.let {
                    stack = emptyList(); vm.preorderLater = it; push(Route.Page("preorders"))
                }
                // 常用記帳小工具：tpl:<id> → 記一筆帶好那個常用記帳的內容，按完成才記
                val tid = openRequest?.takeIf { it.startsWith("tpl:") }?.removePrefix("tpl:")?.toLongOrNull()
                if (tid != null) { stack = emptyList(); push(Route.Edit(null, tplId = tid)) }
            }
        }
        if (openRequest != null) onOpenHandled()
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    // 點某一筆記錄修改：買賣連動的轉帳不能直接改（要到投資帳戶改那一筆買賣），先彈出提示
    fun editTxn(id: Long) {
        if (vm.linkedTradeOf(id) != null) vm.linkedEditAsk = id else push(Route.Edit(id))
    }

    // 每一頁的狀態（捲動位置、輸入的字…）記在這裡：點進下一頁再返回時還原，不會跳回最上面；
    // 那一頁離開堆疊（按返回退出）後才清掉，下次進來從頭開始
    val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val liveKeys = remember { HashSet<String>() }
    LaunchedEffect(stack) {
        val now = stack.map { encodeRoute(it) }.toSet()
        (liveKeys - now).forEach { pageStates.removeState(it) }
        liveKeys.clear()
        liveKeys.addAll(now)
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
        pageStates.SaveableStateProvider(top?.let { encodeRoute(it) } ?: "tabs") {
        when (top) {
            null -> MainTabs(
                vm = vm,
                tab = tab,
                // 已經在「明細」分頁時再點一下「明細」：切換列表和日曆
                onTab = { if (it == tab && it == 0) vm.homeCalendar = !vm.homeCalendar else tab = it },
                // 在日曆上選了日期再按記一筆，就預設那一天
                onAdd = { push(Route.Edit(null, presetDay = if (tab == 0 && vm.homeCalendar) vm.calSelected else null)) },
                onEdit = { editTxn(it) },
                // 「我的 → 帳戶管理」直接切到帳戶分頁
                open = {
                    when {
                        it == "accounts" -> tab = 1
                        // 刷卡待確認的「改一下」：打開記一筆，內容帶好
                        it.startsWith("pend:") -> push(Route.Edit(null, pendId = it.removePrefix("pend:").toLongOrNull()))
                        // 首頁「買好了嗎」：買了 → 記一筆帶好那筆預購；延後 → 預購清單選日期
                        it.startsWith("po-buy:") -> push(Route.Edit(null, preorder = "buy:" + it.removePrefix("po-buy:")))
                        it.startsWith("po-later:") -> { vm.preorderLater = it.removePrefix("po-later:").toLongOrNull(); push(Route.Page("preorders")) }
                        // 首頁預算「其中預留給預購」：預購清單只列那個月；從「我的」進去看全部
                        it.startsWith("po-month:") -> { vm.preorderMonth = runCatching { java.time.YearMonth.parse(it.removePrefix("po-month:")) }.getOrNull(); push(Route.Page("preorders")) }
                        it == "preorders" -> { vm.preorderMonth = null; push(Route.Page("preorders")) }
                        else -> push(Route.Page(it))
                    }
                },
            )
            is Route.Edit -> EditScreen(
                vm, top.id, top.presetTo, top.presetAmount, top.tplMode, top.tplId, presetDay = top.presetDay, presetFrom = top.presetFrom, presetAcc = top.presetAcc, pendId = top.pendId,
                preorder = top.preorder,
                presetType = top.presetType,
                onMakeSub = { push(Route.Page("sub:from:$it")) },
                onGoRecords = { who ->
                    // 關掉記一筆，打開這個人的紀錄分頁；本來就是從報銷頁過來的就不再疊一層
                    vm.reimbPerson = who
                    vm.reimbPersonTab[who] = 2
                    vm.reimbPersonLists.remove("$who:2")
                    vm.reimbGoto = who to (top.id ?: 0L)
                    val rest = stack.dropLast(1)
                    stack = if (rest.lastOrNull() == Route.Page("reimb")) rest else rest + Route.Page("reimb")
                },
                onClose = { pop() },
            )
            is Route.Page -> when (top.name) {
                "books" -> BooksScreen(vm) { pop() }
                "categories" -> CategoriesScreen(vm) { pop() }
                "templates" -> TemplatesScreen(vm, onOpen = { push(Route.Edit(null, tplMode = true, tplId = it)) }) { pop() }
                "appearance" -> AppearanceScreen(vm) { pop() }
                "data" -> DataScreen(vm) { pop() }
                "reimb" -> ReimbScreen(vm, onEdit = { editTxn(it) }) { pop() }
                "fxpending" -> FxPendingScreen(vm) { pop() }
                "cardnoti" -> CardNotifyScreen(vm) { pop() }
                "preorders" -> PreorderScreen(
                    vm,
                    onNew = { push(Route.Edit(null, preorder = "new")) },
                    onEdit = { push(Route.Edit(null, preorder = "edit:$it")) },
                    onBuy = { push(Route.Edit(null, preorder = "buy:$it")) },
                    onOpenTxn = { editTxn(it) },
                ) { pop() }
                "subs" -> SubsScreen(vm, onOpen = { push(Route.Page(if (it == null) "sub:new" else "sub:$it")) }) { pop() }
                "search" -> SearchScreen(vm, onEdit = { editTxn(it) }) { pop() }
                "drill" -> DrillScreen(vm, onEdit = { editTxn(it) }) { pop() }
                "asset" -> AssetScreen(vm, onLayout = { push(Route.Page("layout:$it")) }) { pop() }
                "widgets" -> WidgetLayoutsScreen(vm, onOpen = { push(Route.Page("wlayout:$it")) }) { pop() }
                "invest" -> InvestAllScreen(vm, onOpen = { vm.accMonths.remove(it); push(Route.Page("account:$it")) }) { pop() }
                else -> {
                    // 調整卡片排版：layout:<CardPage.key>
                    val cardPage = if (top.name.startsWith("layout:")) tw.moneybook.app.CardPages.of(top.name.removePrefix("layout:")) else null
                    if (cardPage != null) CardLayoutScreen(vm, cardPage) { pop() }
                    // 那一天的明細（統計的每天熱度點進來）：day:<epochDay>
                    val dayEp = top.name.removePrefix("day:").toLongOrNull()
                    if (top.name.startsWith("day:") && dayEp != null) DayScreen(vm, dayEp, onEdit = { editTxn(it) }) { pop() }
                    // 訂閱：sub:new 新增、sub:<id> 修改、sub:from:<記錄 id> 用那筆記錄新增
                    if (top.name.startsWith("sub:")) {
                        val rest = top.name.removePrefix("sub:")
                        SubEditScreen(vm, rest.toLongOrNull(), rest.removePrefix("from:").takeIf { rest.startsWith("from:") }?.toLongOrNull()) { pop() }
                    }
                    val accId = top.name.removePrefix("account:").toLongOrNull()
                    if (cardPage == null && top.name.startsWith("account:") && accId != null) {
                        AccountDetailScreen(
                            vm, accId,
                            onEdit = { editTxn(it) },
                            onAdd = { acc -> push(Route.Edit(null, presetAcc = acc)) },
                            onPayCard = { acc, amt -> push(Route.Edit(null, presetTo = acc, presetAmount = amt)) },
                            onFxTrade = { acc, buy -> push(if (buy) Route.Edit(null, presetTo = acc) else Route.Edit(null, presetFrom = acc)) },
                            onFxPending = { push(Route.Page("fxpending")) },
                            onHolding = { acc, sym -> push(Route.Page("holding:$acc:$sym")) },
                        ) { pop() }
                    }
                    // 一檔持股：holding:<帳戶 id>:<代號>
                    // 自組小工具的排版頁：wlayout:<排版 id>[:寬:高]（從桌面上的小工具打開時帶那個小工具的大小）
                    if (top.name.startsWith("wlayout:")) {
                        val parts = top.name.split(":")
                        parts.getOrNull(1)?.toLongOrNull()?.let { wid ->
                            WidgetEditScreen(vm, wid, parts.getOrNull(2)?.toIntOrNull(), parts.getOrNull(3)?.toIntOrNull()) { pop() }
                        }
                    }
                    if (top.name.startsWith("holding:")) {
                        val parts = top.name.split(":", limit = 3)
                        val hAcc = parts.getOrNull(1)?.toLongOrNull()
                        if (hAcc != null && parts.size == 3) HoldingScreen(vm, hAcc, parts[2]) { pop() }
                    }
                }
            }
        }
        }
        }
        // 買賣連動的轉帳：點進去改 → 提示去改買賣；左滑刪除 → 問要不要連買賣一起刪
        vm.linkedEditAsk?.let { tid ->
            val tr = vm.linkedTradeOf(tid)
            if (tr == null) {
                vm.linkedEditAsk = null
            } else {
                AlertDialog(
                    onDismissRequest = { vm.linkedEditAsk = null },
                    title = { Text("買賣連動的轉帳") },
                    text = {
                        Text("這筆轉帳是「${if (tr.buy) "買進" else "賣出"} ${tr.symbol}」時一起記的。\n金額、手續費、日期、帳戶都跟著買賣走，要修改請到投資帳戶的買賣記錄。")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.linkedEditAsk = null
                            vm.openTradeEdit = tr.id
                            // 已經在那個投資帳戶的頁面就不用再開一層
                            val route = Route.Page("account:${tr.accountId}")
                            if (stack.lastOrNull() != route) push(route)
                        }) { Text("前往修改") }
                    },
                    dismissButton = { TextButton(onClick = { vm.linkedEditAsk = null }) { Text("取消") } },
                )
            }
        }
        vm.linkedDeleteAsk?.let { tid ->
            val tr = vm.linkedTradeOf(tid)
            if (tr == null) {
                vm.linkedDeleteAsk = null
            } else {
                AlertDialog(
                    onDismissRequest = { vm.linkedDeleteAsk = null },
                    title = { Text("買賣連動的轉帳") },
                    text = {
                        Text("這筆轉帳是「${if (tr.buy) "買進" else "賣出"} ${tr.symbol}」時一起記的。\n刪除會連同這筆買賣一起刪掉，持股、成本會跟著變（可以復原）。")
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.linkedDeleteAsk = null; vm.deleteTrade(tr.id) }) {
                            Text("一起刪除", color = LocalCute.current.expense)
                        }
                    },
                    dismissButton = { TextButton(onClick = { vm.linkedDeleteAsk = null }) { Text("取消") } },
                )
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
                    onFxPending = { open("fxpending") },
                    onDrill = { open("drill") },
                    onEditPend = { open("pend:$it") },
                    onPreorder = { open(it) },
                )
                1 -> AccountsScreen(vm, onOpen = { vm.accMonths.remove(it); open("account:$it") }, onAsset = { open("asset") }, onInvest = { open("invest") })
                2 -> StatsScreen(vm, onSearch = { open("search") }, onDrill = { open("drill") }, onLayout = { open("layout:$it") }, onAsset = { open("asset") }, onDay = { open("day:$it") })
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
