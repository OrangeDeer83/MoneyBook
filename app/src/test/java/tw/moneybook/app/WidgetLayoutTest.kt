package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 自組桌面小工具（草圖 ci-out/widget_compose2.png）：排版存檔、放不下就不畫、區塊的數字、點了打開哪裡 */
class WidgetLayoutTest {
    private val base = Defaults.create()
    private val today = LocalDate.of(2026, 11, 21)
    private val book = base.currentBook.id
    private val cat = base.categories.first { it.kind == TxType.EXPENSE && it.parentId != null }
    private val acc = base.accounts.first().id

    private fun tpl(id: Long, name: String) = Template(id, name, TxType.EXPENSE, 0L, cat.id, acc, "", emptyList())
    private fun spend(id: Long, amount: Long, day: LocalDate) =
        Txn(id, book, TxType.EXPENSE, amount, cat.id, acc, null, day.toEpochDay(), "", emptyList())

    @Test
    fun exampleIsTheTrialLayoutAndEverythingElseIsAddable() {
        val d = base.copy(templates = listOf(tpl(901, "早餐"), tpl(902, "午餐"), tpl(903, "捷運"), tpl(904, "晚餐")))
        val l = WBlocks.example(1000, "每天看", d)
        assertEquals(
            listOf(listOf("spend", "left"), listOf("bar"), listOf("tpls"), listOf("reimb", "add"), listOf("income"), listOf("perday")),
            l.layout.rows.map { it.ids },
        )
        assertEquals(listOf(901L, 902L, 903L), WBlocks.tplIds(d, l.opts))
        // 可以加的：沒用到的都在，捷徑只列一個
        val add = WBlocks.addable(l.layout)
        assertTrue("week" in add && "mascot" in add && "net" in add)
        assertEquals(listOf("shortcut1"), add.filter { WBlocks.isShortcut(it) })
        assertFalse("spend" in add)
        // 加了第一個捷徑，下一個捷徑才出來
        val l2 = WBlocks.PAGE.show(l.layout, "shortcut1")
        assertEquals(listOf("shortcut2"), WBlocks.addable(l2).filter { WBlocks.isShortcut(it) })
    }

    @Test
    fun saveAndLoadWithTheBackup() {
        val d = base.copy(templates = listOf(tpl(901, "早餐")))
        val l = WBlocks.example(1000, "每天看", d).copy(
            opts = mapOf("tpls" to "901", "add" to "INCOME", "shortcut1" to "reimb"), bookId = book, bg = 2, tone = 1, font = 0,
        )
        val back = Codec.decode(Codec.encode(d.copy(widgetLayouts = listOf(l))))
        assertEquals(listOf(l), back.widgetLayouts)
        assertTrue(back.nextId > 1000L)
        // 舊的資料沒有這個欄位
        assertEquals(emptyList<WLayout>(), Codec.decode(Codec.encode(base)).widgetLayouts)
    }

    @Test
    fun templatesInTheBlockFollowTheSavedOrderAndDropDeletedOnes() {
        val d = base.copy(templates = (1..10).map { tpl(900L + it, "T$it") })
        assertEquals(listOf(903L, 901L), WBlocks.tplIds(d, mapOf("tpls" to "903,999,901,903")))
        // 最多 8 個
        assertEquals(8, WBlocks.tplIds(d, mapOf("tpls" to (901..910).joinToString(","))).size)
        // 空的＝都不放（只剩＋）
        assertEquals(emptyList<Long>(), WBlocks.tplIds(d, mapOf("tpls" to "")))
    }

