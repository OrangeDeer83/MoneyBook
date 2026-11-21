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

/** 待確認是哪一種：一般刷卡、退款、預先授權 */
object PendKind {
    const val SPEND = 0
    const val REFUND = 1
    const val PREAUTH = 2
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
    /** 一般刷卡、退款、預先授權（PendKind） */
    val kind: Int = PendKind.SPEND,
    /** 分幾期（通知寫「分 12 期」）；0＝不是分期 */
    val inst: Int = 0,
    /** 使用者按了「是另一筆」：不再當成預先授權的請款 */
    val notPre: Boolean = false,
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
 * 4. 沒寫末四碼：這個 App 的預設卡（使用者自己選的，比 5 優先）
 * 5. 這個 App 只設了一張卡
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
    if (s.last4.isEmpty()) app?.defaultCard?.takeIf { id -> cards.any { it.id == id } }?.let { return it }
    if (appCards.size == 1) return appCards[0].id
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
        kotlin.math.abs(p.time - n.time) <= PEND_MERGE_MINUTES * 60_000L && p.amount == n.amount && p.cur == n.cur && p.kind == n.kind &&
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
                inst = maxOf(p.inst, n.inst),
            )
        }
    }
    return prunePend(out, now)
}

fun prunePend(list: List<CardPend>, now: Long): List<CardPend> =
    list.filter { now - it.time < PEND_KEEP_DAYS * 86_400_000L }.sortedByDescending { it.time }

/** 讀到的通知 → 一筆待確認（消費、退款、預先授權；其他回傳 null） */
fun pendOf(n: CapturedNoti, r: NotiRead): CardPend? {
    val kind = when (r.kind) {
        NotiKind.SPEND -> PendKind.SPEND
        NotiKind.REFUND -> PendKind.REFUND
        NotiKind.PREAUTH -> PendKind.PREAUTH
        else -> return null
    }
    return CardPend(
        id = n.id, src = listOf(NotiSrc(n.id, n.pkg, n.label, n.title, r.last4)), amount = r.amount, cur = r.cur,
        merchant = r.merchant, merchantSure = r.merchantSure, wallet = r.wallet, day = r.day, minute = r.minute, time = n.time,
        kind = kind, inst = r.inst,
    )
}

/**
 * 已經記好了才收到同一筆有店名的另一則（例如玉山先到、按了記，Samsung Wallet 才到；草圖 ci-out/noti_no_shop.png ③）：
 * 記好的那筆還沒有店家就補上。回傳（那筆的 id、店家），不用補回傳 null。
 */
fun AppData.pendShopBackfill(p: CardPend): Pair<Long, String>? {
    if (p.state != PendState.DONE || p.txnId == 0L || p.merchant.isBlank()) return null
    val t = txnMap[p.txnId] ?: return null
    return if (t.shop.isBlank()) t.id to p.merchant else null
}

/** 「其實是刷卡」：被當成廣告的那則照一般刷卡放進待確認（已經在待確認裡的不動）；回傳新的清單，沒放進去回傳 null */
fun forceSpendPend(list: List<CardPend>, n: CapturedNoti, d: AppData, now: Long): List<CardPend>? {
    if (list.any { p -> p.src.any { it.notiId == n.id } }) return null
    val r = d.readNoti(n)
    val p = pendOf(n, if (r.kind == NotiKind.AD) r.copy(kind = NotiKind.SPEND) else r) ?: return null
    return addPend(list, p, d, now)
}

/** 記成一筆時留著的來源：有末四碼的那則優先 */
val CardPend.mainSrc: NotiSrc get() = src.firstOrNull { it.last4.isNotEmpty() } ?: src.first()

/** 記在那一筆上的通知來源（Txn.noti） */
object NotiSrcCodec {
    fun toJson(s: NotiSrc): JSONObject =
        JSONObject().put("id", s.notiId).put("pkg", s.pkg).put("label", s.label).put("title", s.title).put("last4", s.last4)

