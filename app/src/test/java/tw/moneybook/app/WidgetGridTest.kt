package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常用記帳小工具的格子排法（大小用使用者平板上的實際尺寸）、名稱兩行平均分 */
class WidgetGridTest {
    @Test
    fun fewTemplatesInTallWidgetUseFlatterCells() {
        // 4 個＋1，415×285：不再排成一排 5 根長條
        val g = tplGrid(4, 415f, 285f)
        assertTrue(g.rows.size >= 2)
        assertEquals(5, g.rows.sum())
        assertEquals(4, g.shown)
    }

    @Test
    fun manyTemplatesShowAsManyAsFit() {
        // 8 個＋1，415×285：最多 2 欄×4 排 → 放 7 個
        val g = tplGrid(8, 415f, 285f)
        assertEquals(7, g.shown)
        assertTrue(g.rows.all { it <= 2 })
        assertTrue(g.cellW >= GRID_MIN_W && g.cellH >= GRID_MIN_H)
    }

    @Test
    fun oneRowAndTwoRowWidgets() {
        // 4×1 約 400×74：每格至少 135dp → 1 個＋1；4×2 約 415×160：3 個＋1
        assertEquals(listOf(2), tplGrid(8, 400f, 74f).rows)
        assertEquals(3, tplGrid(8, 415f, 160f).shown)
    }

    @Test
    fun tinyWidgetStillHasPlus() {
        val g = tplGrid(3, 100f, 50f)
        assertEquals(listOf(1), g.rows)
        assertEquals(0, g.shown)
        assertEquals(listOf(1), tplGrid(0, 415f, 285f).rows)
    }

    @Test
    fun longNamesSplitEvenly() {
        // 放得下就不斷
        assertEquals("加油", splitName("加油", 5f))
        // 兩行平均分
        assertEquals("禮拜三\n羽毛球", splitName("禮拜三羽毛球", 5f))
        assertEquals("大眾\n運輸", splitName("大眾運輸", 3f))
        // 有空白斷在空白
        assertEquals("早餐\n雙蛋餅", splitName("早餐 雙蛋餅", 5f))
        // 不在英文字中間斷
        assertEquals("Netflix\n訂閱", splitName("Netflix 訂閱", 4.5f))
        assertEquals("7-11\n咖啡", splitName("7-11咖啡", 3f))
    }
}