    @Test
    fun drawOnlyTheRowsThatFit() {
        val l = WBlocks.example(1000, "每天看", base)
        // 排高：54（數字半排）、50（進度條）、常用記帳 3 個＋＝4 格一排 44、54、54、54
        assertEquals(listOf(54f, 50f, 44f, 54f, 54f, 54f), WBlocks.rowHeights(l, 3))
        // 4×2 約 160dp：8＋54＋6＋50＋6＋44＝168 放不下第 3 排
        assertEquals(2, WBlocks.fitRows(l, 3, 160f))
        assertEquals(3, WBlocks.fitRows(l, 3, 180f))
        // 很小也至少畫一排
        assertEquals(1, WBlocks.fitRows(l, 3, 20f))
        assertEquals(6, WBlocks.fitRows(l, 3, 1000f))
        // 字大一點就放得比較少；常用記帳超過 3 個（加上＋超過 4 格）變兩排
        assertEquals(2, WBlocks.fitRows(l.copy(font = 2), 3, 190f))
        assertEquals(44f * 2 + 4f, WBlocks.rowHeights(l, 4)[2])
    }

    @Test
    fun numbersFollowTheBookAndBudget() {
        val d0 = base.copy(
            txns = listOf(spend(1, 300, today), spend(2, 700, today.minusDays(3)), spend(3, 999, today.minusMonths(1))),
            books = base.books.map { it.copy(budget = 10_000L) },
        )
        assertEquals(WNum("本月支出", "$1,000"), d0.widgetNum(WBlocks.SPEND, today, 0))
        assertEquals(WNum("今天花了", "$300"), d0.widgetNum(WBlocks.TODAY, today, 0))
        assertEquals(WNum("剩餘預算", "$9,000", 1), d0.widgetNum(WBlocks.LEFT, today, 0))
        // 11/21 到月底還有 10 天
        assertEquals(WNum("每天還能花", "$900", 1, "還有 10 天"), d0.widgetNum(WBlocks.PERDAY, today, 0))
        assertEquals(WNum("刷卡待確認", "3 筆", 2), d0.widgetNum(WBlocks.PEND, today, 3))
        val bar = d0.widgetBar(today)!!
        assertEquals(0.1f, bar.used, 0.0001f)
        assertEquals("11 月預算 10%・還剩 \$9,000", bar.label)
        // 超出預算
        val over = d0.copy(books = d0.books.map { it.copy(budget = 500L) })
        assertEquals(WNum("超出預算", "$500", 2), over.widgetNum(WBlocks.LEFT, today, 0))
        assertTrue(over.widgetBar(today)!!.over)
        // 沒設預算
        val none = d0.copy(books = d0.books.map { it.copy(budget = 0L) })
        assertEquals(WNum("剩餘預算", "還沒設定"), none.widgetNum(WBlocks.LEFT, today, 0))
        assertEquals(null, none.widgetBar(today))
        // 近 7 天：舊的在前，今天最後
        val week = d0.widgetWeek(today)
        assertEquals(7, week.size)
        assertEquals(21 to 300L, week.last())
        assertEquals(18 to 700L, week[3])
        // 花最多：只有一個分類
        assertEquals(listOf(Triple(base.topOf(cat).name, 1000L, 100)), d0.widgetTop3(today))
    }

    @Test
    fun otherBookOnlyWhenItExists() {
        val l = WBlocks.example(1000, "旅行", base).copy(bookId = 424242L)
        assertEquals(base.currentBook.id, base.forWidget(l).currentBook.id)
    }

    @Test
    fun whereTappingOpens() {
        val l = WBlocks.example(1000, "每天看", base).copy(opts = mapOf("add" to "TRANSFER", "shortcut2" to "stats"))
        assertEquals("home", widgetOpen(WBlocks.SPEND, l, today))
        assertEquals("rank", widgetOpen(WBlocks.BAR, l, today))
        assertEquals("po-month:" + YearMonth.of(2026, 11), widgetOpen(WBlocks.PRE, l, today))
        assertEquals("add:TRANSFER", widgetOpen(WBlocks.ADD, l, today))
        assertEquals("stats", widgetOpen("shortcut2", l, today))
        // 沒設定過的捷徑：預購清單
        assertEquals("preorders", widgetOpen("shortcut1", l, today))
        assertEquals("捷徑：統計", WBlocks.name("shortcut2", l.opts))
    }
}
