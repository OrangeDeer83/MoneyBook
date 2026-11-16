package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * 刷卡通知第二步：讀出來的消費先放在「待確認」，要使用者按「記」才記帳。
 * 待確認存在手機自己的檔案（不跟著備份，見 notify/CardPend.kt），放 14 天。
 * 卡片在顯示時才對（resolveCard）：之後補了末四碼、選過卡，還沒處理的會跟著變。
 */

/** 一則通知的來源（同一筆兩則通知合併時會有兩個） */
data class NotiSrc(val notiId: Long, val pkg: String, val label: String, val title: String, val last4: String)

/** 待確認的狀態 */
object PendState {
    /** 等使用者確認 */
    const val WAIT = 0
    /** 已記（txnId 是記成的那一筆） */
    const val DONE = 1
    /** 不是消費 */
    const val NOT = 2
    /** 已經有這一筆（txnId 是使用者自己記的那筆，或訂閱記的） */
    const val HAD = 3
}

data class CardPend(
    val id: Long,
    val src: List<NotiSrc>,
    /** 台幣是元；外幣是最小單位 */
    val amount: Long,
    val cur: String,
    val merchant: String,
    val merchantSure: Boolean,
    val wallet: String,
    val day: Long,
    val minute: Int,
    /** 收到通知的時間（毫秒）：合併、放幾天看它 */
    val time: Long,
    /** 使用者在這一筆選的卡（沒勾「記住」時）；null＝照規則對 */
    val cardId: Long? = null,
    val state: Int = PendState.WAIT,
    /** 記成的那一筆、或認走的那一筆 */
    val txnId: Long = 0L,
    /** 使用者按了「不是這個訂閱」：不再對訂閱 */
    val notSub: Boolean = false,
) {
    val last4: String get() = src.firstOrNull { it.last4.isNotEmpty() }?.last4 ?: ""
    val label: String get() = src.firstOrNull()?.label ?: ""
}

/** 待確認放幾天 */
const val PEND_KEEP_DAYS = 14

/** 兩則通知算同一筆：收到的時間差幾分鐘以內 */
const val PEND_MERGE_MINUTES = 10

/** 名稱比對用：轉小寫、去掉空白 */
private fun nameKey(s: String) = normalizeNoti(s).lowercase().filter { !it.isWhitespace() }

/**
 * 這則通知是哪張卡（找不到回傳 null，要問使用者）：
 * 1. 有末四碼：先在這個 App 設定的卡裡找，再到所有信用卡找（剛好一張才算）
 * 2. 以前「記住」的：通知標題 → 卡（Samsung Wallet 這種標題是卡片名稱的）
 * 3. 標題和卡名一樣（去掉空白比）
 * 4. 這個 App 只設了一張卡
 * 5. 沒寫末四碼：這個 App 的預設卡
 */
fun AppData.matchCard(s: NotiSrc): Long? {
    val cards = accounts.filter { it.type == AccountType.CARD && !it.hidden }
    val app = notiApps.firstOrNull { it.pkg == s.pkg }
    val appCards = app?.cardIds?.mapNotNull { id -> cards.firstOrNull { it.id == id } }.orEmpty()
    if (s.last4.isNotEmpty()) {
        appCards.firstOrNull { c -> c.cardNos.any { it.digits == s.last4 } }?.let { return it.id }
        cards.filter { c -> c.cardNos.any { it.digits == s.last4 } }.singleOrNull()?.let { return it.id }
    }
    val tk = nameKey(s.title)
    if (tk.isNotEmpty()) {
        app?.titles?.get(tk)?.takeIf { id -> cards.any { it.id == id } }?.let { return it }
        cards.filter { nameKey(it.name) == tk }.singleOrNull()?.let { return it.id }
    }
    if (appCards.size == 1) return appCards[0].id
    if (s.last4.isEmpty()) app?.defaultCard?.takeIf { id -> cards.any { it.id == id } }?.let { return it }
    return null
}

