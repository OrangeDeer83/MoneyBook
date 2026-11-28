package tw.moneybook.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Store
import tw.moneybook.app.ui.LocalCute
import tw.moneybook.app.ui.MoneyTheme
import tw.moneybook.app.ui.SoftButton

/**
 * 放自組小工具到桌面時、長按 → 設定時跳出來（草圖 ci-out/widget_compose2.png ②）：「這個小工具用哪一份排版？」
 *  - 用這份：記下綁定、更新小工具
 *  - 改這份排版：記下綁定，打開 App 的排版頁（預覽用這個小工具的大小）
 *  - 還沒有任何一份：直接交給 App 新增一份範例排版、綁好、打開排版頁
 * 新增排版一律交給 App（MainActivity）做，免得兩邊各自讀寫存檔互相蓋掉。
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 沒按「用這份」就離開：系統不放這個小工具（重新設定時則是什麼都不改）
        setResult(Activity.RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val d: AppData = MoneyViewModel.live?.get()?.data ?: Store(filesDir).load()
        if (d.widgetLayouts.isEmpty()) {
            done()
            openApp("wnew:$widgetId:${sizeText()}")
            return
        }
        val cur = WidgetBinds.layoutOf(this, widgetId)
        setContent {
            val dark = when (d.prefs.dark) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MoneyTheme(d.prefs.palette, dark) {
                val cute = LocalCute.current
                var pick by remember { mutableLongStateOf(d.widgetLayouts.firstOrNull { it.id == cur }?.id ?: d.widgetLayouts.first().id) }
                Box(Modifier.fillMaxSize().clickable { finish() }, contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(26.dp))
                            .background(cute.card).clickable(enabled = false) {}.padding(22.dp),
                    ) {
                        Text("這個小工具用哪一份排版？", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            d.widgetLayouts.forEach { l ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick = l.id }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = pick == l.id, onClick = { pick = l.id })
                                    Column {
                                        Text(l.name, style = MaterialTheme.typography.bodyLarge)
                                        Text("${l.layout.rows.sumOf { it.ids.size }} 塊", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { done(); openApp("wnew:$widgetId:${sizeText()}") }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                            ) { Text("＋ 新增一份", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { bindTo(pick); done() }, modifier = Modifier.fillMaxWidth()) { Text("用這份") }
                        Spacer(Modifier.height(8.dp))
                        SoftButton("改這份排版 ›", { bindTo(pick); done(); openApp("wlayout:$pick:${sizeText()}") }, Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { finish() }) { Text("取消") }
                        }
                    }
                }
            }
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    /** 小工具現在的大小「寬:高」（dp）：畫過一次就有記錄，剛放上去的用系統給的大小 */
    private fun sizeText(): String {
        WidgetBinds.sizeOf(this, widgetId)?.let { return "${it.first}:${it.second}" }
        val o = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return if (w > 0 && h > 0) "$w:$h" else ""
    }

    private fun bindTo(layoutId: Long) {
        WidgetBinds.bind(this, widgetId, layoutId)
        refresh(applicationContext)
    }

    private fun done() {
        setResult(Activity.RESULT_OK, result())
        finish()
    }

    private fun openApp(open: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open", open)
            }
        )
    }

    companion object {
        /** 綁好之後讓桌面上的小工具照新的排版畫 */
        fun refresh(ctx: Context) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    ComposeWidget().updateAll(ctx)
                } catch (_: Exception) {
                }
            }
        }
    }
}
