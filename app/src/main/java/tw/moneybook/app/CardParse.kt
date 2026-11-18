package tw.moneybook.app

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 刷卡通知第二步：把一則通知的文字讀成「金額、商店、末四碼、時間」。
 * 不靠特定銀行的格式，用通用規則：
 *  1. 全形轉半形；空白、換行旁邊是中文就直接接起來（國泰「ＯＰ\n錢包」→「OP錢包」）
 *  2. 末四碼、日期時間先找出來，這些數字不會被當成金額
 *  3. 金額用打分數挑：前面有幣別 +3、後面有「元」+2、前面有「消費、金額」這類字 +2、有千分位逗號 +1，2 分以上才算
 *  4. 商店：「在／於 ___ 刷卡／消費」→ 括號裡的字 → 把金額、日期和常見字刪掉剩下最長的一段；錢包字樣另外拆出來
 */

/**
 * 讀的結果：SPEND＝一筆消費；NO_AMOUNT＝讀不到金額（例如「您已獲得 2 點」）；REFUND＝退款；PREAUTH＝預先授權（金額是預估的）；
 * INST_PAY＝分期的每月請款（「第 1/12 期」，分期第一次刷的時候記過了，不記）；OTHER＝取消、繳款這種，不會讀；
 * AD＝像廣告（沒有末四碼、沒寫刷卡這類字、又有「最高」「回饋」這種字），不放進待確認，其他欄位照樣讀（「其實是刷卡」時用）
 */
enum class NotiKind { SPEND, NO_AMOUNT, OTHER, REFUND, PREAUTH, INST_PAY, AD }

data class NotiRead(
    val kind: NotiKind,
    /** 台幣是元；外幣是最小單位（US$15.49 → 1549） */
    val amount: Long = 0L,
    /** 幣別；空白＝台幣 */
    val cur: String = "",
    val merchant: String = "",
    /** 用哪個錢包付的（OP錢包、LINE Pay…）；空白＝沒寫 */
    val wallet: String = "",
    /** 通知上的卡號末四碼；空白＝沒寫 */
    val last4: String = "",
    /** 刷卡那天（epoch day） */
    val day: Long = 0L,
    /** 幾點幾分（0～1439）；-1＝不知道 */
    val minute: Int = -1,
    /** 商店是用句型或括號讀到的（比較可靠；兩則通知合併時優先用） */
    val merchantSure: Boolean = false,
    /** 每個欄位在內文（normalizeNoti 過的）裡的位置：教範本時先照這個標好；標題裡的不算 */
    val spans: Map<TplField, IntRange> = emptyMap(),
    /** 分幾期（「分 12 期」）；0＝不是分期 */
    val inst: Int = 0,
    /** 像廣告時找到的廣告字（AD 才有） */
    val adWords: List<String> = emptyList(),
)

internal val WALLETS = listOf(
    "OP錢包" to "OP錢包", "OPEN錢包" to "OP錢包", "LINE Pay" to "LINE Pay", "LINEPay" to "LINE Pay", "街口支付" to "街口支付", "街口" to "街口支付",
    "全支付" to "全支付", "Apple Pay" to "Apple Pay", "Google Pay" to "Google Pay", "Samsung Pay" to "Samsung Pay", "悠遊付" to "悠遊付",
    "icash Pay" to "icash Pay", "全盈+PAY" to "全盈+PAY", "Pi錢包" to "Pi錢包", "Pi 拍錢包" to "Pi錢包", "台灣Pay" to "台灣Pay",
)

/** 退款、預先授權（先看這兩種，再看其他不記的） */
private val REFUND_WORDS = listOf("退款", "退貨", "退刷", "刷退")
private val PREAUTH_WORDS = listOf("預先授權", "預授權")
/** 分期的每月請款：「第 1/12 期」「第3期」 */
private val INST_PAY = Regex("""第\s*\d{1,2}\s*(?:/\s*\d{1,2}\s*)?期""")
/** 分期第一次刷：「分 12 期」 */
private val INST_COUNT = Regex("""分\s*(\d{1,2})\s*期""")
/** 有這些字的不記 */
private val OTHER_WORDS = listOf("取消", "繳款", "入帳", "核卡", "開卡", "失敗", "未成功", "拒絕", "交易不成功")
/**
 * 廣告常見字：沒有末四碼、也沒寫刷卡這類字（VERBS）時，有這些字就當廣告。
 * 不放單一個字（拿、送、享、抽、券）：Samsung Wallet 只寫「店名 $金額」，「拿坡里」「外送」這種店名會被誤認
 */
