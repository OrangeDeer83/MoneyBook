package tw.moneybook.app

/** 一次自動分出來的結果：amounts 是每個要自動填的人各分到多少，self 是留給自己的 */
data class SplitResult(val amounts: List<Long>, val self: Long)

/**
 * 報銷平分：把 pool（最小單位：台幣是元、外幣是該幣別最小單位）分給 targets 個人。
 *  - includeSelf = false：只分給這幾個人（幫別人付），除不盡的零頭給第一個人，自己 0
 *  - includeSelf = true：自己也算一份（人數 +1），每個人拿一樣的整數，除不盡的零頭留在自己身上
 * 「平分剩下的」就是 includeSelf = true、pool 是扣掉已指定金額後剩下的。
 */
fun splitAmounts(includeSelf: Boolean, pool: Long, targets: Int): SplitResult {
    if (targets <= 0) return SplitResult(emptyList(), pool.coerceAtLeast(0L))
    val p = pool.coerceAtLeast(0L)
    return if (includeSelf) {
        val per = p / (targets + 1)
        SplitResult(List(targets) { per }, p - per * targets)
    } else {
        val per = p / targets
        SplitResult(List(targets) { i -> if (i == 0) per + p % targets else per }, 0L)
    }
}

/**
 * 報銷／欠款名單裡名字一樣的幾欄合成一個人，金額加總（名字前後的空白不算、英文不分大小寫），
 * 保留第一次出現的寫法和順序；沒填名字的各自一欄，不合併。金額用同一種單位（台幣，或外幣的最小單位）。
 */
fun mergeSameNames(rows: List<Pair<String, Long>>): List<Pair<String, Long>> {
    val out = ArrayList<Pair<String, Long>>()
    val at = HashMap<String, Int>()
    for ((who, amt) in rows) {
        val name = who.trim()
        val key = name.lowercase()
        val i = if (name.isEmpty()) null else at[key]
        if (i == null) {
            if (name.isNotEmpty()) at[key] = out.size
            out.add(name to amt)
        } else {
            out[i] = out[i].first to out[i].second + amt
        }
    }
    return out
}
