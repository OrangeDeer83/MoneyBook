package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/**
 * 訂閱／固定扣款：設定一次，到了扣款日首頁出現提醒，按「記」才記成一筆支出（見 ui/Subs.kt）。
 * 處理過（記了或略過）的扣款日用 doneUntil ＋ doneDays 記：doneUntil（含）以前都處理過了，
 * doneDays 是 doneUntil 之後跳著處理過的那幾天（先處理後面的那一筆時用）。
 */
data class Sub(
    val id: Long,
    val bookId: Long,
    val name: String,
    /** 金額：台幣（cur 空白）或外幣的最小單位（US$15.49 → 1549） */
    val amount: Long,
    /** 幣別；空白＝台幣 */
    val cur: String = "",
    val categoryId: Long?,
    val accountId: Long?,
    val note: String = "",
    val tags: List<String> = emptyList(),
    /** 多久扣一次：FREQ_WEEK、FREQ_MONTH、FREQ_YEAR */
    val freq: Int = FREQ_MONTH,
    /** 間隔：每 every 週／個月／年 */
    val every: Int = 1,
    /** 每週：星期幾（0＝星期日…6＝星期六）；每月：幾號（1～31，MONTH_END＝月底）；每年：月×100＋日（301＝3 月 1 號） */
    val days: List<Int>,
    /** 從哪天開始（epoch day）；也是每 N 週／個月／年從哪一期開始數 */
    val start: Long,
    /** 結束：END_NONE 不結束、END_COUNT 扣 endCount 次、END_UNTIL 到 endDay（含）為止 */
    val endMode: Int = END_NONE,
    val endCount: Int = 12,
    val endDay: Long = 0L,
    val paused: Boolean = false,
    val doneUntil: Long,
    val doneDays: List<Long> = emptyList(),
) {
    val isForeign: Boolean get() = cur.isNotEmpty()

    companion object {
        const val FREQ_WEEK = 0
        const val FREQ_MONTH = 1
        const val FREQ_YEAR = 2
        const val MONTH_END = 32
        const val END_NONE = 0
        const val END_COUNT = 1
        const val END_UNTIL = 2
        /** 自動記的記錄都加這個標籤 */
        const val TAG = "訂閱"
    }
}

/**
 * 從開始那天到 until（含）的所有扣款日（epoch day，由早到晚），照結束條件截斷。
 * 每月選 31 號遇到小月算那個月最後一天；同一天只算一次。
 */
fun Sub.dates(until: Long): List<Long> {
    val out = ArrayList<Long>()
    val s = LocalDate.ofEpochDay(start)
    val ev = every.coerceAtLeast(1).toLong()
    val limit = if (endMode == Sub.END_UNTIL) minOf(until, endDay) else until
    if (days.isEmpty() || limit < start) return out
    val sunday = s.minusDays((s.dayOfWeek.value % 7).toLong())
    var k = 0L
    while (k < 100_000) {
        val batch: List<LocalDate> = when (freq) {
            Sub.FREQ_WEEK -> {
                val w = sunday.plusWeeks(k * ev)
                days.filter { it in 0..6 }.distinct().sorted().map { w.plusDays(it.toLong()) }
            }
            Sub.FREQ_YEAR -> {
                val md = days.first()
                val ym = YearMonth.of(s.year + (k * ev).toInt(), (md / 100).coerceIn(1, 12))
                listOf(ym.atDay((md % 100).coerceIn(1, ym.lengthOfMonth())))
            }
            else -> {
                val ym = YearMonth.from(s).plusMonths(k * ev)
                days.map { if (it >= Sub.MONTH_END) ym.atEndOfMonth() else ym.atDay(it.coerceIn(1, ym.lengthOfMonth())) }.distinct().sorted()
            }
        }
        for (dt in batch) {
            val e = dt.toEpochDay()
            if (e < start) continue
            if (e > limit) return out
            if (endMode == Sub.END_COUNT && out.size >= endCount) return out
            out.add(e)
        }
        if (endMode == Sub.END_COUNT && out.size >= endCount) return out
        k++
    }
    return out
}

/** 已經處理過（記了或略過）這一天的扣款了嗎 */
fun Sub.isDone(day: Long): Boolean = day <= doneUntil || day in doneDays

/** 到期還沒處理的扣款日（今天以前，由早到晚）；暫停的沒有 */
fun Sub.due(today: Long): List<Long> = if (paused) emptyList() else dates(today).filter { !isDone(it) }

/** 下一次扣款日：還沒處理的最早那一天（可能已經過了）；之後不會再扣（結束了）回傳 null */
fun Sub.next(today: Long): Long? {
    val ev = every.coerceAtLeast(1).toLong()
    val horizon = maxOf(today, doneUntil) + when (freq) {
        Sub.FREQ_WEEK -> 7L * ev + 7L
        Sub.FREQ_YEAR -> 366L * ev + 366L
        else -> 31L * ev + 31L
    }
    return dates(horizon).firstOrNull { !isDone(it) }
}

/** 把 day 這一次標成處理過；連續處理過的往前併進 doneUntil */
fun Sub.markDone(day: Long): Sub {
    val set = (doneDays + day).filter { it > doneUntil }.toSet()
    if (set.isEmpty()) return this
    var du = doneUntil
    for (e in dates(set.max())) {
        if (e <= du) continue
        if (e in set) du = e else break
    }
    return copy(doneUntil = du, doneDays = set.filter { it > du }.sorted())
}

/** 新的訂閱從哪天開始提醒：開始日在今天以前的，今天以前那幾次不提醒（要補記自己記） */
fun newDoneUntil(start: Long, today: Long): Long = maxOf(start, today) - 1

