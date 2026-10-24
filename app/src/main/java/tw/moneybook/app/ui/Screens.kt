package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.flowFor
import tw.moneybook.app.formatFx
import tw.moneybook.app.fmt
import tw.moneybook.app.fxNote
import tw.moneybook.app.fxPendingList
import tw.moneybook.app.debtGroups
import tw.moneybook.app.isFxSpend
import tw.moneybook.app.isDebt
import tw.moneybook.app.debtRemainingTwd
import tw.moneybook.app.debtCurrency
import tw.moneybook.app.debtTotal
import tw.moneybook.app.effective
import tw.moneybook.app.totalUnits
import tw.moneybook.app.isForeign
import tw.moneybook.app.TxType
import tw.moneybook.app.ReimbPay
import tw.moneybook.app.Account
import tw.moneybook.app.Txn
import tw.moneybook.app.expenseSum
import tw.moneybook.app.billMonthFor
import tw.moneybook.app.formatMoney
import tw.moneybook.app.formatShort
import tw.moneybook.app.inMonth
import tw.moneybook.app.incomeSum
import tw.moneybook.app.pendingReimb
import java.time.LocalDate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// ───────────────────────── 共用：報銷收款也列在明細裡 ─────────────────────────

/** 欠款還款在 ReimbRecv.idx 的起始編號（和 [tw.moneybook.app.AppData.runningBalances] 的順序一致：報銷收款在前，欠款還款接在後面） */
const val DEBT_IDX = 499

/**
 * 明細裡的一筆報銷收款或欠款還款（只顯示，不能點）：t 是原本那筆支出，pay 是這次收到（或還出去）的款，
 * idx 是這筆支出所有收款裡的第幾筆；debt＝true 是欠款的還款（idx 從 [DEBT_IDX] 起算），錢是從帳戶出去的
 */
data class ReimbRecv(val t: Txn, val who: String, val pay: ReimbPay, val idx: Int, val debt: Boolean = false) {
    /** 在 runningBalances 裡的 key */
    val runKey: String get() = if (debt) "d${t.id}_${idx - DEBT_IDX}" else "p${t.id}_$idx"

    /** 讓帳戶 a 的餘額增加多少（欠款還款是負的） */
    fun signedFlow(a: Account): Long = if (debt) -pay.flowFor(a) else pay.flowFor(a)
}

/** 分帳結清實際轉的一筆錢（第 k 筆）：明細、帳戶明細各自一列 */
class SettleLine(val s: tw.moneybook.app.Settle, val k: Int) {
    val move: tw.moneybook.app.SettleMove get() = s.moves[k]
    /** 在 runningBalances 裡的 key */
    val runKey: String get() = "s${s.id}_$k"
}

/** 某個帳本（或所有帳本）的分帳結清，每一筆實際轉的錢一列 */
fun AppData.settleLines(bookId: Long? = null): List<SettleLine> =
    settles.filter { bookId == null || it.bookId == bookId }.flatMap { s -> s.moves.indices.map { SettleLine(s, it) } }

/** 明細列表的一列：一般記錄、一筆報銷收款，或分帳結清的一筆錢 */
class LedgerEntry(val txn: Txn?, val recv: ReimbRecv?, val settle: SettleLine? = null) {
    val day: Long get() = txn?.day ?: recv?.pay?.day ?: settle!!.s.day
    val time: Int get() = txn?.time ?: recv?.pay?.time ?: settle!!.s.time

    /** 排序用的時間：報銷收款、分帳結清沒有時間的當成當天最後（1440），和算餘額的順序一致 */
    val sortTime: Int get() = txn?.time ?: (recv?.pay?.time ?: settle!!.s.time).let { if (it >= 0) it else 1440 }

    /** 排序用的先後：一般記錄是 id×1000，收款接在它那筆記錄後面（依收款順序），分帳結清用它的 id，和算餘額的順序一致 */
    val sortOrder: Long get() = txn?.let { it.id * 1000 } ?: recv?.let { it.t.id * 1000 + 1 + it.idx } ?: (settle!!.s.id * 1000 + settle.k)

    /** 列表用的 key：一般記錄用 id，收款用「r 原記錄 id _ 第幾筆」，分帳結清用「s 結清 id _ 第幾筆」 */
    val key: Any get() = txn?.id ?: recv?.let { "r${it.t.id}_${it.idx}" } ?: "s${settle!!.s.id}_${settle.k}"
}

/** 這些記錄底下所有的報銷收款（分帳結清的不列：它另外照實際轉的錢列，見 settleLines） */
fun reimbRecvsOf(txns: List<Txn>, settleIds: Set<Long> = emptySet()): List<ReimbRecv> = txns.flatMap { t ->
    t.items.flatMap { i -> i.pays.map { pay -> i.who to pay } }.mapIndexed { idx, (who, pay) -> ReimbRecv(t, who, pay, idx) }
        .filter { it.pay.offsetId !in settleIds } +
        t.debtItems.flatMap { i -> i.pays.map { i.who to it } }
            .mapIndexed { k, (who, p) -> ReimbRecv(t, who, ReimbPay(p.day, p.accountId, p.amount, p.time, p.fxAmount, p.offsetId), DEBT_IDX + k, debt = true) }
            // 互相抵銷只列一次（報銷那一邊），欠款那一邊不重複列
            .filter { it.pay.offsetId == 0L }
}

