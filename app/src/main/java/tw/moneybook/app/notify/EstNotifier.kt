package tw.moneybook.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.R
import tw.moneybook.app.estDue
import tw.moneybook.app.estNotice
import tw.moneybook.app.mascotTitle
import tw.moneybook.app.todayEpoch
import tw.moneybook.app.ui.mascotDefaultName

/**
 * 預估的刷卡放太久（見 EstDue.kt）：跟訂閱的到期通知同一個定時檢查，每一筆只通知一次，之後就掛在首頁的「試刷」膠囊。
 * 開關跟刷卡待確認的手機通知一樣（Prefs.cardNotify）。點通知回到首頁、展開那張提醒。
 */
object EstNotifier {
    const val CHANNEL = "estimates"
    const val NOTI_ID = 4401
    private const val SP = "est_notifier"
    private const val KEY = "notified"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "預估金額提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "試刷、預先授權記成預估之後放太久還沒填實際金額時，提醒一次"
            }
        )
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTI_ID)

    private fun isShown(ctx: Context): Boolean = try {
        ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == NOTI_ID } == true
    } catch (_: Exception) {
        false
    }

    private fun notified(ctx: Context): Set<Long> =
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    /** 通知還掛著時，都填好、刪掉了就收掉 */
    fun refreshShown(ctx: Context, d: AppData) {
        if (isShown(ctx) && d.estDue(todayEpoch()).isEmpty()) cancel(ctx)
    }

    /** 定時檢查：有還沒通知過的放太久預估就發一次，記下來之後不再發 */
    fun post(ctx: Context, d: AppData) {
        val p = d.prefs
        val today = todayEpoch()
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        // 已經不是預估（填好、刪掉）的不用再記
        val due = d.estDue(today).map { it.id }.toSet()
        val old = notified(ctx).filter { it in due }.toSet()
        val n = if (p.cardNotify) d.estNotice(today, old, p.mascotTitle(::mascotDefaultName)) else null
        if (n == null || !SubNotifier.allowed(ctx)) {
            sp.edit().putStringSet(KEY, old.map { it.toString() }.toSet()).apply()
            return
        }
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 40,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open", "est")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sub)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        SubNotifier.mascotIcon(ctx, p, true)?.let { b.setLargeIcon(it) }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTI_ID, b.build())
        } catch (_: SecurityException) {
            // 沒有通知權限（Android 13 以上被拒絕）：不發
        }
        sp.edit().putStringSet(KEY, (old + n.ids).map { it.toString() }.toSet()).apply()
    }
}
