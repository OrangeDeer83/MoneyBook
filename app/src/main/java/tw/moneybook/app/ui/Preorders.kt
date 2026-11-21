package tw.moneybook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.PRE_BOUGHT
import tw.moneybook.app.PRE_SKIPPED
import tw.moneybook.app.PRE_WAIT
import tw.moneybook.app.Preorder
import tw.moneybook.app.bookPreorders
import tw.moneybook.app.formatMoney
import tw.moneybook.app.mascotTitle
import tw.moneybook.app.preorderInBudget
import tw.moneybook.app.preorderName
import java.time.LocalDate

/*
 * 預購清單（草圖 ci-out/preorder_plan.png ②④）：我的 → 預購清單，和首頁的「該買了嗎？」卡片。
 */

/** 「12/20・還有 29 天」「今天」「12/1・過了 3 天」 */
private fun whenText(day: Long, today: Long): String = when {
    day == today -> "今天"
    day > today -> "${shortDay(day, today)}・還有 ${day - today} 天"
    else -> "${shortDay(day, today)}・過了 ${today - day} 天"
}

/** 分類・帳戶 */
private fun subLine(d: AppData, p: Preorder): String = listOfNotNull(
    p.categoryId?.let { d.catMap[it] }?.let { c -> c.parentId?.let { d.catMap[it]?.name + " › " }.orEmpty() + c.name },
    p.accountId?.let { d.accMap[it]?.name },
).joinToString("・")

/** 延後到哪一天：預設一週後（還沒到的從原本那天往後一週），要選明天以後 */
@Composable
fun PreorderLaterDialog(vm: MoneyViewModel, p: Preorder, onDone: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    CuteDatePickerDialog(
        initial = maxOf(p.day, today) + 7,
        onPick = { day ->
            if (day <= today) vm.toast("要選明天以後的日期") else vm.postponePreorder(p.id, day)
            onDone()
        },
        onDismiss = onDone,
        title = "${vm.data.preorderName(p)} 延後到哪一天",
    )
}

