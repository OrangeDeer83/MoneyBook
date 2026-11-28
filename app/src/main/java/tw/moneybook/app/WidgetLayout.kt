package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth

/*
 * 自組桌面小工具（使用者 11/28 在試玩頁選的「要」，草圖 ci-out/widget_compose2.png）：
 *  - 一份一份的排版存在 App 資料裡（跟著備份），桌面上每個小工具選一份來畫（綁定記在手機上，見 widget/ComposeWidget.kt）
 *  - 排版跟統計卡片的調整排版一樣是一排一排：整排、半排（兩個半排並排）；還沒加的區塊放在 hidden（排版頁的「可以加的區塊」）
 *  - 照順序從上面畫，放不下的不畫（不縮小字）
 */

/**
 * 一份排版。opts 是區塊的設定：tpls＝常用記帳 id（逗號分隔，照順序）、add＝記一筆打開的類型（EXPENSE／INCOME／TRANSFER）、
 * shortcut1～3＝捷徑打開的頁面（見 WBlocks.SHORTCUTS）
 */
data class WLayout(
    val id: Long,
    val name: String,
    val layout: CardLayout,
    val opts: Map<String, String> = emptyMap(),
    /** 看哪一本帳本；0＝跟著 App 目前選的 */
    val bookId: Long = 0L,
    /** 背景：0 不透明、1 半透明、2 透明（只留區塊的底色） */
    val bg: Int = 0,
    /** 顏色：0 跟 App 一樣、1 淺色、2 深色 */
    val tone: Int = 0,
    /** 字的大小：0 小、1 中、2 大 */
    val font: Int = 1,
)

object WBlocks {
    const val SPEND = "spend"
    const val INCOME = "income"
    const val NET = "net"
    const val TODAY = "today"
    const val LEFT = "left"
    const val PERDAY = "perday"
    const val BAR = "bar"
    const val REIMB = "reimb"
    const val DEBT = "debt"
    const val PRE = "pre"
    const val PEND = "pend"
    const val SUB = "sub"
    const val ASSET = "asset"
    const val ADD = "add"
    const val TPLS = "tpls"
    const val WEEK = "week"
    const val TOP3 = "top3"
    const val MASCOT = "mascot"
    val SHORTCUT_IDS = listOf("shortcut1", "shortcut2", "shortcut3")

    /** 常用記帳區塊最多放幾個 */
    const val TPL_MAX = 8

    /** 捷徑可以打開的頁面（open 的值、名字） */
    val SHORTCUTS = listOf(
        "preorders" to "預購清單", "reimb" to "報銷／欠款", "stats" to "統計", "accounts" to "帳戶", "budget" to "預算", "search" to "搜尋",
    )

    /** 數字區塊（標題＋一個數字） */
    val NUMBERS = setOf(SPEND, INCOME, NET, TODAY, LEFT, PERDAY, REIMB, DEBT, PRE, PEND, SUB, ASSET)

    private val specs = listOf(
        CardSpec(SPEND, "本月支出", canHalf = true),
        CardSpec(INCOME, "本月收入", canHalf = true),
        CardSpec(NET, "本月結餘", canHalf = true),
        CardSpec(TODAY, "今天花了", canHalf = true),
        CardSpec(LEFT, "剩餘預算", canHalf = true),
        CardSpec(PERDAY, "每天還能花", canHalf = true),
        CardSpec(BAR, "預算進度條"),
        CardSpec(REIMB, "待收報銷", canHalf = true),
        CardSpec(DEBT, "我欠別人", canHalf = true),
        CardSpec(PRE, "預購預留", canHalf = true),
        CardSpec(PEND, "刷卡待確認", canHalf = true),
        CardSpec(SUB, "下一筆扣款", canHalf = true),
        CardSpec(ASSET, "總資產", canHalf = true),
        CardSpec(ADD, "＋ 記一筆", canHalf = true),
        CardSpec(TPLS, "常用記帳", canHalf = true),
        CardSpec(SHORTCUT_IDS[0], "捷徑", canHalf = true),
        CardSpec(SHORTCUT_IDS[1], "捷徑", canHalf = true),
        CardSpec(SHORTCUT_IDS[2], "捷徑", canHalf = true),
        CardSpec(WEEK, "近 7 天支出"),
        CardSpec(TOP3, "本月花最多"),
        CardSpec(MASCOT, "吉祥物說一句"),
    )

