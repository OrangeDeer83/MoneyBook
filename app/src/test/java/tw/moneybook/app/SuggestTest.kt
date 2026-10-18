package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 常用備註：用過 2 次以上、同分類排前面、照次數；打字篩選 */
class SuggestTest {
    private val day = LocalDate.of(2026, 10, 18).toEpochDay()

    private fun t(id: Long, cat: Long, note: String, d: Long = day) =
        Txn(id, 1L, TxType.EXPENSE, 100, cat, 1L, null, d, note, emptyList())

    @Test
    fun frequentNotesSameCategoryFirst() {
        val d = Defaults.create().copy(txns = sortTxns(listOf(
            t(1, 10, "全家"), t(2, 10, "全家"), t(3, 10, "全家"),
            t(4, 20, "捷運"), t(5, 20, "捷運"), t(6, 20, "捷運"), t(7, 20, "捷運"),
            t(8, 10, "早餐店"), t(9, 10, "早餐店\n第二行不算"),
            t(10, 10, "只用一次"),
            t(11, 20, "早午餐", day - 6), t(12, 20, "早午餐", day - 5),   // 次數和早餐店一樣，但比較久以前
        )), nextId = 100)
        // 分類 10：用過的全家、早餐店排前面（全家 3 次在前），再來是別的分類照次數
        assertEquals(listOf("全家", "早餐店", "捷運", "早午餐"), d.noteSuggestions(10L))
        assertEquals(listOf("捷運", "全家", "早餐店", "早午餐"), d.noteSuggestions(null))
    }

    @Test
    fun filterByTyped() {
        val list = listOf("全家", "早餐店", "早午餐", "週末早午餐", "Amy", "amanda")
        assertEquals(list, filterSuggestions(list, " "))
        assertEquals(listOf("早餐店", "早午餐", "週末早午餐"), filterSuggestions(list, "早"))
        assertEquals(listOf("週末早午餐"), filterSuggestions(list, "早午餐"))   // 一模一樣的不列
        assertEquals(listOf("Amy", "amanda"), filterSuggestions(list, "am"))
    }
}