/** 我的 → 預購清單：上面預留了多少、到期通知開關，下面分等著買／買了／沒買 */
@Composable
fun PreorderScreen(
    vm: MoneyViewModel,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onBuy: (Long) -> Unit,
    onOpenTxn: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    val all = d.bookPreorders
    val waits = all.filter { it.status == PRE_WAIT }.sortedWith(compareBy({ it.day }, { it.id }))
    val boughts = all.filter { it.status == PRE_BOUGHT }.sortedByDescending { it.doneDay }
    val skips = all.filter { it.status == PRE_SKIPPED }.sortedByDescending { it.doneDay }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var act by remember { mutableStateOf<Preorder?>(null) }
    var later by remember { mutableStateOf<Preorder?>(null) }
    // 通知按「延後」打開的：先問延後到哪天
    LaunchedEffect(vm.preorderLater) {
        vm.preorderLater?.let { id -> later = d.preorders.firstOrNull { it.id == id && it.waiting } }
        vm.preorderLater = null
    }

    SubPage("預購清單", onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("已經預留", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatMoney(waits.filter { d.preorderInBudget(it) }.sumOf { it.amount }), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("${waits.size} 樣等著買", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                    }
                    Text("從預計買的那個月的預算先扣掉，不動帳戶、不算統計的支出", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
            }
            item { PreorderNotifyCard(vm) }
            item {
                PillSegment(
                    listOf("等著買 ${waits.size}", "買了 ${boughts.size}", "沒買 ${skips.size}"), tab, { tab = it },
                    Modifier.fillMaxWidth(), equal = true,
                )
            }
            val list = when (tab) { 0 -> waits; 1 -> boughts; else -> skips }
            if (list.isEmpty()) {
                item {
                    Text(
                        when (tab) {
                            0 -> "還沒有要買的東西。記一筆時日期選以後、按「預購」，或按下面的「＋ 新增預購」"
                            1 -> "還沒有買了的預購"
                            else -> "沒有沒買的預購"
                        },
                        style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(list, key = { it.id }) { p ->
                SwipeRow(onDelete = { vm.deletePreorder(p.id) }) {
                    PreorderLine(d, p, today) {
                        when (p.status) {
                            PRE_BOUGHT -> d.txnMap[p.txnId]?.let { onOpenTxn(it.id) } ?: vm.toast("買的那一筆已經刪掉了")
                            else -> act = p
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增預購") } }
            item {
                Text(
                    "到了預計那天會問你買了沒：買了就打開記一筆，照實際的金額記，預留的拿掉；沒買就把預留的放回預算。左滑可以刪除。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
            }
        }
    }

    act?.let { p ->
        val name = d.preorderName(p)
        if (p.status == PRE_SKIPPED) {
            AlertDialog(
                onDismissRequest = { act = null },
                title = { Text(name) },
                text = { Text("沒買・預估 ${formatMoney(p.amount)}。放回等著買的話，會再從 ${p.month.monthValue} 月的預算預留。") },
                confirmButton = { TextButton(onClick = { act = null; vm.unskipPreorder(p.id) }) { Text("放回等著買") } },
                dismissButton = { TextButton(onClick = { act = null }) { Text("取消") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { act = null },
                title = { Text(name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("預估 ${formatMoney(p.amount)}・${whenText(p.day, today)}", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { act = null; onBuy(p.id) }, modifier = Modifier.fillMaxWidth()) { Text("買了") }
                        SoftButton("沒買", { act = null; vm.skipPreorder(p.id) }, Modifier.fillMaxWidth())
                        SoftButton("延後", { act = null; later = p }, Modifier.fillMaxWidth())
                        SoftButton("修改", { act = null; onEdit(p.id) }, Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { act = null }) { Text("取消") } },
            )
        }
    }
    later?.let { p -> PreorderLaterDialog(vm, p) { later = null } }
}

/** 清單的一列：名稱、金額；第二行日期（過了紅字）或買了／沒買；第三行分類・帳戶 */
@Composable
private fun PreorderLine(d: AppData, p: Preorder, today: Long, onClick: () -> Unit) {
    val cute = LocalCute.current
    val c = p.categoryId?.let { d.catMap[it] }
    val txn = if (p.status == PRE_BOUGHT) d.txnMap[p.txnId] else null
    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.preorderName(p), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (line, warn) = when (p.status) {
                    PRE_BOUGHT -> {
                        val real = txn?.spent
                        (if (real == null) "買了・那一筆已經刪掉" else {
                            val diff = real - p.amount
                            "${shortDay(p.doneDay, today)} 買了・實際 ${formatMoney(real)}" + when {
                                diff > 0 -> "（比預估多 ${formatMoney(diff)}）"
                                diff < 0 -> "（比預估少 ${formatMoney(-diff)}）"
                                else -> ""
                            }
                        }) to false
                    }
                    PRE_SKIPPED -> "${shortDay(p.doneDay, today)} 沒買" to false
                    else -> whenText(p.day, today) to (p.day < today)
                }
                Text(line, style = MaterialTheme.typography.labelMedium, color = if (warn) cute.expense else MaterialTheme.colorScheme.primary, maxLines = 1)
                val sub = subLine(d, p) + if (p.status == PRE_WAIT && !d.preorderInBudget(p)) "・預算外（不預留）" else ""
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                formatMoney(p.amount), style = MaterialTheme.typography.titleSmall,
                color = if (p.status == PRE_WAIT) cute.ink else cute.sub, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** 預購清單上面：到了那天問買了沒的開關（時間、再提醒天數跟訂閱的到期通知一樣） */
@Composable
private fun PreorderNotifyCard(vm: MoneyViewModel) {
    val cute = LocalCute.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = vm.data.prefs
    // 從系統設定回來時重新看一次通知權限
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val allowed = remember(tick, p.preorderNotify) { tw.moneybook.app.notify.SubNotifier.allowed(ctx) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) tick++ else vm.toast("沒有通知權限：到手機設定允許記帳本傳送通知，才收得到提醒")
    }
    val name = p.mascotTitle(::mascotDefaultName)
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("到了那天問我買了沒", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
                checked = p.preorderNotify, onCheckedChange = { vm.setPreorderNotify(it) },
                modifier = Modifier.semantics { contentDescription = "預購通知" },
            )
        }
        Text(
            if (p.preorderNotify) "$name 會在預計那天${noticeTimeText(p.subNotifyTime)}問你（跟訂閱的到期通知同一個時間；沒回答照那邊的設定再問）"
            else "關掉後只在首頁問，不跳手機通知",
            style = MaterialTheme.typography.labelSmall, color = cute.sub,
        )
        if (p.preorderNotify && !allowed) {
            Text(
                "手機不允許記帳本發通知，收不到提醒。點這裡打開",
                style = MaterialTheme.typography.bodySmall, color = cute.expense,
                modifier = Modifier.padding(top = 4.dp).clickable {
                    val need = android.os.Build.VERSION.SDK_INT >= 33
                    if (need) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else try {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }.padding(vertical = 4.dp),
            )
        }
    }
}

/** 首頁：到了預計那天還沒回答的預購，每一筆「沒買」「延後」「買了」 */
@Composable
fun PreorderDueCard(vm: MoneyViewModel, due: List<Preorder>, onPreorder: (String) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    var later by remember { mutableStateOf<Preorder?>(null) }
    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
            if (due.size == 1) "該買了嗎？" else "有 ${due.size} 樣預購該買了",
            style = MaterialTheme.typography.titleSmall,
        )
        due.take(5).forEach { p ->
            val c = p.categoryId?.let { d.catMap[it] }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.preorderName(p), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "預計 " + whenText(p.day, today) + (p.accountId?.let { d.accMap[it]?.name }?.let { "・$it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = if (p.day < today) cute.expense else cute.sub, maxLines = 1,
                        )
                    }
                    Text("預估 ${formatMoney(p.amount)}", color = cute.expense, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
                Row(Modifier.padding(start = 44.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftButton("沒買", { vm.skipPreorder(p.id) }, compact = true)
                    SoftButton("延後", { later = p }, compact = true)
                    Button(
                        onClick = { onPreorder("po-buy:${p.id}") },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).semantics { contentDescription = "買了 ${d.preorderName(p)}" },
                    ) { Text("買了", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        if (due.size > 5) Text("還有 ${due.size - 5} 樣，在「我的 → 預購清單」", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(top = 6.dp))
    }
    later?.let { p -> PreorderLaterDialog(vm, p) { later = null } }
}
