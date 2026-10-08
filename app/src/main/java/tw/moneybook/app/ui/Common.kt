package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import tw.moneybook.app.Defaults
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
        if (AppImages.isImg(emoji)) IconImage(emoji, size * 0.7f)
        else Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** 膠囊狀的切換按鈕 */
@Composable
fun PillSegment(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equal: Boolean = false,
) {
    val cute = LocalCute.current
    Row(
        modifier = modifier.clip(CircleShape).background(cute.soft).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = (if (equal) Modifier.weight(1f) else Modifier)
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
fun CuteChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null) {
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
        val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else cute.ink
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides textColor) { IconGlyph(icon, 15.sp) }
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
            )
        }
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
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { IconGlyph(emoji, 22.sp) }
        }
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

/** 圖示挑選對話框：表情符號或內建圖片 */
@Composable
fun EmojiPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(if (AppImages.isImg(current)) 1 else 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選一個圖示") },
        text = {
            Column {
                PillSegment(listOf("表情符號", "圖片"), tab, { tab = it }, Modifier.fillMaxWidth(), equal = true)
                Spacer(Modifier.height(10.dp))
                val list = if (tab == 0) Defaults.emojis else AppImages.all.keys.toList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list) { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (e == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onPick(e) },
                            contentAlignment = Alignment.Center,
                        ) { IconGlyph(e, 22.sp) }
                    }
                }
                if (tab == 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.take(8) },
                        label = { Text("或自己輸入表情符號") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (tab == 0 && custom.isNotBlank()) onPick(custom.trim()) else onDismiss() }) {
                Text(if (tab == 0 && custom.isNotBlank()) "使用" else "關閉")
            }
        },
    )
}

