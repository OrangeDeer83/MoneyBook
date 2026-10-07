package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 報銷平分：不含自己、含自己、平分剩下的（含自己） */
class ReimbSplitTest {
    @Test
    fun withoutSelfSplitsAmongTheListedPeopleOnly() {
        val r = splitAmounts(false, 600, 3)
        assertEquals(listOf(200L, 200L, 200L), r.amounts)
        assertEquals(0L, r.self)
    }

    @Test
    fun withoutSelfGivesTheRemainderToTheFirstPerson() {
        val r = splitAmounts(false, 100, 3)
        assertEquals(listOf(34L, 33L, 33L), r.amounts)
        assertEquals(100L, r.amounts.sum())
    }

    @Test
    fun withSelfCountsMeAsOneMorePerson() {
        val r = splitAmounts(true, 600, 3)       // 600 ÷ 4
        assertEquals(listOf(150L, 150L, 150L), r.amounts)
        assertEquals(150L, r.self)
    }

    @Test
    fun withSelfLeavesTheRemainderOnMe() {
        val r = splitAmounts(true, 10, 3)        // 10 ÷ 4 = 2 餘 2：每人 2，我 4
        assertEquals(listOf(2L, 2L, 2L), r.amounts)
        assertEquals(4L, r.self)
        assertEquals(10L, r.amounts.sum() + r.self)
    }

    @Test
    fun theRestAfterFixedAmountsIsSplitIncludingMe() {
        // 實付 600，Amy 固定 300：剩 300 分給 Bob、Cat 和我
        val r = splitAmounts(true, 600 - 300, 2)
        assertEquals(listOf(100L, 100L), r.amounts)
        assertEquals(100L, r.self)
    }

    @Test
    fun edgeCases() {
        assertEquals(SplitResult(emptyList(), 500L), splitAmounts(true, 500, 0))
        assertEquals(listOf(0L, 0L), splitAmounts(true, -50, 2).amounts)
        assertEquals(listOf(600L), splitAmounts(false, 600, 1).amounts)
        assertEquals(listOf(300L), splitAmounts(true, 600, 1).amounts)     // 我和 Amy 平分
    }
}
