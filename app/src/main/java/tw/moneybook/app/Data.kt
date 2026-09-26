package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate

data class Txn(
    val id: Long,
    val isExpense: Boolean,
    val amount: Long,
    val category: String,
    val date: LocalDate,
    val note: String,
)

data class Category(val name: String, val emoji: String)

object Categories {
    val expense = listOf(
        Category("餐飲", "🍜"),
        Category("交通", "🚌"),
        Category("購物", "🛍️"),
        Category("娛樂", "🎮"),
        Category("居住", "🏠"),
        Category("日用", "🧻"),
        Category("醫療", "💊"),
        Category("教育", "📚"),
        Category("社交", "🎁"),
        Category("其他", "📦"),
    )
    val income = listOf(
        Category("薪水", "💼"),
        Category("獎金", "🏆"),
        Category("投資", "📈"),
        Category("兼職", "🧑‍💻"),
        Category("其他", "💰"),
    )

    fun emojiOf(name: String, isExpense: Boolean): String =
        (if (isExpense) expense else income).firstOrNull { it.name == name }?.emoji ?: "📦"
}

fun formatMoney(v: Long): String = "$" + NumberFormat.getIntegerInstance().format(v)

/** 用 JSON 檔存資料，放在 App 私有目錄，不需要任何權限 */
class Store(private val file: File) {

    fun load(): Pair<List<Txn>, Long> {
        if (!file.exists()) return Pair(emptyList(), 0L)
        return try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("txns") ?: JSONArray()
            val list = ArrayList<Txn>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Txn(
                        id = o.getLong("id"),
                        isExpense = o.getBoolean("isExpense"),
                        amount = o.getLong("amount"),
                        category = o.getString("category"),
                        date = LocalDate.parse(o.getString("date")),
                        note = o.optString("note", ""),
                    )
                )
            }
            Pair(list, root.optLong("budget", 0L))
        } catch (e: Exception) {
            Pair(emptyList(), 0L)
        }
    }

    fun save(txns: List<Txn>, budget: Long) {
        val arr = JSONArray()
        for (t in txns) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("isExpense", t.isExpense)
                    .put("amount", t.amount)
                    .put("category", t.category)
                    .put("date", t.date.toString())
                    .put("note", t.note)
            )
        }
        val root = JSONObject().put("budget", budget).put("txns", arr)
        // 先寫暫存檔再改名，避免寫到一半當機把資料弄壞
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }
}

fun buildCsv(txns: List<Txn>): ByteArray {
    fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    val sb = StringBuilder()
    sb.append('﻿') // BOM，讓 Excel 正確顯示中文
    sb.append("日期,類型,分類,金額,備註\n")
    for (t in txns.sortedBy { it.date }) {
        sb.append(t.date.toString()).append(',')
            .append(if (t.isExpense) "支出" else "收入").append(',')
            .append(esc(t.category)).append(',')
            .append(t.amount).append(',')
            .append(esc(t.note)).append('\n')
    }
    return sb.toString().toByteArray(Charsets.UTF_8)
}
