package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常用記帳小工具的格子排法（大小用使用者平板上的實際尺寸） */
class WidgetGridTest {
    @Test
    fun fewTemplatesInTallWidgetUseTwoRows() {
        // 4 個＋1，415×285：原本排成一排 5 根長條，改成 3＋2 兩排
        val g = tplGrid(4, 415f, 285f)
        assertEquals(listOf(3, 2), g.rows)
        assertEquals(4, g.shown)
        assertTrue(g.cellH >= GRID_TALL)        // 格子還是高：圖示在上
    }

    @Test
    fun manyTemplatesShowAsManyAsFit() {
        // 8 個＋1，415×285：最多 3 欄×4 排 → 全部放得下
        val g = tplGrid(8, 415f, 285f)
        assertEquals(8, g.shown)
        assertEquals(9, g.rows.sum())
        assertTrue(g.rows.all { it <= 3 })
        assertTrue(g.cellW >= GRID_MIN_W && g.cellH >= GRID_MIN_H)
    }

    @Test
    fun oneRowWidgetKeepsCellsWideEnough() {
        // 4×1 約 400×74：每格至少 110dp → 2 個＋1
        val g = tplGrid(8, 400f, 74f)
        assertEquals(listOf(3), g.rows)
        assertEquals(2, g.shown)
        assertTrue(g.cellW >= GRID_MIN_W)
        // 寬一點的一排：4 個＋1
        assertEquals(listOf(5), tplGrid(8, 610f, 74f).rows)
    }

    @Test
    fun tinyWidgetStillHasPlus() {
        val g = tplGrid(3, 100f, 50f)
        assertEquals(listOf(1), g.rows)
        assertEquals(0, g.shown)
        assertEquals(listOf(1), tplGrid(0, 415f, 285f).rows)
    }
}
