package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.AppData
import tw.moneybook.app.CardPend
import tw.moneybook.app.Currencies
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.PendState
import tw.moneybook.app.claimedTxns
import tw.moneybook.app.pendSub
import tw.moneybook.app.pendSubDone
import tw.moneybook.app.subTwd
import tw.moneybook.app.formatFx
import tw.moneybook.app.isForeign
import tw.moneybook.app.formatMoney
import tw.moneybook.app.fxPlain
import tw.moneybook.app.guessCat
import tw.moneybook.app.possibleHad
import tw.moneybook.app.rateOf
import tw.moneybook.app.resolveCard

/** 首頁最多列幾筆，多的折起來 */
private const val PEND_SHOW = 3

/** 待確認的金額文字：台幣「$85」、外幣「US$15.49」 */
fun pendAmountText(p: CardPend): String = if (p.cur.isEmpty()) formatMoney(p.amount) else formatFx(p.amount, p.cur)

/** 「今天 09:11」「11/13」 */
private fun pendWhen(p: CardPend, today: Long): String =
    shortDay(p.day, today) + if (p.minute >= 0) " %02d:%02d".format(p.minute / 60, p.minute % 60) else ""

/**
 * 首頁「有 N 筆刷卡等你確認」：每一筆要按「記」才記；分類直接寫自動帶的（找不到寫「還沒有分類」）。
 * 對不到卡的問是哪張卡；那天那張卡已經有同金額的，問是不是已經記過了。
 */
