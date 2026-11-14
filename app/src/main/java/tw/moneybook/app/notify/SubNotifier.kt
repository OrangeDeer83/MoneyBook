package tw.moneybook.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Prefs
import tw.moneybook.app.R
import tw.moneybook.app.Store
import tw.moneybook.app.mascotTitle
import tw.moneybook.app.nextNotifyAt
import tw.moneybook.app.subNotice
import tw.moneybook.app.todayEpoch
import tw.moneybook.app.ui.Mood
import tw.moneybook.app.ui.mascotDefaultName
import tw.moneybook.app.ui.mascotRes
import tw.moneybook.app.withSubRecorded
import tw.moneybook.app.withSubSkipped

/**
 * 訂閱到期的手機通知：每天在設定的時間（AlarmManager）檢查一次，有到期沒記的就由吉祥物發一則通知
 * （好幾筆合成一則）。通知上可以「記好了」「略過這次」（只有一筆、不用確認匯率時）或「全部記好」。
 * 在 App 裡記了或略過，畫面上的通知跟著更新或消失（MoneyViewModel.commit 呼叫 refreshShown）。
 */
object SubNotifier {
    const val CHANNEL = "subs"
    const val NOTI_ID = 4201
    const val ACTION_CHECK = "tw.moneybook.app.SUB_CHECK"
    const val ACTION_RECORD = "tw.moneybook.app.SUB_RECORD"
    const val ACTION_SKIP = "tw.moneybook.app.SUB_SKIP"
    const val ACTION_ALL = "tw.moneybook.app.SUB_ALL"

    /** 現在的資料：App 開著就用畫面上的（避免兩邊各存一份互相蓋掉），沒開就從檔案讀 */
    internal fun current(ctx: Context): AppData = MoneyViewModel.live?.get()?.data ?: Store(ctx.filesDir).load()

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "訂閱到期提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "訂閱／固定扣款到了扣款日還沒記時，由吉祥物提醒"
            }
        )
    }

    private fun checkIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, SubNotifyReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 排下一次檢查（開關關掉就取消）；App 打開、改設定、開機後都會重排 */
    fun schedule(ctx: Context, p: Prefs) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = checkIntent(ctx)
        if (!p.subNotify) {
            am.cancel(pi)
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextNotifyAt(System.currentTimeMillis(), p.subNotifyTime), pi)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTI_ID)

    /** 手機有沒有允許記帳本發通知 */
    fun allowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun isShown(ctx: Context): Boolean = try {
        ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == NOTI_ID } == true
    } catch (_: Exception) {
        false
    }

    /** 通知還掛在手機上時，資料變了（在 App 裡記了、略過了）就照新的內容更新，沒有要提醒的就收掉；不會再響一次 */
    fun refreshShown(ctx: Context, d: AppData) {
        if (isShown(ctx)) post(ctx, d, silent = true)
    }

    private fun mascotIcon(ctx: Context, p: Prefs, worried: Boolean): Bitmap? {
        if (p.mascot == "none") return null
        return try {
            val b = BitmapFactory.decodeResource(ctx.resources, mascotRes(p.mascot, if (worried) Mood.WORRIED else Mood.HAPPY)) ?: return null
            Bitmap.createScaledBitmap(b, 192, 192, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun action(ctx: Context, code: Int, act: String, items: List<Pair<Long, Long>>): PendingIntent = PendingIntent.getBroadcast(
        ctx, code,
        Intent(ctx, SubNotifyReceiver::class.java).setAction(act)
            .putExtra("ids", items.map { it.first }.toLongArray())
            .putExtra("days", items.map { it.second }.toLongArray()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 照今天的到期情況發（或更新、收掉）通知；silent＝更新時不再響 */
    fun post(ctx: Context, d: AppData, silent: Boolean) {
        val p = d.prefs
        val n = if (p.subNotify) d.subNotice(todayEpoch(), p.subNotifyRepeat, p.subNotifyAhead, p.mascotTitle(::mascotDefaultName)) else null
        if (n == null || !allowed(ctx)) {
            cancel(ctx)
            return
        }
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open", "home")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sub)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        mascotIcon(ctx, p, n.worried)?.let { b.setLargeIcon(it) }
        if (n.lines.isNotEmpty()) {
            b.setStyle(NotificationCompat.InboxStyle().also { st -> n.lines.forEach { st.addLine(it) }; st.setSummaryText(n.body) })
        }
        n.single?.let {
            b.addAction(0, "記好了", action(ctx, 10, ACTION_RECORD, listOf(it)))
            b.addAction(0, "略過這次", action(ctx, 11, ACTION_SKIP, listOf(it)))
        }
        if (n.single == null && n.all.isNotEmpty()) b.addAction(0, "全部記好", action(ctx, 12, ACTION_ALL, n.all))
        try {
            NotificationManagerCompat.from(ctx).notify(NOTI_ID, b.build())
        } catch (_: SecurityException) {
            // 沒有通知權限（Android 13 以上被拒絕）：不發
        }
    }
}

/** 定時檢查與通知上的按鈕（只收自己 App 發的，不對外開放） */
class SubNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            SubNotifier.ACTION_CHECK -> {
                val d = SubNotifier.current(ctx)
                SubNotifier.post(ctx, d, silent = false)
                SubNotifier.schedule(ctx, d.prefs)
            }
            SubNotifier.ACTION_RECORD, SubNotifier.ACTION_SKIP, SubNotifier.ACTION_ALL -> {
                val ids = intent.getLongArrayExtra("ids") ?: return
                val days = intent.getLongArrayExtra("days") ?: return
                val items = ids.indices.map { ids[it] to days[it] }
                val skip = intent.action == SubNotifier.ACTION_SKIP
                val vm = MoneyViewModel.live?.get()
                if (vm != null) {
                    // App 開著：交給畫面那邊記（有「復原」，存檔後通知跟著更新）
                    vm.handleSubNotice(items, skip)
                    return
                }
                val store = Store(ctx.filesDir)
                var d = store.load()
                for ((id, day) in items) d = (if (skip) d.withSubSkipped(id, day) else d.withSubRecorded(id, day)) ?: d
                store.save(d)
                SubNotifier.post(ctx, d, silent = true)
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
    }
}

/** 開機、App 更新後重排定時檢查；SUB_CHECK 也可以從外面叫（只會檢查、發提醒，不會改資料，畫面測試用） */
class SubBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val d = SubNotifier.current(ctx)
        if (intent.action == SubNotifier.ACTION_CHECK) SubNotifier.post(ctx, d, silent = false)
        SubNotifier.schedule(ctx, d.prefs)
    }
}