/** 平均一個月扣幾次（每週 = 每週幾次 × 52 ÷ 12） */
fun Sub.perMonth(): Double {
    val ev = every.coerceAtLeast(1).toDouble()
    return when (freq) {
        Sub.FREQ_WEEK -> days.filter { it in 0..6 }.distinct().size * 52.0 / 12.0 / ev
        Sub.FREQ_YEAR -> 1.0 / 12.0 / ev
        else -> days.map { if (it >= 31) Sub.MONTH_END else it }.distinct().size / ev
    }
}

/** 一次扣款的台幣金額：外幣用目前匯率換算，沒有匯率回傳 null */
fun AppData.subTwd(s: Sub): Long? =
    if (!s.isForeign) s.amount else rateOf(s.cur)?.let { fxToTwd(s.amount, Currencies.of(s.cur).decimals, it) }

/** 目前帳本沒暫停的訂閱，平均每個月扣多少台幣（每年的除以 12；外幣用目前匯率，沒有匯率的不算） */
fun AppData.subsMonthly(today: Long): Long = bookSubs.filter { !it.paused && it.next(today) != null }
    .sumOf { s -> (subTwd(s) ?: 0L) * s.perMonth() }.let { Math.round(it) }

/** 目前帳本的訂閱 */
val AppData.bookSubs: List<Sub> get() = subs.filter { it.bookId == currentBook.id }

/** 目前帳本到期還沒處理的扣款（訂閱, 扣款日），最早的在前面 */
fun AppData.subsDue(today: Long): List<Pair<Sub, Long>> =
    bookSubs.flatMap { s -> s.due(today).map { s to it } }.sortedWith(compareBy({ it.second }, { it.first.id }))

/**
 * 這一次扣款要記成的那一筆支出（備註、標籤照訂閱，多一個「訂閱」標籤；沒有時間）。
 * twd 是台幣金額（外幣用匯率換的）；外幣訂閱扣台幣帳戶時記成「外幣消費」，pending＝待請款。
 */
fun AppData.subTxn(s: Sub, day: Long, id: Long, twd: Long, pending: Boolean): Txn {
    val acc = s.accountId?.let { accMap[it] }
    val base = Txn(
        id = id, bookId = s.bookId, type = TxType.EXPENSE, amount = twd,
        categoryId = s.categoryId, accountId = s.accountId, toAccountId = null,
        day = day, note = s.note, tags = (s.tags + Sub.TAG).distinct(),
    )
    return when {
        !s.isForeign -> base.copy(amount = s.amount)
        acc != null && acc.isForeign -> base.copy(fxAmount = s.amount)
        else -> base.copy(fxSpendAmount = s.amount, fxSpendCur = s.cur, fxPending = pending)
    }
}

/** 扣款頻率的說明文字：「每月 5 號」「每 2 週的星期二、四」「每年 3/1」 */
fun Sub.ruleText(): String {
    val ev = every.coerceAtLeast(1)
    return when (freq) {
        Sub.FREQ_WEEK -> {
            val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val w = days.filter { it in 0..6 }.distinct().sorted().joinToString("、") { names[it] }
            (if (ev == 1) "每週" else "每 $ev 週的") + "星期$w"
        }
        Sub.FREQ_YEAR -> {
            val md = days.firstOrNull() ?: 101
            (if (ev == 1) "每年 " else "每 $ev 年 ") + "${md / 100}/${md % 100}"
        }
        else -> {
            val ds = days.distinct().sorted().joinToString("、") { if (it >= Sub.MONTH_END) "月底" else "$it" }
            (if (ev == 1) "每月 " else "每 $ev 個月 ") + ds + if (days.all { it >= Sub.MONTH_END }) "" else " 號"
        }
    }
}

object SubCodec {
    fun toJson(list: List<Sub>): JSONArray = JSONArray().apply {
        list.forEach { s ->
            put(
                JSONObject().put("id", s.id).put("bookId", s.bookId).put("name", s.name).put("amount", s.amount).put("cur", s.cur)
                    .put("categoryId", s.categoryId ?: JSONObject.NULL).put("accountId", s.accountId ?: JSONObject.NULL)
                    .put("note", s.note).put("tags", JSONArray(s.tags))
                    .put("freq", s.freq).put("every", s.every).put("days", JSONArray(s.days)).put("start", s.start)
                    .put("endMode", s.endMode).put("endCount", s.endCount).put("endDay", s.endDay)
                    .put("paused", s.paused).put("doneUntil", s.doneUntil).put("doneDays", JSONArray(s.doneDays))
            )
        }
    }

    fun fromJson(a: JSONArray?, defaultBook: Long): List<Sub> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                val days = o.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.optInt(it) } } ?: emptyList()
                val start = o.getLong("start")
                Sub(
                    id = o.getLong("id"),
                    bookId = o.optLong("bookId", defaultBook),
                    name = o.optString("name", ""),
                    amount = o.optLong("amount", 0L),
                    cur = o.optString("cur", ""),
                    categoryId = if (o.isNull("categoryId")) null else o.optLong("categoryId"),
                    accountId = if (o.isNull("accountId")) null else o.optLong("accountId"),
                    note = o.optString("note", ""),
                    tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
                    freq = o.optInt("freq", Sub.FREQ_MONTH),
                    every = o.optInt("every", 1).coerceAtLeast(1),
                    days = days,
                    start = start,
                    endMode = o.optInt("endMode", Sub.END_NONE),
                    endCount = o.optInt("endCount", 12),
                    endDay = o.optLong("endDay", 0L),
                    paused = o.optBoolean("paused", false),
                    doneUntil = o.optLong("doneUntil", start - 1),
                    doneDays = o.optJSONArray("doneDays")?.let { d -> (0 until d.length()).map { d.optLong(it) } } ?: emptyList(),
                )
            }.getOrNull()
        }
    }
}
