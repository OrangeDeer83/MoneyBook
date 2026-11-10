package tw.moneybook.app

/*
 * 預算外支出：有些支出不想算進每個月的預算（醫療、房租、保險、旅行、大型購物…）。
 * 分類的設定 Category.budgetMode：0＝跟著大分類（大分類本身的 0 就是算預算）、1＝預算外、2＝算預算；
 * 單筆 Txn.budgetOut：0＝跟著分類、1＝預算外、2＝算預算。只有支出會算預算外。
 */

/** 這個分類預設算不算預算外（小分類沒自己設就跟著大分類） */
fun AppData.categoryOutOfBudget(catId: Long?): Boolean {
    val c = catId?.let { catMap[it] } ?: return false
    return when (c.budgetMode) {
        1 -> true
        2 -> false
        else -> c.parentId?.let { catMap[it] }?.budgetMode == 1
    }
}

/** 這筆是不是預算外（單筆設定優先，沒設就看分類） */
fun AppData.isOutOfBudget(t: Txn): Boolean = t.type == TxType.EXPENSE && when (t.budgetOut) {
    1 -> true
    2 -> false
    else -> categoryOutOfBudget(t.categoryId)
}

/** 這些記錄算預算的支出（首頁預算、吉祥物、本月重點的預算都用它） */
fun AppData.budgetSpent(list: List<Txn>): Long = list.filter { !isOutOfBudget(it) }.sumOf { it.spent }

/** 這些記錄預算外的支出 */
fun AppData.outOfBudgetSpent(list: List<Txn>): Long = list.filter { isOutOfBudget(it) }.sumOf { it.spent }

/** 算預算的支出這一筆（首頁預算排行用）：支出、不是預算外、真的有花錢 */
fun AppData.countsInBudget(t: Txn): Boolean = t.type == TxType.EXPENSE && !t.adjust && !isOutOfBudget(t) && t.spent > 0L

/** 預算排行「依分類」：算預算的支出照大分類（含子分類）加總，由多到少；沒有分類的是 null */
fun AppData.budgetRankCats(list: List<Txn>): List<Pair<Long?, Long>> =
    list.filter { countsInBudget(it) }
        .groupBy { t -> t.categoryId?.let { catMap[it] }?.let { topOf(it).id } }
        .map { (k, v) -> k to v.sumOf { it.spent } }
        .sortedWith(compareByDescending<Pair<Long?, Long>> { it.second }.thenBy { it.first ?: Long.MAX_VALUE })

/** 預算排行「單筆最大」：算預算的支出由大到小前 n 筆（一樣大的新的在前） */
fun AppData.budgetTopTxns(list: List<Txn>, n: Int = 10): List<Txn> =
    list.filter { countsInBudget(it) }.sortedWith(compareByDescending<Txn> { it.spent }.thenByDescending { it.day }.thenByDescending { it.id }).take(n)