/**
 * 淡底色圓角塊按鈕（取代一行純文字的按鈕，看得出可以點）：
 * danger＝淡紅底紅字（刪除這類動作），平常是淡棕底主色字；compact 是比較小的版本，放在卡片角落、列表列裡。
 */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, compact: Boolean = false) {
    val cute = LocalCute.current
    val fg = if (danger) cute.expense else MaterialTheme.colorScheme.primary
    val bg = if (danger) cute.expense.copy(alpha = 0.14f) else cute.soft
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    Box(
        modifier.clip(shape).background(bg).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 7.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = fg, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 淡底色圓角塊的圖示按鈕（例如帳戶明細右上角的鉛筆）；desc 是無障礙說明，也是畫面測試找得到的名字 */
@Composable
fun SoftIconButton(icon: String, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    Box(
        modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cute.soft)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { IconGlyph(icon, 20.sp) }
    }
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

/** 日期加上時間（沒有時間就只有日期），例如「今天・10/5 14:30」 */
fun dayTimeLabel(day: Long, time: Int): String = dayLabel(day) + if (time >= 0) " " + tw.moneybook.app.formatTime(time) else ""

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

fun shortDate(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.year}/${d.monthValue}/${d.dayOfMonth}"
}

/** 自己設計的日期選擇器：快速按鈕 + 月曆 */
@Composable
fun CuteDatePickerDialog(
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String = "選擇日期",
) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val today = LocalDate.now()
    var sel by remember { mutableStateOf(LocalDate.ofEpochDay(initial)) }
    var shown by remember { mutableStateOf(YearMonth.from(sel)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(18.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = cute.sub)
            Text(
                "${sel.monthValue} 月 ${sel.dayOfMonth} 日・週${WEEKDAYS[sel.dayOfWeek.value - 1]}",
                style = MaterialTheme.typography.headlineSmall,
                color = primary,
            )
            if (sel.year != today.year) {
                Text("${sel.year} 年", style = MaterialTheme.typography.labelMedium, color = cute.sub)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("今天" to 0L, "昨天" to 1L, "前天" to 2L).forEach { (label, back) ->
                    val d = today.minusDays(back)
                    CuteChip(label, sel == d, {
                        sel = d
                        shown = YearMonth.from(d)
                    })
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cute.card).padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "«",
                        fontSize = 20.sp,
                        color = cute.sub,
                        modifier = Modifier.clip(CircleShape).clickable { shown = shown.minusYears(1) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    IconButton(onClick = { shown = shown.minusMonths(1) }, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.ChevronLeft, contentDescription = "上個月")
                    }
                    Text(
                        "${shown.year} 年 ${shown.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { shown = shown.plusMonths(1) }, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.ChevronRight, contentDescription = "下個月")
                    }
                    Text(
                        "»",
                        fontSize = 20.sp,
                        color = cute.sub,
                        modifier = Modifier.clip(CircleShape).clickable { shown = shown.plusYears(1) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { i, w ->
                        Text(
                            w,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == 0 || i == 6) cute.expense.copy(alpha = 0.7f) else cute.sub,
                        )
                    }
                }
                val lead = shown.atDay(1).dayOfWeek.value % 7
                val days = shown.lengthOfMonth()
                val weeks = (lead + days + 6) / 7
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val n = w * 7 + c - lead + 1
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                                if (n in 1..days) {
                                    val date = shown.atDay(n)
                                    val isSel = date == sel
                                    val isToday = date == today
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) primary else Color.Transparent)
                                            .then(if (isToday && !isSel) Modifier.border(1.5.dp, primary, CircleShape) else Modifier)
                                            .clickable { sel = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$n",
                                            color = when {
                                                isSel -> MaterialTheme.colorScheme.onPrimary
                                                isToday -> primary
                                                else -> cute.ink
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.Button(onClick = { onPick(sel.toEpochDay()) }) { Text("確定") }
            }
        }
    }
}

/**
 * 記完（或改完）一筆回到列表：把那一筆捲到畫面正中間；上面的內容不夠多就停在最上面（下面不夠就停在最下面）。
 * 如果它就是列表裡最上面的第一筆，上面沒有其他明細，就直接回到最上面，不把上方的摘要卡捲掉。
 * indexOf 回傳那一筆在這個列表裡的項目編號，不在這個列表（例如在別的月份）回傳 -1；firstTxnIndex 是第一筆明細的項目編號。
 * 不在目前這個列表時，先呼叫 switchTo 切到那一筆所在的月份（切了回傳 true），等畫面換好再捲過去。
 */
@Composable
fun CenterOnSaved(
    vm: MoneyViewModel,
    state: androidx.compose.foundation.lazy.LazyListState,
    firstTxnIndex: Int,
    switchTo: (Txn) -> Boolean = { false },
    indexOf: (Long) -> Int,
) {
    val id = vm.savedTxnId
    // 換月份之後要用新的列表算編號，所以讀最新一次組合的函式
    val latest = androidx.compose.runtime.rememberUpdatedState(indexOf)
    androidx.compose.runtime.LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        try {
            var idx = latest.value(id)
            if (idx < 0) {
                val t = vm.data.txns.firstOrNull { it.id == id }
                if (t != null && switchTo(t)) {
                    repeat(2) { androidx.compose.runtime.withFrameNanos { } }
                    idx = latest.value(id)
                }
            }
            if (idx < 0) return@LaunchedEffect
            if (idx <= firstTxnIndex) {
                state.scrollToItem(0)
                return@LaunchedEffect
            }
            state.scrollToItem(idx)
            val info = kotlinx.coroutines.withTimeoutOrNull(1000) {
                androidx.compose.runtime.snapshotFlow { state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx } }
                    .filterNotNull().first()
            } ?: return@LaunchedEffect
            val lay = state.layoutInfo
            val want = lay.viewportStartOffset + (lay.viewportEndOffset - lay.viewportStartOffset - info.size) / 2
            state.scrollBy((info.offset - want).toFloat())
        } finally {
            vm.savedTxnId = null
        }
    }
}

/** 依「日期標題＋當天每一筆」排列的列表裡，某一筆的項目編號（base = 前面固定項目的數量）；不在裡面回傳 -1 */
fun txnItemIndex(groups: Map<Long, List<Txn>>, base: Int, id: Long): Int {
    var i = base
    for ((_, l) in groups) {
        i += 1
        val k = l.indexOfFirst { it.id == id }
        if (k >= 0) return i + k
        i += l.size
    }
    return -1
}

