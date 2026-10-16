package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 欠款：別人先幫我付、我還沒還的錢（見 Debt.kt） */
class DebtTest {
    private val bank = Account(10, "銀行", AccountType.BANK.emoji, AccountType.BANK, 50_000L, 1)
    private val jpy = Account(12, "日圓帳戶", AccountType.BANK.emoji, AccountType.BANK, 100_000L, 2, currency = "JPY")

    private fun base(vararg txns: Txn): AppData {
        val b = Defaults.create()
        return b.copy(accounts = listOf(bank, jpy), txns = txns.toList().map { it.copy(bookId = b.currentBook.id) }.sortedByDescending { it.id }, nextId = 1000)
    }

    /** 整筆都欠別人（沒有帳戶） */
    private fun debt(id: Long, amount: Long, day: Long, who: String = "Amy", note: String = "") =
        Txn(id, 1L, TxType.EXPENSE, amount, null, null, null, day, note, emptyList(), debtItems = listOf(DebtItem(who, amount)))

    /** 自己付 self（從銀行扣），其他欠 items 裡的人 */
    private fun split(id: Long, amount: Long, day: Long, self: Long, vararg items: Pair<String, Long>) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, if (self > 0L) 10L else null, null, day, "", emptyList(),
            debtItems = items.map { DebtItem(it.first, it.second) }, debtSelf = self)

    private fun fxDebt(id: Long, twd: Long, units: Long, day: Long, who: String = "Ken") =
        Txn(id, 1L, TxType.EXPENSE, twd, null, null, null, day, "", emptyList(), fxSpendAmount = units, fxSpendCur = "JPY",
            debtItems = listOf(DebtItem(who, twd, units)))

    private fun AppData.t(id: Long) = txns.first { it.id == id }
    private fun AppData.item(id: Long, i: Int = 0) = t(id).debtItems[i]

    @Test
    fun openDebtDoesNotTouchAnyAccountButCountsAsExpense() {
        val d = base(debt(1, 800, 100))
        assertEquals(50_000L, d.balances()[10])
        assertEquals(800L, d.txns.expenseSum())                      // 記的那天就算支出
        assertEquals(800L, d.debtOutstanding())
    }

    @Test
    fun exactRepayClearsDebtAndMovesBalanceOnce() {
        val d = base(debt(1, 800, 100)).withDebtRepaid("Amy", "", 10, 110, -1, 800, 0, false)!!
        assertEquals(49_200L, d.balances()[10])
        assertEquals(800L, d.t(1).paid)
        assertTrue(d.item(1).done)
        assertEquals(0L, d.debtOutstanding())
        assertEquals(800L, d.txns.expenseSum())                      // 還款不再算一次支出
    }

    @Test
    fun shortRepayCanContinueOwing() {
        // 機票 2000（day 10）、晚餐 800（day 15）共欠 2800，還 2500：先還舊的，機票還清、晚餐還 500
        val d = base(debt(1, 2000, 10, note = "機票"), debt(2, 800, 15, note = "晚餐")).withDebtRepaid("Amy", "", 10, 20, -1, 2500, 0, false)!!
        assertTrue(d.item(1).done)
        assertFalse(d.item(2).done)
        assertEquals(300L, d.item(2).remainingUnits)
        assertEquals(800L, d.t(2).paid)                              // 還沒結清，支出不變
        assertEquals(300L, d.debtOutstanding())
        assertEquals(47_500L, d.balances()[10])
    }

    @Test
    fun shortRepayCanSettleAndReduceExpense() {
        val d = base(debt(1, 2000, 10), debt(2, 800, 15)).withDebtRepaid("Amy", "", 10, 20, -1, 2500, 0, true)!!
        assertTrue(d.item(1).done && d.item(2).done)
        assertEquals(2000L, d.t(1).paid)
        assertEquals(500L, d.t(2).paid)                              // 被免掉的 300 從支出扣掉
        assertEquals(800L, d.t(2).recorded)                          // 灰色小字的總額不變
        assertEquals(0L, d.debtOutstanding())
        assertEquals(2500L, d.txns.expenseSum())
    }

    @Test
    fun overRepayAddsExtraToLastItem() {
        val d = base(debt(1, 2000, 10), debt(2, 800, 15)).withDebtRepaid("Amy", "", 10, 20, -1, 2900, 0, false)!!
        assertEquals(2000L, d.t(1).paid)
        assertEquals(900L, d.t(2).paid)                              // 多付的 100 算在最後一項
        assertTrue(d.item(1).done && d.item(2).done)
        assertEquals(47_100L, d.balances()[10])
        assertEquals(2900L, d.txns.expenseSum())
    }

    @Test
    fun removingAPayRestoresTheItem() {
        val paid = base(debt(1, 800, 10)).withDebtRepaid("Amy", "", 10, 20, -1, 900, 0, false)!!
        assertEquals(900L, paid.t(1).paid)
        val back = paid.withDebtPayRemoved(1, 0, 0)!!
        assertEquals(800L, back.t(1).paid)
        assertFalse(back.item(1).done)
        assertEquals(800L, back.debtOutstanding())
        assertEquals(50_000L, back.balances()[10])
        assertNull(paid.withDebtPayRemoved(1, 0, 5))
        assertNull(paid.withDebtPayRemoved(1, 3, 0))
    }

    @Test
    fun otherPeopleAndBadInputAreRejected() {
        val d = base(debt(1, 800, 10))
        assertNull(d.withDebtRepaid("Ken", "", 10, 20, -1, 100, 0, false))        // 沒有欠 Ken
        assertNull(d.withDebtRepaid("Amy", "", 10, 20, -1, 0, 0, false))
        assertNull(d.withDebtRepaid("Amy", "", 99, 20, -1, 100, 0, false))        // 沒有這個帳戶
        assertNull(d.withDebtRepaid("Amy", "", 12, 20, -1, 100, 0, false))        // 台幣欠款不能用外幣帳戶還
    }

    @Test
    fun partSelfPaidPartOwed() {
        // 晚餐 300：我付 100（銀行），A、B 各先付 100
        val d0 = base(split(1, 300, 10, 100, "A" to 100L, "B" to 100L))
        assertEquals(49_900L, d0.balances()[10])                     // 帳戶只扣自己付的
        assertEquals(300L, d0.txns.expenseSum())
        assertEquals(200L, d0.debtOutstanding())
        val d1 = d0.withDebtRepaid("A", "", 10, 12, -1, 100, 0, false)!!
        assertEquals(49_800L, d1.balances()[10])
        assertTrue(d1.item(1, 0).done)
        assertFalse(d1.item(1, 1).done)                              // 還 A 不會動到 B
        assertEquals(100L, d1.debtOutstanding())
        assertEquals(300L, d1.txns.expenseSum())
    }

    @Test
    fun reimbAndDebtTogether() {
        // 你的例子：晚餐 300，我付 100、欠 A、B 各 100，幫 C、D 各付 150（報銷）
        val t = split(1, 300, 10, 100, "A" to 100L, "B" to 100L).withItems(listOf(ReimbItem("C", 150), ReimbItem("D", 150)))
        val d = base(t)
        assertEquals(0L, d.txns.expenseSum())                        // 實際支出 = 300 − 報銷 300
        assertEquals(300L, d.t(1).recorded)
        assertEquals(49_900L, d.balances()[10])
        assertEquals(200L, d.debtOutstanding())
    }

    @Test
    fun feeAndDiscountWorkWithDebt() {
        // 原價 1000、優惠 100、手續費 20 → 總額 920；欠 A 500，自己付 420
        val t = Txn(1, 1L, TxType.EXPENSE, 1000, null, 10L, null, 10, "", emptyList(), fee = 20, discount = 100,
            debtItems = listOf(DebtItem("A", 500)), debtSelf = 420)
        val d = base(t)
        assertEquals(920L, d.t(1).recorded)
        assertEquals(920L, d.t(1).paid)
        assertEquals(49_580L, d.balances()[10])
    }

    @Test
    fun foreignDebtRepaidInTwdUsesActualAmount() {
        // 朋友先墊 ¥12,000，預估 $2,640；實際用台幣還 $2,700
        val d0 = base(fxDebt(1, 2640, 12_000, 10))
        assertEquals(2640L, d0.debtOutstanding())
        val d = d0.withDebtRepaid("Ken", "JPY", 10, 20, -1, 12_000, 2700, false)!!
        assertEquals(2700L, d.t(1).paid)                             // 支出改成實際台幣
        assertTrue(d.item(1).done)
        assertEquals(47_300L, d.balances()[10])
    }

    @Test
    fun foreignDebtPartialRepayKeepsEstimateUntilDone() {
        val d1 = base(fxDebt(1, 2640, 12_000, 10)).withDebtRepaid("Ken", "JPY", 10, 20, -1, 6_000, 1_350, false)!!
        assertEquals(6_000L, d1.item(1).remainingUnits)
        assertEquals(2640L, d1.t(1).paid)
        assertEquals(1320L, d1.debtOutstanding())                    // 剩 ¥6,000 × 預估匯率
        val d2 = d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 6_000, 1_400, false)!!
        assertEquals(2750L, d2.t(1).paid)                            // 1,350 + 1,400
        assertTrue(d2.item(1).done)
        assertNull(d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 7_000, 1_400, false))   // 外幣不能多還
        assertNull(d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 6_000, 0, false))       // 台幣帳戶要填實際台幣
    }

    @Test
    fun foreignDebtRepaidFromSameCurrencyAccountDeductsForeign() {
        val d = base(fxDebt(1, 2640, 12_000, 10)).withDebtRepaid("Ken", "JPY", 12, 20, -1, 12_000, 0, false)!!
        assertEquals(88_000L, d.balances()[12])                      // 日圓帳戶 100,000 − 12,000
        assertEquals(2640L, d.t(1).paid)                             // 用預估匯率，支出不變
        assertTrue(d.item(1).done)
    }

    @Test
    fun mixedForeignSelfPartIsSettledLikeCardCharge() {
        // ¥12,000（預估 $2,640）：我刷卡 ¥4,000，Amy 先付 ¥8,000
        val items = splitDebtUnits(listOf(DebtItem("Amy", 1760)), 2640, 12_000)
        assertEquals(8_000L, items.single().units)
        val t = Txn(1, 1L, TxType.EXPENSE, 2640, null, 10L, null, 10, "", emptyList(), fxSpendAmount = 12_000, fxSpendCur = "JPY",
            fxPending = true, debtItems = items, debtSelf = 880)
        val d0 = base(t)
        assertEquals(listOf(1L), d0.fxPendingList().map { it.id })   // 自己刷卡的那份要請款
        assertEquals(49_120L, d0.balances()[10])
        val d1 = d0.withFxSettled(1, 900)!!                          // 銀行實際請款 $900
        assertEquals(900L, d1.t(1).debtSelf)
        assertEquals(2640L, d1.t(1).amount)
        assertEquals(900L + 1760L, d1.t(1).paid)
        assertTrue(d1.fxPendingList().isEmpty())
        assertTrue(base(fxDebt(2, 2640, 12_000, 10)).fxPendingList().isEmpty())     // 全部欠別人：沒有要請款的
    }

    @Test
    fun repayOnlyTheChosenItems() {
        val d = base(debt(1, 2000, 10), debt(2, 800, 15)).withDebtRepaid("Amy", "", 10, 20, -1, 800, 0, false, only = setOf(2L to 0))!!
        assertFalse(d.item(1).done)
        assertTrue(d.item(2).done)
    }

    @Test
    fun runningBalancesIncludeRepayments() {
        val d = base(split(1, 300, 10, 100, "A" to 100L, "B" to 100L))
            .withDebtRepaid("A", "", 10, 20, -1, 100, 0, false)!!
            .withDebtRepaid("B", "", 10, 21, -1, 100, 0, false)!!
        val run = d.runningBalances(10)
        assertEquals(49_900L, run["t1"])
        assertEquals(49_800L, run["d1_0"])                           // 欠款還款依名單攤平編號
        assertEquals(49_700L, run["d1_1"])
        assertEquals(d.balances()[10], run["d1_1"])
    }

    @Test
    fun separatesPeopleAndCurrencies() {
        val d = base(debt(1, 800, 10), split(2, 500, 11, 0, "Ken" to 300L, "Amy" to 200L), fxDebt(3, 2640, 12_000, 12))
        val g = d.debtGroups()
        assertEquals(setOf("Amy" to "", "Ken" to "", "Ken" to "JPY"), g.map { it.who to it.currency }.toSet())
        assertEquals(1000L, g.first { it.who == "Amy" }.remainingTwd)
        assertEquals(setOf("Amy", "Ken"), d.debtNames().toSet())
    }

    /** Amy 欠我：午餐 1000（報銷）、機票 1000（報銷，已收 500）；我欠 Amy：晚餐 800（欠款） */
    private fun mutual(): AppData {
        val lunch = Txn(1, 1L, TxType.EXPENSE, 1000, null, 10L, null, 3, "午餐", emptyList()).withItems(listOf(ReimbItem("Amy", 1000)))
        val flight = Txn(2, 1L, TxType.EXPENSE, 1000, null, 10L, null, 10, "機票", emptyList())
            .withItems(listOf(ReimbItem("Amy", 1000, listOf(ReimbPay(12, 10L, 500)))))
        return base(lunch, flight, debt(3, 800, 15, note = "晚餐"))
    }

    @Test
    fun offsetUsesSmallerSideOldestFirstWithoutTouchingAccounts() {
        val d0 = mutual()
        val bal = d0.balances()[10]
        val d = d0.withOffset("Amy", setOf(1L to 0, 2L to 0), setOf(3L to 0), 20, -1)!!
        // 她欠我 1500、我欠她 800 → 抵 800：午餐先抵 800（還剩 200），機票不動
        assertEquals(200L, d.t(1).items[0].remaining)
        assertEquals(500L, d.t(2).items[0].remaining)
        assertTrue(d.item(3).done)
        assertEquals(bal, d.balances()[10])                          // 不動任何帳戶
        assertEquals(d0.txns.expenseSum(), d.txns.expenseSum())      // 不改變支出
        assertEquals(0L, d.debtOutstanding())
        val oid = d.t(1).items[0].pays.last().offsetId
        assertTrue(oid != 0L)
        assertEquals(oid, d.item(3).pays.single().offsetId)
        assertEquals(d0.nextId + 1, d.nextId)
    }

    @Test
    fun offsetOnlyChosenItemsAndClosesFullyCovered() {
        val d = mutual().withOffset("Amy", setOf(2L to 0), setOf(3L to 0), 20, -1)!!
        // 只勾機票（剩 500）→ 抵 500：機票收齊、晚餐還欠 300
        assertTrue(d.t(2).items[0].closed)
        assertEquals(1000L, d.t(1).items[0].remaining)
        assertEquals(300L, d.item(3).remainingUnits)
        assertNull(mutual().withOffset("Amy", emptySet(), setOf(3L to 0), 20, -1))
        assertNull(mutual().withOffset("Ken", setOf(1L to 0), setOf(3L to 0), 20, -1))
    }

    @Test
    fun removingOffsetRestoresBothSides() {
        val d0 = mutual()
        val d = d0.withOffset("Amy", setOf(2L to 0), setOf(3L to 0), 20, -1)!!
        val oid = d.item(3).pays.single().offsetId
        val back = d.withOffsetRemoved(oid)!!
        assertEquals(500L, back.t(2).items[0].remaining)
        assertFalse(back.t(2).items[0].closed)
        assertEquals(800L, back.item(3).remainingUnits)
        assertFalse(back.item(3).done)
        assertNull(back.withOffsetRemoved(oid))
        val again = Codec.decode(Codec.encode(d))
        assertEquals(oid, again.item(3).pays.single().offsetId)
        assertEquals(oid, again.t(2).items[0].pays.last().offsetId)
    }

    @Test
    fun debtFieldsSurviveSaveAndLoad() {
        val d = base(split(1, 300, 10, 100, "A" to 100L, "B" to 100L)).withDebtRepaid("A", "", 10, 20, 600, 100, 0, false)!!
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.t(1).debtItems, back.t(1).debtItems)
        assertEquals(100L, back.t(1).debtSelf)
        assertEquals(d.balances()[10], back.balances()[10])
    }

    @Test
    fun firstVersionSingleDebtIsMigrated() {
        // 第一版（10/16 測試版）：debtTo／debtOrig／debtDone／debtPays，還清後 amount 改成實際還的 900
        val root = JSONObject(Codec.encode(base(debt(1, 800, 10))))
        val o = root.getJSONArray("txns").getJSONObject(0)
        o.remove("debtItems"); o.remove("debtSelf")
        o.put("amount", 900).put("debtTo", "Amy").put("debtOrig", 800).put("debtDone", true)
            .put("debtPays", JSONArray().put(JSONObject().put("day", 20).put("accountId", 10).put("amount", 900).put("time", -1).put("fxAmount", 0).put("units", 800)))
        val back = Codec.decode(root.toString())
        val t = back.t(1)
        assertEquals(800L, t.amount)
        assertEquals("Amy", t.debtItems.single().who)
        assertTrue(t.debtItems.single().done)
        assertEquals(900L, t.paid)
        assertEquals(49_100L, back.balances()[10])
    }

    @Test
    fun repayManyCurrenciesAtOnce() {
        val d0 = base(debt(1, 894, 10, who = "Ken"), fxDebt(2, 2640, 12_000, 11))
        val parts = listOf(
            DebtRepayPart("", 10, 894, 0, false),
            DebtRepayPart("JPY", 10, 12_000, 2700, false),
        )
        val d = d0.withDebtsRepaid("Ken", parts, 20, 600)!!
        assertTrue(d.item(1).done)
        assertTrue(d.item(2).done)
        assertEquals(2700L, d.t(2).paid)                             // 外幣那筆改成實際扣的台幣
        assertEquals(50_000L - 894 - 2700, d.balances()[10])
        assertEquals(0L, d.debtOutstanding())
        // 只要其中一種還不了（外幣還得比欠的多），整個不算
        assertNull(d0.withDebtsRepaid("Ken", listOf(parts[0], DebtRepayPart("JPY", 10, 13_000, 2800, false)), 20, 600))
        assertNull(d0.withDebtsRepaid("Ken", emptyList(), 20, 600))
    }

    @Test
    fun csvKeepsDebtPeopleAndSelfPart() {
        val d = base(debt(1, 800, 10, note = "晚餐"), split(2, 300, 11, 100, "A" to 100L, "B" to 100L))
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        val (back, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(2, n)
        val all = back.txns.first { it.debtItems.size == 1 }
        assertNull(all.accountId)                                    // 全部欠別人：不能補成預設帳戶
        assertEquals(800L, all.debtItems.single().amount)
        val part = back.txns.first { it.debtItems.size == 2 }
        assertEquals(100L, part.debtSelf)
        assertEquals(listOf("A" to 100L, "B" to 100L), part.debtItems.map { it.who to it.amount })
    }
}