@Composable
fun CardPendCard(vm: MoneyViewModel, all: List<CardPend>, onEdit: (Long) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    val waiting = d.pendWaiting(all)
    val claimed = remember(all) { claimedTxns(all) }
    var expanded by remember { mutableStateOf(false) }
    // 正在處理的那一筆：選卡片、選分類、外幣金額
    var pickCard by remember { mutableStateOf<CardPend?>(null) }
    var pickCat by remember { mutableStateOf<Pair<CardPend, Long>?>(null) }
    var fx by remember { mutableStateOf<Triple<CardPend, Long, Long?>?>(null) }
    // 對到訂閱、外幣對外幣記在台幣卡又沒有匯率：先問台幣金額
    var subFx by remember { mutableStateOf<CardPend?>(null) }

    /** 按「記」：沒有分類先選分類；外幣記在台幣卡、又沒有匯率，先問金額 */
    fun record(p: CardPend, cardId: Long, catId: Long?) {
        val acc = d.accMap[cardId]
        when {
            catId == null -> pickCat = p to cardId
            p.cur.isNotEmpty() && !(acc?.isForeign == true && acc.currency == p.cur) && d.rateOf(p.cur) == null -> fx = Triple(p, cardId, catId)
            else -> vm.recordPend(p.id, cardId, catId)
        }
    }

    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Text("有 ${waiting.size} 筆刷卡等你確認", style = MaterialTheme.typography.titleSmall)
        val shown = if (expanded) waiting else waiting.take(PEND_SHOW)
        shown.forEach { p ->
            val cardId = d.resolveCard(p)
            val card = cardId?.let { d.accMap[it] }
            val catId = d.guessCat(p.merchant, cardId)
            val cat = catId?.let { d.catMap[it] }
            val sm = d.pendSub(p, cardId)?.takeIf { !it.done }
            val had = if (sm != null) null else d.possibleHad(p, cardId, claimed)
            val subCat = sm?.sub?.categoryId?.let { d.catMap[it] }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft.copy(alpha = 0.45f)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sm != null && sm.exact) CatBubble(sm.sub.icon.ifEmpty { subCat?.emoji ?: "img:cat_box" }, subCat?.color ?: 8, 34.dp)
                    else CatBubble(cat?.emoji ?: "？", cat?.color ?: 8, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (sm != null && sm.exact) sm.sub.name else p.merchant.ifEmpty { "（通知沒寫商店）" },
                                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (sm != null && sm.exact) {
                                Text(
                                    "訂閱", style = MaterialTheme.typography.labelSmall, color = cute.income,
                                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(cute.income.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        val where = card?.name ?: (p.label + if (p.last4.isNotEmpty()) "・末四碼 ${p.last4}" else "")
                        val fxNote = if (p.cur.isNotEmpty() && !(card?.isForeign == true && card.currency == p.cur)) "外幣消費（待請款）" else null
                        Text(
                            listOfNotNull(
                                where, pendWhen(p, today),
                                when {
                                    card == null -> null
                                    sm != null && sm.exact -> subCat?.name ?: "還沒有分類"
                                    else -> cat?.name ?: "還沒有分類"
                                },
                                fxNote,
                            ).joinToString("・"),
                            style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 2,
                        )
                    }
                    Text(pendAmountText(p), color = cute.expense, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
                val hint: Pair<String, String>? = when {
                    card == null -> (if (p.last4.isNotEmpty()) "${p.label} 的 ${p.last4} 是哪張卡？" else "${p.label} 的通知是哪張卡？") to "選一次就記住"
                    sm != null && !sm.exact -> "可能是訂閱：${sm.sub.name}（${subAmountText(sm.sub)}）" to
                        (d.subTwd(sm.sub)?.let { "用現在的匯率算大約 ${formatMoney(it)}" } ?: "")
                    had != null -> "${shortDay(had.day, today)} ${card.name} 已經有一筆 ${formatMoney(had.recorded)}" to
                        "（" + listOfNotNull(had.categoryId?.let { d.catMap[it]?.name }, had.note.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }).joinToString("，") + "）"
                    else -> null
                }
                if (hint != null) {
                    Column(
                        Modifier.padding(start = 44.dp, top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .background(if (card == null) cute.accent2.copy(alpha = 0.18f) else cute.soft).padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(hint.first, style = MaterialTheme.typography.labelMedium, color = cute.ink)
                        if (hint.second.length > 2) Text(hint.second, style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    }
                }
                Row(Modifier.padding(start = 44.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val name = p.merchant.ifEmpty { pendAmountText(p) }
                    when {
                        card == null -> {
                            PendMainButton("選卡片", "選卡片 $name") { pickCard = p }
                            SoftButton("不是消費", { vm.notSpendPend(p.id) }, compact = true)
                        }
                        sm != null -> {
                            PendMainButton(if (sm.exact) "記" else "是訂閱", (if (sm.exact) "記 " else "是訂閱 ") + sm.sub.name) {
                                if (!vm.recordPendSub(p.id) && sm.sub.isForeign) subFx = p
                            }
                            SoftButton(if (sm.exact) "不是這個訂閱" else "不是", { vm.notSubPend(p.id) }, compact = true)
                        }
                        had != null -> {
                            PendMainButton("已經記過了", "已經記過了 $name") { vm.hadPend(p.id, had.id) }
                            SoftButton("還是要記", { record(p, card.id, catId) }, compact = true)
                        }
                        else -> {
                            PendMainButton("記", "記 $name") { record(p, card.id, catId) }
                            SoftButton("改一下", { onEdit(p.id) }, compact = true)
                            SoftButton("不是消費", { vm.notSpendPend(p.id) }, compact = true)
                        }
                    }
                }
            }
        }
        if (waiting.size > PEND_SHOW) {
            Text(
                if (expanded) "收起來" else "還有 ${waiting.size - PEND_SHOW} 筆 ›",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(4.dp),
            )
        }
    }

    pickCard?.let { p ->
        CardPickDialog(d, p, onDismiss = { pickCard = null }) { cardId, remember ->
            pickCard = null
            vm.pickPendCard(p.id, cardId, remember)
            record(p, cardId, vm.data.guessCat(p.merchant, cardId))
        }
    }
    subFx?.let { p ->
        val s = d.pendSub(p, d.resolveCard(p))?.sub
        if (s == null) subFx = null
        else FxSpendDialog(
            vm = vm,
            init = FxSpendInit(cur = s.cur, amount = fxPlain(p.amount, Currencies.of(s.cur).decimals), pending = true, twd = 0L),
            hasValue = false,
            onConfirm = { _, _, twd, pending -> subFx = null; vm.recordPendSub(p.id, twd, pending) },
            onClear = { subFx = null },
            onDismiss = { subFx = null },
            title = s.name,
        )
    }
    pickCat?.let { (p, cardId) ->
        SubCatPickDialog(d, null, onPick = { c -> pickCat = null; record(p, cardId, c) }, onDismiss = { pickCat = null })
    }
    fx?.let { (p, cardId, catId) ->
        FxSpendDialog(
            vm = vm,
            init = FxSpendInit(cur = p.cur, amount = fxPlain(p.amount, Currencies.of(p.cur).decimals), pending = true, twd = 0L),
            hasValue = false,
            onConfirm = { _, _, twd, pending -> fx = null; vm.recordPend(p.id, cardId, catId, twd, pending) },
            onClear = { fx = null },
            onDismiss = { fx = null },
            title = p.merchant.ifEmpty { "外幣消費" },
        )
    }
}

/** 一列裡主要的那顆按鈕（實心） */
@Composable
private fun PendMainButton(text: String, desc: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp).semantics { contentDescription = desc },
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 一張卡的末四碼說明：「已記：1234、5566（手機）」「還沒有記末四碼」 */
private fun cardNosText(a: Account): String =
    if (a.cardNos.isEmpty()) "還沒有記末四碼" else "已記：" + a.cardNos.joinToString("、") { it.digits + if (it.note.isNotEmpty()) "（${it.note}）" else "" }

/**
 * 對不到卡時選卡片：來源 App 設定的卡排上面，其他信用卡在下面；
 * 「記住」預設打勾（9012 記到這張卡、之後不會再問）。按下去就照「記」一樣記好。
 */
@Composable
private fun CardPickDialog(d: AppData, p: CardPend, onDismiss: () -> Unit, onPick: (Long, Boolean) -> Unit) {
    val cute = LocalCute.current
    val cards = d.accounts.filter { it.type == AccountType.CARD && !it.hidden }
    val appIds = p.src.flatMap { s -> d.notiApps.firstOrNull { it.pkg == s.pkg }?.cardIds.orEmpty() }.toSet()
    val mine = cards.filter { it.id in appIds }
    val others = cards.filter { it.id !in appIds }
    var sel by remember { mutableStateOf<Long?>(null) }
    var keep by remember { mutableStateOf(true) }
    val tries = rememberNeedTries()
    val need = firstNeed(if (sel == null) Need("card", "請選一張卡") else null)
    val nv = NeedView(need, tries.count)
    val today = LocalDate.now().toEpochDay()
    val selName = sel?.let { id -> cards.firstOrNull { it.id == id }?.name }
    val title = p.src.firstOrNull { it.last4.isEmpty() }?.title ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.last4.isNotEmpty()) "${p.label} 的 ${p.last4} 是哪張卡？" else "這筆是哪張卡？") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOf(p.merchant.ifEmpty { "（通知沒寫商店）" }, pendAmountText(p), pendWhen(p, today)).joinToString("・"),
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                if (cards.isEmpty()) Text("還沒有信用卡，請先到帳戶新增一張信用卡。", style = MaterialTheme.typography.bodyMedium)
                @Composable
                fun group(label: String, list: List<Account>) {
                    if (list.isEmpty()) return
                    Text(label, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { a ->
                        val on = sel == a.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) cute.soft else cute.soft.copy(alpha = 0.4f))
                                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { sel = a.id }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge)
                                Text(cardNosText(a), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                            }
                            RadioButton(selected = on, onClick = { sel = a.id })
                        }
                    }
                }
                group("${p.label} 的卡", mine)
                group(if (mine.isEmpty()) "信用卡" else "其他信用卡", others)
                nv.Message("card")
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { keep = !keep }.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Column {
                        val what = when {
                            p.last4.isNotEmpty() -> p.last4
                            title.isNotEmpty() -> "「$title」"
                            else -> "${p.label} 沒寫末四碼的"
                        }
                        Text("記住：$what 是${selName ?: "這張卡"}", style = MaterialTheme.typography.bodyMedium)
                        Text(if (keep) "以後直接記到${selName ?: "這張卡"}" else "只有這一筆記到${selName ?: "這張卡"}", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { val s = sel; if (need != null || s == null) tries.count++ else onPick(s, keep) }) { Text(if (selName != null) "記到$selName" else "記下來") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 首頁要列的待確認：還沒處理、也不是已經記過的那次訂閱（照收到的時間，早的在前） */
fun AppData.pendWaiting(list: List<CardPend>): List<CardPend> =
    list.filter { it.state == PendState.WAIT && pendSubDone(it) == null }.sortedBy { it.time }