/** 這筆待確認是哪張卡：使用者選過的優先，再照規則對每一則來源 */
fun AppData.resolveCard(p: CardPend): Long? =
    p.cardId?.takeIf { id -> accounts.any { it.id == id } } ?: p.src.firstNotNullOfOrNull { matchCard(it) }

/** 「記住」用的標題 key（存在 NotiApp.titles） */
fun titleKey(title: String) = nameKey(title)

/**
 * 加進一筆新讀到的消費：10 分鐘內、不同 App、金額幣別一樣、卡一樣（或其中一邊還對不到卡）的算同一筆，合併：
 * 商店用句型讀到的那則（都不是就用有寫的那則），來源兩則都留著（對卡用）。
 * 已經處理過的那筆也會合併（後來的那則就跟著算處理過了）。超過 14 天的丟掉。
 */
fun addPend(list: List<CardPend>, n: CardPend, d: AppData, now: Long): List<CardPend> {
    val nCard = d.resolveCard(n)
    val partner = list.firstOrNull { p ->
        kotlin.math.abs(p.time - n.time) <= PEND_MERGE_MINUTES * 60_000L && p.amount == n.amount && p.cur == n.cur &&
            p.src.none { s -> n.src.any { it.pkg == s.pkg } } &&
            d.resolveCard(p).let { pc -> pc == null || nCard == null || pc == nCard }
    }
    val out = if (partner == null) listOf(n) + list else list.map { p ->
        if (p.id != partner.id) p else {
            val useNew = (n.merchantSure && !p.merchantSure) || (p.merchant.isEmpty() && n.merchant.isNotEmpty())
            p.copy(
                src = p.src + n.src,
                merchant = if (useNew) n.merchant else p.merchant,
                merchantSure = if (useNew) n.merchantSure else p.merchantSure,
                wallet = p.wallet.ifEmpty { n.wallet },
                // 時間用有寫幾點的那則
                minute = if (p.minute < 0) n.minute else p.minute,
            )
        }
    }
    return prunePend(out, now)
}

fun prunePend(list: List<CardPend>, now: Long): List<CardPend> =
    list.filter { now - it.time < PEND_KEEP_DAYS * 86_400_000L }.sortedByDescending { it.time }

/** 讀到的通知 → 一筆待確認（不是消費的回傳 null） */
fun pendOf(n: CapturedNoti, r: NotiRead): CardPend? =
    if (r.kind != NotiKind.SPEND) null
    else CardPend(
        id = n.id, src = listOf(NotiSrc(n.id, n.pkg, n.label, n.title, r.last4)), amount = r.amount, cur = r.cur,
        merchant = r.merchant, merchantSure = r.merchantSure, wallet = r.wallet, day = r.day, minute = r.minute, time = n.time,
    )

object CardPendCodec {
    fun encode(list: List<CardPend>): String = JSONArray().apply {
        list.forEach { p ->
            put(
                JSONObject().put("id", p.id).put("amount", p.amount).put("cur", p.cur).put("merchant", p.merchant).put("sure", p.merchantSure)
                    .put("wallet", p.wallet).put("day", p.day).put("minute", p.minute).put("time", p.time)
                    .put("cardId", p.cardId ?: JSONObject.NULL).put("state", p.state).put("txnId", p.txnId)
                    .apply { if (p.notSub) put("notSub", true) }
                    .put("src", JSONArray().apply {
                        p.src.forEach { s -> put(JSONObject().put("n", s.notiId).put("pkg", s.pkg).put("label", s.label).put("title", s.title).put("last4", s.last4)) }
                    }),
            )
        }
    }.toString()