    /** 使用者在試玩頁排的樣子（第一次放到桌面、還沒有任何一份時用它） */
    private val exampleRows = listOf(
        CardRow(SPEND, LEFT, true), CardRow(BAR), CardRow(TPLS), CardRow(REIMB, ADD, true), CardRow(INCOME, null, true), CardRow(PERDAY),
    )

    val PAGE = CardPage(
        "widget", "桌面小工具", specs,
        CardLayout(exampleRows, hidden = specs.map { it.id }.filter { id -> exampleRows.none { id in it.ids } }),
    )

    fun isShortcut(id: String) = id in SHORTCUT_IDS

    /** 排版頁、清單上寫的名字（捷徑寫打開哪一頁） */
    fun name(id: String, opts: Map<String, String>): String =
        if (isShortcut(id)) "捷徑：" + shortcutName(opts[id]) else PAGE.name(id)

    fun shortcutName(open: String?): String = SHORTCUTS.firstOrNull { it.first == open }?.second ?: SHORTCUTS[0].second
    fun shortcutOpen(opts: Map<String, String>, id: String): String = opts[id]?.takeIf { o -> SHORTCUTS.any { it.first == o } } ?: SHORTCUTS[0].first

    /** 記一筆打開的類型 */
    fun addType(opts: Map<String, String>): TxType = runCatching { TxType.valueOf(opts[ADD] ?: "") }.getOrDefault(TxType.EXPENSE)

    /** 常用記帳區塊放哪幾個（照存的順序，拿掉已經刪掉的；沒設定過就用常用記帳的前 3 個） */
    fun tplIds(d: AppData, opts: Map<String, String>): List<Long> {
        val have = d.templates.map { it.id }.toSet()
        val saved = opts[TPLS] ?: return d.templates.take(3).map { it.id }
        return saved.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it in have }.distinct().take(TPL_MAX)
    }

    /** 範例排版（常用記帳先放前 3 個） */
    fun example(id: Long, name: String, d: AppData): WLayout =
        WLayout(id, name, PAGE.default, mapOf(TPLS to d.templates.take(3).joinToString(",") { it.id.toString() }))

    /** 可以加的區塊：捷徑只列還沒用到的第一個 */
    fun addable(l: CardLayout): List<String> {
        val firstSc = SHORTCUT_IDS.firstOrNull { it in l.hidden }
        return PAGE.specs.map { it.id }.filter { it in l.hidden && (!isShortcut(it) || it == firstSc) }
    }

    // ───────── 放不下就不畫 ─────────

    /** 字的大小的倍率 */
    fun fontScale(font: Int): Float = when (font) {
        0 -> 0.88f
        2 -> 1.14f
        else -> 1f
    }

    const val PAD = 8f
    const val GAP = 6f
    const val TPL_CELL = 44f

    /** 常用記帳一排幾格：整排 4、半排 2 */
    fun tplPerRow(half: Boolean) = if (half) 2 else 4

    /** 一個區塊大約多高（dp，字中等）；tplCount＝常用記帳幾個（不含最後的＋） */
    fun blockHeight(id: String, half: Boolean, tplCount: Int): Float = when {
        id == TPLS -> {
            val n = tplCount + 1
            val rows = (n + tplPerRow(half) - 1) / tplPerRow(half)
            rows * TPL_CELL + (rows - 1) * 4f
        }
        id == BAR -> 50f
        id == ADD || isShortcut(id) -> 44f
        id == WEEK || id == TOP3 -> 84f
        id == MASCOT -> 58f
        else -> 54f
    }

    /** 每一排的高度（dp，照字的大小） */
    fun rowHeights(l: WLayout, tplCount: Int): List<Float> {
        val f = fontScale(l.font)
        return l.layout.rows.map { r -> r.ids.maxOfOrNull { blockHeight(it, r.half, tplCount) * f } ?: 0f }
    }

    /** 小工具高 heightDp 時畫得下前面幾排（至少一排，免得整個空白） */
    fun fitRows(l: WLayout, tplCount: Int, heightDp: Float): Int {
        val hs = rowHeights(l, tplCount)
        var used = PAD * 2
        var n = 0
        for (h in hs) {
            val add = h + if (n > 0) GAP else 0f
            if (n > 0 && used + add > heightDp) break
            used += add
            n++
        }
        return n
    }
}

// ───────── 區塊的內容 ─────────

