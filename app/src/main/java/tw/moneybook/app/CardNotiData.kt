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
)

object NotiAppCodec {
    fun toJson(list: List<NotiApp>): JSONArray = JSONArray().apply {
        list.forEach { a ->
            put(
                JSONObject().put("pkg", a.pkg).put("label", a.label).put("cardIds", JSONArray(a.cardIds))
                    .put("defaultCard", a.defaultCard ?: JSONObject.NULL)
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
            out.add(NotiApp(pkg, o.optString("label", pkg), ids, if (o.isNull("defaultCard")) null else o.optLong("defaultCard").takeIf { it > 0L }))
        }
        return out.distinctBy { it.pkg }
    }
}

/**
 * 收到的一則通知（只存勾選的 App 的）：原文留 7 天給你看、之後教範本用；不跟著備份。
 * time 是通知送達的時間（毫秒）。
 */
data class CapturedNoti(val id: Long, val pkg: String, val label: String, val title: String, val text: String, val time: Long)

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
