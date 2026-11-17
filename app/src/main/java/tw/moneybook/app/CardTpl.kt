package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/*
 * 刷卡通知第三步：自己教範本（草圖 ci-out/notify_template.png）。
 * 通用規則讀錯的通知，在「收到的通知」點一則，標出金額、商店（必標）、末四碼、時間（可以不標），
 * 存成這個 App 的範本：沒標的字照抄（數字當任何數字、空白可有可無），標了的地方每次會變。
 * 同一個 App 的通知先試範本，都對不上才用通用規則。範本跟著備份。
 */

/** 範本要標的欄位 */
enum class TplField(val label: String) { AMOUNT("金額"), MERCHANT("商店"), LAST4("末四碼"), TIME("時間") }

/** 範本上標的一段：sample 的第 start～end 個字（含） */
data class TplMark(val field: TplField, val start: Int, val end: Int)

/**
 * 一個範本：sample 是當初那則通知的內文（normalizeNoti 過的），marks 是標的欄位（每種最多一段、不重疊）；
 * created 是學的那天（epoch day）。
 */
data class NotiTpl(val id: Long, val pkg: String, val sample: String, val marks: List<TplMark>, val created: Long)

/** 沒標的字：數字當任何數字、空白可有可無，其他照抄 */
private fun litRegex(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isDigit() -> {
                while (i < s.length && s[i].isDigit()) i++
                sb.append("""\d+""")
            }
            c.isWhitespace() -> {
                while (i < s.length && s[i].isWhitespace()) i++
                sb.append("""\s*""")
            }
            else -> {
                if (!c.isLetterOrDigit() && c.code < 128) sb.append('\\')
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}

/** 範本換成比對用的規則；標得不完整（沒有金額）回傳 null */
fun NotiTpl.regex(): Regex? {
    val ms = marks.sortedBy { it.start }
    if (ms.none { it.field == TplField.AMOUNT }) return null
    val sb = StringBuilder()
    var pos = 0
    ms.forEachIndexed { i, m ->
        if (m.start < pos || m.end >= sample.length || m.end < m.start) return null
        sb.append(litRegex(sample.substring(pos, m.start)))
        val last = i == ms.lastIndex && m.end == sample.lastIndex
        sb.append(
            when (m.field) {
                TplField.AMOUNT -> """(\d[\d,]*(?:\.\d+)?)"""
                TplField.LAST4 -> """(\d{4})"""
                else -> if (last) "(.+)" else "(.+?)"
            }
        )
        pos = m.end + 1
    }
    sb.append(litRegex(sample.substring(pos)))
    return runCatching { Regex(sb.toString()) }.getOrNull()
}

/** 範本裡金額前後的字看幣別（NT$、US$、元、日圓…）；沒寫是台幣 */
private fun tplCur(before: String, after: String): String {
    val b = before.trimEnd()
    val a = after.trimStart()
    CUR_PREFIX.firstOrNull { (p, _) -> b.endsWith(p, ignoreCase = true) }?.let { return it.second }
    return CUR_SUFFIX.firstOrNull { (p, _) -> a.startsWith(p) }?.second ?: ""
}

/**
 * 用範本讀一則通知；對不上（或金額讀不出來）回傳 null。
 * 沒標的末四碼、時間照通用規則讀；時間那一段裡沒寫日期（或時間）的也照通用規則。
 */
fun NotiTpl.read(title: String, text: String, postTime: Long, zone: ZoneId = ZoneId.systemDefault()): NotiRead? {
    val body = normalizeNoti(text)
    val m = regex()?.find(body) ?: return null
    val ms = marks.sortedBy { it.start }
    fun g(f: TplField) = ms.indexOfFirst { it.field == f }.takeIf { it >= 0 }?.let { m.groups[it + 1] }
    val am = ms.first { it.field == TplField.AMOUNT }
    val cur = tplCur(sample.substring(0, am.start), sample.substring(am.end + 1))
    val amount = amountValue(g(TplField.AMOUNT)?.value ?: return null, cur)?.takeIf { it > 0L } ?: return null
    val base = parseCardNoti(title, text, postTime, zone)
    val (shop, wallet) = splitWallet(g(TplField.MERCHANT)?.value?.trim().orEmpty(), body)
    var day = base.day
    var minute = base.minute
    g(TplField.TIME)?.value?.let { s ->
        val w = parseCardNoti("", s, postTime, zone)
        // 這一段有寫日期才換日期；有寫時間才換時間
        if (w.spans[TplField.TIME] != null) {
            val ns = normalizeNoti(s)
            val hasTime = Regex("""\d{1,2}:\d{2}""").containsMatchIn(ns)
            val hasDate = Regex("""\d{1,4}[/.\-月]\d{1,2}""").containsMatchIn(ns)
            // 只寫日期：不是今天就不知道幾點（通用規則一樣）
            if (hasDate) { day = w.day; if (!hasTime) minute = w.minute }
            if (hasTime) minute = w.minute
        }
    }
    val spans = HashMap<TplField, IntRange>()
    ms.forEachIndexed { i, mk -> m.groups[i + 1]?.range?.let { spans[mk.field] = it } }
    // 退款、預先授權、分期請款照通用規則看；其他（通用規則當成不記、讀不到金額的）對得上範本就是消費
    val kind = when (base.kind) {
        NotiKind.REFUND, NotiKind.PREAUTH, NotiKind.INST_PAY -> base.kind
        else -> NotiKind.SPEND
    }
    return NotiRead(
        kind, amount, cur, shop.take(30), wallet, g(TplField.LAST4)?.value ?: base.last4, day, minute,
        merchantSure = shop.isNotEmpty(), spans = spans, inst = base.inst,
    )
}

/** 這則通知對得上哪個範本（同一個 App 的，新的先試）；都對不上回傳 null */
fun AppData.tplFor(n: CapturedNoti): NotiTpl? =
    notiTpls.filter { it.pkg == n.pkg }.sortedByDescending { it.id }.firstOrNull { it.read(n.title, n.text, n.time) != null }

/** 讀一則通知：先試這個 App 的範本，都對不上才用通用規則 */
fun AppData.readNoti(n: CapturedNoti): NotiRead =
    tplFor(n)?.read(n.title, n.text, n.time) ?: parseCardNoti(n.title, n.text, n.time)

/** 範本的名字：「X 銀行・刷卡」（照這則通知裡的字取；同一個 App 有好幾個同名的加編號） */
fun AppData.tplName(t: NotiTpl): String {
    val label = notiApps.firstOrNull { it.pkg == t.pkg }?.label ?: t.pkg
    fun kind(x: NotiTpl) = listOf("刷卡", "消費", "交易", "扣款", "支付", "付款").firstOrNull { it in x.sample } ?: "刷卡"
    val same = notiTpls.filter { it.pkg == t.pkg && kind(it) == kind(t) }.sortedBy { it.id }
    val i = same.indexOfFirst { it.id == t.id }
    return "$label・${kind(t)}" + if (same.size > 1 && i > 0) " ${i + 1}" else ""
}

/** 標好一段（first、last 是點的兩個字，順序不拘）：同一個欄位原本那段、跟這段重疊的都拿掉 */
fun setMark(marks: List<TplMark>, field: TplField, first: Int, last: Int): List<TplMark> {
    val a = minOf(first, last)
    val b = maxOf(first, last)
    return (marks.filter { it.field != field && (it.end < a || it.start > b) } + TplMark(field, a, b)).sortedBy { it.start }
}

/** 第 i 個字被哪一段標了 */
fun markAt(marks: List<TplMark>, i: Int): TplMark? = marks.firstOrNull { i in it.start..it.end }

/** 存之前要標好的：金額、商店一定要標 */
fun tplNeed(marks: List<TplMark>): TplField? =
    listOf(TplField.AMOUNT, TplField.MERCHANT).firstOrNull { f -> marks.none { it.field == f } }

/**
 * 存好範本後重讀：這個 App 收到的通知用範本讀得出來的，待確認裡還沒處理的換成新讀到的內容，
 * 還沒放進待確認的放進去。回傳新的清單和更新了幾則。
 */
fun retplPends(pends: List<CardPend>, captured: List<CapturedNoti>, d: AppData, tpl: NotiTpl, now: Long): Pair<List<CardPend>, Int> {
    var out = pends
    var n = 0
    for (c in captured.filter { it.pkg == tpl.pkg }.sortedBy { it.time }) {
        val r = tpl.read(c.title, c.text, c.time) ?: continue
        val p = out.firstOrNull { pp -> pp.src.any { it.notiId == c.id } }
        if (p == null) {
            val np = pendOf(c, r) ?: continue
            val before = out.size
            out = addPend(out, np, d, now)
            if (out.size > before) n++
        } else if (p.state == PendState.WAIT) {
            val np = p.copy(
                src = p.src.map { if (it.notiId == c.id) it.copy(last4 = r.last4) else it },
                amount = r.amount, cur = r.cur, merchant = r.merchant, merchantSure = r.merchantSure,
                wallet = r.wallet.ifEmpty { p.wallet }, day = r.day, minute = if (r.minute >= 0) r.minute else p.minute,
            )
            if (np != p) {
                out = out.map { if (it.id == p.id) np else it }
                n++
            }
        }
    }
    return out to n
}

object NotiTplCodec {
    fun toJson(list: List<NotiTpl>): JSONArray = JSONArray().apply {
        list.forEach { t ->
            put(
                JSONObject().put("id", t.id).put("pkg", t.pkg).put("sample", t.sample).put("created", t.created)
                    .put("marks", JSONArray().apply { t.marks.forEach { m -> put(JSONObject().put("f", m.field.name).put("s", m.start).put("e", m.end)) } })
            )
        }
    }

    fun fromJson(arr: JSONArray?): List<NotiTpl> {
        if (arr == null) return emptyList()
        val out = ArrayList<NotiTpl>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg", "")
            val sample = o.optString("sample", "")
            if (pkg.isEmpty() || sample.isEmpty()) continue
            val ma = o.optJSONArray("marks")
            val marks = (0 until (ma?.length() ?: 0)).mapNotNull { j ->
                val m = ma!!.optJSONObject(j) ?: return@mapNotNull null
                val f = TplField.entries.firstOrNull { it.name == m.optString("f") } ?: return@mapNotNull null
                TplMark(f, m.optInt("s", -1), m.optInt("e", -1)).takeIf { it.start >= 0 && it.end >= it.start && it.end < sample.length }
            }
            out.add(NotiTpl(o.optLong("id", 0L), pkg, sample, marks, o.optLong("created", 0L)))
        }
        return out
    }
}