/** 數字區塊：標題、數字、顏色（0 一般、1 好的綠色、2 要注意的紅色）、下面一行小字 */
data class WNum(val label: String, val value: String, val tone: Int = 0, val sub: String = "")

/** 點區塊打開 App 的哪裡（MainActivity 的 open） */
fun widgetOpen(id: String, l: WLayout, today: LocalDate = LocalDate.now()): String = when (id) {
    WBlocks.SPEND, WBlocks.INCOME, WBlocks.NET, WBlocks.TODAY, WBlocks.MASCOT -> "home"
    WBlocks.LEFT, WBlocks.PERDAY, WBlocks.BAR, WBlocks.TOP3 -> "rank"
    WBlocks.REIMB, WBlocks.DEBT -> "reimb"
    WBlocks.PRE -> "po-month:" + YearMonth.from(today)
    WBlocks.PEND -> "pend-home"
    WBlocks.SUB -> "subs"
    WBlocks.ASSET -> "accounts"
    WBlocks.WEEK -> "stats"
    WBlocks.ADD -> when (WBlocks.addType(l.opts)) {
        TxType.INCOME -> "add:INCOME"
        TxType.TRANSFER -> "add:TRANSFER"
        else -> "add"
    }
    else -> if (WBlocks.isShortcut(id)) WBlocks.shortcutOpen(l.opts, id) else "home"
}

/** 這份排版看的帳本（0 或找不到＝App 目前的） */
fun AppData.forWidget(l: WLayout): AppData =
    if (l.bookId == 0L || books.none { it.id == l.bookId }) this else copy(prefs = prefs.copy(bookId = l.bookId))

/** 數字區塊的內容；pendN＝刷卡待確認幾筆（存在手機上，要從外面給） */
fun AppData.widgetNum(id: String, today: LocalDate, pendN: Int): WNum? {
    val m = YearMonth.from(today)
    val list = bookTxns.inMonth(m)
    val budget = currentBook.budgetFor(m)
    val used = budgetUsed(list, m)
    val daysLeft = m.lengthOfMonth() - today.dayOfMonth + 1
    return when (id) {
        WBlocks.SPEND -> WNum("本月支出", formatMoney(list.expenseSum()))
        WBlocks.INCOME -> WNum("本月收入", formatMoney(list.incomeSum()), 1)
        WBlocks.NET -> {
            val v = list.incomeSum() - list.expenseSum()
            WNum("本月結餘", formatMoney(v), if (v >= 0) 1 else 2)
        }
        WBlocks.TODAY -> WNum("今天花了", formatMoney(bookTxns.filter { it.day == today.toEpochDay() }.expenseSum()))
        WBlocks.LEFT -> when {
            budget <= 0L -> WNum("剩餘預算", "還沒設定")
            used > budget -> WNum("超出預算", formatMoney(used - budget), 2)
            else -> WNum("剩餘預算", formatMoney(budget - used), 1)
        }
        WBlocks.PERDAY -> when {
            budget <= 0L -> WNum("每天還能花", "還沒設定")
            used >= budget -> WNum("每天還能花", formatMoney(0L), 2)
            else -> WNum("每天還能花", formatMoney((budget - used) / daysLeft), 1, "還有 $daysLeft 天")
        }
        WBlocks.REIMB -> {
            val v = bookTxns.pendingReimb().sumOf { it.reimbOutstanding }
            if (v <= 0L) WNum("待收報銷", "都收齊了") else WNum("待收報銷", formatMoney(v), 2)
        }
        WBlocks.DEBT -> {
            val v = debtOutstanding()
            if (v <= 0L) WNum("我欠別人", "沒有欠") else WNum("我欠別人", formatMoney(v), 2)
        }
        WBlocks.PRE -> {
            val v = preorderReserved(m)
            val n = bookPreorders.count { it.waiting && it.month == m && preorderInBudget(it) }
            if (n == 0) WNum("預購預留", "沒有") else WNum("預購預留", formatMoney(v), 0, "$n 樣")
        }
        WBlocks.PEND -> if (pendN <= 0) WNum("刷卡待確認", "沒有") else WNum("刷卡待確認", "$pendN 筆", 2)
        WBlocks.SUB -> {
            val t = today.toEpochDay()
            val next = bookSubs.filter { !it.paused }.mapNotNull { s -> s.next(t)?.let { s to it } }.minWithOrNull(compareBy({ it.second }, { it.first.id }))
            if (next == null) WNum("下一筆扣款", "沒有")
            else {
                val day = LocalDate.ofEpochDay(next.second)
                WNum("下一筆扣款", next.first.name, if (next.second < t) 2 else 0, "${day.monthValue}/${day.dayOfMonth}")
            }
        }
        WBlocks.ASSET -> {
            val now = netWorthAt(listOf(Long.MAX_VALUE))[0].net
            val prev = netWorthMonths(listOf(m.minusMonths(1)), today)[0].net
            val diff = now - prev
            WNum("總資產", formatMoney(now), 0, "比上個月 " + (if (diff >= 0) "+" else "-") + formatMoney(kotlin.math.abs(diff)))
        }
        else -> null
    }
}