/** 分帳結清的一筆錢（只顯示，不能點）：「分帳結清・收 Bob」，第二行寫一起結清了幾筆，金額是實際收到（＋）或付出（－）的錢 */
@Composable
fun SettleLineRow(d: AppData, line: SettleLine, showAcc: Boolean, balance: String? = null, balanceNegative: Boolean = false) {
    val cute = LocalCute.current
    val mv = line.move
    val acc = mv.accountId?.let { d.accMap[it] }
    val n = d.txns.sumOf { t -> t.items.count { i -> i.pays.any { it.offsetId == line.s.id } } + t.debtItems.count { i -> i.pays.any { it.offsetId == line.s.id } } }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble("img:acc_receipt", 2, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("分帳結清・" + (if (mv.receive) "收 " else "付給 ") + mv.who.ifBlank { "對方" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("一起結清 $n 筆報銷／欠款", style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1)
            if (showAcc && acc != null) {
                Row(Modifier.padding(top = 3.dp)) { TxnChip(acc.name) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text((if (mv.receive) "+" else "-") + formatMoney(mv.amount), color = if (mv.receive) cute.income else cute.expense, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (balance != null) {
                Text(balance, style = MaterialTheme.typography.labelSmall, color = if (balanceNegative) cute.expense else cute.sub, maxLines = 1)
            }
        }
    }
}

/** 報銷結案時少收的金額（不追了的部分） */
private fun reimbShort(t: Txn): Long = t.items.filter { it.closed }.sumOf { (it.amount - it.received).coerceAtLeast(0L) }

/** 名單的簡短寫法：一個人寫名字；多人時名字加起來不超過 8 個字就全列（A、B），太長就寫「3 人」 */
private fun namesLabel(names: List<String>): String {
    val list = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val joined = list.joinToString("、")
    return if (list.size <= 1 || joined.length <= 8) joined else "${list.size} 人"
}

/**
 * 欠款那一行小字（明細列）：還沒還時寫「我付 $100（現金）・欠 A、B 共 $200」；還了一部分寫「總欠・已還・還剩」；
 * 結清寫「欠 誰 $x・已還 $y」（多付、免掉時寫「實際還」）；同時有報銷再接「報銷 C、D 共 $300」。
 */
fun debtInfo(d: AppData, t: Txn): String {
    val items = t.debtItems
    val cur = t.debtCurrency
    val names = items.map { it.who }
    val many = names.map { it.trim() }.distinct().size > 1
    val who = namesLabel(names)
    val owe = if (cur.isNotEmpty()) formatFx(items.sumOf { it.totalUnits }, cur) else formatMoney(t.debtTotal)
    val back = items.sumOf { it.pays.sumOf { p -> p.amount } }
    val parts = ArrayList<String>()
    when {
        items.all { it.done } -> {
            parts.add("欠 $who ${if (many) "共 " else ""}$owe")
            parts.add((if (items.sumOf { it.effective } == t.debtTotal) "已還 " else "實際還 ") + formatMoney(back))
        }
        items.any { it.pays.isNotEmpty() } -> {
            parts.add("總欠 $owe")
            parts.add("已還 ${formatMoney(back)}")
            parts.add("還剩 ${formatMoney(t.debtRemainingTwd)}")
        }
        else -> {
            val acc = t.accountId?.let { d.accMap[it] }
            parts.add("我付 ${formatMoney(t.debtSelf)}" + (acc?.let { "（${it.name}）" } ?: ""))
            parts.add("欠 $who ${if (many) "共 " else ""}$owe")
        }
    }
    if (t.items.isNotEmpty()) {
        val rNames = t.items.map { it.who }
        val rMany = rNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().size > 1
        val rWho = namesLabel(rNames)
        parts.add("報銷" + (if (rWho.isNotEmpty()) " $rWho" else "") + " " + (if (rMany) "共 " else "") + formatMoney(t.items.sumOf { it.amount }))
    }
    return parts.joinToString("・")
}

/** 欠款對象的簡短寫法：一個人寫名字；多人時名字加起來不超過 8 個字就全列（A、B），太長就寫「3 人」 */
fun debtWhoLabel(t: Txn): String {
    val names = t.debtItems.map { it.who.trim() }.distinct()
    val joined = names.joinToString("、")
    return if (names.size <= 1 || joined.length <= 8) joined else "${names.size} 人"
}

/**
 * 一般記錄和報銷收款合在一起，由新到舊排。排序規則要和 [AppData.runningBalances]（算每一筆做完之後的餘額）
 * 剛好相反，這樣同一天裡由下往上讀，餘額才會一筆一筆加上去，最上面那筆是當天最後的餘額：
 * 日期 → 時間（一般記錄沒有時間的排最前面；報銷收款沒有時間的排當天最後）→ 記錄編號（收款接在它那筆記錄後面、依收款順序）。
 */
fun ledgerEntries(txns: List<Txn>, recvs: List<ReimbRecv>, settles: List<SettleLine> = emptyList()): List<LedgerEntry> =
    (txns.map { LedgerEntry(it, null) } + recvs.map { LedgerEntry(null, it) } + settles.map { LedgerEntry(null, null, it) })
        .sortedWith(
            compareByDescending<LedgerEntry> { it.day }
                .thenByDescending { it.sortTime }
                .thenByDescending { it.sortOrder },
        )

/**
 * 報銷收款的一列（和一般明細一樣大，但不能點、不能左滑）：「報銷收款・對象」，
 * 第二行是原本那筆的分類路徑和備註，金額是收到的錢。showAcc：顯示收進哪個帳戶；balance：帳戶明細裡那一筆之後的餘額。
 */
@Composable
fun ReimbRecvRow(d: AppData, r: ReimbRecv, showAcc: Boolean, amount: String, balance: String? = null, balanceNegative: Boolean = false) {
    val cute = LocalCute.current
    val acc = r.pay.accountId?.let { d.accMap[it] }
    val note = r.t.note.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatBubble("img:acc_receipt", 2, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val off = r.pay.offsetId != 0L
            Text((if (off) "互相抵銷" else if (r.debt) "還款" else "報銷收款") + if (r.who.isNotBlank()) "・${r.who}" else "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(catPath(d, r.t), note).filter { it.isNotEmpty() }.joinToString("・"),
                style = MaterialTheme.typography.bodySmall, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showAcc && acc != null) {
                Row(Modifier.padding(top = 3.dp)) { TxnChip(acc.name) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            // 互相抵銷沒有動帳戶：不寫正負號、用灰色
            if (r.pay.offsetId != 0L) Text(amount, color = cute.sub, fontWeight = FontWeight.SemiBold, maxLines = 1)
            else Text((if (r.debt) "-" else "+") + amount, color = if (r.debt) cute.expense else cute.income, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (balance != null) {
                Text(balance, style = MaterialTheme.typography.labelSmall, color = if (balanceNegative) cute.expense else cute.sub, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── 共用：一筆記錄 ─────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TxnRow(d: AppData, t: Txn, signedFor: Long? = null, balance: Long? = null, highlight: Boolean = false, onClick: () -> Unit) {
    val cute = LocalCute.current
    val cat = t.categoryId?.let { d.catMap[it] }
    val acc = t.accountId?.let { d.accMap[it] }
    val title: androidx.compose.ui.text.AnnotatedString
    val emoji: String
    val color: Int
    // 帳戶、分期、入帳月份和標籤一樣用小膠囊；第二行只放備註（外幣備註接在後面）；手續費、優惠放在金額上面
    val chips = ArrayList<String>()
    val fees = ArrayList<String>()
    // 在某個帳戶的明細裡（signedFor 有值）已經知道是哪個帳戶，不用再標帳戶
    // 有欠款的那一筆：帳戶寫在分配那行小字裡，不另外用膠囊
    val showAcc = acc != null && d.accounts.size > 1 && signedFor == null && !t.isDebt
    if (t.adjust) {
        title = androidx.compose.ui.text.AnnotatedString("餘額調整")
        emoji = "img:ui_ledger"
        color = 5
        if (showAcc) chips.add(acc!!.name)
    } else if (t.type == TxType.TRANSFER) {
        title = androidx.compose.ui.text.AnnotatedString("轉帳")
        emoji = "img:ui_transfer"
        color = 5
        val to = t.toAccountId?.let { d.accMap[it] }
        chips.add("${acc?.name ?: "?"} → ${to?.name ?: "?"}")
    } else {
        // 標題一律從大分類開始：「餐飲」或「餐飲 › 午餐」，不會一筆顯示大分類、一筆顯示子分類
        val parent = cat?.parentId?.let { d.catMap[it] }
        title = if (cat != null && parent != null) androidx.compose.ui.text.buildAnnotatedString {
            append(parent.name)
            withStyle(androidx.compose.ui.text.SpanStyle(color = cute.sub)) { append(" › ${cat.name}") }
        } else androidx.compose.ui.text.AnnotatedString(cat?.name ?: "未分類")
        emoji = cat?.emoji ?: "img:cat_box"
        color = cat?.color ?: 8
        if (showAcc) chips.add(acc!!.name)
    }
    if (t.instTotal > 1) chips.add("分期 ${t.instIndex}/${t.instTotal}")
    // 信用卡手動指定了入帳月份（和消費日期自動算的不同）才標出來
    if (acc != null && acc.type == tw.moneybook.app.AccountType.CARD && acc.statementDay in 1..31 && t.type != TxType.TRANSFER) {
        val eff = t.billMonthFor(acc)
        val auto = tw.moneybook.app.autoBillMonth(acc.statementDay, t.date)
        if (eff != null && eff != auto) chips.add("入帳 ${eff.monthValue} 月")
    }
    if (t.fee > 0) fees.add("手續費 ${formatMoney(t.fee)}")
    if (t.discount > 0) fees.add("優惠 -${formatMoney(t.discount)}")
    // 外幣：在外幣帳戶的明細裡主金額是外幣、備註放台幣；其他地方主金額是台幣、備註放外幣
    val signedAcc = signedFor?.let { d.accMap[it] }
    val inForeign = signedAcc?.isForeign == true
    val notes = ArrayList<String>()
    if (t.note.isNotBlank()) notes.add(t.note.lineSequence().first())
    d.fxNote(t, inForeign)?.let { notes.add(it) }
    // 欠款：列裡多一行寫分配（我付多少、欠誰多少…），帳戶明細不顯示
    val info: String? = if (t.isDebt && signedFor == null) debtInfo(d, t) else null
    // 首頁／日曆／搜尋：粗體是實際支出（總額 − 報銷），灰色小字是總額（兩者不一樣時才有）
    val actualMode = signedFor == null && t.type == TxType.EXPENSE && !t.adjust
    // 帳戶明細：粗體是這個帳戶動了多少，實際支出不一樣時寫在小字
    if (signedAcc != null && !inForeign && t.type == TxType.EXPENSE && !t.adjust) {
        val flow = kotlin.math.abs(t.flowFor(signedAcc))
        if (t.spent != flow) notes.add(0, "實際支出 ${formatMoney(t.spent)}")
    }

    // 高亮：剛出現時深一點（淺色 50%、深色 45%），約 1 秒後定格（淺色 30%、深色 24%）
    val hlAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight) {
            hlAnim.snapTo(if (cute.dark) 0.45f else 0.50f)
            hlAnim.animateTo(if (cute.dark) 0.24f else 0.30f, tween(1000))
        } else hlAnim.snapTo(0f)
    }
    val hlAlpha = hlAnim.value
    // 標題列的狀態標籤（待請款、欠款、報銷）：完整寫法和放不下時的短寫法（拿掉金額，金額在下面那行小字或右邊都看得到）
    val tags = ArrayList<StatusTag>()
    if (t.fxPending && t.isFxSpend) {
        // 外幣刷卡還沒請款：金額是預估的
        tags.add(StatusTag("待請款", "待請款", cute.ink, cute.accent2.copy(alpha = 0.4f)))
    }
    if (t.isDebt) {
        // 欠款：還沒付、還了一部分（剩多少）、已還清
        val open = t.debtItems.any { !it.done }
        val who = debtWhoLabel(t)
        // 結清時多付（多還了）或被免掉的差額
        val diff = t.debtItems.sumOf { it.effective } - t.debtTotal
        val col = if (open) cute.expense else cute.income
        tags.add(
            StatusTag(
                when {
                    !open -> "已還清・$who" + when {
                        diff > 0L -> " 多付 ${formatMoney(diff)}"
                        diff < 0L -> " 免 ${formatMoney(-diff)}"
                        else -> ""
                    }
                    t.debtItems.all { it.pays.isEmpty() } -> if (t.debtSelf > 0L) "欠 $who ${formatMoney(t.debtTotal)}" else "還沒付・欠 $who"
                    else -> "欠 $who 還剩 ${formatMoney(t.debtRemainingTwd)}"
                },
                if (open) "欠 $who" else "已還清",
                col, col.copy(alpha = 0.14f),
            )
        )
    }
    if (t.reimb != 0) {
        val col = if (t.reimb == 1) cute.expense else cute.income
        tags.add(
            StatusTag(
                (if (t.reimb == 1) "待報銷" else "已報銷") + when {
                    t.reimb == 1 && t.reimbOutstanding < t.reimbAmount -> " 剩${formatMoney(t.reimbOutstanding)}"
                    // 結案時收得比較多（回饋算收入）或比較少（不追了）
                    t.reimb == 2 && t.reimbGain > 0L -> " ${formatMoney(t.reimbAmount)}・回饋 +${formatMoney(t.reimbGain)}"
                    t.reimb == 2 && reimbShort(t) > 0L -> " ${formatMoney(t.reimbAmount)}・少收 ${formatMoney(reimbShort(t))}"
                    t.reimbAmount < t.paid -> " ${formatMoney(t.reimbAmount)}"
                    // 待報銷：全額也標出金額；已報銷全額不另外標
                    t.reimb == 1 -> " ${formatMoney(t.reimbAmount)}"
                    else -> ""
                },
                if (t.reimb == 1) "待報銷" else "已報銷",
                col, col.copy(alpha = 0.14f),
            )
        )
    }
    val (sign, c) = when {
        t.type == TxType.EXPENSE -> "-" to cute.expense
        t.type == TxType.INCOME -> "+" to cute.income
        signedFor != null && t.toAccountId == signedFor -> "+" to cute.income
        signedFor != null && t.accountId == signedFor -> "-" to cute.expense
        else -> "" to cute.sub
    }
    val shown = when {
        inForeign && signedAcc != null -> kotlin.math.abs(t.flowFor(signedAcc))
        t.type == TxType.TRANSFER && signedFor != null && t.accountId == signedFor -> t.amount + t.fee
        // 帳戶明細：這個帳戶實際扣了多少（有欠款時只有自己付的那一份）
        signedAcc != null && t.type == TxType.EXPENSE -> kotlin.math.abs(t.flowFor(signedAcc))
        actualMode -> t.spent
        else -> t.paid
    }
    val shownText = if (inForeign && signedAcc != null) formatFx(shown, signedAcc.currency) else formatMoney(shown)
    val gray = if (actualMode && t.recorded != shown) formatMoney(t.recorded).removePrefix(tw.moneybook.app.Money.twd) else null
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val typo = MaterialTheme.typography
    // 標題至少要留得下大分類（後面接「 …」）
    val titleHead = title.text.substringBefore(" ›")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 依這一列實際的寬度決定排法：0＝標籤寫完整；1＝標籤拿掉金額、灰色總額移到金額下面；2＝標籤移到下一行
        val level = with(density) {
            fun w(s: String, style: androidx.compose.ui.text.TextStyle): Float = measurer.measure(s, style, maxLines = 1).size.width.toFloat()
            fun tagW(s: String): Float = w(s, typo.labelSmall) + 12.dp.toPx() + 6.dp.toPx()
            // 列寬扣掉左右留白、圖示（40）、圖示後的間距（12）、金額前的間距（8）
            val inner = (maxWidth - (if (highlight) 22.dp else 12.dp) - 12.dp - 60.dp).toPx()
            // 大分類名稱很長時最多保留標題區的 45%，其餘照舊用「…」截掉，不然標籤永遠被推到下一行
            val titleNeed = minOf(w(titleHead, typo.bodyLarge), inner * 0.45f) + 28.dp.toPx()
            val amountW = w(sign + shownText, typo.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            val feesW = if (fees.isEmpty()) 0f else w(fees.joinToString("・"), typo.labelSmall)
            val right0 = maxOf(amountW + (gray?.let { w(it, typo.bodyMedium) + 8.dp.toPx() } ?: 0f), feesW)
            val right1 = maxOf(amountW, gray?.let { w("總額 $it", typo.labelSmall) } ?: 0f, feesW)
            when {
                titleNeed + tags.map { tagW(it.full) }.sum() <= inner - right0 -> 0
                tags.isEmpty() || titleNeed + tags.map { tagW(it.short) }.sum() <= inner - right1 -> 1
                else -> 2
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cute.card)
                // 剛記好（或剛改好）的那一筆：主色的底色、外框，左邊一條粗色條；剛出現時比較深，約 1 秒後淡成定格的樣子
                // （用主色不用輔助色：輔助色和卡片、背景太接近，會融進去；深色模式的主色是淺色，疊上去整張會變亮）
                .then(
                    if (highlight) {
                        val hl = MaterialTheme.colorScheme.primary
                        Modifier
                            .background(hl.copy(alpha = hlAlpha))
                            // 外框細一點、淡一點；色條離外框一段距離，兩者才分得出來
                            .border(2.dp, hl.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = hl,
                                    topLeft = Offset(11.dp.toPx(), 12.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height - 24.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                                )
                            }
                    } else Modifier
                )
                .clickable(onClick = onClick)
                // 高亮時左邊要放色條，內容往右讓一點
                .padding(start = if (highlight) 22.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatBubble(emoji, color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (level < 2) tags.forEach { StatusTagText(it, level == 0) }
                }
                if (notes.isNotEmpty()) {
                    Text(
                        notes.joinToString("・"),
                        style = MaterialTheme.typography.bodySmall,
                        color = cute.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (info != null) {
                    Text(info, style = MaterialTheme.typography.bodySmall, color = cute.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // 窄到標題列放不下：狀態標籤（短寫法）移到這裡，一排放不下再換行
                if (level == 2) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) { tags.forEach { StatusTagText(it, false, Modifier) } }
                }
                if (chips.isNotEmpty() || t.tags.isNotEmpty()) {
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 第一個膠囊（通常是帳戶，名字可能很長）可以縮短，後面的照原樣
                        chips.forEachIndexed { i, text -> TxnChip(text, if (i == 0) Modifier.weight(1f, fill = false) else Modifier) }
                        t.tags.take(3).forEach { tag -> TxnChip("#$tag") }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // 金額下面小字：這一筆做完之後這個帳戶的餘額（只有帳戶明細會帶 balance），和左邊的標題／備註一大一小
            Column(horizontalAlignment = Alignment.End) {
                if (fees.isNotEmpty()) {
                    Text(fees.joinToString("・"), style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (gray != null && level == 0) {
                        Text(gray, color = cute.sub.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                    }
                    // 實際支出是 0（全額報銷）也照寫，顏色淡一點
                    Text(sign + shownText, color = if (actualMode && shown == 0L) c.copy(alpha = 0.5f) else c, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                // 放不下時灰色總額移到粗體金額下面
                if (gray != null && level > 0) Text("總額 $gray", style = MaterialTheme.typography.labelSmall, color = cute.sub.copy(alpha = 0.8f), maxLines = 1)
                if ((t.fxPending && t.isFxSpend) || (t.isFxSpend && t.debtItems.any { !it.done })) Text("預估", style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1)
                if (balance != null) {
                    Text(
                        "餘額 ${signedAcc?.fmt(balance) ?: formatMoney(balance)}", style = MaterialTheme.typography.labelSmall,
                        color = if (balance < 0) cute.expense else cute.sub, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 明細列標題旁的狀態標籤：完整寫法、放不下時的短寫法、文字色、底色 */
private class StatusTag(val full: String, val short: String, val fg: Color, val bg: Color)

@Composable
private fun StatusTagText(tag: StatusTag, full: Boolean, modifier: Modifier = Modifier.padding(start = 6.dp)) {
    Text(
        if (full) tag.full else tag.short,
        style = MaterialTheme.typography.labelSmall,
        color = tag.fg,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(tag.bg).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 明細列的小膠囊（帳戶、分期、入帳月份、標籤共用） */
@Composable
private fun TxnChip(text: String, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(cute.soft).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun DayHeader(day: Long, list: List<Txn>) {
    val cute = LocalCute.current
    val exp = list.expenseSum()
    val inc = list.incomeSum()
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f))
        if (inc > 0) Text("收 ${formatMoney(inc)}  ", style = MaterialTheme.typography.labelMedium, color = cute.income)
        if (exp > 0) Text("支 ${formatMoney(exp)}", style = MaterialTheme.typography.labelMedium, color = cute.sub)
    }
}

/** 明細與日曆共用的切換鈕：目前是明細就顯示日曆圖示，點一下切過去；反之亦然 */
@Composable
fun ViewToggle(vm: MoneyViewModel) {
    val cute = LocalCute.current
    val cal = vm.homeCalendar
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(cute.card).clickable { vm.homeCalendar = !cal },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (cal) AppIcons.ListAlt else AppIcons.Calendar,
            contentDescription = if (cal) "切換成明細列表" else "切換成日曆",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ───────────────────────── 首頁（明細） ─────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: MoneyViewModel,
    onEdit: (Long) -> Unit,
    onManageBooks: () -> Unit,
    onReimb: () -> Unit,
    onFxPending: () -> Unit = {},
    onDebt: () -> Unit = {},
) {
    val d = vm.data
    val cute = LocalCute.current
    val month = vm.month
    val list = d.bookTxns.inMonth(month)
    val income = list.incomeSum()
    val expense = list.expenseSum()
    val book = d.currentBook
    val mascot = d.prefs.mascot
    var showBooks by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    // 前面固定的項目：書本列、吉祥物（可以關）、預算、本月摘要
    val homeBase = 3 + if (mascot != "none") 1 else 0
    // 這個月收到的報銷款也列在明細裡（只顯示，不算進收支），和一般記錄一起照日期時間排
    val settleIds = d.settles.map { it.id }.toSet()
    val homeRecvs = reimbRecvsOf(d.bookTxns, settleIds).filter { java.time.YearMonth.from(LocalDate.ofEpochDay(it.pay.day)) == month }
    val homeSettles = d.settleLines(d.currentBook.id).filter { java.time.YearMonth.from(LocalDate.ofEpochDay(it.s.day)) == month }
    val homeEntries = ledgerEntries(list, homeRecvs, homeSettles)
    val homeGroups = homeEntries.groupBy { it.day }
    val homeHl = CenterOnSaved(vm, vm.homeListState, homeBase + 1, month, switchTo = { t ->
        // 記在別的月份（例如現在看 10 月，記了 8/20）：切到那個月
        val m = java.time.YearMonth.from(t.date)
        (m != vm.month).also { if (it) vm.month = m }
    }) { txnItemIndex(homeGroups, homeBase, it) }
    LazyColumn(
        state = vm.homeListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clip(CircleShape).background(cute.card).clickable { showBooks = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconGlyph(book.emoji, 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(book.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
                    Text(" ▾", color = cute.sub)
                }
                Spacer(Modifier.weight(1f))
                ViewToggle(vm)
                Spacer(Modifier.width(8.dp))
                MonthSwitcher(month, { vm.month = it })
            }
        }

        if (mascot != "none") {
            item {
                val mood = moodOf(d)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Mascot(mascot, mood, Modifier.size(68.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp))
                            .background(cute.card)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        val name = d.prefs.mascotName.ifBlank { mascotDefaultName(mascot) }
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(mascotLine(mood, d), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val budget = book.budgetFor(month)
        item {
            if (budget > 0) {
                val frac = expense.toFloat() / budget.toFloat()
                val color = budgetColor(frac)
                val level = when {
                    frac > 1f -> 2
                    frac >= 0.8f -> 1
                    else -> 0
                }
                // 越接近上限卡片越醒目；超支時邊框會輕輕閃
                val pulse = rememberInfiniteTransition(label = "budget")
                val glow by pulse.animateFloat(
                    initialValue = 0.35f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow",
                )
                val bg = when (level) {
                    2 -> cute.expense.copy(alpha = 0.16f)
                    1 -> cute.accent2.copy(alpha = 0.22f)
                    else -> cute.card
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.card).background(bg)
                        .then(
                            if (level == 2) Modifier.border(2.dp, cute.expense.copy(alpha = glow), RoundedCornerShape(20.dp))
                            else if (level == 1) Modifier.border(1.5.dp, cute.accent2, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { showBudget = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalContentColor provides (if (level == 2) cute.expense else cute.ink)) {
                            IconGlyph(when (level) { 2 -> "vec:siren"; 1 -> "vec:warning"; else -> "vec:target" }, 16.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (level) {
                                2 -> "已經超出預算了！"
                                1 -> "預算快用完了"
                                else -> "${month.monthValue} 月預算"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when (level) { 2 -> cute.expense; else -> cute.ink },
                            fontWeight = if (level > 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(frac * 100).toInt()}%",
                            style = if (level > 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    RatioBar(frac, color, thick = level > 0)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (frac > 1f) "超出 ${formatMoney(expense - budget)}（預算 ${formatMoney(budget)}）"
                        else "已花 ${formatMoney(expense)}・還剩 ${formatMoney(budget - expense)}（預算 ${formatMoney(budget)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (level == 2) cute.expense else cute.sub,
                        fontWeight = if (level == 2) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            } else {
                Row(
                    Modifier.clip(CircleShape).clickable { showBudget = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { IconGlyph("vec:target", 16.sp) }
                    Spacer(Modifier.width(6.dp))
                    Text("設定每月預算", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("本月支出", style = MaterialTheme.typography.labelLarge, color = cute.sub)
                Text(formatMoney(expense), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("收入", formatMoney(income), cute.income, Modifier.weight(1f))
                    MiniStat("結餘", formatMoney(income - expense), cute.ink, Modifier.weight(1f))
                }
                val pending = d.bookTxns.pendingReimb()
                val fxPend = d.fxPendingList()
                val debts = d.debtGroups()
                if (pending.isNotEmpty() || fxPend.isNotEmpty() || debts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    // 兩顆小標籤放不下時換行，每顆自己不折行
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 報銷（別人欠我）和欠款（我欠別人）合成一顆，寫淨額
                        if (pending.isNotEmpty() || debts.isNotEmpty()) {
                            val net = pending.sumOf { it.reimbOutstanding } - debts.sumOf { it.remainingTwd }
                            Row(
                                Modifier.clip(CircleShape).background(cute.soft).clickable(onClick = onReimb)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconGlyph("vec:receipt", 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "報銷／欠款 " + when {
                                        net > 0L -> "淨收 ${formatMoney(net)}"
                                        net < 0L -> "淨欠 ${formatMoney(-net)}"
                                        else -> "兩清"
                                    } + "  ›",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary, maxLines = 1,
                                )
                            }
                        }
                        // 外幣刷卡還沒請款的提醒
                        if (fxPend.isNotEmpty()) {
                            Row(
                                Modifier.clip(CircleShape).background(cute.soft).clickable(onClick = onFxPending)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconGlyph("vec:coin_pending", 18.sp)
                                Spacer(Modifier.width(6.dp))
                                // 顏色和旁邊的「待報銷」一樣
                                Text("待請款外幣 ${fxPend.size} 筆  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        if (homeEntries.isEmpty()) {
            item { EmptyHint(mascot, "這個月還沒有記錄\n按下方的 ＋ 記下第一筆吧") }
        }
        homeGroups.forEach { (day, dayList) ->
            item(key = "d$day") { DayHeader(day, dayList.mapNotNull { it.txn }) }
            items(dayList, key = { it.key }) { e ->
                val t = e.txn
                if (t != null) {
                    SwipeRow(onDelete = { vm.deleteWithUndo(t.id) }) { TxnRow(d, t, highlight = t.id == homeHl) { onEdit(t.id) } }
                } else if (e.settle != null) {
                    SettleLineRow(d, e.settle, showAcc = d.accounts.size > 1)
                } else {
                    val r = e.recv!!
                    ReimbRecvRow(d, r, showAcc = d.accounts.size > 1, amount = formatMoney(r.pay.amount))
                }
            }
        }
    }

    if (showBudget) BudgetDialog(vm, month) { showBudget = false }

    if (showBooks) {
        AlertDialog(
            onDismissRequest = { showBooks = false },
            title = { Text("切換帳本") },
            text = {
                // 帳本多的時候要能往下滑
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.books.forEach { b ->
                        val on = b.id == book.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.switchBook(b.id); showBooks = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconGlyph(b.emoji, 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(b.name, modifier = Modifier.weight(1f))
                            if (on) Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBooks = false; onManageBooks() }) { Text("管理帳本") } },
            dismissButton = { TextButton(onClick = { showBooks = false }) { Text("關閉") } },
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(LocalCute.current.soft.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalCute.current.sub)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

// ───────────────────────── 日曆 ─────────────────────────

@Composable
fun CalendarScreen(vm: MoneyViewModel, onEdit: (Long) -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val month = vm.month
    val list = d.bookTxns.inMonth(month)
    val byDay = list.groupBy { it.day }
    val today = LocalDate.now()
    var selected by vm::calSelected
    LaunchedEffect(month) {
        val sel = LocalDate.ofEpochDay(selected)
        if (sel.year != month.year || sel.monthValue != month.monthValue) {
            selected = if (today.year == month.year && today.monthValue == month.monthValue) today.toEpochDay()
            else month.atDay(1).toEpochDay()
        }
    }
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // 週日開頭
    val days = month.lengthOfMonth()
    val cells = lead + days
    val weeks = (cells + 6) / 7
    val selList = byDay[selected] ?: emptyList()
    // 選到的那天收到的報銷款也列出來（只顯示）
    val selRecvs = reimbRecvsOf(d.bookTxns, d.settles.map { it.id }.toSet()).filter { it.pay.day == selected }
    val selEntries = ledgerEntries(selList, selRecvs, d.settleLines(d.currentBook.id).filter { it.s.day == selected })
    // 前面固定 4 項（標題、本月收支、日曆、選到的日期）＋ 1 項（沒記錄的提示或小技巧，只有收款時兩個都沒有），選到的那天的明細接在後面
    val calFirst = 4 + if (selEntries.isEmpty() || selList.isNotEmpty()) 1 else 0
    val calHl = CenterOnSaved(vm, vm.calListState, calFirst, month to selected, switchTo = { t ->
        // 記在別的月份或別天：切到那一天（月份跟著切）
        val m = java.time.YearMonth.from(t.date)
        val changed = m != vm.month || t.day != vm.calSelected
        if (changed) {
            vm.calSelected = t.day
            vm.month = m
        }
        changed
    }) { id -> selEntries.indexOfFirst { it.txn?.id == id }.let { k -> if (k >= 0) calFirst + k else -1 } }

    // 長按拖曳：記錄每個日期格子的位置
    val cellRects = remember(month) { HashMap<Long, Rect>() }
    var dragging by remember { mutableStateOf<Txn?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var boxPos by remember { mutableStateOf(Offset.Zero) }
    val hoverDay: Long? = if (dragging != null) cellRects.entries.firstOrNull { it.value.contains(dragPos) }?.key else null

    Box(Modifier.fillMaxSize().onGloballyPositioned { boxPos = it.positionInRoot() }) {
    LazyColumn(
        state = vm.calListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("日曆", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                ViewToggle(vm)
                Spacer(Modifier.width(8.dp))
                MonthSwitcher(month, { vm.month = it })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("本月收入 ${formatMoney(list.incomeSum())}", color = cute.income, style = MaterialTheme.typography.labelLarge)
                Text("本月支出 ${formatMoney(list.expenseSum())}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
            }
        }
        item {
            CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                        Text(
                            w,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = cute.sub,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = w * 7 + c
                            val dayNum = idx - lead + 1
                            if (dayNum < 1 || dayNum > days) {
                                Spacer(Modifier.weight(1f).height(64.dp))
                            } else {
                                val date = month.atDay(dayNum)
                                val ep = date.toEpochDay()
                                val dl = byDay[ep] ?: emptyList()
                                val exp = dl.expenseSum()
                                val inc = dl.incomeSum()
                                val isSel = ep == selected
                                val isToday = date == today
                                val isHover = hoverDay == ep
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .onGloballyPositioned { cellRects[ep] = it.boundsInRoot() }
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                isHover -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                isSel -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                            else Modifier
                                        )
                                        .clickable { selected = ep },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else cute.ink,
                                    )
                                    if (inc > 0) Text("+" + formatShort(inc), fontSize = 9.sp, lineHeight = 11.sp, color = cute.income, maxLines = 1)
                                    if (exp > 0) Text("-" + formatShort(exp), fontSize = 9.sp, lineHeight = 11.sp, color = cute.expense, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val i = selList.incomeSum()
                val e = selList.expenseSum()
                if (i > 0) Text("收 ${formatMoney(i)}  ", color = cute.income, style = MaterialTheme.typography.labelLarge)
                if (e > 0) Text("支 ${formatMoney(e)}", color = cute.expense, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (selEntries.isEmpty()) {
            item {
                Text(
                    "這天沒有記錄",
                    color = cute.sub,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (selList.isNotEmpty()) {
            item {
                Text(
                    "小技巧：長按記錄可以拖到其他日期，左滑可以刪除",
                    style = MaterialTheme.typography.labelSmall,
                    color = cute.sub,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(selEntries, key = { it.key }) { e ->
          val t = e.txn
          if (t == null && e.settle != null) {
            SettleLineRow(d, e.settle, showAcc = d.accounts.size > 1)
          } else if (t == null) {
            val r = e.recv!!
            ReimbRecvRow(d, r, showAcc = d.accounts.size > 1, amount = formatMoney(r.pay.amount))
          } else {
            SwipeRow(
                onDelete = { vm.deleteWithUndo(t.id) },
                drag = DragCallbacks(
                    onStart = { p -> dragging = t; dragPos = p },
                    onMove = { p -> dragPos = p },
                    onEnd = {
                        val target = cellRects.entries.firstOrNull { it.value.contains(dragPos) }?.key
                        if (target != null && target != t.day) {
                            vm.moveTxnDay(t.id, target)
                            selected = target
                        }
                        dragging = null
                    },
                    onCancel = { dragging = null },
                ),
            ) { TxnRow(d, t, highlight = t.id == calHl) { onEdit(t.id) } }
          }
        }
    }
        // 拖曳中跟著手指的小卡
        val dt = dragging
        if (dt != null) {
            val c = dt.categoryId?.let { d.catMap[it] }
            Row(
                Modifier
                    .offset { IntOffset((dragPos.x - boxPos.x).roundToInt() - 60, (dragPos.y - boxPos.y).roundToInt() - 90) }
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${iconLabel(c?.emoji ?: "img:ui_transfer", c?.name ?: "轉帳")} ${formatMoney(dt.paid)}" +
                        (hoverDay?.let { "  →  ${LocalDate.ofEpochDay(it).monthValue}/${LocalDate.ofEpochDay(it).dayOfMonth}" } ?: ""),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
