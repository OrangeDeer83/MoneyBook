package tw.moneybook.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import tw.moneybook.app.AccountType
import tw.moneybook.app.CapturedNoti
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.NotiApp
import tw.moneybook.app.looksLikePayApp
import tw.moneybook.app.notify.CardNotiListener
import tw.moneybook.app.notify.CardNotiLog
import tw.moneybook.app.notify.cardNotiComponent
import tw.moneybook.app.notify.cardNotiEnabled
import java.time.Instant
import java.time.ZoneId

/** 通知時間：今天只寫時分，其他寫月/日 時分 */
private fun notiTime(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val hm = "${t.hour}:${t.minute.toString().padStart(2, '0')}"
    return if (t.toLocalDate() == java.time.LocalDate.now()) "今天 $hm" else "${t.monthValue}/${t.dayOfMonth} $hm"
}

/** 一個 App 的圖示（拿不到就用名稱第一個字） */
@Composable
private fun AppIcon(pkg: String, label: String) {
    val ctx = LocalContext.current
    val cute = LocalCute.current
    val bmp: ImageBitmap? = remember(pkg) {
        try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(cute.soft), contentAlignment = Alignment.Center) {
            Text(label.take(1), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 最上面的三步：開權限、選 App、對應卡片 */
@Composable
private fun StepPills(cur: Int) {
    val cute = LocalCute.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("開權限", "選 App", "對應卡片").forEachIndexed { i, l ->
            val on = i == cur
            val done = i < cur
            Box(
                Modifier.weight(1f).clip(CircleShape).background(
                    when {
                        on -> MaterialTheme.colorScheme.primary
                        done -> cute.income.copy(alpha = 0.18f)
                        else -> cute.card
                    }
                ).padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (if (done) "完成・" else "${i + 1}. ") + l, style = MaterialTheme.typography.labelMedium,
                    color = when {
                        on -> MaterialTheme.colorScheme.onPrimary
                        done -> cute.income
                        else -> cute.sub
                    },
                )
            }
        }
    }
}

/**
 * 刷卡通知（我的 → 刷卡通知）。第一步「收得到」：開通知存取權 → 選要讀的 App → 每個 App 是哪幾張卡，
 * 之後列出收到的通知（只有勾選的 App 的，原文留 7 天）。這一步還不會記帳。
 */
@Composable
fun CardNotifyScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val d = vm.data
    // 從系統設定回來時重新看權限
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val enabled = remember(tick) { cardNotiEnabled(ctx) }
    // 1＝選 App、2＝對應卡片、3＝總覽（還沒選過 App 就從選 App 開始）
    var step by rememberSaveable { mutableIntStateOf(if (d.notiApps.isEmpty()) 1 else 3) }
    SubPage("刷卡通知", onBack) {
        when {
            !enabled -> PermissionStep(tick)
            step == 1 || d.notiApps.isEmpty() -> AppPickStep(vm) { step = 2 }
            step == 2 -> CardMapStep(vm) { step = 3 }
            else -> NotiOverview(vm) { step = 1 }
        }
    }
}

@Composable
private fun PermissionStep(@Suppress("UNUSED_PARAMETER") tick: Int) {
    val ctx = LocalContext.current
    val cute = LocalCute.current
    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StepPills(0) }
        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("讓記帳本讀刷卡通知", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "刷卡後銀行或支付 App 會跳通知，記帳本讀這則通知幫你記帳。只在手機上讀、不連網；只看你等一下勾選的 App，其他通知直接丟掉。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("打開通知存取權", style = MaterialTheme.typography.titleSmall)
                        Text("在清單裡找到「記帳本」打開", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                    Button(onClick = {
                        open(
                            if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cardNotiComponent(ctx).flattenToString())
                            else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }) { Text("前往") }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft.copy(alpha = 0.5f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("開關是灰色、按不下去？", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "記帳本不是從 Play 商店裝的，Android 會先擋住這個設定。請先打開記帳本的「應用程式資訊」，按右上角選單 → 「允許受限制的設定」，再回來按「前往」。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        SoftButton("打開應用程式資訊", {
                            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }, compact = true)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("打開後回到這裡，會自動到下一步。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
            }
        }
    }
}

/** 選 App 的一列資料 */
private class AppEntry(val pkg: String, val label: String, val active: Boolean, val pay: Boolean)

