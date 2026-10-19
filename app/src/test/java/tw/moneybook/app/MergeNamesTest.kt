package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 報銷／欠款名單：名字一樣的合成一個人、金額加總 */
class MergeNamesTest {
    @Test
    fun sameNamesAreMerged() {
        assertEquals(
            listOf("Amy" to 500L, "Bob" to 100L),
            mergeSameNames(listOf("Amy" to 200L, "Bob" to 100L, " amy " to 300L)),
        )
    }

    @Test
    fun blankNamesStaySeparate() {
        assertEquals(listOf("" to 100L, "" to 50L), mergeSameNames(listOf(" " to 100L, "" to 50L)))
    }
}
