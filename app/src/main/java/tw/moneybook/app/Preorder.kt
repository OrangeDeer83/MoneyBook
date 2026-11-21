package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth

/*
 * 預購清單（草圖 ci-out/preorder_plan.png）：先記下以後要買的東西（預估金額、分類、帳戶、預計哪天買），
 * 還沒買之前從「預計買的那個月」的預算先扣掉（預留），不動帳戶、不算統計的支出、不出現在明細。
 * 到了那天問「買了嗎？」：買了＝記成一般的一筆（照實際金額，預留的拿掉）；沒買＝預留的放回預算；延後＝換日期。
 */

const val PRE_WAIT = 0
const val PRE_BOUGHT = 1
const val PRE_SKIPPED = 2

data class Preorder(
    val id: Long,
    val bookId: Long,
    /** 預估金額（台幣） */
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    /** 預計哪天買（epoch day） */
    val day: Long,
    val note: String = "",
    val shop: String = "",
    val tags: List<String> = emptyList(),
    /** 什麼時候記的（epoch day） */
    val created: Long = 0L,
    /** PRE_WAIT 等著買、PRE_BOUGHT 買了、PRE_SKIPPED 沒買 */
    val status: Int = PRE_WAIT,
    /** 買了：記成的那一筆 */
    val txnId: Long = 0L,
    /** 買了／沒買是哪天回答的 */
    val doneDay: Long = 0L,
) {
    val waiting: Boolean get() = status == PRE_WAIT
    val month: YearMonth get() = YearMonth.from(LocalDate.ofEpochDay(day))
}

/** 清單、通知、首頁預算上的名字：備註第一行（買什麼），沒有就分類（選了子分類寫子分類），再沒有就店家 */
fun AppData.preorderName(p: Preorder): String =
    p.note.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: p.categoryId?.let { catMap[it]?.name }
        ?: p.shop.takeIf { it.isNotBlank() }
        ?: "預購"

/** 這個月預留給哪些預購（首頁預算那行）：一樣寫名字，好幾樣寫「耳機 等 3 樣」 */
fun AppData.preorderReservedNames(m: java.time.YearMonth): String {
    val list = bookPreorders.filter { it.waiting && it.month == m && preorderInBudget(it) }.sortedWith(compareBy({ it.day }, { it.id }))
    return when (list.size) {
        0 -> ""
        1 -> preorderName(list[0])
        else -> "${preorderName(list[0])} 等 ${list.size} 樣"
    }
}

/** 目前帳本的預購 */
val AppData.bookPreorders: List<Preorder> get() = preorders.filter { it.bookId == currentBook.id }

/** 還沒買的預購算不算預算（分類預設不算預算的就不預留） */
fun AppData.preorderInBudget(p: Preorder): Boolean = !categoryOutOfBudget(p.categoryId)

/** 這個月預留給預購的錢：目前帳本、還在等著買、預計在這個月買、算預算的 */
fun AppData.preorderReserved(m: YearMonth): Long =
    bookPreorders.filter { it.waiting && it.month == m && preorderInBudget(it) }.sumOf { it.amount }

/** 算預算的支出＋這個月預留給預購的（首頁預算、吉祥物、本月重點、桌面小工具都用它） */
fun AppData.budgetUsed(list: List<Txn>, m: YearMonth): Long = budgetSpent(list) + preorderReserved(m)

/** 到期（預計那天已經到了）還在等著買的，舊的在前面 */
fun AppData.preordersDue(today: Long): List<Preorder> =
    bookPreorders.filter { it.waiting && it.day <= today }.sortedWith(compareBy({ it.day }, { it.id }))

/** 新增或修改一筆預購（id 是 null 就新增）；回傳新的資料和那筆的 id */
fun AppData.withPreorder(id: Long?, p: Preorder): Pair<AppData, Long> =
    if (id == null) {
        val np = p.copy(id = nextId, bookId = currentBook.id)
        copy(preorders = preorders + np, nextId = nextId + 1) to np.id
    } else {
        copy(preorders = preorders.map { if (it.id == id) p.copy(id = id, bookId = it.bookId, created = it.created, status = it.status, txnId = it.txnId, doneDay = it.doneDay) else it }) to id
    }

/** 買了：記成的那一筆是 txnId */
fun AppData.withPreorderBought(id: Long, txnId: Long, today: Long): AppData =
    copy(preorders = preorders.map { if (it.id == id) it.copy(status = PRE_BOUGHT, txnId = txnId, doneDay = today) else it })