@Composable
private fun AppPickStep(vm: MoneyViewModel, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val cute = LocalCute.current
    val d = vm.data
    val entries = remember {
        val pm = ctx.packageManager
        fun labelOf(pkg: String): String = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            d.notiApps.firstOrNull { it.pkg == pkg }?.label ?: pkg
        }
        val active = CardNotiListener.instance?.activePackages().orEmpty().toSet()
        val launch = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        } catch (_: Exception) {
            emptyList()
        }
        val labels = LinkedHashMap<String, String>()
        launch.forEach { (p, l) -> labels[p] = l }
        (active + d.notiApps.map { it.pkg }).forEach { p -> if (p !in labels) labels[p] = labelOf(p) }
        labels.remove(ctx.packageName)
        labels.map { (p, l) -> AppEntry(p, l, p in active, looksLikePayApp(l)) }
            .sortedWith(compareBy({ !(it.active && it.pay) }, { !it.pay }, { !it.active }, { it.label }))
    }
    val picked = remember { mutableStateListOf<String>().apply { addAll(d.notiApps.map { it.pkg }) } }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    val shown = if (showAll) entries else entries.filter { it.active || it.pay || it.pkg in picked }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StepPills(1) }
        item {
            Text(
                "勾選會發刷卡通知的 App：銀行、支付、錢包（例如 Samsung Wallet）。銀行和支付類、通知欄裡現在有通知的排前面；沒勾的 App 完全不看。",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
        }
        if (shown.isEmpty()) {
            item { Text("沒有找到像銀行或支付的 App，按下面「顯示全部 App」自己挑。", style = MaterialTheme.typography.bodySmall, color = cute.sub) }
        }
        items(shown, key = { it.pkg }) { e ->
            val on = e.pkg in picked
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cute.card)
                    .clickable { if (on) picked.remove(e.pkg) else picked.add(e.pkg) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(e.pkg, e.label)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    val tags = listOfNotNull(if (e.pay) "銀行・支付" else null, if (e.active) "通知欄有通知" else null)
                    if (tags.isNotEmpty()) Text(tags.joinToString("・"), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
                Checkbox(checked = on, onCheckedChange = { if (it) picked.add(e.pkg) else picked.remove(e.pkg) }, modifier = Modifier.semantics { contentDescription = "讀 ${e.label} 的通知" })
            }
        }
        if (!showAll) {
            item { SoftButton("找不到？顯示全部 App（${entries.size} 個）", { showAll = true }, Modifier.fillMaxWidth()) }
        }
        item {
            if (tries > 0 && picked.isEmpty()) {
                Text("請至少勾選一個 App", style = MaterialTheme.typography.bodySmall, color = cute.expense, modifier = Modifier.padding(bottom = 6.dp))
            }
            Button(
                onClick = {
                    if (picked.isEmpty()) {
                        tries++
                        return@Button
                    }
                    vm.setNotiApps(picked.map { p -> p to (entries.firstOrNull { it.pkg == p }?.label ?: p) })
                    onNext()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("下一步") }
        }
    }
}

