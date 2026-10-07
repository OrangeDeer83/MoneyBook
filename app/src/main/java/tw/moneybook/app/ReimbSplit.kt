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
