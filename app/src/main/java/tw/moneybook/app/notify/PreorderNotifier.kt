package tw.moneybook.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.R
import tw.moneybook.app.Store
import tw.moneybook.app.mascotTitle
import tw.moneybook.app.preorderNotice
import tw.moneybook.app.todayEpoch
import tw.moneybook.app.ui.mascotDefaultName
import tw.moneybook.app.withPreorderSkipped

/**
 * 預購到了預計那天，由吉祥物問「買了嗎？」（草圖 ci-out/preorder_plan.png ④）。跟訂閱的到期通知同一個定時檢查
 * （SubNotifier.schedule、時間、再提醒天數都一樣），通知分開。只有一筆時通知上有：
 * 「買了」打開記一筆（帶好預購的內容，存好才算買了）、「沒買」直接移到沒買、「延後」打開預購清單選日期。
 */
object PreorderNotifier {
    const val CHANNEL = "preorders"
    const val NOTI_ID = 4301
    const val ACTION_SKIP = "tw.moneybook.app.PRE_SKIP"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "預購提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "預購清單的東西到了預計那天，由吉祥物問買了沒"
            }
        )
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTI_ID)

    private fun isShown(ctx: Context): Boolean = try {
        ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == NOTI_ID } == true
    } catch (_: Exception) {
        false
    }

    /** 通知還掛著時，資料變了（買了、沒買、延後）就照新的內容更新或收掉；不會再響一次 */
    fun refreshShown(ctx: Context, d: AppData) {
        if (isShown(ctx)) post(ctx, d, silent = true)
    }

    private fun openApp(ctx: Context, code: Int, open: String): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open", open)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 照今天的情況發（或更新、收掉）通知；silent＝更新時不再響 */
    fun post(ctx: Context, d: AppData, silent: Boolean) {
        val p = d.prefs
        val n = if (p.preorderNotify) d.preorderNotice(todayEpoch(), p.subNotifyRepeat, p.mascotTitle(::mascotDefaultName)) else null
        if (n == null || !SubNotifier.allowed(ctx)) {
            cancel(ctx)
            return
        }
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sub)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setContentIntent(openApp(ctx, 30, "preorders"))
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        SubNotifier.mascotIcon(ctx, p, n.worried)?.let { b.setLargeIcon(it) }
        if (n.lines.isNotEmpty()) {
            b.setStyle(NotificationCompat.InboxStyle().also { st -> n.lines.forEach { st.addLine(it) }; st.setSummaryText(n.body) })
        }
        n.single?.let { id ->
            b.addAction(0, "買了", openApp(ctx, 31, "po-buy:$id"))
            b.addAction(
                0, "沒買",
                PendingIntent.getBroadcast(
                    ctx, 32, Intent(ctx, PreorderReceiver::class.java).setAction(ACTION_SKIP).putExtra("id", id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            b.addAction(0, "延後", openApp(ctx, 33, "po-later:$id"))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTI_ID, b.build())
        } catch (_: SecurityException) {
            // 沒有通知權限（Android 13 以上被拒絕）：不發
        }
    }
}

/** 通知上的「沒買」（只收自己 App 發的，不對外開放） */
class PreorderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != PreorderNotifier.ACTION_SKIP) return
        val id = intent.getLongExtra("id", 0L).takeIf { it > 0L } ?: return
        val vm = MoneyViewModel.live?.get()
        if (vm != null) {
            // App 開著：交給畫面那邊（有「復原」，存檔後通知跟著更新）
            vm.skipPreorder(id)
            return
        }
        val store = Store(ctx.filesDir)
        val d = store.load().withPreorderSkipped(id, todayEpoch())
        store.save(d)
        PreorderNotifier.post(ctx, d, silent = true)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                tw.moneybook.app.widget.updateAllWidgets(ctx)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
