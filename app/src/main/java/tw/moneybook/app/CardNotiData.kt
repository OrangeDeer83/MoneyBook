package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * 刷卡通知要讀的一個 App（例如國泰世華 CUBE App、Samsung Wallet）。
 * cardIds：這個 App 的通知是哪幾張信用卡（只有一張就都記它；好幾張再用末四碼分）；
 * defaultCard：通知沒寫末四碼時記到哪張（LINE Pay、Samsung Wallet 這種綁好幾家卡的 App 用；null＝每次問）。
 */
data class NotiApp(
    val pkg: String,
    val label: String,
    val cardIds: List<Long> = emptyList(),
    val defaultCard: Long? = null,
    /** 「記住」的：通知標題（titleKey）→ 卡（Samsung Wallet 這種沒有末四碼、標題是卡片名稱的）；舊資料沒有 */
    val titles: Map<String, Long> = emptyMap(),
)

object NotiAppCodec {
    fun toJson(list: List<NotiApp>): JSONArray = JSONArray().apply {
        list.forEach { a ->
            put(
                JSONObject().put("pkg", a.pkg).put("label", a.label).put("cardIds", JSONArray(a.cardIds))
                    .put("defaultCard", a.defaultCard ?: JSONObject.NULL)
                    .apply { if (a.titles.isNotEmpty()) put("titles", JSONObject().apply { a.titles.forEach { (k, v) -> put(k, v) } }) }
            )
        }
    }

    fun fromJson(arr: JSONArray?): List<NotiApp> {
        if (arr == null) return emptyList()
        val out = ArrayList<NotiApp>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg", "")
            if (pkg.isEmpty()) continue
            val ids = o.optJSONArray("cardIds")?.let { a -> (0 until a.length()).mapNotNull { j -> a.optLong(j, 0L).takeIf { it > 0L } } } ?: emptyList()
            val tj = o.optJSONObject("titles")
            val titles = tj?.keys()?.asSequence()?.mapNotNull { k -> tj.optLong(k, 0L).takeIf { it > 0L }?.let { k to it } }?.toMap() ?: emptyMap()
            out.add(NotiApp(pkg, o.optString("label", pkg), ids, if (o.isNull("defaultCard")) null else o.optLong("defaultCard").takeIf { it > 0L }, titles))
        }
        return out.distinctBy { it.pkg }
    }
}

/**
 * 收到的一則通知（只存勾選的 App 的）：原文留 7 天給你看、之後教範本用；不跟著備份。
 * time 是通知送達的時間（毫秒）。
 */
data class CapturedNoti(val id: Long, val pkg: String, val label: String, val title: String, val text: String, val time: Long)

/**
 * 信用卡的一組卡號末四碼。note：自己加的備註（實體卡、手機、舊卡…，空白＝沒寫）；
 * learned：從刷卡通知學到的那天（epoch day），0＝自己輸入的。
 */
data class CardNo(val digits: String, val note: String = "", val learned: Long = 0L)

/** 末四碼是不是 4 個數字 */
fun validCardNo(s: String): Boolean = s.length == 4 && s.all { it in '0'..'9' }

/** 這組末四碼在哪張卡（排除 exceptId 那張）；沒有回傳 null */
fun AppData.cardWithNo(digits: String, exceptId: Long = 0L): Account? =
    accounts.firstOrNull { it.id != exceptId && it.cardNos.any { n -> n.digits == digits } }

/** 存一個帳戶（id＝null 是新增）：卡號末四碼同一組只能在一張卡，這張卡有的從別張卡拿掉 */
fun saveAccountTo(d: AppData, id: Long?, a: Account): AppData {
    val mine = a.cardNos.map { it.digits }.toSet()
    fun others(list: List<Account>, selfId: Long) =
        if (mine.isEmpty()) list else list.map { o -> if (o.id != selfId && o.cardNos.any { it.digits in mine }) o.copy(cardNos = o.cardNos.filter { it.digits !in mine }) else o }
    return if (id == null) {
        val n = a.copy(id = d.nextId, order = (d.accounts.maxOfOrNull { it.order } ?: 0) + 1)
        d.copy(accounts = others(d.accounts, n.id) + n, nextId = d.nextId + 1)
    } else {
        d.copy(accounts = others(d.accounts.map { if (it.id == id) a.copy(id = id, order = it.order) else it }, id))
    }
}

object CardNoCodec {
    fun toJson(list: List<CardNo>): JSONArray = JSONArray().apply {
        list.forEach { n ->
            put(JSONObject().put("d", n.digits).apply { if (n.note.isNotEmpty()) put("note", n.note); if (n.learned != 0L) put("learned", n.learned) })
        }
    }

    fun fromJson(arr: JSONArray?): List<CardNo> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CardNo(o.optString("d", ""), o.optString("note", ""), o.optLong("learned", 0L)).takeIf { validCardNo(it.digits) }
        }.distinctBy { it.digits }
    }
}

/** 收到的通知最多留幾天、幾則 */
const val NOTI_KEEP_DAYS = 7
const val NOTI_KEEP_MAX = 500

/**
 * 加進一則通知：同一個 App、同樣標題與內文、10 分鐘內的算重複（系統重送、App 更新同一則）不再加；
 * 超過 7 天或超過 500 則的舊的丟掉。回傳新的清單（新的在前）。
 */
fun addCaptured(list: List<CapturedNoti>, n: CapturedNoti, now: Long): List<CapturedNoti> {
    val dup = list.any { it.pkg == n.pkg && it.title == n.title && it.text == n.text && kotlin.math.abs(it.time - n.time) < 10 * 60_000L }
    val base = if (dup) list else listOf(n) + list
    return pruneCaptured(base, now)
}

fun pruneCaptured(list: List<CapturedNoti>, now: Long): List<CapturedNoti> =
    list.filter { now - it.time < NOTI_KEEP_DAYS * 86_400_000L }.sortedByDescending { it.time }.take(NOTI_KEEP_MAX)

object CapturedCodec {
    fun encode(list: List<CapturedNoti>): String = JSONArray().apply {
        list.forEach { n ->
            put(JSONObject().put("id", n.id).put("pkg", n.pkg).put("label", n.label).put("title", n.title).put("text", n.text).put("time", n.time))
        }
    }.toString()

    fun decode(text: String): List<CapturedNoti> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CapturedNoti(o.optLong("id"), o.optString("pkg"), o.optString("label"), o.optString("title"), o.optString("text"), o.optLong("time"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 看名稱像不像銀行、信用卡、支付、錢包的 App（選 App 時排前面）；猜不到的也能選，只是排後面 */
fun looksLikePayApp(label: String): Boolean {
    val l = label.lowercase()
    val words = listOf(
        "銀行", "bank", "信用卡", "pay", "錢包", "wallet", "cube", "e.fingo", "efingo", "大戶", "dawho", "dawway",
        "國泰", "玉山", "永豐", "中信", "台新", "富邦", "聯邦", "第一", "華南", "兆豐", "彰銀", "土銀", "合庫", "星展", "滙豐", "渣打",
        "街口", "全支付", "悠遊付", "icash", "全盈", "line pay", "open point", "samsung",
    )
    return words.any { it in l }
}
