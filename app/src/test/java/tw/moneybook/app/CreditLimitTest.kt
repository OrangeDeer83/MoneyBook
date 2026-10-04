package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 信用卡共用額度 */
class CreditLimitTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()

    private fun card(id: Long, name: String, limit: Long = 0L, shareWith: Long = 0L, type: AccountType = AccountType.CARD) =
        Account(id, name, type.emoji, type, 0L, id.toInt(), creditLimit = limit, sharedLimitOf = shareWith)

    private fun spend(id: Long, acc: Long, amount: Long) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, acc, null, day, "", emptyList())

    private fun data(accs: List<Account>, vararg t: Txn) = Defaults.create().copy(accounts = accs, txns = t.toList())

    @Test
    fun aCardWithoutSharingUsesItsOwnLimit() {
        val d = data(listOf(card(1, "A", 100_000)), spend(1, 1, 30_000))
        val info = d.limitInfo(d.accMap[1L]!!)!!
        assertEquals(100_000L, info.limit)
        assertEquals(30_000L, info.used)
        assertEquals(70_000L, info.available)
        assertFalse(info.shared)
        assertEquals(listOf(1L), info.members.map { it.id })
    }

    @Test
    fun noLimitMeansNoInfo() {
        val d = data(listOf(card(1, "A")), spend(1, 1, 500))
        assertNull(d.limitInfo(d.accMap[1L]!!))
    }

    @Test
    fun sharedCardsUseTheOwnersLimitAndAddUpTheirUsage() {
        // B 沒有自己的額度（填了也不算），跟著 A 的額度走
        val d = data(listOf(card(1, "A", 100_000), card(2, "B", 50_000, shareWith = 1)), spend(1, 1, 30_000), spend(2, 2, 20_000))
        for (id in listOf(1L, 2L)) {
            val info = d.limitInfo(d.accMap[id]!!)!!
            assertEquals("額度取主卡的", 100_000L, info.limit)
            assertEquals("已用是兩張加總", 50_000L, info.used)
            assertEquals(50_000L, info.available)
            assertTrue(info.shared)
            assertEquals(listOf(1L, 2L), info.members.map { it.id })
            assertEquals(1L, info.owner.id)
        }
    }

    @Test
    fun overpaidCardDoesNotReduceTheOthersUsage() {
        // A 多繳了（餘額為正）不能抵銷 B 的已用
        val accs = listOf(card(1, "A", 100_000).copy(initial = 5_000L), card(2, "B", shareWith = 1))
        val d = data(accs, spend(1, 2, 20_000))
        assertEquals(20_000L, d.limitInfo(d.accMap[2L]!!)!!.used)
    }

    @Test
    fun usedBeyondTheLimitLeavesZeroAvailable() {
        val d = data(listOf(card(1, "A", 10_000)), spend(1, 1, 12_000))
        assertEquals(0L, d.limitInfo(d.accMap[1L]!!)!!.available)
    }

    @Test
    fun brokenLinksFallBackToTheCardItself() {
        // 指向的主卡不存在／不是信用卡／兩張互相指向：都當成沒有共用，不會當掉
        val missing = data(listOf(card(1, "A", 80_000, shareWith = 99)))
        assertEquals(80_000L, missing.limitInfo(missing.accMap[1L]!!)!!.limit)
        val notCard = data(listOf(card(1, "A", 80_000, shareWith = 2), card(2, "銀行", type = AccountType.BANK)))
        assertEquals(80_000L, notCard.limitInfo(notCard.accMap[1L]!!)!!.limit)
        val loop = data(listOf(card(1, "A", 80_000, shareWith = 2), card(2, "B", 60_000, shareWith = 1)))
        assertEquals(80_000L, loop.limitInfo(loop.accMap[1L]!!)!!.limit)
        assertEquals(60_000L, loop.limitInfo(loop.accMap[2L]!!)!!.limit)
    }

    @Test
    fun sharedFieldSurvivesSaveAndOldBackupsStillLoad() {
        val d = data(listOf(card(1, "A", 100_000), card(2, "B", shareWith = 1)))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(1L, back.accMap[2L]!!.sharedLimitOf)
        assertEquals(0L, back.accMap[1L]!!.sharedLimitOf)
        val old = Codec.encode(d).replace(Regex(",\"sharedLimitOf\":\\d+|\"sharedLimitOf\":\\d+,"), "")
        assertFalse(old.contains("sharedLimitOf"))
        assertEquals(0L, Codec.decode(old).accMap[2L]!!.sharedLimitOf)
    }
}
