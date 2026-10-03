package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常用帳戶（記一筆選帳戶時放最上面）：手動標星號＋自動補滿 */
class FrequentAccountsTest {
    private val today = LocalDate.of(2026, 10, 3)
    private val day = today.toEpochDay()

    private fun acc(id: Long, name: String, type: AccountType, order: Int, hidden: Boolean = false, favorite: Boolean = false, initial: Long = 0L) =
        Account(id, name, type.emoji, type, initial, order, hidden, favorite = favorite)

    private fun base(accs: List<Account>, txns: List<Txn>): AppData = Defaults.create().copy(accounts = accs, txns = txns)

    /** 在目前帳本、指定的天數前記一筆（預設就是今天） */
    private fun AppData.tx(id: Long, from: Long?, to: Long? = null, daysAgo: Int = 0, adjust: Boolean = false, inst: Long? = null, book: Long? = null) =
        Txn(
            id, book ?: currentBook.id, if (to != null) TxType.TRANSFER else TxType.EXPENSE, 10, null, from, to,
            day - daysAgo, "", emptyList(), instGroup = inst, adjust = adjust,
        )

    private val accs = listOf(
        acc(1, "現金", AccountType.CASH, 0),
        acc(2, "銀行A", AccountType.BANK, 1),
        acc(3, "銀行B", AccountType.BANK, 2),
        acc(4, "信用卡", AccountType.CARD, 3),
        acc(5, "舊帳戶", AccountType.BANK, 4, hidden = true),
    )

    private fun ids(d: AppData, limit: Int = 3) = d.frequentAccounts(limit, today).map { it.id }

    // ───── 自動統計 ─────
    @Test
    fun orderedByRecentUsage() {
        val d = base(accs, emptyList())
        val d2 = d.copy(txns = listOf(d.tx(1, 3), d.tx(2, 3), d.tx(3, 3), d.tx(4, 2), d.tx(5, 2), d.tx(6, 1)))
        assertEquals(listOf(3L, 2L, 1L), ids(d2))
    }

    @Test
    fun transferCountsBothSides() {
        val d = base(accs, emptyList()).let { it.copy(txns = listOf(it.tx(1, 2, 3), it.tx(2, 2, 3), it.tx(3, 1))) }
        assertEquals(listOf(2L, 3L, 1L), ids(d))
    }

    @Test
    fun neverUsedAndHiddenAccountsAreNotListed() {
        val d = base(accs, emptyList()).let { it.copy(txns = listOf(it.tx(1, 5), it.tx(2, 5), it.tx(3, 2))) }
        assertEquals(listOf(2L), ids(d))
    }

    @Test
    fun balanceAdjustmentsDoNotCountAsUsage() {
        // 帳戶 1 只有餘額調整，不是真的在用；帳戶 2 用了 1 次
        val d = base(accs, emptyList()).let {
            it.copy(txns = listOf(it.tx(1, 1, adjust = true), it.tx(2, 1, adjust = true), it.tx(3, 1, adjust = true), it.tx(4, 2)))
        }
        assertEquals(listOf(2L), ids(d))
    }

    @Test
    fun installmentsCountOnceNotPerPeriod() {
        // 帳戶 1 是 12 期分期的同一組（算 1 次），帳戶 2 單筆用了 2 次
        val d = base(accs, emptyList()).let { b ->
            val inst = (1L..12L).map { b.tx(it, 1, inst = 777) }
            b.copy(txns = inst + listOf(b.tx(20, 2), b.tx(21, 2)))
        }
        assertEquals(listOf(2L, 1L), ids(d))
    }

    @Test
    fun recentUsageBeatsOldUsage() {
        // 帳戶 1 很久以前（200 天前）用了 5 次，帳戶 2 最近用了 1 次 → 最近的優先
        val d = base(accs, emptyList()).let { b ->
            b.copy(txns = (1L..5L).map { b.tx(it, 1, daysAgo = 200) } + b.tx(10, 2, daysAgo = 5))
        }
        assertEquals(listOf(2L, 1L), ids(d))
    }

    @Test
    fun onlyTheCurrentBookCountsAsRecent() {
        // 帳戶 1 最近在別的帳本用了 5 次；帳戶 2 最近在目前帳本用 1 次 → 目前帳本優先
        val b = base(accs, emptyList())
        val d = b.copy(txns = (1L..5L).map { b.tx(it, 1, book = 99999L) } + b.tx(10, 2))
        assertEquals(listOf(2L, 1L), ids(d))
    }

    @Test
    fun fallsBackToAllTimeWhenNothingIsRecent() {
        val b = base(accs, emptyList())
        val d = b.copy(txns = listOf(b.tx(1, 3, daysAgo = 400), b.tx(2, 3, daysAgo = 400), b.tx(3, 2, daysAgo = 400)))
        assertEquals(listOf(3L, 2L), ids(d))
    }

    @Test
    fun tieBreaksByDisplayOrderAndLimitApplies() {
        val b = base(accs, emptyList())
        val d = b.copy(txns = listOf(b.tx(1, 1), b.tx(2, 2), b.tx(3, 3), b.tx(4, 4)))
        assertEquals(listOf(1L, 2L), ids(d, 2))
    }

    @Test
    fun noRecordsMeansNoFrequentAccounts() {
        assertTrue(base(accs, emptyList()).frequentAccounts(3, today).isEmpty())
    }

    // ───── 手動標星號 ─────
    @Test
    fun starredAccountsComeFirstEvenIfNeverUsed() {
        val withStar = accs.map { if (it.id == 4L) it.copy(favorite = true) else it }
        val b = base(withStar, emptyList())
        val d = b.copy(txns = listOf(b.tx(1, 3), b.tx(2, 3), b.tx(3, 2), b.tx(4, 1)))
        // 星號的 4（沒用過）在最前面；其餘名額（共 3 個）由用得多的補：3（2 次），再來 1、2 各 1 次，照帳戶頁順序取 1
        assertEquals(listOf(4L, 3L, 1L), ids(d))
    }

    @Test
    fun starredAccountsAreNeverCutOffByTheLimit() {
        val allStarred = accs.map { if (it.id in 1L..4L) it.copy(favorite = true) else it }
        val d = base(allStarred, emptyList())
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(d))   // 4 個星號超過 limit 3，全部都列，照帳戶頁順序
    }

    @Test
    fun hiddenStarredAccountsAreNotListed() {
        val s = accs.map { if (it.id == 5L) it.copy(favorite = true) else it }
        assertTrue(ids(base(s, emptyList())).isEmpty())
    }

    @Test
    fun starSurvivesSaveAndOldBackupsHaveNoStars() {
        val d = base(accs.map { if (it.id == 2L) it.copy(favorite = true) else it }, emptyList())
        val back = Codec.decode(Codec.encode(d))
        assertEquals(listOf(2L), back.accounts.filter { it.favorite }.map { it.id })
        val root = org.json.JSONObject(Codec.encode(d))
        val arr = root.getJSONArray("accounts")
        for (i in 0 until arr.length()) arr.getJSONObject(i).remove("favorite")
        assertTrue(Codec.decode(root.toString()).accounts.none { it.favorite })
    }
}