@Composable
private fun CardMapStep(vm: MoneyViewModel, onDone: () -> Unit) {
    val cute = LocalCute.current
    val d = vm.data
    val cards = d.visibleAccounts.filter { it.type == AccountType.CARD }
    var pickDefault by remember { mutableStateOf<NotiApp?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { StepPills(2) }
        item {
            Text(
                "每個 App 的通知是哪幾張信用卡？卡片名稱隨便取都可以，不用寫銀行。只選一張的，那個 App 的通知都記它；好幾張的之後用末四碼分。不知道可以先跳過，收到通知時再問。",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
        }
        if (cards.isEmpty()) {
            item { Text("還沒有信用卡帳戶：先到帳戶頁新增信用卡，再回來設定。", style = MaterialTheme.typography.bodySmall, color = cute.expense) }
        }
        items(d.notiApps, key = { it.pkg }) { a ->
            CuteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg, a.label)
                    Spacer(Modifier.width(10.dp))
                    Text("${a.label} 的通知是哪幾張卡？", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    cards.forEach { c ->
                        val on = c.id in a.cardIds
                        CuteChip(c.name, on, {
                            val ids = if (on) a.cardIds - c.id else a.cardIds + c.id
                            vm.setNotiAppCards(a.pkg, ids, a.defaultCard?.takeIf { it in ids || ids.isEmpty() })
                        })
                    }
                }
                val valid = a.cardIds.filter { d.accMap[it] != null }
                if (valid.size != 1 && cards.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "通知沒寫末四碼時記到：" + (a.defaultCard?.let { d.accMap[it]?.name } ?: "每次問我") + " ›",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { pickDefault = a }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        item {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
    pickDefault?.let { a ->
        val options = (if (a.cardIds.isNotEmpty()) cards.filter { it.id in a.cardIds } else cards)
        AlertDialog(
            onDismissRequest = { pickDefault = null },
            title = { Text("${a.label} 沒寫末四碼時記到") },
            text = {
                Column {
                    (listOf<Pair<Long?, String>>(null to "每次問我") + options.map { it.id to it.name }).forEach { (id, n) ->
                        Text(
                            n, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (a.defaultCard == id) FontWeight.Bold else FontWeight.Normal,
                            color = if (a.defaultCard == id) MaterialTheme.colorScheme.primary else LocalCute.current.ink,
                            modifier = Modifier.fillMaxWidth().clickable { vm.setNotiAppCards(a.pkg, a.cardIds, id); pickDefault = null }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickDefault = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NotiOverview(vm: MoneyViewModel, onEditApps: () -> Unit) {
    val ctx = LocalContext.current
    val cute = LocalCute.current
    val d = vm.data
    val ver by CardNotiLog.version.collectAsState()
    val list: List<CapturedNoti> = remember(ver) { CardNotiLog.load(ctx) }
    var ask by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("正在收集刷卡通知", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (list.isEmpty()) "還沒收到。刷卡後，勾選的 App 跳出的通知會出現在下面。"
                    else "最後收到：${notiTime(list.first().time)}，最近 7 天 ${list.size} 則。") +
                        "\n現在只收集、還不會記帳；讀出金額、商店、按一下記帳是下一步。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("讀哪些 App", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("修改 ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onEditApps() }.padding(4.dp))
                }
                d.notiApps.forEach { a ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg, a.label)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = MaterialTheme.typography.bodyMedium)
                            val names = a.cardIds.mapNotNull { d.accMap[it]?.name }
                            Text(
                                (if (names.isEmpty()) "還沒選卡（收到時再問）" else names.joinToString("、")) +
                                    (if (names.size != 1) "・沒寫末四碼時：" + (a.defaultCard?.let { d.accMap[it]?.name } ?: "每次問我") else ""),
                                style = MaterialTheme.typography.labelSmall, color = cute.sub,
                            )
                        }
                    }
                }
            }
        }
        item { Text("收到的通知（最近 7 天）", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        if (list.isEmpty()) {
            item { EmptyHint(d.prefs.mascot, "還沒收到通知\n刷一筆小額試試看") }
        }
        items(list, key = { it.id }) { n ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cute.card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("${n.label}・${notiTime(n.time)}", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                if (n.title.isNotEmpty()) Text(n.title, style = MaterialTheme.typography.titleSmall)
                if (n.text.isNotEmpty()) Text(n.text, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            if (list.isNotEmpty()) SoftButton("清空收到的通知", { ask = "clear" }, Modifier.fillMaxWidth(), danger = true)
            Spacer(Modifier.height(8.dp))
            SoftButton("停用刷卡通知", { ask = "off" }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("原文只存在這支手機裡、不跟著備份，超過 7 天的自動刪掉。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        }
    }
    when (ask) {
        "clear" -> AlertDialog(
            onDismissRequest = { ask = "" },
            title = { Text("清空收到的通知？") },
            text = { Text("最近 7 天收到的 ${list.size} 則原文會刪掉，不能復原。勾選的 App 和卡片設定不會變。") },
            confirmButton = { TextButton(onClick = { CardNotiLog.clear(ctx); ask = "" }) { Text("清空", color = cute.expense) } },
            dismissButton = { TextButton(onClick = { ask = "" }) { Text("取消") } },
        )
        "off" -> AlertDialog(
            onDismissRequest = { ask = "" },
            title = { Text("停用刷卡通知？") },
            text = { Text("會清掉勾選的 App 和收到的通知。接著打開系統的通知存取權頁面，請把「記帳本」關掉。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNotiApps(emptyList())
                    CardNotiLog.clear(ctx)
                    ask = ""
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }) { Text("停用", color = cute.expense) }
            },
            dismissButton = { TextButton(onClick = { ask = "" }) { Text("取消") } },
        )
    }
}
