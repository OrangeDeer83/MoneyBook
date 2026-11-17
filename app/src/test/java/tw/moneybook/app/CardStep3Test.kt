package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 刷卡通知第三步：退款、預先授權、分期放進待確認、記成的那幾筆、請款 */
class CardStep3Test {
    private val zone = ZoneId.of("Asia/Taipei")
    private val base0 = Defaults.create()
    private val daily = base0.categories.first { it.name == "日用" }.id
    private val other = base0.categories.first { it.name == "其他" && it.kind == TxType.EXPENSE }.id
    // 每月 25 號結帳
    private val cube = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1, statementDay = 25, dueDay = 10, cardNos = listOf(CardNo("1234")))
    private val book = base0.books.first().id
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private fun ms(m: Int, d: Int, h: Int = 12) = LocalDate.of(2026, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun data(vararg t: Txn) = base0.copy(accounts = base0.accounts + cube, notiApps = listOf(NotiApp("x", "X 銀行", listOf(9001))), txns = sortTxns(t.toList()))
    private fun noti(id: Long, text: String, m: Int = 11, d: Int = 20) = CapturedNoti(id, "x", "X 銀行", "", text, ms(m, d))
    private fun pendFrom(n: CapturedNoti) = pendOf(n, parseCardNoti(n.title, n.text, n.time, zone))

    @Test
    fun kindsBecomePends() {
        val r = pendFrom(noti(1, "末四碼1234於11/14在全聯福利中心之消費退款NT\$350"))!!
        assertEquals(PendKind.REFUND, r.kind)
        val p = pendFrom(noti(2, "末四碼1234在福華飯店預先授權NT\$10,000"))!!
        assertEquals(PendKind.PREAUTH, p.kind)
        val i = pendFrom(noti(3, "末四碼1234在燦坤消費NT\$12,000，分12期"))!!
        assertEquals(PendKind.SPEND, i.kind)
        assertEquals(12, i.inst)
        assertTrue(i.isInst)
        // 分期請款、取消不放進來
        assertNull(pendFrom(noti(4, "分期付款第3期NT\$1,000")))
        assertNull(pendFrom(noti(5, "交易取消 NT\$85")))
        // 跟著存
        val back = CardPendCodec.decode(CardPendCodec.encode(listOf(r, p, i.copy(notPre = true))))
        assertEquals(listOf(r, p, i.copy(notPre = true)), back)
        // 同時間、同金額的消費和退款不合併
        val d = data()
        val spend = pendFrom(CapturedNoti(6, "y", "Y 錢包", "", "在全聯福利中心消費NT\$350", ms(11, 20)))!!
        val list = addPend(listOf(r.copy(time = ms(11, 20))), spend, d, ms(11, 20))
        assertEquals(2, list.size)
        // 退款不問「可能已經記過」、不對訂閱
        assertNull(data(Txn(50, book, TxType.EXPENSE, 350, daily, 9001, null, r.day, "x", emptyList())).possibleHad(r, 9001, emptySet()))
    }

    @Test
    fun refundRecordsAsNotIncomeLinked() {
        val buy = Txn(50, book, TxType.EXPENSE, 350, daily, 9001, null, day(11, 14), "全聯福利中心 文心店", emptyList())
        val (d, cat) = data(buy).ensureRefundCat()
        val r = pendFrom(noti(1, "末四碼1234於11/20在全聯福利中心之消費退款NT\$350"))!!
        assertEquals(50L, d.pendRefundOf(r, 9001)?.id)
        val t = d.pendRefundTxn(r, 99, book, 9001, cat, 50)!!
        assertEquals(TxType.INCOME, t.type)
        assertTrue(t.noIncome)
        assertEquals(50L, t.refundOf)
        assertEquals(cat, t.categoryId)
        assertEquals(listOf(CARD_NOTI_TAG), t.tags)
        val after = d.copy(txns = sortTxns(d.txns + t))
        assertEquals(0L, after.txns.expenseSum())
        // 手機通知
        val n = after.copy(txns = d.txns).pendNotice(listOf(r), "記帳本")!!
        assertEquals("記帳本：退款 ${formatMoney(350)}", n.title)
        assertTrue(n.body.contains("退的是 11/14 全聯福利中心 文心店"))
        assertEquals(r.id, n.single)
    }

    @Test
    fun preauthThenCharge() {
        val d0 = data()
        val auth = pendFrom(noti(1, "末四碼1234於11/20 15:10在福華飯店預先授權NT\$10,000"))!!
        val est = d0.pendTxn(auth, 60, book, 9001, other)!!.copy(est = true)
        val d = data(est)
        // 12/3 同一家店請款 $8,200：問要不要改
        val charge = pendFrom(noti(2, "末四碼1234於12/03在福華飯店消費NT\$8,200", 12, 3))!!
        assertEquals(60L, d.preauthFor(charge, 9001)?.id)
        // 11/20 是 11 月帳單（25 號結帳），12/3 請款是 12 月帳單：入帳月份改 12 月
        assertEquals(202612, d.preauthBillMonth(est, charge.day))
        val s = d.settlePreauth(60, 8200, charge.day).txnMap[60]!!
        assertEquals(8200L, s.amount)
        assertFalse(s.est)
        assertEquals(202612, s.billMonth)
        assertEquals(day(11, 20), s.day)
        // 同一期請款：入帳月份不動
        assertEquals(0, d.preauthBillMonth(est, day(11, 24)))
        // 按過「是另一筆」、別家店、超過 60 天、已經不是預估：不問
        assertNull(d.preauthFor(charge.copy(notPre = true), 9001))
        assertNull(d.preauthFor(charge.copy(merchant = "鼎泰豐"), 9001))
        assertNull(d.preauthFor(charge.copy(day = est.day + 61), 9001))
        assertNull(data(est.copy(est = false)).preauthFor(charge, 9001))
        // 帳戶頁：還沒請款的
        assertEquals(listOf(60L), d.preauthWaiting(9001).map { it.id })
        // 手機通知問要不要改
        val n = d.pendNotice(listOf(charge), "記帳本")!!
        assertTrue(n.body.contains("要改成"))
        assertNull(n.single)
        // 跟著備份
        assertTrue(Codec.decode(Codec.encode(d)).txnMap[60]!!.est)
    }

    @Test
    fun installmentPurchase() {
        val p = pendFrom(noti(1, "末四碼1234在燦坤消費NT\$12,001，分12期", 11, 20))!!
        val list = data().pendInstTxns(p, 70, book, 9001, other)
        assertEquals(12, list.size)
        assertEquals(1001L, list[0].amount)
        assertEquals(1000L, list[11].amount)
        assertEquals(12001L, list.sumOf { it.amount })
        assertTrue(list.all { it.instGroup == 70L && it.instTotal == 12 })
        assertEquals((1..12).toList(), list.map { it.instIndex })
        assertEquals(day(11, 20), list[0].day)
        assertEquals(LocalDate.of(2027, 10, 20).toEpochDay(), list[11].day)
        assertEquals((70L..81L).toList(), list.map { it.id })
        assertNotNull(list[0].noti)
    }
}