/** 預算進度條：用了幾成（含預購預留）、預留那段多長、標題那行；沒設預算回傳 null */
data class WBar(val used: Float, val reserved: Float, val over: Boolean, val label: String)

fun AppData.widgetBar(today: LocalDate): WBar? {
    val m = YearMonth.from(today)
    val budget = currentBook.budgetFor(m)
    if (budget <= 0L) return null
    val list = bookTxns.inMonth(m)
    val used = budgetUsed(list, m)
    val reserved = preorderReserved(m)
    val pct = (used * 100 / budget).toInt()
    val over = used > budget
    val label = "${m.monthValue} 月預算 $pct%・" + if (over) "超出 ${formatMoney(used - budget)}" else "還剩 ${formatMoney(budget - used)}"
    return WBar((used.toFloat() / budget).coerceIn(0f, 1f), (reserved.toFloat() / budget).coerceIn(0f, 1f), over, label)
}

/** 近 7 天（含今天）每天的支出，舊的在前：（幾號, 金額） */
fun AppData.widgetWeek(today: LocalDate): List<Pair<Int, Long>> {
    val from = today.minusDays(6).toEpochDay()
    val by = bookTxns.filter { it.day in from..today.toEpochDay() }.groupBy { it.day }
    return (0..6).map { i ->
        val day = today.minusDays(6L - i)
        day.dayOfMonth to (by[day.toEpochDay()]?.expenseSum() ?: 0L)
    }
}

/** 本月支出最多的 3 個大分類：（名字, 金額, 佔本月支出幾 %） */
fun AppData.widgetTop3(today: LocalDate): List<Triple<String, Long, Int>> {
    val list = bookTxns.inMonth(YearMonth.from(today)).filter { (it.type == TxType.EXPENSE || it.isRefund) && !it.adjust }
    val total = list.sumOf { it.statAmount }
    if (total <= 0L) return emptyList()
    return list.groupBy { t -> statCat(t)?.let { catMap[it] }?.let { topOf(it).id } }
        .map { (k, v) -> (k?.let { catMap[it]?.name } ?: "沒有分類") to v.sumOf { it.statAmount } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }
        .take(3)
        .map { (n, v) -> Triple(n, v, (v * 100 / total).toInt()) }
}

// ───────── 存檔 ─────────

object WLayoutCodec {
    fun toJson(list: List<WLayout>): org.json.JSONArray = org.json.JSONArray().apply {
        list.forEach { l ->
            put(
                org.json.JSONObject().put("id", l.id).put("name", l.name).put("rows", org.json.JSONArray(l.layout.encode()))
                    .put("opts", org.json.JSONObject(l.opts)).put("bookId", l.bookId).put("bg", l.bg).put("tone", l.tone).put("font", l.font)
            )
        }
    }

    fun fromJson(arr: org.json.JSONArray?): List<WLayout> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val rows = o.optJSONArray("rows")?.let { r -> (0 until r.length()).map { r.optString(it) } } ?: emptyList()
            val opts = o.optJSONObject("opts")?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } } ?: emptyMap()
            WLayout(
                id = o.optLong("id", 0L).takeIf { it > 0L } ?: return@mapNotNull null,
                name = o.optString("name", "").ifBlank { "排版" },
                layout = WBlocks.PAGE.normalize(CardLayout.decode(rows)),
                opts = opts,
                bookId = o.optLong("bookId", 0L),
                bg = o.optInt("bg", 0).coerceIn(0, 2),
                tone = o.optInt("tone", 0).coerceIn(0, 2),
                font = o.optInt("font", 1).coerceIn(0, 2),
            )
        }
    }
}
