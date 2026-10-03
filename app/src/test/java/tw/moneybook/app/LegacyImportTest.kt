package tw.moneybook.app

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 舊記帳 App 匯出檔的轉換規則（用假資料，不含任何真實帳目） */
class LegacyImportTest {
    private val header = "類型,記帳時間,交易帳戶,交易帳本,貨幣符號,交易金額,一級分類,二級分類,交易標籤,備註,報銷,退款,收回,歸還,手續費,待報銷,分期,地點,借出方/借入方,多人記帳,匯率"

    private fun row(
        type: String, time: String, acc: String, amount: String, top: String, sub: String = top,
        note: String = "", reimb: String = "", fee: String = "", cur: String = "NT$", rate: String = "", book: String = "日常",
    ): String = listOf(type, time, acc, book, cur, amount, top, sub, "/", note, reimb, "", "", "", fee, "", "", "", "", "", rate).joinToString(",")

    private val rows = listOf(
        row("支出", "2026-09-01 10:00:00", "國泰", "-100", "吃喝", "午餐", "便當"),
        row("收入", "2026-09-02 09:00:00", "國泰", "5000", "收入", "薪資", "九月"),
        // 轉帳：轉出 1015（含手續費 15）、轉入 1000
        row("支出", "2026-09-03 12:00:00", "國泰", "-1015", "轉帳", fee = "15"),
        row("收入", "2026-09-03 12:00:00", "錢包", "1000", "轉帳"),
        // 還款（信用卡）
        row("支出", "2026-09-04 08:00:00", "國泰", "-2000", "還款"),
        row("收入", "2026-09-04 08:00:00", "cube卡", "2000", "還款"),
        // 股票基金買賣
        row("支出", "2026-09-05 13:00:00", "國泰", "-14893", "投資", "股票基金", "0050"),
        row("收入", "2026-09-06 13:00:00", "國泰", "5000", "收入", "股票基金", "賣出"),
        // 更新餘額
        row("收入", "2026-09-07 15:00:00", "錢包", "300", "更新餘額"),
        // 報銷：支出 370 報了 155，另外一筆報銷入帳 155
        row("支出", "2026-09-08 09:00:00", "錢包", "-370", "吃喝", "早餐", "美而美", reimb = "155"),
        row("收入", "2026-09-08 18:00:00", "錢包", "155", "報銷", note = "宗樺"),
        // 手續費為負數＝優惠：付 98，原價 1098
        row("支出", "2026-09-09 11:00:00", "cube卡", "-98", "購物", "3C", "滑鼠", fee = "-1000"),
        // 外幣：1000 円 × 0.2 = 200 NT$
        row("支出", "2026-09-10 20:00:00", "日幣錢包", "-1000", "旅行", "交通", "電車", cur = "円", rate = "0.2"),
        // 找不到另一半的轉帳
        row("支出", "2026-09-11 08:00:00", "國泰", "-50", "轉帳"),
        // 另一個帳本
        row("支出", "2026-09-12 08:00:00", "錢包", "-20", "吃喝", "點心", book = "日常分帳"),
        // 日期壞掉的一列
        row("支出", "abc", "錢包", "-1", "吃喝"),
        // 正的手續費：金額 675 已含手續費 10
        row("支出", "2026-09-13 08:00:00", "國泰", "-675", "生活", "其他", fee = "10"),
    )
    private val csv = (listOf(header) + rows).joinToString("\n")

    private fun convert() = LegacyImport.convert(Defaults.create(), csv)!!

    @Test
    fun detectsOnlyTheLegacyFormat() {
        assertTrue(LegacyImport.detect(CsvIO.parse(csv)))
        assertFalse(LegacyImport.detect(CsvIO.parse("日期,金額,備註\n2026-01-01,100,a")))
        assertNull(LegacyImport.convert(Defaults.create(), "日期,金額\n2026-01-01,100"))
        assertNull(LegacyImport.convert(Defaults.create(), ""))
    }

