package tw.moneybook.app

/**
 * 報銷收款的分配：把「這次收到的錢」（可以同時有好幾種幣別）分配到某個人還欠的幾筆帳。
 * 所有帳的應收都用台幣記（外幣消費的是當時換算的台幣）；收外幣時用「那筆消費當時的匯率」換算，
 * 所以對方還你原本付的外幣金額就剛好收齊，不會因為匯率變動出現差額。
 */

/** 一次收款裡的一種幣別：currency 空白是台幣；amount 是該幣別的最小單位（美元是分） */
class ReceiptLine(val accountId: Long?, val currency: String, val amount: Long)

/** 要分配的一筆帳：remaining 是還欠的台幣 */
class ClaimInput(val key: String, val txn: Txn, val remaining: Long)

/** 某一種幣別分給這筆帳多少：units 是該幣別收的金額；credit 是沖掉的台幣 */
class ReceiptPart(val line: Int, val units: Long, val credit: Long)

class ClaimOutcome(val key: String, val remaining: Long, val parts: List<ReceiptPart>) {
    /** 這次沖掉的台幣（含多收的部分） */
    val credit: Long get() = parts.sumOf { it.credit }

    /** 有收到、但沒收齊 */
    val short: Boolean get() = credit in 1 until remaining
}

/**
 * 分配規則：
 * 1. 外幣的先分：只分給「同幣別外幣消費」的帳，由舊到新；台幣最後分，可以分給所有還有欠的帳。
 * 2. 每一種幣別由舊到新分配，多收的算在最後一筆。
 * 3. overrides：使用者手動改的金額（帳的 key → 第一種幣別的金額），只套用在第 0 種。
 * claimCurrency：這筆帳是用哪個外幣付的（台幣付的回傳空白）。
 */
fun allocateReceipt(
    claims: List<ClaimInput>,
    lines: List<ReceiptLine>,
    claimCurrency: (Txn) -> String,
    overrides: Map<String, Long> = emptyMap(),
): List<ClaimOutcome> {
    val rem = claims.map { it.remaining }.toMutableList()
    val parts = claims.map { ArrayList<ReceiptPart>() }
    val order = lines.indices.sortedBy { if (lines[it].currency.isEmpty()) 1 else 0 }
    for (li in order) {
        val line = lines[li]
        val cur = line.currency
        val eligible = claims.indices.filter { cur.isEmpty() || claimCurrency(claims[it].txn) == cur }
        fun remU(i: Int): Long = if (cur.isEmpty()) rem[i] else claims[i].txn.twdToFxAt(rem[i])
        var left = line.amount
        val auto = HashMap<Int, Long>()
        for (i in eligible) {
            val a = minOf(remU(i), left)
            auto[i] = a
            left -= a
        }
        if (left > 0L && eligible.isNotEmpty()) auto[eligible.last()] = (auto[eligible.last()] ?: 0L) + left
        for (i in eligible) {
            val u = (if (li == 0) overrides[claims[i].key] else null) ?: auto[i] ?: 0L
            if (u <= 0L) continue
            val r = remU(i)
            val credit = when {
                cur.isEmpty() -> u
                u == r -> rem[i]
                u > r -> rem[i] + claims[i].txn.fxToTwdAt(u - r)
                else -> claims[i].txn.fxToTwdAt(u)
            }
            parts[i].add(ReceiptPart(li, u, credit))
            rem[i] = (rem[i] - credit).coerceAtLeast(0L)
        }
    }
    return claims.indices.map { ClaimOutcome(claims[it].key, claims[it].remaining, parts[it]) }
}
