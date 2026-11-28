package tw.moneybook.app

/*
 * 預估的刷卡放太久（使用者 11/28 確認草圖 ci-out/noti_test_charge3.png 甲＋乙）。
 * 加油站這類的試刷只會通知 $1，加完油直接請款、不會再通知真正的金額，所以記成預估的 $1 永遠等不到請款通知：
 *  - 試刷記成預估 $1：記的隔天起首頁提醒「填金額／刪掉」
 *  - 預先授權記成預估：14 天還沒請款也一起提醒
 *  - 手機通知每一筆只發一次（記在 EstNotifier），之後就掛在首頁
 */

/** 試刷的預估記了幾天後開始提醒（隔天） */
const val EST_TEST_DAYS = 1

/** 預先授權的預估記了幾天還沒請款開始提醒 */
const val EST_PRE_DAYS = 14

/** 試刷記成的預估：剛好 $1 的預估支出 */
val Txn.isTestEst: Boolean get() = est && type == TxType.EXPENSE && amount == 1L

/** 放太久還沒填實際金額的預估支出（所有帳本；舊的在前） */
fun AppData.estDue(today: Long): List<Txn> =
    txns.filter { it.est && it.type == TxType.EXPENSE && today - it.day >= (if (it.isTestEst) EST_TEST_DAYS else EST_PRE_DAYS) }
        .sortedWith(compareBy({ it.day }, { it.id }))

/** 提醒上寫的名字：店家 → 備註 → 分類 */
fun AppData.estName(t: Txn): String =
    t.shop.ifBlank { t.note.ifBlank { t.categoryId?.let { catMap[it]?.name } ?: "刷卡" } }

/** 預估放太久的手機通知；ids＝這次通知到的（之後不再通知） */
data class EstNotice(val title: String, val body: String, val ids: List<Long>)

/** 還沒通知過的放太久預估；都通知過了回傳 null */
fun AppData.estNotice(today: Long, notified: Set<Long>, name: String): EstNotice? {
    val fresh = estDue(today).filter { it.id !in notified }
    if (fresh.isEmpty()) return null
    if (fresh.size == 1) {
        val t = fresh[0]
        val acc = t.accountId?.let { accMap[it]?.name }
        return if (t.isTestEst) EstNotice(
            "$name：${estName(t)} 的試刷還沒填實際金額",
            listOfNotNull(acc, "加完油看加油機或發票，點開來填").joinToString("・"), listOf(t.id),
        ) else EstNotice(
            "$name：${estName(t)} 預估 ${formatMoney(t.amount)} 還沒請款",
            listOfNotNull(acc, "記了 ${today - t.day} 天，點開來填實際金額").joinToString("・"), listOf(t.id),
        )
    }
    return EstNotice("$name：有 ${fresh.size} 筆預估的刷卡還沒填實際金額", fresh.joinToString("、") { estName(it) }, fresh.map { it.id })
}
