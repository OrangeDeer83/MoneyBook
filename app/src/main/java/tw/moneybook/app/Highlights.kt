package tw.moneybook.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.random.Random

/** 本月重點一句話裡的一段：一般、好的（綠）、不好的（紅）、名稱（主色） */
enum class Tone { PLAIN, GOOD, BAD, NAME }

/** 吉祥物講這句時的表情（對應 ui.Mood） */
enum class Feel { HAPPY, NORMAL, WORRIED, SLEEPY, CHEER }

data class Part(val text: String, val tone: Tone = Tone.PLAIN)

/** 一句重點：key 是哪一種句子（測試、去重用） */
data class Highlight(val key: String, val feel: Feel, val parts: List<Part>) {
    val text: String get() = parts.joinToString("") { it.text }
}

private class Say(val key: String, val feel: Feel) {
    val parts = ArrayList<Part>()
    fun t(s: String) { parts.add(Part(s)) }
    fun good(s: String) { parts.add(Part(s, Tone.GOOD)) }
    fun bad(s: String) { parts.add(Part(s, Tone.BAD)) }
    fun name(s: String) { parts.add(Part(s, Tone.NAME)) }
}

private fun say(key: String, feel: Feel, f: Say.() -> Unit): Highlight = Say(key, feel).apply(f).let { Highlight(it.key, it.feel, it.parts) }

private fun pct(a: Long, b: Long): Long = if (b == 0L) 0L else Math.round(a * 100.0 / b)
private fun md(day: Long): String = LocalDate.ofEpochDay(day).let { "${it.monthValue}/${it.dayOfMonth}" }

/**
 * 看哪一段期間的重點：月（month 不是 null）或區間（start～end）。
 * 這個月還沒過完時只看到今天為止，和上個月「同一段日子」比，才不會拿半個月去比整個月。
 */
class HlPeriod(val start: Long, val end: Long, val month: YearMonth?, val today: LocalDate) {
    val isMonth: Boolean get() = month != null
    /** 這個月（還沒過完） */
    val current: Boolean get() = month != null && month == YearMonth.from(today)
    /** 句子開頭怎麼稱呼這段期間 */
    val label: String get() = when {
        month == null -> "這段期間"
        current -> "這個月"
        else -> "${month.monthValue} 月"
    }
    /** 實際算到哪一天（這個月只到今天） */
    val until: Long get() = if (current) today.toEpochDay() else end
    val days: Int get() = (until - start + 1).toInt().coerceAtLeast(1)
    /** 拿來比較的前一段：月是上個月同樣的日子（這個月還沒過完）或整個上個月；區間是前一段同樣長的期間 */
    val prev: Pair<Long, Long>
        get() = if (month != null) {
            val pm = month.minusMonths(1)
            val s = pm.atDay(1).toEpochDay()
            if (current) s to minOf(pm.atEndOfMonth().toEpochDay(), s + days - 1) else s to pm.atEndOfMonth().toEpochDay()
        } else (start - days) to (start - 1)

    companion object {
        fun month(m: YearMonth, today: LocalDate = LocalDate.now()) = HlPeriod(m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay(), m, today)
        fun range(start: Long, end: Long, today: LocalDate = LocalDate.now()) = HlPeriod(start, end, null, today)
    }
}

/** 這筆支出算在哪個大分類 */
private fun AppData.topName(t: Txn): String = t.categoryId?.let { catMap[it] }?.let { topOf(it).name } ?: "未分類"
private fun AppData.topId(t: Txn): Long = t.categoryId?.let { catMap[it] }?.let { topOf(it).id } ?: -1L

/** 這筆記錄怎麼稱呼：有備註用備註，沒有用分類 */
private fun AppData.callOf(t: Txn): String = t.note.trim().lines().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.take(12)
    ?: t.categoryId?.let { catMap[it]?.name } ?: "未分類"

/**
 * 某段期間（目前帳本）所有符合條件的重點句子，順序固定（預算在最前面）。
 * 每種句子都有門檻，沒達到就不講；這段期間沒有記錄時只有一句提醒。
 */
