package tw.moneybook.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 拖曳的回呼：位置都是整個畫面（root）座標 */
class DragCallbacks(
    val onStart: (Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * 可左滑露出「刪除」的列；傳入 drag 時，長按後可以拖曳。
 * 左滑與長按拖曳用同一個手勢判斷，避免互相搶。
 */
@Composable
fun SwipeRow(
    onDelete: () -> Unit,
    drag: DragCallbacks? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxShift = with(density) { 68.dp.toPx() }
    var shift by remember { mutableFloatStateOf(0f) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val expense = LocalCute.current.expense

    fun settle(to: Float) {
        val from = shift
        scope.launch { animate(from, to) { v, _ -> shift = v } }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootPos = it.positionInRoot() }
    ) {
        // 後面的刪除鈕
        if (shift < -1f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(expense.copy(alpha = 0.9f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .clickable {
                            settle(0f)
                            onDelete()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("刪除", color = Color.White)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(shift.roundToInt(), 0) }
                .pointerInput(drag != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var total = Offset.Zero
                        // 1 = 左右滑, -1 = 放掉或上下捲動, null = 長按
                        val decided: Int? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var result = 0
                            while (result == 0) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null || !ch.pressed) {
                                    result = -1
                                } else {
                                    total += ch.positionChange()
                                    if (abs(total.x) > slop && abs(total.x) > abs(total.y)) result = 1
                                    else if (abs(total.y) > slop) result = -1
                                }
                            }
                            result
                        }
                        when {
                            decided == 1 -> {
                                // 左滑露出刪除鈕
                                var cur = (shift + total.x).coerceIn(-maxShift, 0f)
                                shift = cur
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    cur = (cur + ch.positionChange().x).coerceIn(-maxShift, 0f)
                                    shift = cur
                                    ch.consume()
                                }
                                settle(if (shift < -maxShift / 2f) -maxShift else 0f)
                            }
                            decided == null && drag != null -> {
                                // 長按後拖曳
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (shift != 0f) settle(0f)
                                var pos = rootPos + down.position
                                drag.onStart(pos)
                                var ended = false
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch == null) break
                                    if (!ch.pressed) {
                                        ch.consume()
                                        ended = true
                                        break
                                    }
                                    pos += ch.positionChange()
                                    drag.onMove(pos)
                                    ch.consume()
                                }
                                if (ended) drag.onEnd() else drag.onCancel()
                            }
                            decided == -1 && shift != 0f -> settle(0f)
                            else -> {}
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/** 拖曳把手：三條橫線 */
@Composable
fun DragHandle(modifier: Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.Menu,
            contentDescription = "按住拖曳排序",
            tint = LocalCute.current.sub,
        )
    }
}

/**
 * 可以用把手拖曳排序的清單（非 Lazy，適合項目不多的清單）。
 * itemContent 會拿到要掛在把手上的 Modifier（用來記錄把手位置）。
 * 手勢由外層 Column 統一處理：外層不會跟著項目移動，座標才不會亂跳。
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    key: (T) -> Long,
    onReorder: (List<Long>) -> Unit,
    spacing: androidx.compose.ui.unit.Dp = 8.dp,
    itemContent: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val keys = items.map(key)
    var order by remember(keys) { mutableStateOf(keys) }
    val byKey = items.associateBy(key)
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragTrans by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<Long, Int>() }
    val handleRects = remember { HashMap<Long, androidx.compose.ui.geometry.Rect>() }
    var colRoot by remember { mutableStateOf(Offset.Zero) }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptic = LocalHapticFeedback.current
    val latestReorder by androidx.compose.runtime.rememberUpdatedState(onReorder)

    fun topOf(k: Long, ord: List<Long>): Float {
        var y = 0f
        for (x in ord) {
            if (x == k) break
            y += (heights[x] ?: 0) + spacingPx
        }
        return y
    }

    androidx.compose.foundation.layout.Column(
        Modifier
            .onGloballyPositioned { colRoot = it.positionInRoot() }
            .pointerInput(keys) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val rootPt = colRoot + down.position
                    val k = handleRects.entries.firstOrNull { it.key in order && it.value.contains(rootPt) }?.key
                    if (k != null) {
                        down.consume()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val grab = down.position.y - topOf(k, order)
                        draggingKey = k
                        dragTrans = 0f
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                            val fingerY = ch.position.y
                            var idx = order.indexOf(k)
                            var t = fingerY - grab - topOf(k, order)
                            while (idx < order.lastIndex && t > ((heights[order[idx + 1]] ?: 0) + spacingPx) / 2f) {
                                val next = order[idx + 1]
                                order = order.toMutableList().also { it[idx] = next; it[idx + 1] = k }
                                idx++
                                t = fingerY - grab - topOf(k, order)
                            }
                            while (idx > 0 && t < -((heights[order[idx - 1]] ?: 0) + spacingPx) / 2f) {
                                val prev = order[idx - 1]
                                order = order.toMutableList().also { it[idx] = prev; it[idx - 1] = k }
                                idx--
                                t = fingerY - grab - topOf(k, order)
                            }
                            dragTrans = t
                        }
                        draggingKey = null
                        dragTrans = 0f
                        if (order != keys) latestReorder(order)
                    }
                }
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
    ) {
        order.forEach { k ->
            val item = byKey[k]
            if (item != null) {
                androidx.compose.runtime.key(k) {
                    val isDrag = k == draggingKey
                    Box(
                        Modifier
                            .onSizeChanged { heights[k] = it.height }
                            .zIndex(if (isDrag) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDrag) dragTrans else 0f
                                scaleX = if (isDrag) 1.02f else 1f
                                scaleY = if (isDrag) 1.02f else 1f
                                shadowElevation = if (isDrag) 18f else 0f
                                shape = RoundedCornerShape(20.dp)
                                clip = false
                            }
                    ) {
                        val handle = Modifier.onGloballyPositioned { handleRects[k] = it.boundsInRoot() }
                        itemContent(item, handle, isDrag)
                    }
                }
            }
        }
    }
}