    fun decode(text: String): List<CardPend> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val sa = o.optJSONArray("src") ?: JSONArray()
            val src = (0 until sa.length()).mapNotNull { j ->
                val s = sa.optJSONObject(j) ?: return@mapNotNull null
                NotiSrc(s.optLong("n"), s.optString("pkg"), s.optString("label"), s.optString("title"), s.optString("last4"))
            }
            if (src.isEmpty()) return@mapNotNull null
            CardPend(
                o.optLong("id"), src, o.optLong("amount"), o.optString("cur"), o.optString("merchant"), o.optBoolean("sure"),
                o.optString("wallet"), o.optLong("day"), o.optInt("minute", -1), o.optLong("time"),
                if (o.isNull("cardId")) null else o.optLong("cardId").takeIf { it > 0L },
                o.optInt("state", PendState.WAIT), o.optLong("txnId", 0L), o.optBoolean("notSub", false),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ───────── 首頁顯示時才算的：自動帶分類、可能已經記過 ─────────

/** 刷卡記的那一筆加的標籤 */
const val CARD_NOTI_TAG = "刷卡通知"

/**
 * 商店名稱整理成比對用的字串：全形轉半形、轉小寫，刪掉括號和裡面的字、
 * 英文最後一段有數字的（付款代碼，例如 SPOTIFY P1F2），再刪掉空白和標點。
 */
fun shopKey(s: String): String {
    var t = normalizeNoti(s).lowercase()
    t = t.replace(Regex("""[(（\[【「][^)）\]】」]*[)）\]】」]"""), " ")
    val parts = t.trim().split(' ').filter { it.isNotEmpty() }
    if (parts.size > 1 && parts.last().any { it.isDigit() } && parts.last().any { it.isLetter() && !(it in '㐀'..'鿿') }) {
        t = parts.dropLast(1).joinToString(" ")
    }
    return t.filter { it.isLetterOrDigit() }
}

private val BRANCH = listOf("門市", "分店", "營業所", "店")

/**
 * 兩個整理過的名稱算不算同一家（都至少 2 個字）：一樣；一個是另一個的開頭
 * （「統一超商」和「統一超商信義門市」、被截斷的商店名稱、備註「統一超商買咖啡」）；
 * 或兩個都以門市／分店／店結尾、前 4 個字一樣（「統一超商信義門市」和「統一超商松仁門市」）。
 */
fun sameShop(a: String, b: String): Boolean {
    if (a.length < 2 || b.length < 2) return false
    if (a.startsWith(b) || b.startsWith(a)) return true
    return a.length >= 4 && b.length >= 4 && a.take(4) == b.take(4) && BRANCH.any { a.endsWith(it) } && BRANCH.any { b.endsWith(it) }
}

/**
 * 自動帶的分類（找不到回傳 null，要使用者選）：
 * 1. 同一家商店上次記成什麼：記錄從新到舊，第一筆「支出、有分類、不是餘額調整、備註和商店是同一家」的分類
 * 2. 這張卡最常記的分類：這張卡最近 30 筆有分類的支出（不算餘額調整、#訂閱），至少 5 筆，最多的那個分類超過一半才算
 */
fun AppData.guessCat(merchant: String, cardId: Long?): Long? {
    fun ok(id: Long?) = id?.let { catMap[it] }?.takeIf { it.kind == TxType.EXPENSE }?.id
    val key = shopKey(merchant)
    if (key.length >= 2) {
        // txns 已經是新到舊（同一天的照時間）
        for (t in txns) {
            if (t.type != TxType.EXPENSE || t.adjust || t.categoryId == null) continue
            if (sameShop(key, shopKey(t.note.lineSequence().firstOrNull() ?: ""))) ok(t.categoryId)?.let { return it }
        }
    }
    if (cardId == null) return null
    val recent = txns.asSequence()
        .filter { it.accountId == cardId && it.type == TxType.EXPENSE && !it.adjust && it.categoryId != null && Sub.TAG !in it.tags }
        .take(30).toList()
    if (recent.size < 5) return null
    val (top, n) = recent.groupingBy { it.categoryId!! }.eachCount().maxByOrNull { it.value } ?: return null
    return if (n * 2 > recent.size) ok(top) else null
}

/** 待確認裡已經記過、認走的那些記錄（不會再被當成「可能已經記過」） */
fun claimedTxns(list: List<CardPend>): Set<Long> = list.filter { it.state == PendState.DONE || it.state == PendState.HAD }.map { it.txnId }.toSet()

/**
 * 可能已經記過：那一天、那張卡有一筆實付金額一樣的支出（外幣比外幣金額），不是餘額調整、還沒被別的待確認認走。
 */
fun AppData.possibleHad(p: CardPend, cardId: Long?, claimed: Set<Long>): Txn? {
    if (cardId == null) return null
    val acc = accMap[cardId]
    return txns.firstOrNull { t ->
        t.accountId == cardId && t.type == TxType.EXPENSE && !t.adjust && t.day == p.day && t.id !in claimed &&
            when {
                p.cur.isEmpty() -> t.recorded == p.amount
                acc?.isForeign == true && acc.currency == p.cur -> t.fxAmount == p.amount
                else -> t.fxSpendCur == p.cur && t.fxSpendAmount == p.amount
            }
    }
}

/** 記成那一筆的備註：商店（有錢包加在後面）；都沒有就空白 */
fun pendNote(p: CardPend): String = when {
    p.merchant.isNotEmpty() && p.wallet.isNotEmpty() -> "${p.merchant}（${p.wallet}）"
    else -> p.merchant.ifEmpty { p.wallet }
}

/**
 * 待確認記成的一筆支出（記在 bookId 這本帳本）。外幣：記在同幣別的外幣卡就是外幣金額；
 * 記在台幣卡是「外幣消費」，twd 是預估的台幣（pending＝待請款）。外幣沒有給 twd 回傳 null。
 */
fun AppData.pendTxn(p: CardPend, id: Long, bookId: Long, cardId: Long, catId: Long?, twd: Long? = null, pending: Boolean = true): Txn? {
    val acc = accMap[cardId] ?: return null
    val base = Txn(
        id = id, bookId = bookId, type = TxType.EXPENSE, amount = p.amount, categoryId = catId, accountId = cardId, toAccountId = null,
        day = p.day, note = pendNote(p), tags = listOf(CARD_NOTI_TAG), time = p.minute,
    )
    if (p.cur.isEmpty()) return base
    val dec = Currencies.of(p.cur).decimals
    val est = twd ?: rateOf(p.cur)?.let { fxToTwd(p.amount, dec, it) } ?: return null
    return if (acc.isForeign && acc.currency == p.cur) base.copy(amount = est, fxAmount = p.amount)
    else base.copy(amount = est, fxSpendAmount = p.amount, fxSpendCur = p.cur, fxPending = pending)
}

// ───────── 刷卡通知對到訂閱（不會跟訂閱提醒重複） ─────────

/** 刷卡和訂閱的扣款日差幾天以內算同一次 */
const val SUB_MATCH_DAYS = 3

/** 外幣訂閱、通知寫台幣：用匯率算，差多少以內才問「可能是訂閱」 */
const val SUB_FX_TOLERANCE = 0.10

/**
 * 一筆刷卡對到的訂閱：day＝訂閱的哪一次扣款；exact＝金額一樣（直接當成訂閱），
 * false＝外幣訂閱、通知寫台幣，金額差 10% 以內（要問）；done＝那一次已經記過或略過了。
 */
data class SubMatch(val sub: Sub, val day: Long, val exact: Boolean, val done: Boolean)

/**
 * 這筆刷卡是不是某個訂閱的扣款：同一張卡、沒暫停的訂閱，在刷卡日期前後 3 天內有扣款日，金額一樣
 * （外幣訂閱、通知寫台幣時用匯率算，差 10% 以內）。有還沒處理的那一次優先，再來是日期近的。
 */
fun AppData.pendSub(p: CardPend, cardId: Long?): SubMatch? {
    if (cardId == null || p.notSub) return null
    val out = ArrayList<SubMatch>()
    for (s in subs) {
        if (s.paused || s.accountId != cardId) continue
        val exact = when {
            !s.isForeign && p.cur.isEmpty() -> s.amount == p.amount
            s.isForeign && p.cur == s.cur -> s.amount == p.amount
            else -> false
        }
        val near = !exact && s.isForeign && p.cur.isEmpty() && (subTwd(s)?.let { est -> est > 0 && kotlin.math.abs(p.amount - est) <= est * SUB_FX_TOLERANCE } ?: false)
        if (!exact && !near) continue
        s.dates(p.day + SUB_MATCH_DAYS).filter { it >= p.day - SUB_MATCH_DAYS }.forEach { e -> out.add(SubMatch(s, e, exact, s.isDone(e))) }
    }
    return out.minWithOrNull(compareBy<SubMatch>({ it.done }, { kotlin.math.abs(it.day - p.day) }, { !it.exact }))
}

/** 首頁的訂閱提醒不再列這些（已經在刷卡待確認裡出現、金額一樣的）：(訂閱 id, 扣款日) */
fun AppData.subsShownInPend(list: List<CardPend>): Set<Pair<Long, Long>> =
    list.filter { it.state == PendState.WAIT }.mapNotNull { p -> pendSub(p, resolveCard(p))?.takeIf { it.exact && !it.done } }
        .map { it.sub.id to it.day }.toSet()

/** 對到已經處理過的那次訂閱：首頁不出現（「收到的通知」裡標「已記（訂閱）」） */
fun AppData.pendSubDone(p: CardPend): SubMatch? = pendSub(p, resolveCard(p))?.takeIf { it.done }

/**
 * 刷卡記成那次訂閱：名稱、分類、備註、標籤照訂閱（多加 #刷卡通知），日期時間照通知。
 * 台幣：金額照通知；外幣訂閱、通知寫台幣：記成已請款的外幣消費（台幣照通知）；
 * 外幣對外幣：記在外幣卡是外幣金額，記在台幣卡是待請款的外幣消費，twd 沒給就用目前匯率（沒有回傳 null）。
 */
fun AppData.pendSubTxn(p: CardPend, m: SubMatch, id: Long, twd: Long? = null, pending: Boolean = true): Txn? {
    val s = m.sub
    val t = when {
        !s.isForeign -> subTxn(s, p.day, id, p.amount, false).copy(amount = p.amount)
        p.cur.isEmpty() -> subTxn(s, p.day, id, p.amount, false)
        else -> subTxn(s, p.day, id, twd ?: subTwd(s) ?: return null, pending)
    }
    return t.copy(time = p.minute, tags = (t.tags + CARD_NOTI_TAG).distinct())
}

// ───────── 「收到的通知」旁邊的標籤 ─────────

/** 收到的通知讀得怎麼樣：GOOD＝讀對了／已記（綠）、PART＝少了商店（橘）、OFF＝不是消費、還不會讀、已經記過（灰） */
enum class NotiTagKind { GOOD, PART, OFF }

/** 收到的一則通知旁邊的標籤：有放進待確認的看那筆的狀態，沒有的看讀出來的結果 */
fun AppData.notiTag(n: CapturedNoti, pends: List<CardPend>): Pair<NotiTagKind, String> {
    val p = pends.firstOrNull { pp -> pp.src.any { it.notiId == n.id } }
    if (p != null) {
        return when (p.state) {
            PendState.DONE -> NotiTagKind.GOOD to "已記"
            PendState.HAD -> NotiTagKind.OFF to "已經記過"
            PendState.NOT -> NotiTagKind.OFF to "不是消費"
            else -> pendSubDone(p)?.let { NotiTagKind.GOOD to "已記（訂閱：${it.sub.name}）" }
                ?: if (p.merchant.isEmpty()) NotiTagKind.PART to "少了商店" else NotiTagKind.GOOD to "讀對了"
        }
    }
    val r = parseCardNoti(n.title, n.text, n.time)
    return when (r.kind) {
        NotiKind.SPEND -> if (r.merchant.isEmpty()) NotiTagKind.PART to "少了商店" else NotiTagKind.GOOD to "讀對了"
        NotiKind.NO_AMOUNT -> NotiTagKind.OFF to "不是消費"
        NotiKind.OTHER -> NotiTagKind.OFF to "還不會讀"
    }
}
