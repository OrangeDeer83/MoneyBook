package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 記錄的時間：格式、排序、存檔、CSV、舊資料匯入 */
class TxnTimeTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()

    private fun txn(id: Long, time: Int, d: Long = day) =
        Txn(id, 1L, TxType.EXPENSE, 100, null, 1L, null, d, "n$id", emptyList(), time = time)

    @Test
    fun formatAndParse() {
        assertEquals("09:30", formatTime(570))
        assertEquals("00:00", formatTime(0))
        assertEquals("23:59", formatTime(1439))
        assertEquals("", formatTime(-1))
        assertEquals(570, parseTime("9:30"))
        assertEquals(570, parseTime("09:30"))
        assertEquals(1155, parseTime("19:15:19"))     // 秒數忽略
        assertEquals(-1, parseTime(""))
        assertEquals(-1, parseTime("abc"))
        assertEquals(-1, parseTime("25:00"))
        assertEquals(-1, parseTime("10:61"))
    }

    @Test
    fun defaultIsNoTime() {
        assertEquals(-1, txn(1, -1).time)
        assertEquals(-1, Txn(1, 1L, TxType.EXPENSE, 1, null, 1L, null, day, "", emptyList()).time)
    }

    @Test
    fun sameDayIsOrderedByTimeThenEntryOrder() {
        // 同一天：時間晚的在前（新到舊）；沒有時間的當最早；時間一樣就看輸入順序（id 大的在前）
        val list = sortTxns(listOf(txn(1, 600), txn(2, 900), txn(3, -1), txn(4, 600), txn(5, 700)))
        assertEquals(listOf(2L, 5L, 4L, 1L, 3L), list.map { it.id })
    }

    @Test
    fun dayStillBeatsTime() {
        val list = sortTxns(listOf(txn(1, 1000, day - 1), txn(2, 10, day)))
        assertEquals(listOf(2L, 1L), list.map { it.id })
    }

    @Test
    fun timeSurvivesSaveAndOldBackupsHaveNone() {
        val d = Defaults.create().copy(txns = sortTxns(listOf(txn(9001, 570), txn(9002, -1))), nextId = 9100)
        val back = Codec.decode(Codec.encode(d))
        assertEquals(mapOf(9001L to 570, 9002L to -1), back.txns.associate { it.id to it.time })
        val root = org.json.JSONObject(Codec.encode(d))
        val arr = root.getJSONArray("txns")
        for (i in 0 until arr.length()) arr.getJSONObject(i).remove("time")
        assertTrue(Codec.decode(root.toString()).txns.all { it.time == -1 })
    }

    @Test
    fun reimbursementReceiptTimeSurvivesSaveAndOldDataHasNone() {
        val item = ReimbItem("A", 100L, listOf(ReimbPay(day, null, 60L, 750), ReimbPay(day, null, 40L)), true)
        val t = txn(9001, 570).copy(type = TxType.EXPENSE, amount = 100L).withItems(listOf(item))
        val d = Defaults.create().copy(txns = listOf(t), nextId = 9100)
        val pays = Codec.decode(Codec.encode(d)).txns.single().items.single().pays
        assertEquals(listOf(750, -1), pays.map { it.time })
        // 舊備份的收款沒有 time 欄位
        val root = org.json.JSONObject(Codec.encode(d))
        val arr = root.getJSONArray("txns").getJSONObject(0).getJSONArray("reimbItems").getJSONObject(0).getJSONArray("pays")
        for (i in 0 until arr.length()) arr.getJSONObject(i).remove("time")
        assertTrue(Codec.decode(root.toString()).txns.single().items.single().pays.all { it.time == -1 })
    }

    @Test
    fun csvKeepsTheTime() {
        val acc = Defaults.create().accounts.first().id
        val cat = Defaults.create().topCategories(TxType.EXPENSE).first().id
        val t = Txn(9001, Defaults.create().currentBook.id, TxType.EXPENSE, 120, cat, acc, null, day, "午餐", emptyList(), time = 735)
        val csv = String(CsvIO.export(Defaults.create().copy(txns = listOf(t))), Charsets.UTF_8)
        assertTrue(csv.lineSequence().first().contains(",時間,"))      // 時間欄後面還有外幣金額、外幣幣別兩欄
        assertTrue(csv.contains("12:15"))
        val (imported, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(1, n)
        assertEquals(735, imported.txns.single().time)
    }

    @Test
    fun csvWithoutATimeColumnImportsWithNoTime() {
        val (imported, n) = CsvIO.import(Defaults.create(), "日期,金額\n2026-10-03,100")
        assertEquals(1, n)
        assertEquals(-1, imported.txns.single().time)
    }

    @Test
    fun legacyImportKeepsTheOriginalTimes() {
        val header = "類型,記帳時間,交易帳戶,交易帳本,貨幣符號,交易金額,一級分類,二級分類,交易標籤,備註,報銷,退款,收回,歸還,手續費,待報銷,分期,地點,借出方/借入方,多人記帳,匯率"
        fun row(type: String, time: String, acc: String, amt: String, top: String, sub: String = top) =
            listOf(type, time, acc, "日常", "NT$", amt, top, sub, "/", "", "", "", "", "", "", "", "", "", "", "", "").joinToString(",")
        val csv = listOf(
            header,
            row("支出", "2026-09-28 19:15:19", "國泰", "-100", "吃喝", "午餐"),
            row("支出", "2026-09-29 08:05:00", "國泰", "-1000", "轉帳"),
            row("收入", "2026-09-29 08:05:00", "錢包", "1000", "轉帳"),
        ).joinToString("\n")
        val d = LegacyImport.convert(Defaults.create(), csv)!!.data
        val lunch = d.txns.single { it.type == TxType.EXPENSE }
        assertEquals(19 * 60 + 15, lunch.time)
        val transfer = d.txns.single { it.type == TxType.TRANSFER }
        assertEquals(8 * 60 + 5, transfer.time)        // 轉帳用轉出那一筆的時間
    }
}
