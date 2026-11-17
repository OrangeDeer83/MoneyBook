package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 退款：不算收入、統計和預算從原本那筆的分類扣回（扣在退款那天的月份）、跟著備份 */
class RefundTest {
    private val base0 = Defaults.create()
    private val daily = base0.categories.first { it.name == "日用" }.id
    private val shop = base0.categories.first { it.name == "購物" && it.parentId == null }.id
    private val salary = base0.categories.first { it.name == "薪水" }.id
    private val card = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1)
    private val book = base0.books.first().id
    private val nov14 = LocalDate.of(2026, 11, 14).toEpochDay()
    private val nov20 = LocalDate.of(2026, 11, 20).toEpochDay()
    private val oct28 = LocalDate.of(2026, 10, 28).toEpochDay()

    private fun exp(id: Long, amount: Long, cat: Long?, note: String, day: Long = nov14, acc: Long = 9001) =
        Txn(id, book, TxType.EXPENSE, amount, cat, acc, null, day, note, emptyList())

    private fun inc(id: Long, amount: Long, cat: Long?, day: Long = nov20, refundOf: Long = 0L, noIncome: Boolean = refundOf != 0L) =
        Txn(id, book, TxType.INCOME, amount, cat, 9001, null, day, "全聯福利中心", emptyList(), noIncome = noIncome, refundOf = refundOf)

    private fun data(vararg t: Txn): AppData {
        val (d, _) = base0.copy(accounts = base0.accounts + card).ensureRefundCat()
        return d.copy(txns = sortTxns(t.toList()))
    }

    private val refundCat get() = data().categories.first { it.name == REFUND_CAT }.id

    @Test
    fun refundIsNegativeSpendingNotIncome() {
        val e = exp(1, 350, daily, "全聯福利中心")
        val r = inc(2, 350, refundCat, refundOf = 1)
        val pay = inc(3, 30000, salary)
        val d = data(e, r, pay)
        assertEquals(-350L, r.spent)
        assertEquals(-350L, r.statAmount)
        assertTrue(r.isRefund)
        assertFalse(r.countsAsIncome)
        // 同一個月：支出 350 − 350 = 0；收入只有薪水
        assertEquals(0L, d.txns.expenseSum())
        assertEquals(30000L, d.txns.incomeSum())
        assertEquals(0L, d.budgetSpent(d.txns))
        assertEquals(daily, d.statCat(r))
        // 排行：扣完沒有花的分類不列
        assertTrue(d.budgetRankCats(d.txns).isEmpty())
        // 部分退：買 1,000 退 300 → 算 700
        val d2 = data(exp(1, 1000, daily, "全聯福利中心"), inc(2, 300, refundCat, refundOf = 1))
        assertEquals(700L, d2.txns.expenseSum())
        assertEquals(listOf<Pair<Long?, Long>>(daily to 700L), d2.budgetRankCats(d2.txns))
        // 沒選退的是哪一筆：不算收入、也不扣
        val loose = inc(4, 350, refundCat, noIncome = true)
        val d3 = data(e, loose)
        assertEquals(350L, d3.txns.expenseSum())
        assertEquals(0L, d3.txns.incomeSum())
        assertFalse(loose.isRefund)
    }

    @Test
    fun crossMonthDeductsInRefundMonth() {
        // 甲：10/28 買衣服 $1,200，11/5 退 → 10 月照舊、11 月購物 −1,200
        val nov5 = LocalDate.of(2026, 11, 5).toEpochDay()
        val d = data(exp(1, 1200, shop, "UNIQLO", day = oct28), inc(2, 1200, refundCat, day = nov5, refundOf = 1))
        val months = listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11))
        assertEquals(listOf(1200L, -1200L), d.catMonthly(shop, months))
        assertEquals(1200L, d.txns.inMonth(months[0]).expenseSum())
        assertEquals(-1200L, d.budgetSpent(d.txns.inMonth(months[1])))
    }

    @Test
    fun outOfBudgetOriginalRefundsOutOfBudget() {
        val e = exp(1, 2000, daily, "診所").copy(budgetOut = 1)
        val r = inc(2, 500, refundCat, refundOf = 1)
        val d = data(e, r)
        assertTrue(d.isOutOfBudget(r))
        assertEquals(0L, d.budgetSpent(d.txns))
        assertEquals(1500L, d.outOfBudgetSpent(d.txns))
        assertFalse(d.refundInBudget(r))
    }

    @Test
    fun categoryFlagSyncsRecords() {
        var d = data(inc(5, 100, salary))
        assertTrue(d.txns.single().countsAsIncome)
        d = d.withCatNoIncome(salary, true)
        assertTrue(d.catNoIncome(salary))
        assertTrue(d.txns.single().noIncome)
        // 關掉：記錄跟著改回算收入，退款連結拿掉
        val d2 = data(exp(1, 350, daily, "全聯"), inc(2, 350, refundCat, refundOf = 1))
        val off = d2.withCatNoIncome(refundCat, false)
        val r = off.txns.first { it.id == 2L }
        assertFalse(r.noIncome)
        assertEquals(0L, r.refundOf)
        assertTrue(r.countsAsIncome)
    }

    @Test
    fun ensureRefundCategory() {
        val (d1, id1) = base0.ensureRefundCat()
        val c = d1.catMap[id1]!!
        assertEquals(REFUND_CAT, c.name)
        assertEquals(TxType.INCOME, c.kind)
        assertTrue(c.noIncome)
        // 第二次用同一個
        assertEquals(id1 to d1.categories.size, d1.ensureRefundCat().let { (d, id) -> id to d.categories.size })
        // 已經有叫「退款」、沒打開的：打開，記錄跟著改
        val own = Category(500, REFUND_CAT, "img:cat_box", 1, TxType.INCOME, null, 9)
        val withOwn = base0.copy(categories = base0.categories + own, txns = listOf(inc(7, 80, 500, noIncome = false)))
        val (d2, id2) = withOwn.ensureRefundCat()
        assertEquals(500L, id2)
        assertTrue(d2.catMap[500]!!.noIncome)
        assertTrue(d2.txns.single().noIncome)
    }

    @Test
    fun pickerOrderAndAutoLink() {
        val a = exp(1, 350, daily, "全聯福利中心 文心店", day = nov14)
        val b = exp(2, 880, shop, "鼎泰豐", day = nov14 + 1)
        val c = exp(3, 350, daily, "7-ELEVEN", day = nov14 + 2)
        val old = exp(4, 350, daily, "全聯福利中心", day = nov20 - 61)
        val other = exp(5, 350, daily, "全聯福利中心", acc = 9002)
        val d = data(a, b, c, old, other)
        // 金額一樣的在前（同一家店再前），其他照日期新到舊；60 天前、別的帳戶不列
        assertEquals(listOf(1L, 3L, 2L), d.refundCands(9001, nov20, 350, "全聯福利中心").map { it.id })
        assertEquals(1L, d.refundGuess(9001, nov20, 350, "全聯福利中心")?.id)
        // 部分退也連得到同一家店
        assertEquals(1L, d.refundGuess(9001, nov20, 100, "全聯福利中心")?.id)
        // 已經退完的不連
        val done = data(a, inc(9, 350, refundCat, day = nov14 + 3, refundOf = 1))
        assertNull(done.refundGuess(9001, nov20, 350, "全聯福利中心"))
        // 沒寫商店：金額一樣而且只有一筆才連
        assertNull(d.refundGuess(9001, nov20, 350, ""))
        assertEquals(2L, d.refundGuess(9001, nov20, 880, "")?.id)
        // 找不到同一家店
        assertNull(d.refundGuess(9001, nov20, 350, "UNIQLO"))
        assertEquals("11/14 全聯福利中心 文心店 ${formatMoney(350)}", d.refundLabel(a))
    }

    @Test
    fun saveKeepsLinkOnlyForNoIncomeAndDropsDangling() {
        val d = data(exp(1, 350, daily, "全聯"))
        assertEquals(1L, inc(2, 350, refundCat).withRefund(d, 1).refundOf)
        assertTrue(inc(2, 350, refundCat).withRefund(d, 1).noIncome)
        // 一般收入不能連
        assertEquals(0L, inc(2, 350, salary).withRefund(d, 1).refundOf)
        assertFalse(inc(2, 350, salary).withRefund(d, 1).noIncome)
        // 連到不是支出的：拿掉
        assertEquals(0L, inc(2, 350, refundCat).withRefund(d, 99).refundOf)
        // 原本那筆刪掉了：連結拿掉，變成只是不算收入
        val gone = data(inc(2, 350, refundCat, refundOf = 1)).dropDanglingRefunds()
        assertEquals(0L, gone.txns.single().refundOf)
        assertTrue(gone.txns.single().noIncome)
    }

    @Test
    fun backupKeepsFlagsAndOldDataDefaults() {
        val d = data(exp(1, 350, daily, "全聯"), inc(2, 350, refundCat, refundOf = 1))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.txns, back.txns)
        assertTrue(back.catMap[refundCat]!!.noIncome)
        val plain = Codec.decode(Codec.encode(base0))
        assertTrue(plain.categories.none { it.noIncome })
    }
}
