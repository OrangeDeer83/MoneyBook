package tw.moneybook.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tw.moneybook.app.Feel
import tw.moneybook.app.HlPeriod
import tw.moneybook.app.Highlight
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Tone
import tw.moneybook.app.highlightSeed
import tw.moneybook.app.highlights
import tw.moneybook.app.pickHighlights
import java.time.LocalDate

private fun Feel.mood(): Mood = when (this) {
    Feel.HAPPY -> Mood.HAPPY
    Feel.NORMAL -> Mood.NORMAL
    Feel.WORRIED -> Mood.WORRIED
    Feel.SLEEPY -> Mood.SLEEPY
    Feel.CHEER -> Mood.CHEER
}

/**
 * 本月重點（區間是「這段期間的重點」）：吉祥物一次講一句，左右滑換上一句／下一句，點吉祥物或對話框換下一句，
 * 下面的小點表示第幾句；吉祥物的表情跟著那一句。最多 5 句，記一筆、改一筆或隔一天換一批。吉祥物關掉時只有對話框。
 */
@Composable
fun TipsCard(vm: MoneyViewModel, mode: Int, menu: CardMenu) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now()
    val p = if (mode == 2) HlPeriod.range(vm.rangeStart, vm.rangeEnd, today) else HlPeriod.month(vm.month, today)
    val list = remember(d, p.start, p.end, today) { pickHighlights(d.highlights(p), d.highlightSeed(today)) }
    var idx by remember(list) { mutableIntStateOf(0) }
    var dir by remember { mutableIntStateOf(1) }
    if (list.isEmpty()) return
    val cur = list[idx.coerceIn(list.indices)]
    fun go(step: Int) {
        if (list.size < 2) return
        dir = step
        idx = (idx + step + list.size) % list.size
    }
    val title = when {
        mode == 2 -> "這段期間的重點"
        p.current -> "本月重點"
        else -> "${vm.month.monthValue} 月重點"
    }
    val kind = d.prefs.mascot
    val swipe = with(LocalDensity.current) { 40.dp.toPx() }
    val quiet = remember { MutableInteractionSource() }

    CuteCard(Modifier.fillMaxWidth()) {
        CardTitle(title, "tips", menu)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().pointerInput(list) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -swipe) go(1) else if (total > swipe) go(-1) },
                ) { change, dx ->
                    change.consume()
                    total += dx
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind != "none") {
                Box(Modifier.clickable(interactionSource = quiet, indication = null) { go(1) }) {
                    Mascot(kind, cur.feel.mood(), Modifier.size(64.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = if (kind != "none") 4.dp else 18.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { go(1) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (kind != "none") {
                    Text(d.prefs.mascotName.ifBlank { mascotDefaultName(kind) }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                AnimatedContent(
                    targetState = idx.coerceIn(list.indices),
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { w -> w / 6 * dir }) togetherWith (fadeOut() + slideOutHorizontally { w -> -w / 6 * dir })
                    },
                    label = "tip",
                ) { i -> TipText(list[i]) }
            }
        }
        if (list.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                list.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape)
                            .background(if (i == idx) MaterialTheme.colorScheme.primary else cute.soft)
                    )
                }
            }
        }
    }
}

/** 一句重點：好的綠色、不好的紅色、名稱用主色，固定至少三行高，換句子時下面的卡片不會跳 */
@Composable
private fun TipText(h: Highlight) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        h.parts.forEach { part ->
            when (part.tone) {
                Tone.PLAIN -> append(part.text)
                Tone.GOOD -> withStyle(SpanStyle(color = cute.income, fontWeight = FontWeight.Medium)) { append(part.text) }
                Tone.BAD -> withStyle(SpanStyle(color = cute.expense, fontWeight = FontWeight.Medium)) { append(part.text) }
                Tone.NAME -> withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) { append(part.text) }
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, minLines = 3, modifier = Modifier.fillMaxWidth())
}