/** 沒買：預留的放回預算 */
fun AppData.withPreorderSkipped(id: Long, today: Long): AppData =
    copy(preorders = preorders.map { if (it.id == id && it.waiting) it.copy(status = PRE_SKIPPED, doneDay = today) else it })

/** 延後到 day */
fun AppData.withPreorderDay(id: Long, day: Long): AppData =
    copy(preorders = preorders.map { if (it.id == id) it.copy(day = day) else it })

/** 放回等著買（沒買的改變心意） */
fun AppData.withPreorderWaiting(id: Long): AppData =
    copy(preorders = preorders.map { if (it.id == id) it.copy(status = PRE_WAIT, txnId = 0L, doneDay = 0L) else it })

fun AppData.withPreorderRemoved(id: Long): AppData = copy(preorders = preorders.filter { it.id != id })

/** 預購的手機通知要寫什麼（single：只有一筆時那一筆的 id，通知上有「買了／沒買／延後」） */
data class PreorderNotice(val title: String, val body: String, val lines: List<String>, val worried: Boolean, val single: Long?)

/**
 * 今天要發的預購通知（目前帳本），沒有要問的回傳 null。預計那天提醒，之後再提醒 repeat 天
 * （跟訂閱的「沒記的話再提醒」同一個設定，SUB_REPEAT_DAILY＝每天）。
 */
fun AppData.preorderNotice(today: Long, repeat: Int, name: String): PreorderNotice? {
    val due = preordersDue(today).filter { repeat == SUB_REPEAT_DAILY || today - it.day <= repeat }
    if (due.isEmpty()) return null
    fun acc(p: Preorder) = p.accountId?.let { accMap[it]?.name }
    val worried = due.any { it.day < today }
    if (due.size == 1) {
        val p = due[0]
        val age = today - p.day
        val title = if (age == 0L) "$name：${preorderName(p)} 今天要買嗎？" else "$name：${preorderName(p)} 買了嗎？"
        val head = listOfNotNull("預估 ${formatMoney(p.amount)}", acc(p)).joinToString("・")
        val tail = when {
            age == 0L -> ""
            age == 1L -> "・預計昨天買"
            else -> "・預計 $age 天前買"
        }
        return PreorderNotice(title, head + tail, emptyList(), worried, p.id)
    }
    val lines = due.take(5).map { "${preorderName(it)} ${formatMoney(it.amount)}" }
    return PreorderNotice(
        "$name：有 ${due.size} 樣預購買好了嗎？", "預估合計 ${formatMoney(due.sumOf { it.amount })}・點開來看", lines, worried, null,
    )
}

/** 存檔：每一筆一個物件 */
object PreorderCodec {
    fun toJson(list: List<Preorder>): org.json.JSONArray = org.json.JSONArray().apply {
        list.forEach { p ->
            put(
                org.json.JSONObject().put("id", p.id).put("bookId", p.bookId).put("amount", p.amount)
                    .put("categoryId", p.categoryId ?: org.json.JSONObject.NULL).put("accountId", p.accountId ?: org.json.JSONObject.NULL)
                    .put("day", p.day).put("note", p.note).put("shop", p.shop).put("tags", org.json.JSONArray(p.tags))
                    .put("created", p.created).put("status", p.status).put("txnId", p.txnId).put("doneDay", p.doneDay)
            )
        }
    }

    fun fromJson(arr: org.json.JSONArray?, defaultBook: Long): List<Preorder> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Preorder(
                id = o.optLong("id", 0L).takeIf { it > 0L } ?: return@mapNotNull null,
                bookId = o.optLong("bookId", defaultBook),
                amount = o.optLong("amount", 0L),
                categoryId = if (o.isNull("categoryId")) null else o.optLong("categoryId"),
                accountId = if (o.isNull("accountId")) null else o.optLong("accountId"),
                day = o.optLong("day", 0L),
                note = o.optString("note", ""),
                shop = o.optString("shop", ""),
                tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
                created = o.optLong("created", 0L),
                status = o.optInt("status", PRE_WAIT).takeIf { it in PRE_WAIT..PRE_SKIPPED } ?: PRE_WAIT,
                txnId = o.optLong("txnId", 0L),
                doneDay = o.optLong("doneDay", 0L),
            )
        }
    }
}
