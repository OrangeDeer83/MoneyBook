package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 常用備註：只列這個分類（大分類連小分類）用過 2 次以上的，照次數；打字篩選 */
class SuggestTest {
    private val day = LocalDate.of(2026, 10, 18).toEpochDay()

    private fun t(id: Long, cat: Long?, note: String, d: Long = day, type: TxType = TxType.EXPENSE) =
        Txn(id, 1L, type, 100, cat, 1L, if (type == TxType.TRANSFER) 2L else null, d, note, emptyList())

    @Test
    fun onlyNotesOfTheChosenCategory() {
        val d = Defaults.create().copy(txns = sortTxns(listOf(
            t(1, 10, "全家"), t(2, 10, "全家"), t(3, 10, "全家"),
            t(4, 20, "捷運"), t(5, 20, "捷運"), t(6, 20, "捷運"), t(7, 20, "捷運"),
            t(8, 10, "早餐店"), t(9, 10, "早餐店\n第二行不算"),
            t(10, 10, "只用一次"),
            t(11, 20, "早午餐", day - 6), t(12, 20, "早午餐", day - 5),
        )), nextId = 100)
        assertEquals(listOf("全家", "早餐店"), d.noteSuggestions(10L, TxType.EXPENSE))
        assertEquals(listOf("捷運", "早午餐"), d.noteSuggestions(20L, TxType.EXPENSE))
    }

    @Test
    fun parentCategoryIncludesItsChildren() {
        val base = Defaults.create()
        val parent = base.topCategories(TxType.EXPENSE).first { base.childrenOf(it.id).isNotEmpty() }
        val child = base.childrenOf(parent.id).first()
        val other = base.topCategories(TxType.EXPENSE).first { it.id != parent.id }
        val d = base.copy(txns = sortTxns(listOf(
            t(1, child.id, "Lunch Box"), t(2, child.id, "Lunch Box"), t(3, child.id, "Lunch Box"),
            t(4, parent.id, "Plain"), t(5, parent.id, "Plain"),
            t(6, other.id, "Metro"), t(7, other.id, "Metro"),
            t(8, null, "Save", type = TxType.TRANSFER), t(9, null, "Save", type = TxType.TRANSFER),
        )), nextId = 100)
        // 大分類：連小分類一起列，記在大分類本身的排前面
        assertEquals(listOf("Plain", "Lunch Box"), d.noteSuggestions(parent.id, TxType.EXPENSE))
        // 小分類：只有自己的
        assertEquals(listOf("Lunch Box"), d.noteSuggestions(child.id, TxType.EXPENSE))
        // 轉帳：沒有分類的轉帳備註
        assertEquals(listOf("Save"), d.noteSuggestions(null, TxType.TRANSFER))
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
