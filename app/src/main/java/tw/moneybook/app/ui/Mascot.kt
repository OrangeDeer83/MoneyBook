package tw.moneybook.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import tw.moneybook.app.R
import tw.moneybook.app.AppData
import tw.moneybook.app.TxType
import tw.moneybook.app.expenseSum
import tw.moneybook.app.inMonth
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class Mood { HAPPY, NORMAL, WORRIED, SLEEPY, CHEER }

/** 可選的吉祥物：key 與預設名字 */
val MascotKinds: List<Pair<String, String>> = listOf(
    "deer" to "小鹿",
    "cat" to "小貓",
    "bear" to "小熊",
    "bunny" to "小兔",
    "dog" to "小狗",
    "schnauzer" to "雪納瑞",
)

fun mascotDefaultName(kind: String): String = MascotKinds.firstOrNull { it.first == kind }?.second ?: "小鹿"

/**
 * 預算擔心的條件（有設預算才判斷）：
 * 1. 已經超過預算
 * 2. 已用 80% 以上，而且本月還剩超過 7 天（照這樣花會不夠用）
 * 3. 10 號之後，花費比例比月份進度多 20% 以上（月初有房租之類的固定支出，不判斷進度）
 * 已用 80%～100% 但快月底時不擔心，只在對話框輕輕提醒（見 mascotLine）
 */
fun isBudgetWorried(spent: Long, budget: Long, today: LocalDate): Boolean {
    if (budget <= 0) return false
    if (spent > budget) return true
    val ratio = spent.toFloat() / budget.toFloat()
    val len = YearMonth.from(today).lengthOfMonth()
    val daysLeft = len - today.dayOfMonth
    if (ratio >= 0.8f && daysLeft > 7) return true
    if (today.dayOfMonth >= 10 && ratio > today.dayOfMonth.toFloat() / len.toFloat() + 0.2f) return true
    return false
}

fun moodOf(d: AppData, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): Mood {
    val book = d.currentBook
    val m = YearMonth.from(today)
    val list = d.bookTxns
    val spent = list.inMonth(m).expenseSum()
    val budget = book.budgetFor(m)
    if (budget > 0 && isBudgetWorried(spent, budget, today)) return Mood.WORRIED
    val todayEpoch = today.toEpochDay()
    val todays = list.filter { it.day == todayEpoch }
    if (todays.any { it.type == TxType.INCOME }) return Mood.CHEER
    if (now.hour >= 23 || now.hour < 5) return Mood.SLEEPY
    return if (todays.isNotEmpty()) Mood.HAPPY else Mood.NORMAL
}

fun mascotLine(mood: Mood, d: AppData, today: LocalDate = LocalDate.now()): String {
    val i = today.dayOfYear
    val book = d.currentBook
    if (mood != Mood.WORRIED) {
        val spent = d.bookTxns.inMonth(YearMonth.from(today)).expenseSum()
        val budget = book.budgetFor(YearMonth.from(today))
        if (budget > 0 && spent * 10 >= budget * 8) return "這個月預算已經用了 ${spent * 100 / budget}% 囉，最後幾天穩穩收尾～"
    }
    return when (mood) {
        Mood.HAPPY -> listOf(
            "今天也有好好記帳，好棒！",
            "記帳的你最可愛了～",
            "一步一步存錢，一起加油！",
            "錢錢都乖乖待在帳本裡了",
        )[i % 4]
        Mood.NORMAL -> listOf(
            "今天還沒記帳喔，要不要記一筆？",
            "嗨～今天過得好嗎？",
            "花了什麼記得告訴我喔",
        )[i % 3]
        Mood.SLEEPY -> listOf(
            "好晚了…記完這筆就早點睡吧 💤",
            "夜深了，剩下的明天再記也可以喔",
        )[i % 2]
        Mood.CHEER -> listOf(
            "有收入進來了！太棒了～",
            "錢錢進來啦，記得存一點喔",
        )[i % 2]
        Mood.WORRIED -> {
            val spent = d.bookTxns.inMonth(YearMonth.from(today)).expenseSum()
            val budget = book.budgetFor(YearMonth.from(today))
            if (budget in 1 until spent) "已經超過預算 ${tw.moneybook.app.formatMoney(spent - budget)} 了…這個月要節制一下 🥺"
            else "這個月花得有點快，剩下的日子省一點點喔"
        }
    }
}

/** 吉祥物圖片：每種動物有開心、普通、擔心、睡覺、歡呼五種表情 */
private fun mascotRes(kind: String, mood: Mood): Int {
    return when (kind) {
        "deer" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_deer_happy
            Mood.NORMAL -> R.drawable.mascot_deer_normal
            Mood.WORRIED -> R.drawable.mascot_deer_worried
            Mood.SLEEPY -> R.drawable.mascot_deer_sleepy
            Mood.CHEER -> R.drawable.mascot_deer_cheer
        }
        "cat" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_cat_happy
            Mood.NORMAL -> R.drawable.mascot_cat_normal
            Mood.WORRIED -> R.drawable.mascot_cat_worried
            Mood.SLEEPY -> R.drawable.mascot_cat_sleepy
            Mood.CHEER -> R.drawable.mascot_cat_cheer
        }
        "bear" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_bear_happy
            Mood.NORMAL -> R.drawable.mascot_bear_normal
            Mood.WORRIED -> R.drawable.mascot_bear_worried
            Mood.SLEEPY -> R.drawable.mascot_bear_sleepy
            Mood.CHEER -> R.drawable.mascot_bear_cheer
        }
        "bunny" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_bunny_happy
            Mood.NORMAL -> R.drawable.mascot_bunny_normal
            Mood.WORRIED -> R.drawable.mascot_bunny_worried
            Mood.SLEEPY -> R.drawable.mascot_bunny_sleepy
            Mood.CHEER -> R.drawable.mascot_bunny_cheer
        }
        "dog" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_dog_happy
            Mood.NORMAL -> R.drawable.mascot_dog_normal
            Mood.WORRIED -> R.drawable.mascot_dog_worried
            Mood.SLEEPY -> R.drawable.mascot_dog_sleepy
            Mood.CHEER -> R.drawable.mascot_dog_cheer
        }
        "schnauzer" -> when (mood) {
            Mood.HAPPY -> R.drawable.mascot_schnauzer_happy
            Mood.NORMAL -> R.drawable.mascot_schnauzer_normal
            Mood.WORRIED -> R.drawable.mascot_schnauzer_worried
            Mood.SLEEPY -> R.drawable.mascot_schnauzer_sleepy
            Mood.CHEER -> R.drawable.mascot_schnauzer_cheer
        }
        else -> R.drawable.mascot_deer_normal
    }
}

/** 吉祥物：貼紙圖，依心情換表情，會輕輕上下晃動 */
@Composable
fun Mascot(kind: String, mood: Mood, modifier: Modifier = Modifier, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "bob")
    val bob by t.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) -3f else 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    Image(
        painterResource(mascotRes(kind, mood)), null,
        modifier.graphicsLayer { translationY = bob * density },
        contentScale = ContentScale.Fit,
    )
}
