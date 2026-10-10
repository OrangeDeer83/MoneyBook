package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 台幣帳戶（例如台幣信用卡）刷外幣：預估、待請款、請款後填實際台幣 */
class FxSpendTest {
    private val card = Account(1, "卡", AccountType.CARD.emoji, AccountType.CARD, 0L, 0, creditLimit = 100_000L, statementDay = 25, dueDay = 10)

    private fun fxSpend(id: Long, day: Long, twd: Long, jpy: Long, pending: Boolean = true) =
        Txn(id, 1L, TxType.EXPENSE, twd, null, 1L, null, day, "一蘭", emptyList(), fxSpendAmount = jpy, fxSpendCur = "JPY", fxPending = pending)

    private fun data(vararg t: Txn) = Defaults.create().copy(accounts = listOf(card), txns = sortTxns(t.toList()))

    @Test
    fun noteShowsEstimateThenActualRate() {
        val est = fxSpend(1, 100, 2_638, 12_000)                      // 預估：12,000 日圓 ≈ 2,638 台幣 → 0.2198
        assertEquals("¥12,000 @ 0.2198（預估）", est.fxSpendNote())
        val real = est.copy(amount = 2_689, fxPending = false)       // 請款：實際 2,689 → 0.2241
        assertEquals("¥12,000 @ 0.2241（實際）", real.fxSpendNote())
        assertNull(Txn(2, 1L, TxType.EXPENSE, 100, null, 1L, null, 100, "", emptyList()).fxSpendNote())
        assertEquals(est.fxSpendNote(), Defaults.create().copy(accounts = listOf(card), txns = listOf(est)).fxNote(est, false))
    }

    @Test
    fun settleReplacesTheEstimateWithTheActualAmount() {
        val d = data(fxSpend(1, 100, 2_638, 12_000), fxSpend(2, 101, 500, 2_000, pending = false))
        val after = d.withFxSettled(1, 2_689)!!
        val t = after.txns.single { it.id == 1L }
        assertEquals(2_689L, t.amount)
        assertEquals(false, t.fxPending)
        assertEquals(12_000L, t.fxSpendAmount)                       // 外幣金額不變
        // 已經請款的、金額不對的、找不到的：不能再請款
        assertNull(d.withFxSettled(2, 100))
        assertNull(d.withFxSettled(1, 0))
        assertNull(d.withFxSettled(99, 100))
    }

    @Test
    fun pendingListHasOldestFirstAndOnlyUnsettled() {
        val d = data(fxSpend(1, 105, 100, 500), fxSpend(2, 100, 200, 900), fxSpend(3, 101, 300, 1_000, pending = false))
        assertEquals(listOf(2L, 1L), d.fxPendingList().map { it.id })
    }

    @Test
    fun accountBalanceStillUsesTheTwdAmount() {
        val d = data(fxSpend(1, 100, 2_638, 12_000))
        assertEquals(-2_638L, d.balances()[1L])
    }

    @Test
    fun jsonRoundTripAndOldDataDefaults() {
        val d = data(fxSpend(1, 100, 2_638, 12_000))
        val t = Codec.decode(Codec.encode(d)).txns.single()
        assertEquals(12_000L, t.fxSpendAmount)
        assertEquals("JPY", t.fxSpendCur)
        assertEquals(true, t.fxPending)
        val root = org.json.JSONObject(Codec.encode(d))
        root.getJSONArray("txns").getJSONObject(0).apply { remove("fxSpendAmount"); remove("fxSpendCur"); remove("fxPending") }
        val old = Codec.decode(root.toString()).txns.single()
        assertEquals(0L, old.fxSpendAmount)
        assertEquals("", old.fxSpendCur)
        assertEquals(false, old.fxPending)
    }

    @Test
    fun csvRoundTripKeepsForeignSpendAndPending() {
        val d = data(fxSpend(1, 100, 2_638, 12_000), fxSpend(2, 101, 500, 2_000, pending = false))
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        assertTrue(csv.contains(",是"))
        val (back, n) = CsvIO.import(Defaults.create().copy(accounts = listOf(card)), csv)
        assertEquals(2, n)
        val rows = back.txns.sortedBy { it.day }
        assertEquals(listOf(12_000L, 2_000L), rows.map { it.fxSpendAmount })
        assertEquals(listOf("JPY", "JPY"), rows.map { it.fxSpendCur })
        assertEquals(listOf(true, false), rows.map { it.fxPending })
        assertEquals(listOf(2_638L, 500L), rows.map { it.amount })
    }

    @Test
    fun foreignSpendMakesMoneyUseNtDollar() {
        Money.sync(Defaults.create())
        assertEquals("$", Money.twd)
        Money.sync(data(fxSpend(1, 100, 2_638, 12_000)))
        assertEquals("NT$", Money.twd)
        Money.sync(Defaults.create())
    }
}
