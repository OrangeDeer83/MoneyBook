package tw.moneybook.app.widget

import android.content.Context

/**
 * 桌面上每個自組小工具用哪一份排版（appWidgetId → 排版 id），還有那個小工具現在多大（dp）。
 * 存在手機上（SharedPreferences），不跟著備份：小工具的 id 換手機就不一樣了，還原後要重新放、選回那一份。
 */
object WidgetBinds {
    private const val SP = "widget_binds"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun layoutOf(ctx: Context, widgetId: Int): Long = sp(ctx).getLong("w$widgetId", 0L)

    fun bind(ctx: Context, widgetId: Int, layoutId: Long) {
        sp(ctx).edit().putLong("w$widgetId", layoutId).apply()
    }

    fun unbind(ctx: Context, widgetId: Int) {
        sp(ctx).edit().remove("w$widgetId").remove("s$widgetId").apply()
    }

    /** 記下小工具現在的大小（寬、高 dp），排版頁從小工具打開時預覽用 */
    fun setSize(ctx: Context, widgetId: Int, w: Int, h: Int) {
        sp(ctx).edit().putString("s$widgetId", "$w,$h").apply()
    }

    fun sizeOf(ctx: Context, widgetId: Int): Pair<Int, Int>? =
        sp(ctx).getString("s$widgetId", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    /** 桌面上有幾個小工具用這一份排版 */
    fun countFor(ctx: Context, layoutId: Long): Int =
        sp(ctx).all.count { (k, v) -> k.startsWith("w") && v == layoutId }
}
