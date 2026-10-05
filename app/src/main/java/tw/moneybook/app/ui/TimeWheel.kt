@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import kotlin.math.abs

/** 滾輪重複排的圈數：看起來可以一直循環（23 之後接 00），從正中間那一圈開始 */
private const val LOOPS = 400

/**
 * 滾輪選數字：一直往上下滑（會循環），停下來會自動對齊到正中間那一格，正中間的就是目前選的。
 * count 個項目（0 until count），value 是目前選的，停下來後用 onChange 回報；點一下數字呼叫 onTap。
 */
@Composable
fun WheelColumn(
    count: Int,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    label: (Int) -> String = { "%02d".format(it) },
) {
    val cute = LocalCute.current
    val itemH = 44.dp
    val rows = 5   // 看得到幾格（上下各 2 格）
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (LOOPS / 2) * count + value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // 目前在正中間的那一格（離可視範圍中心最近的）
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: ((LOOPS / 2) * count + value)
        }
    }
    // 停下來之後才回報（滑動途中不一直改）
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress && centered % count != value) onChange(centered % count)
    }
    // 外面把值改了，就捲到最近的那一格
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && centered % count != value) {
            val delta = ((value - centered % count) % count + count) % count
            state.scrollToItem(if (delta <= count / 2) centered + delta else centered + delta - count)
        }
    }

    Box(modifier.width(72.dp).height(itemH * rows)) {
        // 中間選取列的底色
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(itemH)
                .clip(RoundedCornerShape(12.dp)).background(cute.soft)
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemH * (rows / 2)),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count * LOOPS) { idx ->
                Box(Modifier.height(itemH).fillMaxWidth().clickable { onTap() }, contentAlignment = Alignment.Center) {
                    val on = idx == centered
                    Text(
                        label(idx % count),
                        style = if (on) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else cute.sub,
                    )
                }
            }
        }
    }
}

/**
 * 鍵盤輸入時間（點一下滾輪就換成這個）：小時、分鐘兩格，一出現就選取小時準備輸入，
 * 小時輸入滿 2 位數自動跳到分鐘。超過範圍的數字會被修正（小時最大 23、分鐘最大 59）。
 */
@Composable
fun TimeTypeInput(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    var h by remember { mutableStateOf(TextFieldValue("%02d".format(hour), TextRange(0, 2))) }
    var m by remember { mutableStateOf(TextFieldValue("%02d".format(minute), TextRange(0, 2))) }
    val hFocus = remember { FocusRequester() }
    val mFocus = remember { FocusRequester() }
    // 剛選取、還沒打字的格子：打的第一個數字要「取代」原本的數字（輸入法有時還沒同步選取範圍，會變成接在後面）
    var hFresh by remember { mutableStateOf(true) }
    var mFresh by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { hFocus.requestFocus() }
    fun report(hv: TextFieldValue, mv: TextFieldValue) =
        onChange(hv.text.toIntOrNull()?.coerceIn(0, 23) ?: 0, mv.text.toIntOrNull()?.coerceIn(0, 59) ?: 0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(h, hFocus, { v ->
            val t = typedDigits(h, v, hFresh)
            hFresh = false
            h = v.copy(text = t, selection = TextRange(t.length))
            report(h, m)
            if (t.length == 2) mFocus.requestFocus()
        }, { hFresh = true; h = h.copy(selection = TextRange(0, h.text.length)) })
        Text(":", style = MaterialTheme.typography.headlineMedium)
        TimeField(m, mFocus, { v ->
            val t = typedDigits(m, v, mFresh)
            mFresh = false
            m = v.copy(text = t, selection = TextRange(t.length))
            report(h, m)
        }, { mFresh = true; m = m.copy(selection = TextRange(0, m.text.length)) })
    }
}

/** 這次輸入的數字（最多 2 位）：fresh 時如果是接在後面多出來的，只取新打的那幾個 */
internal fun typedDigits(old: TextFieldValue, v: TextFieldValue, fresh: Boolean): String {
    val n = v.text.length - old.text.length
    if (fresh && n > 0) {
        val end = v.selection.end.coerceIn(n, v.text.length)
        return v.text.substring(end - n, end).filter { it.isDigit() }.take(2)
    }
    return v.text.filter { it.isDigit() }.take(2)
}

@Composable
private fun TimeField(
    value: TextFieldValue,
    focus: FocusRequester,
    onValue: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
) {
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(primary),
        modifier = Modifier.width(72.dp).height(52.dp)
            .clip(RoundedCornerShape(12.dp)).background(cute.soft)
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
    )
}

/**
 * 選擇時間的對話框（記一筆、報銷收款共用）：預設是滾輪；點一下數字變成鍵盤輸入。
 * 鍵盤輸入時，收起鍵盤或點旁邊的空白處，就自動回到滾輪（不用按任何按鈕）。
 * minute：目前的時間（從 0 點算起的分鐘數），沒有時間（-1）就從現在開始。
 */
@Composable
fun TimePickerDialog(minute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val init = if (minute >= 0) minute else java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    var typing by remember { mutableStateOf(false) }
    var hh by remember { mutableIntStateOf(init / 60) }
    var mm by remember { mutableIntStateOf(init % 60) }
    val view = LocalView.current
    val focus = LocalFocusManager.current
    fun backToWheel() { focus.clearFocus(); typing = false }
    // 鍵盤出現過、之後又收起來 → 回到滾輪（沒有螢幕鍵盤的裝置不會觸發，點旁邊一樣可以回去）
    LaunchedEffect(typing) {
        if (!typing) return@LaunchedEffect
        var shown = false
        while (true) {
            delay(120)
            val ime = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (ime) shown = true else if (shown) { backToWheel(); break }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇時間") },
        text = {
            Column(
                Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (typing) backToWheel() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    if (!typing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WheelColumn(24, hh, { hh = it }, onTap = { typing = true })
                            Text(":", style = MaterialTheme.typography.headlineMedium)
                            WheelColumn(60, mm, { mm = it }, onTap = { typing = true })
                        }
                    } else {
                        TimeTypeInput(hh, mm) { h, m -> hh = h; mm = m }
                    }
                }
                Text(
                    if (typing) "收起鍵盤或點旁邊空白處，回到滾輪" else "點一下數字可以直接輸入",
                    style = MaterialTheme.typography.labelSmall, color = LocalCute.current.sub,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hh * 60 + mm) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
