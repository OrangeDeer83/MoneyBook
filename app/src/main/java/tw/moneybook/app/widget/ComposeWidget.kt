package tw.moneybook.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import tw.moneybook.app.AppData
import tw.moneybook.app.TxType
import tw.moneybook.app.WBlocks
import tw.moneybook.app.WLayout
import tw.moneybook.app.forWidget
import tw.moneybook.app.pendWaiting
import tw.moneybook.app.ui.WColors
import tw.moneybook.app.ui.mascotLine
import tw.moneybook.app.ui.mascotRes
import tw.moneybook.app.ui.moodOf
import tw.moneybook.app.ui.palOf
import tw.moneybook.app.ui.wColors
import tw.moneybook.app.widgetBar
import tw.moneybook.app.widgetNum
import tw.moneybook.app.widgetOpen
import tw.moneybook.app.widgetTop3
import tw.moneybook.app.widgetWeek

/*
 * 自組的桌面小工具（草圖 ci-out/widget_compose2.png）：照綁定的那一份排版（見 WidgetBinds）由上往下畫，放不下的排不畫。
 * 放到桌面時、長按 → 設定會打開 WidgetConfigActivity 選排版。顏色「跟 App 一樣」時跟著手機的深淺色。
 */

/** 小工具的顏色：tone 0 白天、晚上各一套（跟著手機）；1、2 固定淺色或深色 */
private class GColors(l: WLayout, p: tw.moneybook.app.ui.Pal) {
    private val day: WColors = wColors(p, l.copy(tone = if (l.tone == 2) 2 else 1), false)
    private val night: WColors = wColors(p, l.copy(tone = if (l.tone == 1) 1 else 2), true)
    private fun pair(f: (WColors) -> Long, alpha: Float = 1f) = ColorProvider(Color(f(day)).copy(alpha = alpha), Color(f(night)).copy(alpha = alpha))
    val bg = pair({ it.bg }, day.bgAlpha)
    val cell = pair({ it.cell }, if (day.bgAlpha == 0f) 0.9f else 1f)
    val ink = pair({ it.ink })
    val sub = pair({ it.sub })
    val track = pair({ it.sub }, 0.18f)
    val primary = pair({ it.primary })
    val primarySoft = pair({ it.primary }, 0.4f)
    val onPrimary = pair({ it.onPrimary })
    val inc = pair({ it.inc })
    val exp = pair({ it.exp })
    val expSoft = pair({ it.exp }, 0.4f)
}

class ComposeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d0 = loadData(context)
        val wid = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (_: Exception) {
            0
        }
        val lid = WidgetBinds.layoutOf(context, wid)
        val l = d0.widgetLayouts.firstOrNull { it.id == lid }
        val pendN = try {
            d0.pendWaiting(tw.moneybook.app.notify.CardPendLog.load(context)).size
        } catch (_: Exception) {
            0
        }
        provideContent {
            val size = LocalSize.current
            // 記下現在的大小：長按 → 設定打開排版頁時，預覽用這個大小
            if (wid != 0) WidgetBinds.setSize(context, wid, size.width.value.toInt(), size.height.value.toInt())
            if (l == null) {
                val c = GColors(WBlocks.example(0, "", d0), palOf(d0.prefs.palette))
                Column(
                    GlanceModifier.fillMaxSize().background(c.bg).cornerRadius(android.R.dimen.system_app_widget_background_radius)
                        .padding(14.dp).clickable(openApp(context, "widgets")),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (lid == 0L) "還沒選排版" else "排版被刪了", style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text("長按這裡 → 設定，選一份排版", style = TextStyle(color = c.sub, fontSize = 12.sp), maxLines = 2)
                }
            } else {
                Body(context, d0.forWidget(l), l, pendN, size.width.value, size.height.value)
            }
        }
    }
}

@Composable
private fun Body(context: Context, d: AppData, l: WLayout, pendN: Int, wDp: Float, hDp: Float) {
    val c = GColors(l, palOf(d.prefs.palette))
    val f = WBlocks.fontScale(l.font)
    val tplN = WBlocks.tplIds(d, l.opts).size
    val heights = WBlocks.rowHeights(l, tplN)
    val fit = WBlocks.fitRows(l, tplN, hDp)
    val inner = wDp - WBlocks.PAD * 2
    Column(
        GlanceModifier.fillMaxSize().background(c.bg).cornerRadius(android.R.dimen.system_app_widget_background_radius).padding(WBlocks.PAD.dp),
    ) {
        l.layout.rows.take(fit).forEachIndexed { i, r ->
            if (i > 0) Spacer(GlanceModifier.height(WBlocks.GAP.dp))
            Row(GlanceModifier.fillMaxWidth().height(heights[i].dp)) {
                if (!r.half) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Block(context, d, l, c, r.left!!, false, f, pendN, inner) }
                } else {
                    val half = (inner - WBlocks.GAP) / 2
                    listOf(r.left, r.right).forEachIndexed { k, bid ->
                        if (k == 1) Spacer(GlanceModifier.width(WBlocks.GAP.dp))
                        Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                            if (bid != null) Block(context, d, l, c, bid, true, f, pendN, half)
                        }
                    }
                }
            }
        }
    }
}

