package tw.moneybook.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 到期通知「沒記的話再提醒」選「每天」：每天提醒直到記了或略過 */
const val SUB_REPEAT_DAILY = -1

/**
 * 一則訂閱到期通知要寫什麼（手機通知、測試共用；吉祥物圖片由 SubNotifier 依 worried 挑表情）。
 * single：只有一筆到期、可以在通知上直接「記好了／略過這次」的那一筆（訂閱 id, 扣款日）；
 * all：兩筆以上到期時「全部記好」要記的；都空的就只能點開 App。
 */
data class SubNotice(
    val title: String,
    val body: String,
    val lines: List<String>,
    /** 過了扣款日還沒記：吉祥物用擔心的表情 */
    val worried: Boolean,
    val single: Pair<Long, Long>?,
    val all: List<Pair<Long, Long>>,
)

/** 外幣訂閱扣台幣帳戶：記成外幣消費前要先確認匯率，不能在通知上直接記 */
fun AppData.subNeedsFx(s: Sub): Boolean = s.isForeign && s.accountId?.let { accMap[it] }?.isForeign != true

private fun subAmt(s: Sub): String = if (s.isForeign) formatFx(s.amount, s.cur) else formatMoney(s.amount)

private fun ago(age: Long): String = when (age) {
    0L -> "今天"
    1L -> "昨天"
    else -> "$age 天前"
}

private fun soonText(days: Int): String = if (days == 1) "明天" else "$days 天後"

/**
 * 今天要發的到期通知（目前帳本），沒有要提醒的回傳 null。
 * 到期還沒記也沒略過的：扣款日當天提醒，之後再提醒 repeat 天（SUB_REPEAT_DAILY＝每天，0＝只有當天）；
 * ahead > 0 時，扣款日前 ahead 天先提醒一次（只提醒那一天）。name 是吉祥物的名字（放在標題前面）。
 */
fun AppData.subNotice(today: Long, repeat: Int, ahead: Int, name: String): SubNotice? {
    val due = subsDue(today).filter { (_, day) -> repeat == SUB_REPEAT_DAILY || today - day <= repeat }
    val target = today + ahead
    val soon = if (ahead <= 0) emptyList() else bookSubs.filter { s -> !s.paused && !s.isDone(target) && target in s.dates(target) }
    if (due.isEmpty() && soon.isEmpty()) return null
    fun accName(s: Sub) = s.accountId?.let { accMap[it] }?.name
    val worried = due.any { today - it.second > 0 }
    val single = due.singleOrNull()?.takeIf { !subNeedsFx(it.first) }?.let { it.first.id to it.second }
    val all = if (due.size >= 2) due.map { it.first.id to it.second } else emptyList()

    // 只有一筆到期
    if (due.size == 1 && soon.isEmpty()) {
        val (s, day) = due[0]
        val age = today - day
        val last = repeat > 0 && age == repeat.toLong()
        val title = when {
            age == 0L -> "$name：${s.name} 今天扣款喔"
            last -> "$name：${s.name} 已經 $age 天沒記了"
            else -> "$name：${s.name} 還沒記喔"
        }
        val head = listOfNotNull(if (age == 0L) subAmt(s) else "${ago(age)}扣了 ${subAmt(s)}", accName(s)).joinToString("・")
        val tail = when {
            subNeedsFx(s) -> "・點開來確認匯率再記"
            last -> "，最後提醒一次"
            age == 0L -> "・記一下吧"
            else -> ""
        }
        return SubNotice(title, head + tail, emptyList(), worried, single, all)
    }
    // 只有一筆快到期
    if (due.isEmpty() && soon.size == 1) {
        val s = soon[0]
        return SubNotice(
            "$name：${s.name} ${soonText(ahead)}要扣款",
            listOfNotNull(subAmt(s), accName(s)).joinToString("・"), emptyList(), false, null, emptyList(),
        )
    }
    // 好幾筆：合成一則，列出前幾筆
    val title = when {
        due.isEmpty() -> "$name：有 ${soon.size} 筆固定扣款${soonText(ahead)}要扣"
        due.size == 1 -> "$name：${due[0].first.name} ${if (worried) "還沒記喔" else "今天扣款喔"}"
        !worried -> "$name：有 ${due.size} 筆固定扣款今天到期"
        else -> "$name：有 ${due.size} 筆固定扣款還沒記"
    }
    val list = (if (due.isEmpty()) soon else due.map { it.first }).mapNotNull { subTwd(it) }
    val body = "合計 ${formatMoney(list.sum())}・" + if (due.isEmpty()) "先準備一下" else "點開來記"
    val all5 = due.map { (s, day) -> "${s.name} ${subAmt(s)}" + if (today - day > 0) "（${ago(today - day)}）" else "" } +
        soon.map { s -> "${soonText(ahead)}：${s.name} ${subAmt(s)}" }
    val lines = if (all5.size <= 5) all5 else all5.take(4) + "…還有 ${all5.size - 4} 筆"
    return SubNotice(title, body, lines, worried, single, all)
}

/** 下一次檢查到期通知的時間（毫秒）：今天的 minute 分（一天中的第幾分鐘）還沒到就是今天，過了就是明天 */
fun nextNotifyAt(nowMillis: Long, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val todayAt = now.toLocalDate().atStartOfDay(zone).plusMinutes(minute.toLong())
    val at = if (todayAt.isAfter(now)) todayAt else now.toLocalDate().plusDays(1).atStartOfDay(zone).plusMinutes(minute.toLong())
    return at.toInstant().toEpochMilli()
}

/** 在通知上按「記好了」：照首頁「記」的記法記下這一次（外幣用目前匯率、待請款）；已經處理過、找不到或沒有匯率回傳 null */
fun AppData.withSubRecorded(id: Long, day: Long): AppData? {
    val s = subs.firstOrNull { it.id == id } ?: return null
    if (s.isDone(day)) return null
    val amt = subTwd(s) ?: return null
    val t = subTxn(s, day, nextId, amt, true)
    return copy(txns = sortTxns(txns + t), subs = subs.map { if (it.id == id) it.markDone(day) else it }, nextId = nextId + 1)
}

/** 在通知上按「略過這次」；已經處理過或找不到回傳 null */
fun AppData.withSubSkipped(id: Long, day: Long): AppData? {
    val s = subs.firstOrNull { it.id == id } ?: return null
    if (s.isDone(day)) return null
    return copy(subs = subs.map { if (it.id == id) it.markDone(day) else it })
}

/** 吉祥物名字（通知標題用）：改過名字用自己取的，不顯示吉祥物時寫「記帳本」 */
fun Prefs.mascotTitle(defaultName: (String) -> String): String =
    if (mascot == "none") "記帳本" else mascotName.ifBlank { defaultName(mascot) }

/** 今天（epoch day） */
fun todayEpoch(): Long = LocalDate.now().toEpochDay()
