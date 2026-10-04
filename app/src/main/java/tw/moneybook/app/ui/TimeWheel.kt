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
import kotlin.math.abs

/**
 * 滾輪選數字：一直往上下滑，停下來會自動對齊到正中間那一格，正中間的就是目前選的。
 * count 個項目（0 until count），value 是目前選的，停下來後用 onChange 回報。
 */
@Composable
fun WheelColumn(
    count: Int,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { "%02d".format(it) },
) {
    val cute = LocalCute.current
    val itemH = 44.dp
    val rows = 5   // 看得到幾格（上下各 2 格）
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // 目前在正中間的那一格（離可視範圍中心最近的）
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: value
        }
    }
    // 停下來之後才回報（滑動途中不一直改）
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress && centered != value) onChange(centered)
    }
    // 外面把值改了（例如從鍵盤輸入切回滾輪），就捲到那一格
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && centered != value) state.scrollToItem(value.coerceIn(0, count - 1))
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
            items(count) { i ->
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val on = i == centered
                    Text(
                        label(i),
                        style = if (on) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else cute.sub,
                    )
                }
            }
        }
    }
}