/** 一個區塊；wDp＝這個區塊多寬（畫條的長度用） */
@Composable
private fun Block(context: Context, d: AppData, l: WLayout, c: GColors, id: String, half: Boolean, f: Float, pendN: Int, wDp: Float) {
    val today = LocalDate.now()
    val open = openApp(context, widgetOpen(id, l, today))
    val cellMod = GlanceModifier.fillMaxSize().background(c.cell).cornerRadius(android.R.dimen.system_app_widget_inner_radius)
        .padding(horizontal = 10.dp, vertical = 6.dp).clickable(open)
    val barW = (wDp - 20f).coerceAtLeast(10f)
    when {
        id in WBlocks.NUMBERS -> {
            val n = d.widgetNum(id, today, pendN) ?: return
            val col = when (n.tone) {
                1 -> c.inc
                2 -> c.exp
                else -> c.ink
            }
            Column(cellMod, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.label + if (n.sub.isNotEmpty()) "・" + n.sub else "",
                    style = TextStyle(color = c.sub, fontSize = (11 * f).sp), maxLines = 1,
                )
                Text(n.value, style = TextStyle(color = col, fontSize = (19 * f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        id == WBlocks.BAR -> {
            val b = d.widgetBar(today)
            Column(cellMod, verticalAlignment = Alignment.CenterVertically) {
                Text(b?.label ?: "還沒設定預算", style = TextStyle(color = c.sub, fontSize = (11 * f).sp), maxLines = 1)
                Spacer(GlanceModifier.height(5.dp))
                Row(GlanceModifier.width(barW.dp).height(8.dp).background(c.track).cornerRadius(4.dp)) {
                    if (b != null) {
                        val spent = (b.used - b.reserved).coerceAtLeast(0f)
                        val res = b.reserved.coerceAtMost(b.used)
                        if (spent > 0f) Box(GlanceModifier.width((barW * spent).dp).fillMaxHeight().background(if (b.over) c.exp else c.primary)) {}
                        if (res > 0f) Box(GlanceModifier.width((barW * res).dp).fillMaxHeight().background(if (b.over) c.expSoft else c.primarySoft)) {}
                    }
                }
            }
        }
        id == WBlocks.ADD -> {
            val label = when (WBlocks.addType(l.opts)) {
                TxType.INCOME -> "＋ 記收入"
                TxType.TRANSFER -> "＋ 記轉帳"
                else -> "＋ 記一筆"
            }
            Box(
                GlanceModifier.fillMaxSize().background(c.primary).cornerRadius(android.R.dimen.system_app_widget_inner_radius).clickable(open),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = TextStyle(color = c.onPrimary, fontSize = (14 * f).sp, fontWeight = FontWeight.Bold), maxLines = 1) }
        }
        WBlocks.isShortcut(id) -> Box(cellMod, contentAlignment = Alignment.Center) {
            Text(WBlocks.shortcutName(WBlocks.shortcutOpen(l.opts, id)) + " ›", style = TextStyle(color = c.ink, fontSize = (13 * f).sp), maxLines = 1)
        }
        id == WBlocks.TPLS -> {
            val ids = WBlocks.tplIds(d, l.opts)
            val cells: List<Long?> = ids + null
            val per = WBlocks.tplPerRow(half)
            Column(GlanceModifier.fillMaxSize()) {
                cells.chunked(per).forEachIndexed { ri, row ->
                    if (ri > 0) Spacer(GlanceModifier.height(4.dp))
                    Row(GlanceModifier.fillMaxWidth().height((WBlocks.TPL_CELL * f).dp)) {
                        (0 until per).forEach { k ->
                            if (k > 0) Spacer(GlanceModifier.width(4.dp))
                            val tid = row.getOrNull(k)
                            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                                if (k < row.size) TplBlockCell(context, d, c, tid, f)
                            }
                        }
                    }
                }
            }
        }
        id == WBlocks.WEEK -> {
            val week = d.widgetWeek(today)
            val max = week.maxOf { it.second }.coerceAtLeast(1L)
            Column(cellMod) {
                Text("近 7 天支出", style = TextStyle(color = c.sub, fontSize = (11 * f).sp), maxLines = 1)
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { k, (day, v) ->
                        if (k > 0) Spacer(GlanceModifier.width(4.dp))
                        Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            val h = (36f * f * v / max).coerceAtLeast(2f)
                            Box(GlanceModifier.fillMaxWidth().height(h.dp).background(if (v > 0) c.primary else c.track).cornerRadius(3.dp)) {}
                            Text("$day", style = TextStyle(color = c.sub, fontSize = (9 * f).sp), maxLines = 1)
                        }
                    }
                }
            }
        }
        id == WBlocks.TOP3 -> {
            val top = d.widgetTop3(today)
            val nameW = 48f * f
            val pctW = 34f * f
            val trackW = (barW - nameW - pctW).coerceAtLeast(10f)
            Column(cellMod) {
                Text("本月花最多", style = TextStyle(color = c.sub, fontSize = (11 * f).sp), maxLines = 1)
                if (top.isEmpty()) Text("這個月還沒有支出", style = TextStyle(color = c.ink, fontSize = (12 * f).sp), maxLines = 1)
                top.forEach { (name, _, pct) ->
                    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = TextStyle(color = c.ink, fontSize = (11 * f).sp), maxLines = 1, modifier = GlanceModifier.width(nameW.dp))
                        Row(GlanceModifier.width(trackW.dp).height(6.dp).background(c.track).cornerRadius(3.dp)) {
                            if (pct > 0) Box(GlanceModifier.width((trackW * pct / 100f).dp).fillMaxHeight().background(c.primary)) {}
                        }
                        Text("$pct%", style = TextStyle(color = c.ink, fontSize = (11 * f).sp, textAlign = TextAlign.End), maxLines = 1, modifier = GlanceModifier.width(pctW.dp))
                    }
                }
            }
        }
        id == WBlocks.MASCOT -> {
            val mood = moodOf(d)
            Row(cellMod, verticalAlignment = Alignment.CenterVertically) {
                if (d.prefs.mascot != "none") {
                    Image(ImageProvider(mascotRes(d.prefs.mascot, mood)), contentDescription = null, modifier = GlanceModifier.size((40 * f).dp))
                    Spacer(GlanceModifier.width(8.dp))
                }
                Text(mascotLine(mood, d), style = TextStyle(color = c.ink, fontSize = (12.5f * f).sp), maxLines = 2)
            }
        }
    }
}

