package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.moneybook.app.MonthPick
import java.time.YearMonth

/** 期間寫成「2025/3 ～ 2026/2」 */
fun monthSpan(from: YearMonth, to: YearMonth) = "${from.year}/${from.monthValue} ～ ${to.year}/${to.monthValue}"

/**
 * 「自訂」那顆：還沒選過寫「自訂」，選過（這次打開 App 以後）寫期間；點了跳出月份表。
 * current 是現在畫面上的月份，沒選過時視窗從這段開始。
 */
@Composable
fun CustomMonthChip(
    on: Boolean, from: YearMonth?, to: YearMonth?, current: List<YearMonth>, onPick: (YearMonth, YearMonth) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val picked = from != null && to != null
    CuteChip(if (picked) monthSpan(from!!, to!!) else "自訂", on, { open = true }, icon = "vec:calendar")
    if (open) {
        MonthRangeDialog(
            from ?: current.first(), to ?: current.last(),
            onPick = { a, b -> onPick(a, b); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * 自訂期間視窗（草圖 ci-out/stats_custom_range.png ②）：上面開始、結束兩格（正在選的那格框起來，點了可以重選那一邊），
 * 下面一年 12 個月的表，先點開始、自動跳到結束；結束點得比開始早就對調。最少 2 個月，不能選到這個月以後。
 */
@Composable
fun MonthRangeDialog(initFrom: YearMonth, initTo: YearMonth, onPick: (YearMonth, YearMonth) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val now = YearMonth.now()
    var p by remember { mutableStateOf(MonthPick(minOf(initFrom, now), minOf(initTo, now))) }
    var year by remember { mutableIntStateOf(p.from.year) }
    val tries = rememberNeedTries()
    val need = if (p.count < 2) Need("end", "至少要選 2 個月") else null
    val nv = NeedView(need, tries.count)
    val band = primary.copy(alpha = 0.16f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.background).padding(18.dp),
        ) {
            Text("自訂期間", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(false, true).forEach { end ->
                    val m = if (end) p.to else p.from
                    val on = p.editEnd == end
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).clip(shape).background(cute.card)
                            .border(2.dp, if (on) primary else Color.Transparent, shape)
                            .then(if (end) Modifier.needFrame(nv, "end", shape) else Modifier)
                            .clickable { p = p.copy(editEnd = end); year = m.year }
                            .semantics { selected = on }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(if (end) "結束" else "開始", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                        // 窄螢幕也要一行寫完（「2026 年 11 月」）
                        Text("${m.year} 年 ${m.monthValue} 月", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                    }
                    if (!end) Text("～", color = cute.sub, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            nv.Message("end")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cute.card).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.ChevronLeft, contentDescription = "前一年")
                    }
                    Text("$year 年", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { year++ }, enabled = year < now.year, modifier = Modifier.size(40.dp)) {
                        Icon(AppIcons.ChevronRight, contentDescription = "下一年")
                    }
                }
                Spacer(Modifier.height(4.dp))
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 4) {
                            val m = YearMonth.of(year, r * 4 + c + 1)
                            val inRange = m >= p.from && m <= p.to
                            val isEnd = m == p.from || m == p.to
                            val future = m > now
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .drawBehind {
                                        // 期間裡的月份連成一條淡色帶，開始、結束那格只塗一半
                                        if (inRange && p.from != p.to) {
                                            val l = if (m == p.from) size.width / 2 else 0f
                                            val rr = if (m == p.to) size.width / 2 else size.width
                                            val pad = 6.dp.toPx()
                                            drawRect(band, Offset(l, pad), Size(rr - l, size.height - pad * 2))
                                        }
                                    }
                                    .clickable(enabled = !future) { p = p.tap(m) }
                                    .semantics { contentDescription = "${m.year} 年 ${m.monthValue} 月"; selected = isEnd },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isEnd) Box(Modifier.size(42.dp).clip(CircleShape).background(primary))
                                Text(
                                    "${m.monthValue}月",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isEnd -> MaterialTheme.colorScheme.onPrimary
                                        future -> cute.sub.copy(alpha = 0.4f)
                                        else -> cute.ink
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.editEnd) "再點結束的月份（按 ‹ › 換年）" else "先點開始的月份",
                style = MaterialTheme.typography.labelMedium, color = cute.sub,
            )
            Text("共 ${p.count} 個月", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { if (need != null) tries.count++ else onPick(p.from, p.to) }) { Text("好") }
            }
        }
    }
}
