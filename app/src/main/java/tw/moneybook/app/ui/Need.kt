package tw.moneybook.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape

/*
 * 必填提示：不把「儲存」灰掉，按下去才檢查。沒填好的項目亮紅框、訊息顯示在欄位下方，
 * 一次只標出畫面上由上到下的第一個沒填好的；改好之後馬上換下一個，欄位在畫面外會自動捲過去。
 *
 * 用法：
 *   val tries = rememberNeedTries()
 *   val need = firstNeed(if (name.isBlank()) Need("name", "請輸入名稱") else null, ...)   // 照畫面由上到下排
 *   val nv = NeedView(need, tries.count)
 *   OutlinedTextField(..., isError = nv.on("name"), supportingText = nv.supporting("name"), modifier = Modifier.needInView(nv, "name"))
 *   儲存：if (need != null) tries.count++ else { 存檔 }
 */

/** 一個必填項目：key 對應畫面上的欄位，msg 是顯示在欄位下方的提示 */
class Need(val key: String, val msg: String)

/** 由上到下，第一個沒填好的項目；全部都填好回傳 null */
fun firstNeed(vararg needs: Need?): Need? = needs.firstOrNull { it != null }

/** 按過幾次儲存（0 表示還沒按過，不標紅） */
@Stable
class NeedTries {
    var count by mutableIntStateOf(0)
}

@Composable
fun rememberNeedTries(): NeedTries = remember { NeedTries() }

/** 目前要標紅的項目：按過儲存、而且是第一個沒填好的才亮 */
@Stable
class NeedView(private val need: Need?, val tries: Int) {
    fun on(key: String): Boolean = tries > 0 && need?.key == key

    /** OutlinedTextField 的 supportingText：亮紅時顯示提示，不然是 null */
    fun supporting(key: String): (@Composable () -> Unit)? = if (on(key)) ({ Text(need!!.msg) }) else null

    /** 自訂元件（卡片、一排按鈕…）下方的提示文字 */
    @Composable
    fun Message(key: String, modifier: Modifier = Modifier) {
        if (on(key)) {
            Text(
                need!!.msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/** 自訂元件的紅框 */
@Composable
fun Modifier.needFrame(v: NeedView, key: String, shape: Shape, width: Dp = 2.dp): Modifier =
    if (v.on(key)) border(width, MaterialTheme.colorScheme.error, shape) else this

/** 亮紅時把這個欄位捲到看得見的地方（對話框或頁面在捲動、欄位在畫面外時用） */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.needInView(v: NeedView, key: String): Modifier = composed {
    val req = remember { BringIntoViewRequester() }
    val on = v.on(key)
    LaunchedEffect(on, v.tries) { if (on) req.bringIntoView() }
    bringIntoViewRequester(req)
}