/** 常用記帳的一格（null＝最後的＋） */
@Composable
private fun TplBlockCell(context: Context, d: AppData, c: GColors, tid: Long?, f: Float) {
    val base = GlanceModifier.fillMaxSize().background(c.cell).cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    val t = tid?.let { x -> d.templates.firstOrNull { it.id == x } }
    if (t == null) {
        Box(base.clickable(openApp(context, "add")), contentAlignment = Alignment.Center) {
            Text("＋", style = TextStyle(color = c.primary, fontSize = (18 * f).sp, fontWeight = FontWeight.Bold))
        }
        return
    }
    val cat = t.categoryId?.let { d.catMap[it] }
    val emoji = t.icon.ifEmpty { cat?.emoji ?: if (t.type == TxType.TRANSFER) "img:ui_transfer" else "img:cat_box" }
    Row(base.clickable(openApp(context, "tpl:${t.id}")).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        TplIcon(emoji, cat?.color ?: 5, (22 * f).toInt())
        Spacer(GlanceModifier.width(4.dp))
        Text(t.name, style = TextStyle(color = c.ink, fontSize = (12 * f).sp), maxLines = 2)
    }
}

class ComposeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ComposeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTick.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetTick.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetBinds.unbind(context, it) }
    }
}

/**
 * 過了半夜自動換天（「今天花了」「每天還能花」、近 7 天）：每天 0 點多一點更新一次所有小工具，再排下一次。
 * 開機、App 打開、放了自組小工具時都會重排。
 */
object WidgetTick {
    const val ACTION = "tw.moneybook.app.WIDGET_TICK"

    private fun intent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 50, Intent(ctx, WidgetTickReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, intent(ctx))
    }
}

/** 半夜的更新（只收自己 App 排的） */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != WidgetTick.ACTION) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                updateAllWidgets(ctx)
            } catch (_: Exception) {
            } finally {
                WidgetTick.schedule(ctx)
                pending.finish()
            }
        }
    }
}