    fun fromJson(o: JSONObject?): NotiSrc? {
        if (o == null) return null
        val pkg = o.optString("pkg", "")
        if (pkg.isEmpty()) return null
        return NotiSrc(o.optLong("id", 0L), pkg, o.optString("label", ""), o.optString("title", ""), o.optString("last4", ""))
    }
}

object CardPendCodec {
    fun encode(list: List<CardPend>): String = JSONArray().apply {
        list.forEach { p ->
            put(
                JSONObject().put("id", p.id).put("amount", p.amount).put("cur", p.cur).put("merchant", p.merchant).put("sure", p.merchantSure)
                    .put("wallet", p.wallet).put("day", p.day).put("minute", p.minute).put("time", p.time)
                    .put("cardId", p.cardId ?: JSONObject.NULL).put("state", p.state).put("txnId", p.txnId)
                    .apply { if (p.notSub) put("notSub", true) }
                    .apply { if (p.kind != PendKind.SPEND) put("kind", p.kind); if (p.inst > 0) put("inst", p.inst); if (p.notPre) put("notPre", true) }
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
                o.optInt("kind", PendKind.SPEND), o.optInt("inst", 0), o.optBoolean("notPre", false),
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
 * 1. 同一家商店上次記成什麼：記錄從新到舊，第一筆「支出、有分類、不是餘額調整、銀行寫的／店家／備註和商店是同一家」的分類
 * 2. 同種類的別家店記最多次的分類（種類看內建的常見商店表 SHOP_KINDS；一樣多的取最近記的）：7-ELEVEN 沒記過 → 看全家
 * 3. 有跟種類同名的分類（台灣中油 → 「加油」）
 * 4. 這張卡最常記的分類：這張卡最近 30 筆有分類的支出（不算餘額調整、#訂閱），至少 5 筆，最多的那個分類超過一半才算
 */
fun AppData.guessCat(merchant: String, cardId: Long?): Long? {
    fun ok(id: Long?) = id?.let { catMap[it] }?.takeIf { it.kind == TxType.EXPENSE }?.id
    val key = shopKey(merchant)
    if (key.length >= 2) {
        // txns 已經是新到舊（同一天的照時間）
        for (t in txns) {
            if (t.type != TxType.EXPENSE || t.adjust || t.categoryId == null) continue
            if (t.isShop(key)) ok(t.categoryId)?.let { return it }
        }
    }
    shopKindOf(merchant)?.let { kind ->
        // 新到舊數，一樣多的取先數到的（最近記的）
        val count = LinkedHashMap<Long, Int>()
        for (t in txns) {
            val c = t.categoryId ?: continue
            if (t.type != TxType.EXPENSE || t.adjust || ok(c) == null) continue
            if (t.shopNames().any { shopKindOf(it) == kind }) count[c] = (count[c] ?: 0) + 1
        }
        count.entries.maxByOrNull { it.value }?.let { return it.key }
        for (n in kind.cats) categories.firstOrNull { it.kind == TxType.EXPENSE && it.name.equals(n, ignoreCase = true) }?.let { return it.id }
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
    if (cardId == null || p.kind != PendKind.SPEND) return null
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

/** 商店（有錢包加在後面）；都沒有就空白（以前記成那一筆的備註，現在店家另外存，見 Shop.kt） */
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
    val ps = pendShop(p)
    val base = Txn(
        id = id, bookId = bookId, type = TxType.EXPENSE, amount = p.amount, categoryId = catId, accountId = cardId, toAccountId = null,
        day = p.day, note = ps.note, tags = listOf(CARD_NOTI_TAG), time = p.minute, noti = p.mainSrc, shop = ps.shop, bankShop = ps.bankShop,
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
    if (cardId == null || p.notSub || p.kind != PendKind.SPEND) return null
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
    return t.copy(time = p.minute, tags = (t.tags + CARD_NOTI_TAG).distinct(), noti = p.mainSrc)
}

// ───────── 「收到的通知」旁邊的標籤 ─────────

/** 收到的通知讀得怎麼樣：GOOD＝讀對了／已記（綠）、TPL＝用範本讀的（紫）、PART＝少了商店（橘）、OFF＝不是消費、還不會讀、已經記過（灰） */
enum class NotiTagKind { GOOD, TPL, PART, OFF }

/** 收到的一則通知旁邊的標籤：有放進待確認的看那筆的狀態，沒有的看讀出來的結果 */
fun AppData.notiTag(n: CapturedNoti, pends: List<CardPend>): Pair<NotiTagKind, String> {
    val p = pends.firstOrNull { pp -> pp.src.any { it.notiId == n.id } }
    if (p != null) {
        return when (p.state) {
            PendState.DONE -> NotiTagKind.GOOD to when (p.kind) {
                PendKind.REFUND -> "已記（退款）"
                PendKind.PREAUTH -> "已記（預估）"
                else -> when {
                    p.maybeTest && txnMap[p.txnId]?.est == true -> "已記（試刷，預估）"
                    p.isInst -> "已記（分期）"
                    // 改了預先授權那筆的金額（那筆是授權通知記的）
                    txnMap[p.txnId]?.noti?.let { s -> p.src.none { it.notiId == s.notiId } } == true -> "已記（請款）"
                    else -> "已記"
                }
            }
            PendState.HAD -> NotiTagKind.OFF to "已經記過"
            PendState.NOT -> NotiTagKind.OFF to when {
                p.maybeTest -> "試刷，不記"
                p.kind == PendKind.SPEND -> "不是消費"
                else -> "不用記"
            }
            else -> pendSubDone(p)?.let { NotiTagKind.GOOD to "已記（訂閱：${it.sub.name}）" }
                ?: if (p.merchant.isEmpty()) NotiTagKind.OFF to "通知沒寫商店"
                else if (tplFor(n) != null) NotiTagKind.TPL to "用範本" else NotiTagKind.GOOD to "讀對了"
        }
    }
    if (tplFor(n) != null) return NotiTagKind.TPL to "用範本"
    val r = parseCardNoti(n.title, n.text, n.time)
    return when (r.kind) {
        NotiKind.SPEND, NotiKind.REFUND, NotiKind.PREAUTH -> if (r.merchant.isEmpty()) NotiTagKind.OFF to "通知沒寫商店" else NotiTagKind.GOOD to "讀對了"
        NotiKind.NO_AMOUNT -> NotiTagKind.OFF to "不是消費"
        NotiKind.INST_PAY -> NotiTagKind.OFF to "分期請款，不記"
        NotiKind.OTHER -> NotiTagKind.OFF to "還不會讀"
        NotiKind.AD -> NotiTagKind.OFF to "像廣告，不記"
    }
}

/** 「收到的通知」的分頁：刷卡紀錄、廣告、其他 */
enum class NotiTab { CARD, AD, OTHER }

/**
 * 一則通知在哪一頁：放進過待確認的（包括按了「不是消費」、已經記過的）和讀成消費、退款、預先授權的是刷卡紀錄；
 * 像廣告的是廣告；點數、繳款、取消、分期請款、讀不到金額的是其他
 */
fun AppData.notiTab(n: CapturedNoti, pends: List<CardPend>): NotiTab {
    if (pends.any { p -> p.src.any { it.notiId == n.id } }) return NotiTab.CARD
    return when (readNoti(n).kind) {
        NotiKind.SPEND, NotiKind.REFUND, NotiKind.PREAUTH -> NotiTab.CARD
        NotiKind.AD -> NotiTab.AD
        else -> NotiTab.OTHER
    }
}

/** 收到的通知裡的一組：同一筆待確認的好幾則（pend 有值，照收到的順序）或單獨一則 */
data class NotiGroup(val notis: List<CapturedNoti>, val pend: CardPend?)

/** 把收到的通知（新的在前）分組：同一筆的併成一組、放在最新那則的位置 */
fun groupNotis(list: List<CapturedNoti>, pends: List<CardPend>): List<NotiGroup> {
    val byId = list.associateBy { it.id }
    val done = HashSet<Long>()
    val out = ArrayList<NotiGroup>()
    for (n in list) {
        if (n.id in done) continue
        val p = pends.firstOrNull { pp -> pp.src.any { it.notiId == n.id } }
        val members = p?.src?.mapNotNull { byId[it.notiId] }?.distinctBy { it.id }?.sortedBy { it.time }.orEmpty()
        if (p != null && members.size > 1) {
            members.forEach { done.add(it.id) }
            out.add(NotiGroup(members, p))
        } else {
            done.add(n.id)
            out.add(NotiGroup(listOf(n), p))
        }
    }
    return out
}

// ───────── 首頁、手機通知要列的 ─────────

/** 首頁要列的待確認：還沒處理、也不是已經記過的那次訂閱（照收到的時間，早的在前） */
fun AppData.pendWaiting(list: List<CardPend>): List<CardPend> =
    list.filter { it.state == PendState.WAIT && pendSubDone(it) == null }.sortedBy { it.time }

/** 吉祥物的手機通知內容；single＝只有一筆、卡和分類都有，通知上可以直接「記好了／不是消費」 */
data class PendNotice(val title: String, val body: String, val lines: List<String>, val single: Long?)

private fun pendAmt(p: CardPend) = if (p.cur.isEmpty()) formatMoney(p.amount) else formatFx(p.amount, p.cur)

/**
 * 有待確認時的通知：一筆寫「刷了一筆 $85」＋商店・卡・分類；好幾筆寫「有 N 筆刷卡等你確認」。
 * 通知上能直接記的只有：一筆、對得到卡、有分類（或對到訂閱）、不用問匯率、不是「可能已經記過」。
 */
fun AppData.pendNotice(list: List<CardPend>, name: String): PendNotice? {
    val w = pendWaiting(list)
    if (w.isEmpty()) return null
    if (w.size == 1) {
        val p = w[0]
        val card = resolveCard(p)
        val cardName = card?.let { accMap[it]?.name }
        val sm = pendSub(p, card)?.takeIf { !it.done }
        val where0 = cardName ?: (p.label + if (p.last4.isNotEmpty()) " 末四碼 ${p.last4}" else "")
        // 退款、預先授權的請款：照草圖的寫法；退款對得到卡就能直接記，請款要進 App 看
        if (p.kind == PendKind.REFUND) {
            val link = pendRefundOf(p, card)
            val body = listOfNotNull(p.merchant.ifEmpty { null }, where0, link?.let { "退的是 ${refundLabel(it)}" } ?: "不算收入").joinToString("・")
            return PendNotice("$name：退款 ${pendAmt(p)}", body, emptyList(), if (card != null && p.cur.isEmpty()) p.id else null)
        }
        preauthFor(p, card)?.let { e ->
            return PendNotice("$name：${p.merchant} 請款 ${pendAmt(p)}", "預估的 ${formatMoney(e.amount)} 要改成 ${pendAmt(p)} 嗎？", emptyList(), null)
        }
        // 剛好 $1：可能是試刷，要打開 App 選記成預估或不記
        if (p.maybeTest) {
            return PendNotice(
                "$name：刷了一筆 ${pendAmt(p)}，可能是試刷",
                listOfNotNull(p.merchant.ifEmpty { null }, where0, "點開來選要不要先記").joinToString("・"), emptyList(), null,
            )
        }
        val title = when {
            p.kind == PendKind.PREAUTH -> "$name：預先授權 ${pendAmt(p)}"
            p.isInst -> "$name：刷了一筆 ${pendAmt(p)}（分 ${p.inst} 期）"
            else -> "$name：刷了一筆 ${pendAmt(p)}"
        }
        if (sm != null && sm.exact) {
            val ok = pendSubTxn(p, sm, 0L) != null
            return PendNotice(title, listOfNotNull("訂閱：${sm.sub.name}", cardName).joinToString("・"), emptyList(), if (ok) p.id else null)
        }
        val cat = pendCat(p, card)?.let { catMap[it] }
        val where = cardName ?: (p.label + if (p.last4.isNotEmpty()) " 末四碼 ${p.last4}" else "")
        val body = listOfNotNull(pendName(p).takeIf { p.merchant.isNotEmpty() }, where, if (card != null) cat?.name ?: "還沒有分類" else "點開來選是哪張卡").joinToString("・")
        val ok = card != null && cat != null && sm == null && pendTxn(p, 0L, currentBook.id, card, cat.id) != null &&
            possibleHad(p, card, claimedTxns(list)) == null
        return PendNotice(title, body, emptyList(), if (ok) p.id else null)
    }
    val items = w.map { (if (it.kind == PendKind.REFUND) "退款 " else "") + "${it.merchant.ifEmpty { "刷卡" }} ${pendAmt(it)}" }
    return PendNotice(
        "$name：有 ${w.size} 筆刷卡等你確認",
        items.take(3).joinToString("、") + if (w.size > 3) "、…" else "",
        items.take(6),
        null,
    )
}

// ───────── 更新後補一次：第一步收到的通知也放進待確認 ─────────

/**
 * 把「收到的通知」裡還沒放進待確認的（第一步收的、早於讀通知的版本）讀一遍放進去，由早到晚加（同一筆的兩則照樣合併）。
 * 回傳新的清單和補了幾筆。只在更新後第一次打開 App 時做一次（見 notify/CardPendLog.backfillOnce）。
 */
fun backfillPends(pends: List<CardPend>, captured: List<CapturedNoti>, d: AppData, now: Long): Pair<List<CardPend>, Int> {
    val seen = pends.flatMap { p -> p.src.map { it.notiId } }.toSet()
    var out = pends
    var n = 0
    for (c in captured.filter { it.id !in seen }.sortedBy { it.time }) {
        val p = pendOf(c, d.readNoti(c)) ?: continue
        val before = out.size
        out = addPend(out, p, d, now)
        if (out.size > before) n++
    }
    return out to n
}

// ───────── 事後在修改頁改卡：問「以後也記到這張嗎」（使用者選乙） ─────────

/**
 * 修改刷卡通知記的那一筆、帳戶換成另一張信用卡時要不要問：照規則這種通知現在不會記到新的這張才問
 * （已經會了就不用問）。回傳要記住的那則來源，不用問回傳 null。
 */
fun AppData.relearnAsk(t: Txn, newAcc: Long?): NotiSrc? {
    val s = t.noti ?: return null
    if (t.type != TxType.EXPENSE || newAcc == null || newAcc == t.accountId) return null
    if (accMap[newAcc]?.type != AccountType.CARD) return null
    // 沒有末四碼的要記在這個 App 上：App 已經不讀了就不問
    if (s.last4.isEmpty() && notiApps.none { it.pkg == s.pkg }) return null
    return s.takeIf { matchCard(it) != newAcc }
}

/** 「以後 ___ 也記到 X 卡」：末四碼、通知標題、或這個 App 沒寫末四碼的通知 */
fun relearnWhat(s: NotiSrc): String = when {
    s.last4.isNotEmpty() -> " ${s.last4} "
    titleKey(s.title).isNotEmpty() -> "「${s.title}」的通知"
    else -> " ${s.label} 沒寫末四碼的通知"
}

/**
 * 以後這種通知記到 cardId：有末四碼 → 這組末四碼移到這張卡（從別張卡拿掉，標成今天學的）；
 * 沒有末四碼 → 記住「這個標題 → 這張卡」；連標題都沒有 → 當這個 App 的預設卡。
 */
fun AppData.relearnCard(s: NotiSrc, cardId: Long, today: Long): AppData = when {
    s.last4.isNotEmpty() -> copy(accounts = accounts.map { a ->
        when {
            a.id == cardId -> if (a.cardNos.any { it.digits == s.last4 }) a else a.copy(cardNos = a.cardNos + CardNo(s.last4, learned = today))
            a.cardNos.any { it.digits == s.last4 } -> a.copy(cardNos = a.cardNos.filter { it.digits != s.last4 })
            else -> a
        }
    })
    titleKey(s.title).isNotEmpty() -> copy(notiApps = notiApps.map { a -> if (a.pkg == s.pkg) a.copy(titles = a.titles + (titleKey(s.title) to cardId)) else a })
    else -> copy(notiApps = notiApps.map { a -> if (a.pkg == s.pkg) a.copy(defaultCard = cardId) else a })
}