    @Test
    fun summaryCounts() {
        val s = convert().summary
        assertEquals(1, s.skipped)
        assertEquals(4, s.transfers)          // 轉帳 + 還款 + 股票買 + 股票賣
        assertEquals(2, s.stockTransfers)
        assertEquals(2, s.adjusts)            // 更新餘額、報銷入帳
        assertEquals(1, s.unpaired)
        assertEquals(1, s.foreign)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), s.from)
        assertEquals(LocalDate.of(2026, 9, 13).toEpochDay(), s.to)
        assertEquals(listOf("日常", "日常分帳"), s.newBooks)
    }

    @Test
    fun transferPairBecomesOneTransferWithFee() {
        val d = convert().data
        val acc = d.accounts.associateBy { it.name }
        val t = d.txns.single { it.type == TxType.TRANSFER && it.accountId == acc["國泰"]!!.id && it.toAccountId == acc["錢包"]!!.id }
        assertEquals(1000L, t.amount)
        assertEquals(15L, t.fee)
        assertEquals(15L, t.spent)             // 手續費算支出
        val pay = d.txns.single { it.toAccountId == acc["cube卡"]!!.id }
        assertEquals(2000L, pay.amount)         // 還款也是轉帳
    }

    @Test
    fun stockTradesBecomeTransfersToBrokerAndBrokerIsOffsetToZero() {
        val r = convert()
        val d = r.data
        val acc = d.accounts.associateBy { it.name }
        val broker = acc["國泰證券"]!!
        assertEquals(AccountType.INVEST, broker.type)
        assertEquals(listOf("國泰證券"), r.summary.offsetAccounts)
        assertEquals(0L, d.balances()[broker.id])
        val buy = d.txns.single { it.toAccountId == broker.id && it.type == TxType.TRANSFER }
        val sell = d.txns.single { it.accountId == broker.id && it.type == TxType.TRANSFER }
        assertEquals(14893L, buy.amount)
        assertEquals(5000L, sell.amount)
        assertEquals(0L, buy.spent + sell.spent)   // 不算支出
        assertEquals(1, d.txns.count { it.adjust && it.accountId == broker.id })   // 沖回 0 的那一筆
    }

    @Test
    fun adjustAndReimbursement() {
        val d = convert().data
        val acc = d.accounts.associateBy { it.name }
        val adjusts = d.txns.filter { it.adjust && it.accountId == acc["錢包"]!!.id }
        assertEquals(setOf(300L, 155L), adjusts.map { it.amount }.toSet())
        assertTrue(adjusts.any { it.note.contains("報銷入帳") && it.note.contains("宗樺") })
        val e = d.txns.single { it.note.contains("美而美") }
        assertEquals(370L, e.paid)
        assertEquals(2, e.reimb)
        assertEquals(155L, e.reimbAmount)
        assertEquals(215L, e.spent)             // 只算自己負擔的部分
    }

    @Test
    fun negativeFeeIsDiscountAndPositiveFeeIsKept() {
        val d = convert().data
        val mouse = d.txns.single { it.note.contains("滑鼠") }
        assertEquals(1098L, mouse.amount)
        assertEquals(1000L, mouse.discount)
        assertEquals(98L, mouse.paid)
        val fee = d.txns.single { it.fee == 10L }
        assertEquals(665L, fee.amount)
        assertEquals(675L, fee.paid)            // 實付 = 舊 App 的金額
    }

    @Test
    fun foreignCurrencyIsConvertedAndNoted() {
        val d = convert().data
        val t = d.txns.single { it.note.contains("電車") }
        assertEquals(200L, t.amount)
        assertTrue(t.note.contains("原幣 1000円"))
    }

    @Test
    fun unpairedTransferFallsBackToPlainExpense() {
        val d = convert().data
        val t = d.txns.single { it.type == TxType.EXPENSE && it.amount == 50L }
        assertEquals("轉帳", d.catMap[t.categoryId]!!.name)
    }

    @Test
    fun booksAreCreatedAndAssigned() {
        val d = convert().data
        val split = d.books.single { it.name == "日常分帳" }
        assertEquals(listOf(20L), d.txns.filter { it.bookId == split.id }.map { it.amount })
    }

    @Test
    fun accountTypesAreGuessedFromNames() {
        val g = LegacyImport::guessType
        assertEquals(AccountType.INVEST, g("台股"))
        assertEquals(AccountType.INVEST, g("幣安"))
        assertEquals(AccountType.ECARD, g("DAWHO悠遊卡"))
        assertEquals(AccountType.ECARD, g("ipass money"))
        assertEquals(AccountType.EPAY, g("永豐line pay"))
        assertEquals(AccountType.CARD, g("cube卡"))
        assertEquals(AccountType.CARD, g("玉山unicard"))
        assertEquals(AccountType.CASH, g("日幣錢包"))
        assertEquals(AccountType.BANK, g("永豐大戶"))
        assertEquals(AccountType.BANK, g("line bank口袋帳戶"))
        assertEquals(AccountType.OTHER, g("全聯禮卷"))
    }

    @Test
    fun statisticsIgnoreTransfersAdjustmentsAndStocks() {
        val d = convert().data
        // 收入只有薪資 5,000；支出 = 午餐 100 + 轉帳手續費 15 + 早餐自己負擔 215 + 滑鼠 98 + 電車 200 + 50 + 點心 20 + 675
        assertEquals(5000L, d.txns.incomeSum())
        assertEquals(100L + 15 + 215 + 98 + 200 + 50 + 20 + 675, d.txns.expenseSum())
    }

    @Test
    fun everyLegacyAccountKeepsExactlyTheSameBalanceChange() {
        val d = convert().data
        val bal = d.balances()
        val legacy = HashMap<String, Long>()
        for (line in rows) {
            val c = line.split(",")
            val time = c[1]
            if (time == "abc") continue
            val rate = if (c[4] != "NT$") c[20].toDouble() else 1.0
            val v = Math.round(abs(c[5].toDouble()) * rate)
            legacy[c[2]] = (legacy[c[2]] ?: 0L) + if (c[0] == "收入") v else -v
        }
        for ((name, expected) in legacy) {
            val a = d.accounts.first { it.name == name }
            assertEquals("帳戶 $name", expected, bal[a.id] ?: 0L)
        }
    }

    @Test
    fun existingAccountsAndCategoriesAreReused() {
        val base = Defaults.create()
        val cash = base.accounts.first()
        val withName = base.copy(accounts = listOf(cash.copy(name = "錢包")))
        val r = LegacyImport.convert(withName, csv)!!
        assertEquals(1, r.data.accounts.count { it.name == "錢包" })
        assertEquals(cash.id, r.data.accounts.first { it.name == "錢包" }.id)
        assertNotNull(r.data.accounts.firstOrNull { it.name == "國泰" })
        assertFalse(r.summary.newAccounts.any { it.first == "錢包" })
    }
}
