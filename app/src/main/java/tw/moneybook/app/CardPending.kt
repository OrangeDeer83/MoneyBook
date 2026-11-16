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
                o.optInt("state", PendState.WAIT), o.optLong("txnId", 0L),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