fun AppData.highlights(p: HlPeriod): List<Highlight> {
    val book = bookTxns
    val scope = book.filter { it.day in p.start..p.until }
    if (scope.none { !it.adjust }) {
        return listOf(say("empty", Feel.NORMAL) {
            t(if (p.isMonth) "${p.label}還沒有記帳喔，記幾筆我就能幫你整理重點～" else "這段期間沒有記錄喔")
        })
    }
    val out = ArrayList<Highlight>()
    val exp = scope.expenseSum()
    val inc = scope.incomeSum()
    val (ps, pe) = p.prev
    val prevScope = book.filter { it.day in ps..pe }
    val spends = scope.filter { it.type == TxType.EXPENSE && it.spent > 0 }

    // 預算（只有月、有設預算）：只算「算預算」的支出，預算外的另外一句
    val budget = p.month?.let { currentBook.budgetFor(it) } ?: 0L
    val bud = budgetSpent(scope)
    if (budget > 0) {
        out.add(
            if (p.current) {
                val daysLeft = (p.month!!.lengthOfMonth() - p.today.dayOfMonth + 1).coerceAtLeast(1)
                if (bud > budget) say("budget", Feel.WORRIED) {
                    t("預算已經超過 "); bad(formatMoney(bud - budget)); t(" 了…還有 $daysLeft 天，我們一起省一點好嗎？")
                } else {
                    val left = budget - bud
                    // 花的比例比過的日子快 10% 以上就擔心
                    val fast = pct(bud, budget) > pct(p.today.dayOfMonth.toLong(), p.month.lengthOfMonth().toLong()) + 10
                    say("budget", if (fast) Feel.WORRIED else Feel.HAPPY) {
                        t("預算用了 ${pct(bud, budget)}%，還剩 "); if (fast) bad(formatMoney(left)) else good(formatMoney(left))
                        t("，接下來每天還能花 ${formatMoney(left / daysLeft)}")
                    }
                }
            } else if (bud > budget) say("budget", Feel.WORRIED) {
                t("${p.label}超過預算 "); bad(formatMoney(bud - budget)); t("，下個月再加油")
            } else say("budget", Feel.HAPPY) {
                t("${p.label}預算守住了！還剩 "); good(formatMoney(budget - bud))
            }
        )
    }

    // 預算外花了多少，主要是哪個大分類
    val outs = spends.filter { isOutOfBudget(it) }
    if (outs.isNotEmpty()) {
        val main = outs.groupBy { topId(it) }.maxByOrNull { e -> e.value.sumOf { it.spent } }!!
        out.add(say("outBudget", Feel.NORMAL) {
            t("${p.label}預算外花了 ${formatMoney(outs.sumOf { it.spent })}，主要是「"); name(topName(main.value.first())); t("」")
        })
    }

    // 存下多少
    when {
        inc == 0L && exp > 0 -> out.add(say("saved", Feel.NORMAL) { t("${p.label}還沒有收入，目前花了 ${formatMoney(exp)}") })
        inc > 0 && inc >= exp -> {
            val r = pct(inc - exp, inc)
            out.add(say("saved", if (r >= 20) Feel.CHEER else Feel.HAPPY) {
                t(if (p.current) "這個月到現在存下 " else "${p.label}存下 "); good(formatMoney(inc - exp)); t("，是收入的 $r%" + if (r >= 20) " 喔！" else "")
            })
        }
        inc > 0 -> out.add(say("saved", Feel.WORRIED) { t("${p.label}花的比賺的多 "); bad(formatMoney(exp - inc)) })
    }

    // 比上個月（前一段）多花或少花，主要是哪個分類
    val prevExp = prevScope.expenseSum()
    val diff = exp - prevExp
    if (prevExp > 0 && abs(diff) >= 500 && abs(diff) * 10 >= prevExp) {
        val byCat = HashMap<Long, Long>()
        spends.forEach { byCat[topId(it)] = (byCat[topId(it)] ?: 0L) + it.spent }
        prevScope.filter { it.type == TxType.EXPENSE && it.spent > 0 }.forEach { byCat[topId(it)] = (byCat[topId(it)] ?: 0L) - it.spent }
        val main = (if (diff > 0) byCat.maxByOrNull { it.value } else byCat.minByOrNull { it.value })?.takeIf { (it.value > 0) == (diff > 0) }
        val mainName = main?.let { e -> catMap[e.key]?.name ?: "未分類" }
        val than = when {
            !p.isMonth -> "比前 ${p.days} 天"
            p.current -> "比上個月同一段時間"
            else -> "比 ${p.month!!.minusMonths(1).monthValue} 月"
        }
        out.add(
            if (diff > 0) say("vsPrev", Feel.WORRIED) {
                t("${than}多花 "); bad(formatMoney(diff))
                if (main != null) { t("，主要是「"); name(mainName!!); t("」+${formatMoney(main.value)}") }
            } else say("vsPrev", Feel.HAPPY) {
                t("${than}少花 "); good(formatMoney(-diff))
                if (main != null) { t("，「"); name(mainName!!); t("」少了 ${formatMoney(-main.value)}") }
            }
        )
    }

    // 某分類比平常（前 3 個月平均，照天數換算）多或少 30% 以上、差 $1,000 以上
    run {
        val base = p.month ?: YearMonth.from(LocalDate.ofEpochDay(p.start))
        val from = base.minusMonths(3).atDay(1).toEpochDay()
        val to = base.minusMonths(1).atEndOfMonth().toEpochDay()
        val hist = book.filter { it.day in from..to && it.type == TxType.EXPENSE && it.spent > 0 }
        if (hist.isEmpty()) return@run
        val histDays = (to - from + 1).toDouble()
        val now = spends.groupBy { topId(it) }.mapValues { e -> e.value.sumOf { it.spent } }
        val before = hist.groupBy { topId(it) }.mapValues { e -> e.value.sumOf { it.spent } }
        val cand = (now.keys + before.keys).mapNotNull { id ->
            val cur = now[id] ?: 0L
            val expect = Math.round((before[id] ?: 0L) / histDays * p.days)
            val avg = Math.round((before[id] ?: 0L) / 3.0)
            if (expect <= 0 || abs(cur - expect) < 1000 || abs(cur - expect) * 10 < expect * 3) null
            else Triple(id, cur - expect, avg)
        }
        val best = cand.maxByOrNull { abs(it.second) } ?: return@run
        val n = catMap[best.first]?.name ?: "未分類"
        val r = pct(abs(best.second), Math.round((before[best.first] ?: 0L) / histDays * p.days))
        out.add(
            if (best.second > 0) say("catAvg", Feel.WORRIED) {
                t("「"); name(n); t("」比平常多了 "); bad("$r%"); t("（前 3 個月平均一個月 ${formatMoney(best.third)}）")
            } else say("catAvg", Feel.HAPPY) {
                t("「"); name(n); t("」比平常少了 "); good("$r%"); t("（前 3 個月平均一個月 ${formatMoney(best.third)}），很棒！")
            }
        )
    }

    // 第一次花在某分類（這個帳本以前沒花過的小分類），講花最多的那個
    run {
        val seen = book.filter { it.day < p.start && it.type == TxType.EXPENSE }.mapNotNull { it.categoryId }.toSet()
        if (seen.isEmpty()) return@run
        val first = spends.filter { it.categoryId != null && it.categoryId !in seen }.groupBy { it.categoryId!! }
            .maxByOrNull { e -> e.value.sumOf { it.spent } } ?: return@run
        val n = catMap[first.key]?.name ?: return@run
        out.add(say("firstCat", Feel.NORMAL) { t("${p.label}第一次花在「"); name(n); t("」，花了 ${formatMoney(first.value.sumOf { it.spent })}") })
    }

    // 最大一筆
    spends.maxByOrNull { it.spent }?.takeIf { spends.size >= 2 }?.let { t0 ->
        out.add(say("biggest", Feel.NORMAL) { t("最大一筆是 ${md(t0.day)} 的「"); name(callOf(t0)); t("」${formatMoney(t0.spent)}") })
    }

    // 每個月固定的帳：前 3 個月每個月都有、金額差不多（差 10% 以內）的同一個分類＋備註
    if (p.isMonth) run {
        val m = p.month!!
        val months = (1..3).map { m.minusMonths(it.toLong()) }
        val groups = book.filter { it.type == TxType.EXPENSE && it.spent > 0 && it.day >= months.last().atDay(1).toEpochDay() && it.day < p.start }
            .groupBy { (it.categoryId ?: -1L) to it.note.trim() }
        val fixed = groups.filter { (_, ts) ->
            val per = months.map { mm -> ts.filter { it.month == mm } }
            if (per.any { it.size != 1 }) return@filter false
            val amts = per.map { it[0].spent }
            amts.max() - amts.min() <= amts.max() / 10
        }
        if (fixed.isEmpty()) return@run
        val missing = fixed.filter { (k, ts) ->
            val usual = ts.maxOf { it.date.dayOfMonth }
            spends.none { (it.categoryId ?: -1L) == k.first && it.note.trim() == k.second } && (!p.current || p.today.dayOfMonth > usual)
        }
        val one = missing.entries.firstOrNull()
        if (one != null) {
            val t0 = one.value.last()
            out.add(say("fixed", Feel.NORMAL) { t("每個月固定的「"); name(callOf(t0)); t("」${formatMoney(t0.spent)}，${p.label}還沒記喔") })
        } else {
            val sum = fixed.values.sumOf { it.last().spent }
            val names = fixed.values.take(2).joinToString("、") { "「${callOf(it.last())}」" }
            out.add(say("fixed", Feel.NORMAL) {
                t("每個月固定的帳有 ${fixed.size} 筆（$names${if (fixed.size > 2) "…" else ""}），一共 ${formatMoney(sum)}" + if (exp > 0) "，佔支出 ${pct(sum, exp)}%" else "")
            })
        }
    }

    // 收入變化（這個月還沒過完不比，薪水常常一個月才進來一次）
    if (!p.current) {
        val prevInc = prevScope.incomeSum()
        val d = inc - prevInc
        if (inc > 0 && prevInc > 0 && abs(d) >= 1000 && abs(d) * 10 >= prevInc) {
            val than = if (p.isMonth) "比 ${p.month!!.minusMonths(1).monthValue} 月" else "比前 ${p.days} 天"
            out.add(
                if (d > 0) say("income", Feel.CHEER) { t("收入${than}多了 "); good(formatMoney(d)) }
                else say("income", Feel.NORMAL) { t("收入${than}少了 ${formatMoney(-d)}") }
            )
        }
    }

    // 和去年同月比（這個月比去年同一段日子）
    if (p.isMonth) {
        val ly = p.month!!.minusYears(1)
        val s = ly.atDay(1).toEpochDay()
        val e = if (p.current) minOf(ly.atEndOfMonth().toEpochDay(), s + p.days - 1) else ly.atEndOfMonth().toEpochDay()
        val lastYear = book.filter { it.day in s..e }.expenseSum()
        val d = exp - lastYear
        if (lastYear > 0 && abs(d) >= 500 && abs(d) * 10 >= lastYear) {
            out.add(
                if (d > 0) say("yoy", Feel.WORRIED) { t("比去年 ${ly.monthValue} 月${if (p.current) "同一段時間" else ""}多花 "); bad(formatMoney(d)) }
                else say("yoy", Feel.HAPPY) { t("比去年 ${ly.monthValue} 月${if (p.current) "同一段時間" else ""}少花 "); good(formatMoney(-d)) }
            )
        }
    }

    // 平均每天，週末和平日差很多時多講一句
    if (exp > 0 && p.days >= 3) {
        val dayList = (p.start..p.until).map { LocalDate.ofEpochDay(it) }
        val weekend = dayList.filter { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }.map { it.toEpochDay() }.toSet()
        val we = spends.filter { it.day in weekend }.sumOf { it.spent }
        val wd = exp - we
        val nWe = weekend.size
        val nWd = dayList.size - nWe
        val avgWe = if (nWe > 0) we / nWe else 0L
        val avgWd = if (nWd > 0) wd / nWd else 0L
        out.add(say("perDay", Feel.NORMAL) {
            t("平均每天花 ${formatMoney(exp / p.days)}")
            if (avgWe > 0 && avgWd > 0 && avgWe * 10 >= avgWd * 15) t("，週末平均 ${formatMoney(avgWe)}（平日的 ${String.format("%.1f", avgWe.toDouble() / avgWd)} 倍）")
        })
    }

    // 哪個時段花最多（有時間的支出至少 5 筆）
    run {
        val timed = spends.filter { it.time >= 0 }
        if (timed.size < 5) return@run
        fun slot(min: Int) = when (min / 60) {
            in 5..10 -> "早上"
            in 11..13 -> "中午"
            in 14..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
        val g = timed.groupBy { slot(it.time) }.mapValues { e -> e.value.sumOf { it.spent } }
        val top = g.maxByOrNull { it.value } ?: return@run
        out.add(say("timeOfDay", Feel.NORMAL) { t("最常在"); name(top.key); t("花錢：一共 ${formatMoney(top.value)}，佔有記時間的 ${pct(top.value, timed.sumOf { it.spent })}%") })
    }

    // 記帳的習慣：這個月好幾天沒記（3 天以上），或記了幾筆、幾天
    run {
        val real = scope.filter { !it.adjust }
        val last = book.filter { !it.adjust && it.day <= p.today.toEpochDay() }.maxOfOrNull { it.day }
        val gap = last?.let { p.today.toEpochDay() - it } ?: 0L
        if (p.current && gap >= 3) out.add(say("habit", Feel.SLEEPY) { t("已經 $gap 天沒記帳了，最近有花錢嗎？") })
        else out.add(say("habit", Feel.HAPPY) { t("${p.label}記了 ${real.size} 筆，有 ${real.map { it.day }.distinct().size} 天都有記帳") })
    }

    // 總資產比上個月（只有月；所有帳本一起算）
    if (p.isMonth) {
        val (a, b) = netWorthMonths(listOf(p.month!!.minusMonths(1), p.month), p.today)
        val d = b.net - a.net
        if (d != 0L) out.add(
            if (d > 0) say("asset", Feel.CHEER) { t("總資產${if (p.current) "比上個月底" else "比 ${p.month.minusMonths(1).monthValue} 月底"}多了 "); good(formatMoney(d)) }
            else say("asset", Feel.NORMAL) { t("總資產${if (p.current) "比上個月底" else "比 ${p.month.minusMonths(1).monthValue} 月底"}少了 ${formatMoney(-d)}") }
        )
    }

    // 外幣消費還在等請款（只有看這個月時講）
    if (p.current) {
        val pending = fxPendingList()
        if (pending.isNotEmpty()) out.add(say("fxPending", Feel.NORMAL) { t("有 ${pending.size} 筆外幣消費還在等請款（預估 ${formatMoney(pending.sumOf { it.amount })}）") })
    }
    return out
}

/**
 * 從所有句子挑最多 max 句：預算那句（有的話）固定第一句，其他照 seed 隨機挑。
 * seed 在記一筆、改一筆或隔一天時會變（見 highlightSeed），同一天沒動資料時每次打開都一樣。
 */
fun pickHighlights(all: List<Highlight>, seed: Long, max: Int = 5): List<Highlight> {
    val first = all.filter { it.key == "budget" }
    val rest = all.filter { it.key != "budget" }.shuffled(Random(seed))
    return (first + rest).take(max)
}

/** 決定這一批句子的 seed：日期加上記錄的內容（新增、修改、刪除都會變） */
fun AppData.highlightSeed(today: LocalDate = LocalDate.now()): Long =
    today.toEpochDay() * 1_000_003L + txns.size * 7919L + txns.sumOf { it.amount * 31 + it.day + it.id }