internal val AD_WORDS = listOf("最高", "回饋", "優惠", "活動", "限時", "立即", "登錄", "加碼", "好禮", "抽獎", "現折", "免費")

private val VERBS = listOf("刷卡", "消費", "交易", "支付", "付款", "扣款", "預先授權", "預授權", "授權", "退款", "退貨", "退刷")
private val AMOUNT_WORDS = listOf("消費", "金額", "刷卡", "交易", "扣款", "支付", "付款", "簽帳", "退款", "授權", "退貨", "退刷")

/** 找商店時刪掉的常見字（長的在前） */
private val NOISE = listOf(
    "點擊查看消費明細", "查看消費明細", "點擊查看", "查看明細", "簽帳金融卡", "信用卡", "金融卡", "末四碼", "末4碼", "卡號", "刷卡", "消費", "交易",
    "通知", "成功", "金額", "親愛的", "卡友", "您好", "您的", "您", "提醒", "謝謝", "感謝", "新台幣", "台幣", "已於", "已", "明細",
    "預先授權", "預授權", "授權", "退款", "退貨", "退刷", "刷退", "分期",
)

/** 金額前面可能的幣別（長的在前）→ 幣別代碼（空白＝台幣） */
internal val CUR_PREFIX = listOf(
    "NT$" to "", "NTD" to "", "TWD" to "", "新台幣" to "", "台幣" to "", "US$" to "USD", "USD" to "USD", "HK$" to "HKD", "HKD" to "HKD",
    "JPY" to "JPY", "EUR" to "EUR", "GBP" to "GBP", "KRW" to "KRW", "CNY" to "CNY", "RMB" to "CNY", "¥" to "JPY", "€" to "EUR", "$" to "",
)
internal val CUR_SUFFIX = listOf("美元" to "USD", "美金" to "USD", "日圓" to "JPY", "日幣" to "JPY", "日元" to "JPY", "円" to "JPY", "元" to "")

/** 數字後面接這些的不是金額（點數、百分比、期數…） */
private val NOT_AMOUNT_AFTER = listOf("點", "%", "次", "期", "個", "分", "天", "月", "年", "折", "倍", "筆", "張", "歲", "秒")

private fun isCjk(c: Char): Boolean = c in '㐀'..'鿿' || c in '豈'..'﫿' || c in '　'..'〿' || c in '＀'..'￯'

/** 全形轉半形；空白、換行旁邊是中文就直接接起來，不然換成一個空白 */
fun normalizeNoti(s: String): String {
    val n = Normalizer.normalize(s, Normalizer.Form.NFKC)
    val sb = StringBuilder()
    var i = 0
    while (i < n.length) {
        val c = n[i]
        if (c.isWhitespace()) {
            var j = i
            while (j < n.length && n[j].isWhitespace()) j++
            val prev = sb.lastOrNull()
            val next = n.getOrNull(j)
            if (prev != null && next != null && !isCjk(prev) && !isCjk(next)) sb.append(' ')
            i = j
        } else {
            sb.append(c); i++
        }
    }
    return sb.toString()
}

private fun overlaps(r: IntRange, spans: List<IntRange>) = spans.any { it.first <= r.last && r.first <= it.last }

/**
 * 讀一則通知。postTime：通知送達的時間（毫秒），沒寫日期時間就用它；沒寫年份也看它。
 */
