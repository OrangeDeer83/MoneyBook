package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 統計頁卡片排版：存檔往返、整理舊排版、隱藏／顯示、整排／半排、靠左右、拖曳 */
class CardLayoutTest {
    private val page = CardPage(
        "t", "測試",
        listOf(CardSpec("a", "A", true), CardSpec("b", "B", true), CardSpec("c", "C", true), CardSpec("big", "大圖"), CardSpec("new", "新卡片", true)),
        CardLayout(listOf(CardRow("a"), CardRow("big"), CardRow("b"), CardRow("c")), hidden = listOf("new")),
    )

    private fun lay(vararg rows: CardRow, hidden: List<String> = emptyList()) = CardLayout(rows.toList(), hidden)

    @Test
    fun encodeDecodeRoundTrip() {
        val l = lay(CardRow("a", "b", true), CardRow(null, "c", true), CardRow("big"), hidden = listOf("new"))
        assertEquals(listOf("h:a|b", "h:|c", "f:big", "x:new"), l.encode())
        assertEquals(l, CardLayout.decode(l.encode()))
    }

    @Test
    fun layoutsSurviveSaveAndOldDataHasNone() {
        val d = Defaults.create()
        assertEquals(emptyMap<String, List<String>>(), Codec.decode(Codec.encode(d)).prefs.cardLayouts)
        val l = lay(CardRow("a", null, true), CardRow("big"), hidden = listOf("new"))
        val saved = d.copy(prefs = d.prefs.copy(cardLayouts = mapOf("t" to l.encode())))
        val back = Codec.decode(Codec.encode(saved)).prefs.cardLayouts
        assertEquals(l, CardLayout.decode(back["t"]!!))
    }

    @Test
    fun noSavedLayoutUsesDefault() {
        assertEquals(page.default, page.normalize(null))
    }

    @Test
    fun normalizeDropsUnknownAndDuplicatesAndAddsNewCards() {
        // 舊排版：有不認識的 zzz、重複的 a、大圖被放在半排、少了 c 和 new
        val saved = lay(CardRow("zzz"), CardRow("a", "big", true), CardRow("a"), CardRow("b"))
        val n = page.normalize(saved)
        // big 不能半排 → 自己一整排；a 剩下落單的半排（靠左）；新的 c 加到最下面；new 預設隱藏
        assertEquals(lay(CardRow("a", null, true), CardRow("big"), CardRow("b"), CardRow("c"), hidden = listOf("new")), n)
    }

    @Test
    fun hideKeepsPartnerOnItsSideAndShowAddsToBottom() {
        val l = lay(CardRow("a", "b", true), CardRow("big"))
        val h = page.hide(l, "a")
        assertEquals(lay(CardRow(null, "b", true), CardRow("big"), hidden = listOf("a")), h)
        assertEquals(lay(CardRow(null, "b", true), CardRow("big"), CardRow("a")), page.show(h, "a"))
    }

    @Test
    fun setHalfAndFull() {
        val l = lay(CardRow("a"), CardRow("big"))
        val h = page.setHalf(l, "a", true)
        assertEquals(lay(CardRow("a", null, true), CardRow("big")), h)
        // 大圖不能半排
        assertEquals(l, page.setHalf(l, "big", true))
        // 並排的右邊那張改整排：左邊那張留在左邊，整排放在後面
        val pair = lay(CardRow("a", "b", true))
        assertEquals(lay(CardRow("a", null, true), CardRow("b")), page.setHalf(pair, "b", false))
        assertEquals(lay(CardRow("a"), CardRow(null, "b", true)), page.setHalf(pair, "a", false))
    }

    @Test
    fun setSideMovesLoneCardOrSwapsPair() {
        val lone = lay(CardRow("a", null, true))
        assertEquals(lay(CardRow(null, "a", true)), page.setSide(lone, "a", true))
        val pair = lay(CardRow("a", "b", true))
        assertEquals(lay(CardRow("b", "a", true)), page.setSide(pair, "a", true))
        assertEquals(pair, page.setSide(pair, "a", false))
    }

    @Test
    fun moveBetweenRows() {
        val l = lay(CardRow("a"), CardRow("big"), CardRow("b", "c", true))
        // a 拖到最下面
        assertEquals(lay(CardRow("big"), CardRow("b", "c", true), CardRow("a")), page.moveToRow(l, "a", 3))
        // 右邊的 c 拖到最上面：維持半排、在右邊；b 留在左邊
        assertEquals(lay(CardRow(null, "c", true), CardRow("a"), CardRow("big"), CardRow("b", null, true)), page.moveToRow(l, "c", 0))
    }

    @Test
    fun moveIntoEmptySlot() {
        val l = lay(CardRow("a", null, true), CardRow("big"), CardRow("b"))
        // b 拖到 a 右邊的空位
        assertEquals(lay(CardRow("a", "b", true), CardRow("big")), page.moveToSlot(l, "b", 0, true))
        // 大圖不能放進半排的空位；位子有人也不行
        assertEquals(l, page.moveToSlot(l, "big", 0, true))
        assertEquals(l, page.moveToSlot(l, "b", 0, false))
        // 同一排自己換到另一邊
        assertEquals(lay(CardRow(null, "a", true), CardRow("big"), CardRow("b")), page.moveToSlot(l, "a", 0, true))
    }

    @Test
    fun dragHiddenCardIntoPlace() {
        val l = lay(CardRow("a", null, true), CardRow("big"), hidden = listOf("new"))
        assertEquals(lay(CardRow("a", "new", true), CardRow("big")), page.moveToSlot(l, "new", 0, true))
        assertEquals(lay(CardRow("a", null, true), CardRow("new"), CardRow("big")), page.moveToRow(l, "new", 1))
    }
}
