package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth

/**
 * 某一天結束時的總資產組成（台幣），和帳戶頁的總資產同一套算法：顯示中的帳戶餘額（外幣用現在的匯率，還沒有匯率的不算）
 * 減掉還欠別人的錢。cash＝現金、銀行等；invest＝投資類帳戶；debt＝負債（正數）：餘額是負的帳戶（信用卡、貸款）加上還欠別人的錢。
 */
data class NetWorthPoint(val cash: Long, val invest: Long, val debt: Long) {
    val assets: Long get() = cash + invest
    val net: Long get() = cash + invest - debt
}

/**
 * 一段期間總資產變化的原因，加起來一定等於 change：
 * saved＝收入－支出（存下來的，所有帳本）；invest＝投資帳戶的餘額調整（同步市值）；
 * reimb＝先墊還沒收回的報銷（墊出去是負的、收回來是正的）；other＝剩下對不起來的（其他餘額調整、外幣換匯、隱藏帳戶的轉帳…）
 */
data class NetWorthReasons(val change: Long, val saved: Long, val invest: Long, val reimb: Long) {
    val other: Long get() = change - saved - invest - reimb
}

/** 帳戶餘額的一次變動 */
private class Flow(val day: Long, val accountId: Long, val delta: Long)

private fun AppData.flows(): List<Flow> {
    val out = ArrayList<Flow>()
    for (t in txns) {
        t.accountId?.let { id -> accMap[id]?.let { a -> out.add(Flow(t.day, id, t.flowFor(a))) } }
        t.toAccountId?.let { id -> if (id != t.accountId) accMap[id]?.let { a -> out.add(Flow(t.day, id, t.flowFor(a))) } }
        for (p in t.items.flatMap { it.pays }) p.accountId?.let { id -> accMap[id]?.let { a -> out.add(Flow(p.day, id, p.flowFor(a))) } }
        for (p in t.debtItems.flatMap { it.pays }) p.accountId?.let { id -> accMap[id]?.let { a -> out.add(Flow(p.day, id, -p.flowFor(a))) } }
    }
    out.sortBy { it.day }
    return out
}

/** 某一天結束時還欠別人多少（台幣，外幣欠款用預估匯率）；day＝Long.MAX_VALUE 是現在（等於 debtOutstanding） */
fun AppData.debtOutstandingAt(day: Long): Long = txns.filter { it.isDebt && it.day <= day }.sumOf { t ->
    t.debtItems.sumOf { it ->
        val pays = it.pays.filter { p -> p.day <= day }
        // 結清的那一項：最後一筆還款那天之後就不欠了
        val settled = it.done && (it.pays.isEmpty() || it.pays.maxOf { p -> p.day } <= day)
        val units = if (settled) 0L else (it.totalUnits - pays.sumOf { p -> p.units }).coerceAtLeast(0L)
        if (it.units > 0L) scaleRound(units, it.amount, it.units) else units
    }
}

/** 某一天結束時先墊、還沒收回的報銷（所有帳本）：結案的那一項，最後一筆收款那天之後就不算 */
fun AppData.reimbOutstandingAt(day: Long): Long = txns.filter { it.type == TxType.EXPENSE && it.day <= day }.sumOf { t ->
    t.items.sumOf { it ->
        val settled = it.closed && (it.pays.isEmpty() || it.pays.maxOf { p -> p.day } <= day)
        if (settled) 0L else (it.amount - it.pays.filter { p -> p.day <= day }.sumOf { p -> p.amount }).coerceAtLeast(0L)
    }
}

/** 好幾天結束時的總資產組成（days 不用排序）；Long.MAX_VALUE 代表現在（包括記在未來的帳），會等於帳戶頁的總資產 */
fun AppData.netWorthAt(days: List<Long>): List<NetWorthPoint> {
    val flows = flows()
    val order = days.indices.sortedBy { days[it] }
    val bal = HashMap<Long, Long>()
    for (a in accounts) bal[a.id] = a.initial
    val out = arrayOfNulls<NetWorthPoint>(days.size)
    var k = 0
    for (i in order) {
        val day = days[i]
        while (k < flows.size && flows[k].day <= day) {
            val f = flows[k++]
            bal[f.accountId] = (bal[f.accountId] ?: 0L) + f.delta
        }
        var cash = 0L
        var invest = 0L
        var debt = debtOutstandingAt(day)
        for (a in visibleAccounts) {
            val v = twdValue(a, bal[a.id] ?: 0L) ?: continue
            when {
                v < 0L -> debt -= v
                a.type == AccountType.INVEST -> invest += v
                else -> cash += v
            }
        }
        out[i] = NetWorthPoint(cash, invest, debt)
    }
    return out.map { it!! }
}

/** 每個月最後一天的總資產；還沒過完的月份（這個月、未來）用現在的 */
fun AppData.netWorthMonths(months: List<YearMonth>, today: LocalDate = LocalDate.now()): List<NetWorthPoint> =
    netWorthAt(months.map { m -> if (m.atEndOfMonth() >= today) Long.MAX_VALUE else m.atEndOfMonth().toEpochDay() })

/** 這個月（還沒過完就是到現在為止）和上個月底比，總資產變化的原因 */
fun AppData.netWorthReasons(m: YearMonth, today: LocalDate = LocalDate.now()): NetWorthReasons {
    val endDay = if (m.atEndOfMonth() >= today) Long.MAX_VALUE else m.atEndOfMonth().toEpochDay()
    val startDay = m.minusMonths(1).atEndOfMonth().toEpochDay()
    val (start, end) = netWorthAt(listOf(startDay, endDay))
    val inMonth = txns.filter { it.day > startDay && it.day <= endDay }
    val saved = inMonth.incomeSum() - inMonth.expenseSum()
    val visible = visibleAccounts.map { it.id }.toSet()
    val invest = inMonth.filter { it.adjust }.sumOf { t ->
        t.accountId?.let { accMap[it] }?.takeIf { it.type == AccountType.INVEST && it.id in visible }?.let { a -> twdValue(a, t.flowFor(a)) } ?: 0L
    }
    val reimb = -(reimbOutstandingAt(endDay) - reimbOutstandingAt(startDay))
    return NetWorthReasons(end.net - start.net, saved, invest, reimb)
}

/** 有記錄的第一個月（沒有記錄就是這個月）：總資產「全部」從這裡開始 */
fun AppData.firstMonth(today: LocalDate = LocalDate.now()): YearMonth =
    txns.minOfOrNull { it.day }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }?.coerceAtMost(YearMonth.from(today)) ?: YearMonth.from(today)
