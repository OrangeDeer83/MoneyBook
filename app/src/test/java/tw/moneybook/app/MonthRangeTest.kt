package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.moneybook.app.ui.assetMonths
import tw.moneybook.app.ui.trendLabels
import java.time.LocalDate
import java.time.YearMonth

/** 統計自訂期間（草圖 ci-out/stats_custom_range.png）：月份表怎麼點、趨勢分頁和總資產頁的月份、圖上的月份標籤 */
class MonthRangeTest {
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    @Test
    fun monthsBetweenIncludesBothEndsAndSwaps() {
        assertEquals(listOf(ym(2025, 11), ym(2025, 12), ym(2026, 1), ym(2026, 2)), monthsBetween(ym(2025, 11), ym(2026, 2)))
        assertEquals(monthsBetween(ym(2025, 11), ym(2026, 2)), monthsBetween(ym(2026, 2), ym(2025, 11)))
        assertEquals(listOf(ym(2026, 3)), monthsBetween(ym(2026, 3), ym(2026, 3)))
    }

    @Test
    fun tapStartThenEnd() {
        var p = MonthPick(ym(2025, 12), ym(2026, 11))
        assertFalse(p.editEnd)
        p = p.tap(ym(2025, 3))                      // 先點開始，自動換成選結束
        assertEquals(ym(2025, 3), p.from)
        assertEquals(ym(2026, 11), p.to)            // 結束還是原本的
        assertTrue(p.editEnd)
        p = p.tap(ym(2026, 2))
        assertEquals(ym(2025, 3), p.from)
        assertEquals(ym(2026, 2), p.to)
        assertEquals(12, p.count)
        // 結束點得比開始早：對調
        p = p.tap(ym(2024, 10))
        assertEquals(ym(2024, 10), p.from)
        assertEquals(ym(2025, 3), p.to)
        // 重選開始、比結束晚：結束先跟著開始（只有 1 個月，按「好」會提示最少 2 個月）
        p = p.copy(editEnd = false).tap(ym(2025, 8))
        assertEquals(ym(2025, 8), p.from)
        assertEquals(ym(2025, 8), p.to)
        assertEquals(1, p.count)
    }

    @Test
    fun trendMonthsForEachChoice() {
        val now = ym(2026, 11)
        val first = ym(2025, 6)
        assertEquals(monthsEnding(now, 6), trendMonthsOf(6, now, first, now))
        assertEquals(monthsEnding(now, 12), trendMonthsOf(12, now, first, now))
        // 全部：第一筆的月份到這個月
        val all = trendMonthsOf(0, now, first, now)
        assertEquals(first, all.first())
        assertEquals(now, all.last())
        assertEquals(18, all.size)
        // 全部但這個月才開始記：最少 2 個月
        assertEquals(listOf(ym(2026, 10), now), trendMonthsOf(0, now, now, now))
        // 自訂
        assertEquals(monthsBetween(ym(2025, 3), ym(2026, 2)), trendMonthsOf(-1, now, first, now, ym(2025, 3), ym(2026, 2)))
        // 自訂但沒選過：當全部
        assertEquals(all, trendMonthsOf(-1, now, first, now))
    }

    @Test
    fun assetMonthsCustom() {
        val d = Defaults.create()
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(monthsBetween(ym(2025, 3), ym(2026, 2)), d.assetMonths(-1, today, ym(2025, 3), ym(2026, 2)))
        assertEquals(12, d.assetMonths(12, today).size)
    }

    @Test
    fun labelsThinOutForLongRanges() {
        // 6 個月以內每個月都寫
        assertTrue(trendLabels(monthsEnding(ym(2026, 11), 6)).all { it.isNotEmpty() })
        // 12 個月：從最後一個往前隔一個
        val l12 = trendLabels(monthsEnding(ym(2026, 12), 12))
        assertEquals("12月", l12.last())
        assertEquals("", l12[10])
        assertEquals(6, l12.count { it.isNotEmpty() })
        // 30 個月：大約 6 個，最後一個一定寫
        val l30 = trendLabels(monthsEnding(ym(2026, 11), 30))
        assertEquals("11月", l30.last())
        assertTrue(l30.count { it.isNotEmpty() } in 5..7)
    }
}
