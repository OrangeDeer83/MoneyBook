package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 預購清單（草圖 ci-out/preorder_plan.png）：預留扣在預計買的那個月、買了／沒買／延後、通知、存檔 */
class PreorderTest {
    private val base = Defaults.create()
    private val today = LocalDate.of(2026, 11, 21).toEpochDay()
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val cat = base.categories.first { it.kind == TxType.EXPENSE && it.parentId == null }

    private fun po(amount: Long, day: LocalDate, note: String = "", catId: Long? = cat.id) =
        Preorder(0L, 0L, amount, catId, null, day.toEpochDay(), note = note, created = today)

    private fun add(d: AppData, p: Preorder): Pair<AppData, Long> = d.withPreorder(null, p)

    @Test
    fun reservedGoesToTheMonthItIsPlannedFor() {
        val (d1, sw) = add(base, po(10_000, LocalDate.of(2026, 12, 20), "Switch 2"))
        val (d2, _) = add(d1, po(2_980, LocalDate.of(2026, 11, 30), "耳機"))
        assertEquals(2_980L, d2.preorderReserved(nov))
        assertEquals(10_000L, d2.preorderReserved(dec))
        // 預算用掉的＝真的花的＋預留的
        assertEquals(2_980L, d2.budgetUsed(emptyList(), nov))
        // 買了：預留拿掉（真的那一筆照一般的算，不會扣兩次）
        val bought = d2.withPreorderBought(sw, 999L, today)
        assertEquals(0L, bought.preorderReserved(dec))
        assertEquals(PRE_BOUGHT, bought.preorders.first { it.id == sw }.status)
        // 沒買：預留放回預算
        val skipped = d2.withPreorderSkipped(sw, today)
        assertEquals(0L, skipped.preorderReserved(dec))
        // 放回等著買：又預留
        assertEquals(10_000L, skipped.withPreorderWaiting(sw).preorderReserved(dec))
        // 延後到 1 月：12 月不扣、1 月扣
        val later = d2.withPreorderDay(sw, LocalDate.of(2027, 1, 5).toEpochDay())
        assertEquals(0L, later.preorderReserved(dec))
        assertEquals(10_000L, later.preorderReserved(YearMonth.of(2027, 1)))
    }

    @Test
    fun outOfBudgetCategoryIsNotReservedAndOtherBooksDoNotCount() {
        val outCat = cat.copy(budgetMode = 1)
        val d0 = base.copy(categories = base.categories.map { if (it.id == cat.id) outCat else it })
        val (d1, _) = add(d0, po(5_000, LocalDate.of(2026, 12, 1)))
        assertEquals(0L, d1.preorderReserved(dec))
        // 別本帳本的預購不算
        val other = d1.copy(preorders = d1.preorders.map { it.copy(bookId = 12345L) })
        assertTrue(other.bookPreorders.isEmpty())
    }

    @Test
    fun editKeepsStatusAndName() {
        val (d1, id) = add(base, po(1_000, LocalDate.of(2026, 12, 1), ""))
        assertEquals(cat.name, d1.preorderName(d1.preorders.single()))      // 沒備註用分類名稱
        val (d2, id2) = d1.withPreorder(id, po(1_500, LocalDate.of(2026, 12, 3), "相機\n第二行"))
        assertEquals(id, id2)
        val p = d2.preorders.single()
        assertEquals(1_500L, p.amount)
        assertEquals(PRE_WAIT, p.status)
        assertEquals("相機", d2.preorderName(p))
        assertEquals(d1.preorders.single().bookId, p.bookId)
    }

    @Test
    fun noticeOnTheDayThenRepeats() {
        val (d1, id) = add(base, po(10_000, LocalDate.of(2026, 11, 21), "Switch 2"))
        val n = d1.preorderNotice(today, 2, "小鹿")!!
        assertEquals("小鹿：Switch 2 今天要買嗎？", n.title)
        assertEquals(id, n.single)
        assertEquals(false, n.worried)
        // 過了 2 天還會問，第 3 天不問（再提醒 2 天）
        val n2 = d1.preorderNotice(today + 2, 2, "小鹿")!!
        assertEquals("小鹿：Switch 2 買了嗎？", n2.title)
        assertTrue(n2.body.endsWith("預計 2 天前買"))
        assertTrue(n2.worried)
        assertNull(d1.preorderNotice(today + 3, 2, "小鹿"))
        assertEquals(id, d1.preorderNotice(today + 30, SUB_REPEAT_DAILY, "小鹿")?.single)
        // 還沒到、或回答過的不問
        assertNull(d1.preorderNotice(today - 1, 2, "小鹿"))
        assertNull(d1.withPreorderSkipped(id, today).preorderNotice(today, 2, "小鹿"))
        // 好幾樣：合成一則
        val (d2, _) = add(d1, po(500, LocalDate.of(2026, 11, 20), "書"))
        val many = d2.preorderNotice(today, 2, "小鹿")!!
        assertEquals("小鹿：有 2 樣預購該買了", many.title)
        assertNull(many.single)
        assertEquals(listOf("書 $500", "Switch 2 $10,000"), many.lines.map { it.replace(Money.twd, "$") })
    }

    @Test
    fun savedAndLoaded() {
        val (d1, id) = add(base, po(10_000, LocalDate.of(2026, 12, 20), "Switch 2").copy(shop = "巴哈商城", tags = listOf("遊戲")))
        val d2 = d1.withPreorderBought(id, 77L, today).withPreorder(null, po(300, LocalDate.of(2026, 12, 1))).first
        val back = Codec.decode(Codec.encode(d2))
        assertEquals(d2.preorders, back.preorders)
        assertTrue(back.nextId > back.preorders.maxOf { it.id })
        assertEquals(true, back.prefs.preorderNotify)
        assertEquals(false, Codec.decode(Codec.encode(d2.copy(prefs = d2.prefs.copy(preorderNotify = false)))).prefs.preorderNotify)
        // 舊資料沒有預購
        assertTrue(Codec.decode(Codec.encode(base)).preorders.isEmpty())
    }
}