fun parseCardNoti(title: String, text: String, postTime: Long, zone: ZoneId = ZoneId.systemDefault()): NotiRead {
    val body = normalizeNoti(text)
    val head = normalizeNoti(title)
    // 標題和內文接在一起找；內文從 off 開始（找商店只看內文）
    val all = if (head.isEmpty()) body else "$head\n$body"
    val off = all.length - body.length
    val used = ArrayList<IntRange>()

    // 末四碼：遮起來的卡號（****1234）優先，再看「末四碼／卡號」後面
    var last4 = ""
    var last4Span: IntRange? = null
    Regex("""[*xX•●]{2,}[ -]?(\d{4})(?!\d)""").find(all)?.let { last4 = it.groupValues[1]; last4Span = it.groups[1]?.range; used.add(it.range) }
    if (last4.isEmpty()) {
        Regex("""(?:末四碼|末4碼|末四位|末4位|卡號末|尾數|末碼|卡號|卡末)[^0-9]{0,6}(\d{4})(?!\d)""").find(all)?.let { last4 = it.groupValues[1]; last4Span = it.groups[1]?.range; used.add(it.range) }
    }

    // 日期、時間
    val post = Instant.ofEpochMilli(postTime).atZone(zone)
    val postDay = post.toLocalDate()
    var date: LocalDate? = null
    var dateSpan: IntRange? = null
    var timeSpan: IntRange? = null
    fun tryDate(y: Int?, m: Int, d: Int): LocalDate? {
        if (m !in 1..12 || d !in 1..31) return null
        val year = y ?: postDay.year
        val v = runCatching { LocalDate.of(year, m, d) }.getOrNull() ?: return null
        // 沒寫年份、算出來比收到的時間晚超過 1 天：是去年的（1 月收到 12/31 的通知）
        return if (y == null && v.isAfter(postDay.plusDays(1))) v.minusYears(1) else v
    }
    for ((re, kind) in listOf(
        Regex("""(?<!\d)(\d{4})[/.-](\d{1,2})[/.-](\d{1,2})(?!\d)""") to 0,
        Regex("""(?<!\d)(1\d{2})[/.-](\d{1,2})[/.-](\d{1,2})(?!\d)""") to 1,
        Regex("""(?<!\d)(\d{1,2})月(\d{1,2})日""") to 2,
        Regex("""(?<![\d/])(\d{1,2})/(\d{1,2})(?![\d/])""") to 2,
    )) {
        if (date != null) break
        val m = re.find(all) ?: continue
        val g = m.groupValues
        date = when (kind) {
            0 -> tryDate(g[1].toInt(), g[2].toInt(), g[3].toInt())
            1 -> tryDate(g[1].toInt() + 1911, g[2].toInt(), g[3].toInt())
            else -> tryDate(null, g[1].toInt(), g[2].toInt())
        }
        if (date != null) { used.add(m.range); dateSpan = m.range }
    }
    var minute = -1
    Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?::\d{2})?(?![\d:])""").find(all)?.let { m ->
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h in 0..23 && mi in 0..59) { minute = h * 60 + mi; used.add(m.range); timeSpan = m.range }
    }
    // 讀到的日期離收到的時間太遠（例如有效期限），不用
    if (date != null && kotlin.math.abs(date.toEpochDay() - postDay.toEpochDay()) > 31) { date = null; dateSpan = null }
    val postMinute = post.hour * 60 + post.minute
    // 沒寫日期：用收到的那天；沒寫時間：是收到的那天就用收到的時間，不然不知道
    val day = (date ?: postDay).toEpochDay()
    if (minute < 0 && (date == null || date == postDay)) minute = postMinute

    // 金額：每個數字打分數
    data class Cand(val range: IntRange, val value: Long, val cur: String, val score: Int)
    val cands = ArrayList<Cand>()
    for (m in Regex("""\d[\d,]*(?:\.\d+)?""").findAll(all)) {
        val r = m.range
        if (overlaps(r, used)) continue
        val raw = m.value.trimEnd(',')
        val parts = raw.split('.')
        val intPart = parts[0]
        // 千分位要像樣（1,288、12,345）才算；不然是兩個數字黏在一起
        val commaOk = !intPart.contains(',') || Regex("""\d{1,3}(,\d{3})+""").matches(intPart)
        if (!commaOk) continue
        val before = all.substring(0, r.first).trimEnd()
        val afterStart = r.first + raw.length
        val after = all.substring(afterStart).trimStart()
        if (NOT_AMOUNT_AFTER.any { after.startsWith(it) }) continue
        val pre = CUR_PREFIX.firstOrNull { (p, _) -> before.endsWith(p, ignoreCase = true) }
        val suf = CUR_SUFFIX.firstOrNull { (p, _) -> after.startsWith(p) }
        // 黏在英文字中間的（P1F2、CO2）不是金額（幣別字母除外）
        val prevCh = all.getOrNull(r.first - 1)
        if (pre == null && prevCh != null && prevCh.isLetter() && !isCjk(prevCh)) continue
        val nextCh = all.getOrNull(afterStart)
        if (nextCh != null && nextCh.isLetter() && !isCjk(nextCh)) continue
        var score = 0
        if (pre != null) score += 3
        if (suf != null) score += 2
        val look = before.dropLast(pre?.first?.length ?: 0).takeLast(8)
        if (AMOUNT_WORDS.any { it in look }) score += 2
        if (intPart.contains(',')) score += 1
        if (score < 2) continue
        val cur = pre?.second?.takeIf { it.isNotEmpty() } ?: suf?.second ?: ""
        val digits = intPart.replace(",", "")
        val frac = parts.getOrNull(1) ?: ""
        val value = amountValue(raw, cur) ?: continue
        if (value <= 0L) continue
        cands.add(Cand(r.first until r.first + raw.length, value, cur, score))
    }
    // 分數最高的；同分取前面那個
    val topScore = cands.maxOfOrNull { it.score }
    val best = cands.filter { it.score == topScore }.minByOrNull { it.range.first }

    // 內文裡的位置（教範本用）：日期和時間連在一起的當成一段
    fun inBody(r: IntRange?) = r?.takeIf { it.first >= off }?.let { (it.first - off)..(it.last - off) }
    val spans = HashMap<TplField, IntRange>()
    inBody(best?.range)?.let { spans[TplField.AMOUNT] = it }
    inBody(last4Span)?.let { spans[TplField.LAST4] = it }
    val ds = inBody(dateSpan)
    val ts = inBody(timeSpan)
    (if (ds != null && ts != null && ts.first - ds.last in 1..3) ds.first..ts.last else ds ?: ts)?.let { spans[TplField.TIME] = it }

    // 退款、預先授權照樣讀商店；分期的每月請款、取消這些不讀
    val kind = when {
        REFUND_WORDS.any { it in all } -> NotiKind.REFUND
        PREAUTH_WORDS.any { it in all } -> NotiKind.PREAUTH
        INST_PAY.containsMatchIn(all) -> NotiKind.INST_PAY
        OTHER_WORDS.any { it in all } -> NotiKind.OTHER
        else -> NotiKind.SPEND
    }
    if (kind == NotiKind.INST_PAY || kind == NotiKind.OTHER) {
        return NotiRead(kind, best?.value ?: 0L, best?.cur ?: "", last4 = last4, day = day, minute = minute, spans = spans)
    }
    if (best == null) {
        return NotiRead(if (kind == NotiKind.SPEND) NotiKind.NO_AMOUNT else NotiKind.OTHER, last4 = last4, day = day, minute = minute, spans = spans)
    }
    val inst = if (kind == NotiKind.SPEND) INST_COUNT.find(all)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 2..60 } ?: 0 else 0

    // 商店（只看內文：Samsung Wallet 的標題是卡片名稱）
    // 內文裡的金額、日期、時間、末四碼的位置
    val bodyUsed = (used + listOf(best.range)).filter { it.first >= off }.map { (it.first - off)..(it.last - off) }

    var merchant = ""
    var sure = false
    // 1. 「在／於 ___ 刷卡／消費」
    run {
        for (v in VERBS) {
            var vi = body.indexOf(v)
            while (vi >= 0) {
                val from = maxOf(0, vi - 40)
                val seg = body.substring(from, vi)
                val at = maxOf(seg.lastIndexOf('在'), seg.lastIndexOf('於'))
                // 中間夾著金額、日期的不算；只剩常見字（「於您的信用卡消費」）也不算
                if (at >= 0 && !overlaps((from + at + 1) until vi, bodyUsed)) {
                    // 「在全聯福利中心之消費退款」：後面的「之、的」拿掉
                    val cand = seg.substring(at + 1).trim().trim(':', ',', ' ').trimEnd('之', '的').trim()
                    var rest = cand
                    NOISE.forEach { rest = rest.replace(it, "") }
                    if (rest.length >= 2 && rest.any { it.isLetter() }) { merchant = cand; sure = true; return@run }
                }
                vi = body.indexOf(v, vi + 1)
            }
        }
    }
    // 2. 括號裡的字
    if (merchant.isEmpty()) {
        for (m in Regex("""[【「\[〔(（]([^】」\]〕)）]{2,30})[】」\]〕)）]""").findAll(body)) {
            val c = m.groupValues[1].trim()
            if ("通知" in c || "信用卡" in c || c.all { it.isDigit() || it in "/:-. " }) continue
            merchant = c; sure = true; break
        }
    }
    // 3. 刪掉金額、日期、常見字，剩下最長的一段
    if (merchant.isEmpty()) {
        val sb = StringBuilder(body)
        bodyUsed.sortedByDescending { it.first }.forEach { r -> if (r.last < sb.length) sb.replace(r.first, r.last + 1, "\u0000") }
        var rest = sb.toString()
        // 金額旁邊的幣別、元
        CUR_PREFIX.forEach { (p, _) -> rest = rest.replace("$p\u0000", "\u0000") }
        CUR_SUFFIX.forEach { (p, _) -> rest = rest.replace("\u0000$p", "\u0000") }
        NOISE.forEach { rest = rest.replace(it, "\u0000") }
        merchant = rest.split(Regex("""[\u0000，。！!、:：;；【】「」\[\]()（）・·|]+"""))
            .map { it.trim().trim(',', '.', '-', ' ') }
            .filter { s -> s.length >= 2 && s.any { it.isLetter() } && !s.all { it.isDigit() } }
            .maxByOrNull { it.length } ?: ""
    }
    if (merchant.isNotEmpty()) body.indexOf(merchant).takeIf { it >= 0 }?.let { spans[TplField.MERCHANT] = it until it + merchant.length }
    val (shop, wallet) = splitWallet(merchant, all)
    // 像廣告：沒有末四碼、沒寫刷卡這類字，又有廣告常見字（「TWQR條碼付最高拿500元！」）
    val ads = if (kind == NotiKind.SPEND && last4.isEmpty() && VERBS.none { it in all }) AD_WORDS.filter { it in all } else emptyList()
    return NotiRead(
        if (ads.isNotEmpty()) NotiKind.AD else kind, best.value, best.cur, shop.take(30), wallet, last4, day, minute,
        sure && shop.isNotEmpty(), spans, inst, ads,
    )
}

/** 金額的數字（「1,288」「30.00」）換成記帳的金額：台幣小數四捨五入（30.00 → 30），外幣是最小單位；讀不出來回傳 null */
internal fun amountValue(raw: String, cur: String): Long? {
    val parts = raw.trim().trimEnd(',').split('.')
    val digits = parts[0].replace(",", "")
    if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
    val frac = parts.getOrNull(1) ?: ""
    return if (cur.isEmpty()) {
        val whole = digits.toLongOrNull() ?: return null
        whole + if ((frac.firstOrNull() ?: '0') >= '5') 1 else 0
    } else {
        val dec = Currencies.of(cur).decimals
        (digits + (frac + "0".repeat(dec)).take(dec)).toLongOrNull()
    }
}

/** 商店裡的錢包字樣拆出來（「OP錢包統一超商」→ 統一超商、OP錢包）；商店裡沒有就看整則通知有沒有寫 */
internal fun splitWallet(merchant: String, all: String): Pair<String, String> {
    for ((w, name) in WALLETS) {
        val i = merchant.indexOf(w, ignoreCase = true)
        if (i >= 0) return (merchant.substring(0, i) + merchant.substring(i + w.length)).trim().trim('-', '_', '/', ' ') to name
    }
    return merchant to (WALLETS.firstOrNull { (w, _) -> all.contains(w, ignoreCase = true) }?.second ?: "")
}
