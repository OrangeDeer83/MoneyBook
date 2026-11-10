package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 預算外：分類設定（小分類跟著大分類或自己設）、單筆改、預算內／外金額、存檔 */
class BudgetTest {
    private val base = Defaults.create()
    private val parent = base.topCategories(TxType.EXPENSE).first { base.childrenOf(it.id).size >= 2 }
    private val kidA = base.childrenOf(parent.id)[0]
    private val kidB = base.childrenOf(parent.id)[1]
    private val other = base.topCategories(TxType.EXPENSE).first { it.id != parent.id }
    private val day = LocalDate.of(2026, 10, 16).toEpochDay()   // 週五

    private fun t(id: Long, cat: Long, amount: Long, out: Int = 0, d: Long = day, time: Int = -1) =
        Txn(id, 1L, TxType.EXPENSE, amount, cat, base.accounts.first().id, null, d, "", emptyList(), budgetOut = out, time = time)

    private fun data(vararg txns: Txn, parentMode: Int = 1, kidBMode: Int = 0): AppData = base.copy(
        categories = base.categories.map {
            when (it.id) {
                parent.id -> it.copy(budgetMode = parentMode)
                kidB.id -> it.copy(budgetMode = kidBMode)
                else -> it
            }
        },
        txns = sortTxns(txns.toList()), nextId = 999,
    )

    @Test
    fun childFollowsParentUnlessSetOwn() {
        val d = data(kidBMode = 2)
        assertTrue(d.categoryOutOfBudget(parent.id))
        assertTrue(d.categoryOutOfBudget(kidA.id))     // 跟著大分類
        assertFalse(d.categoryOutOfBudget(kidB.id))    // 自己設成算預算
        assertFalse(d.categoryOutOfBudget(other.id))
        // 大分類算預算、小分類自己設預算外
        val d2 = data(parentMode = 0, kidBMode = 1)
        assertFalse(d2.categoryOutOfBudget(kidA.id))
        assertTrue(d2.categoryOutOfBudget(kidB.id))
    }

    @Test
    fun singleRecordOverridesAndAmounts() {
        val d = data(t(1, kidA.id, 1000), t(2, kidA.id, 300, out = 2), t(3, other.id, 500), t(4, other.id, 200, out = 1))
        assertTrue(d.isOutOfBudget(d.txns.first { it.id == 1L }))
        assertFalse(d.isOutOfBudget(d.txns.first { it.id == 2L }))   // 單筆改成算預算
        assertTrue(d.isOutOfBudget(d.txns.first { it.id == 4L }))    // 單筆改成預算外
        assertEquals(800L, d.budgetSpent(d.txns))
        assertEquals(1200L, d.outOfBudgetSpent(d.txns))
        assertEquals(d.txns.expenseSum(), d.budgetSpent(d.txns) + d.outOfBudgetSpent(d.txns))
    }

    @Test
    fun savedAndOldDataDefaultsToInBudget() {
        val d = data(t(1, kidA.id, 1000, out = 2), kidBMode = 1)
        val back = Codec.decode(Codec.encode(d))
        assertEquals(1, back.catMap[parent.id]!!.budgetMode)
        assertEquals(1, back.catMap[kidB.id]!!.budgetMode)
        assertEquals(2, back.txns.first { it.id == 1L }.budgetOut)
        assertEquals(0, Codec.decode(Codec.encode(base)).categories.maxOf { it.budgetMode })
    }

    @Test
    fun rankByCategoryAndTopSingles() {
        // 大分類算預算、kidB 預算外；other 一筆被單筆設成預算外
        val d = data(
            t(1, kidA.id, 300), t(2, parent.id, 200), t(3, kidB.id, 9999),
            t(4, other.id, 400), t(5, other.id, 700, out = 1), t(6, other.id, 400, d = day - 1),
            parentMode = 0, kidBMode = 1,
        )
        // 餐飲類：300＋200（kidB 預算外不算）＝ 500；other：400＋400（700 預算外不算）＝ 800
        assertEquals(listOf<Long?>(other.id, parent.id), d.budgetRankCats(d.txns).map { it.first })
        assertEquals(listOf(800L, 500L), d.budgetRankCats(d.txns).map { it.second })
        assertEquals(d.budgetSpent(d.txns), d.budgetRankCats(d.txns).sumOf { it.second })   // 合計和預算卡一樣
        // 單筆：400（今天）、400（昨天）、300、200；一樣大的新的在前；預算外的不在裡面
        assertEquals(listOf(4L, 6L, 1L, 2L), d.budgetTopTxns(d.txns).map { it.id })
        assertEquals(listOf(4L, 6L), d.budgetTopTxns(d.txns, 2).map { it.id })
    }
}
