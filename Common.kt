package tw.moneybook.app.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Defaults
import java.time.LocalDate
import java.time.YearMonth

val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")

/** 圓角卡片 */
@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    color: Color = LocalCute.current.card,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.clip(RoundedCornerShape(24.dp)).background(color)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(padding), content = content)
}

/** 分類圖示：粉彩底色的圓角方塊 + 表情符號 */
@Composable
fun CatBubble(emoji: String, color: Int, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(catColor(color).copy(alpha = if (LocalCute.current.dark) 0.30f else 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** 膠囊狀的切換按鈕 */
@Composable
fun PillSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    Row(
        modifier = modifier.clip(CircleShape).background(cute.soft).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) cute.card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else cute.sub,
                )
            }
        }
    }
}

/** 小圓角標籤按鈕 */
@Composable
fun CuteChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) primary else cute.card)
            .border(1.5.dp, if (selected) primary else cute.soft, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else cute.ink,
            maxLines = 1,
        )
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(AppIcons.ChevronLeft, contentDescription = "上個月")
        }
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onChange(YearMonth.now()) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(AppIcons.ChevronRight, contentDescription = "下個月")
        }
    }
}

@Composable
fun YearSwitcher(year: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(year - 1) }) { Icon(AppIcons.ChevronLeft, contentDescription = "前一年") }
        Text(
            "$year 年",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clip(CircleShape).clickable { onChange(LocalDate.now().year) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        IconButton(onClick = { onChange(year + 1) }) { Icon(AppIcons.ChevronRight, contentDescription = "下一年") }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** 空狀態：吉祥物 + 一句話 */
@Composable
fun EmptyHint(mascot: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mascot != "none") {
            Mascot(kind = mascot, mood = Mood.NORMAL, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text,
            textAlign = TextAlign.Center,
            color = LocalCute.current.sub,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 設定頁用的一列選單 */
@Composable
fun MenuRow(emoji: String, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalCute.current.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = LocalCute.current.sub)
    }
}

/** 子頁面的標題列 */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, actions: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.ChevronLeft, contentDescription = "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actions != null) actions()
    }
}

@Composable
fun AmountText(amount: Long, color: Color, modifier: Modifier = Modifier, big: Boolean = false) {
    Text(
        tw.moneybook.app.formatMoney(amount),
        color = color,
        modifier = modifier,
        style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = if (big) FontWeight.Normal else FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** 表情符號挑選對話框 */
@Composable
fun EmojiPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選一個圖示") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(Defaults.emojis) { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (e == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onPick(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(8) },
                    label = { Text("或自己輸入表情符號") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (custom.isNotBlank()) onPick(custom.trim()) else onDismiss() }) {
                Text(if (custom.isNotBlank()) "使用" else "關閉")
            }
        },
    )
}

/** 簡單的確認對話框 */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

fun showDatePicker(context: Context, day: Long, onPick: (Long) -> Unit) {
    val d = LocalDate.ofEpochDay(day)
    DatePickerDialog(
        context,
        { _, y, m, dd -> onPick(LocalDate.of(y, m + 1, dd).toEpochDay()) },
        d.year,
        d.monthValue - 1,
        d.dayOfMonth,
    ).show()
}

fun dayLabel(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    val w = WEEKDAYS[d.dayOfWeek.value - 1]
    return when (d) {
        today -> "今天・${d.monthValue}/${d.dayOfMonth}"
        today.minusDays(1) -> "昨天・${d.monthValue}/${d.dayOfMonth}"
        else -> "${d.monthValue}月${d.dayOfMonth}日 週$w"
    }
}
