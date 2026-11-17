package tw.moneybook.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.PendState
import tw.moneybook.app.R
import tw.moneybook.app.Store
import tw.moneybook.app.guessCat
import tw.moneybook.app.markDone
import tw.moneybook.app.mascotTitle
import tw.moneybook.app.pendNotice
import tw.moneybook.app.pendSub
import tw.moneybook.app.pendSubTxn
import tw.moneybook.app.pendTxn
import tw.moneybook.app.PendKind
import tw.moneybook.app.Txn
import tw.moneybook.app.ensureRefundCat
import tw.moneybook.app.pendRefundTxn
import tw.moneybook.app.pendRefundOf
import tw.moneybook.app.pendInstTxns
import tw.moneybook.app.isInst
import tw.moneybook.app.resolveCard
import tw.moneybook.app.sortTxns
import tw.moneybook.app.ui.Mood
import tw.moneybook.app.ui.mascotDefaultName
import tw.moneybook.app.ui.mascotRes

/**
 * 刷卡有待確認時，吉祥物發一則手機通知（同一時間只留一則）。
 * 收到刷卡通知後等 1 分鐘再發：同一筆的第二則通知（例如 Samsung Wallet）趕得上合併。
 * 只有一筆、卡和分類都有時，通知上可以直接「記好了／不是消費」；其他點了打開首頁。
 */
object CardPendNotifier {
    const val CHANNEL = "cardpend"
    const val NOTI_ID = 4301
    const val ACTION_RECORD = "tw.moneybook.app.PEND_RECORD"
    const val ACTION_NOT = "tw.moneybook.app.PEND_NOT"
    /** 收到刷卡通知後等多久才發 */
    const val DELAY_MS = 60_000L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val token = Any()

    /** 收到刷卡通知：1 分鐘後再看要不要發（這 1 分鐘內又收到的，從最後一則重新算） */
    fun later(ctx: Context) {
        val app = ctx.applicationContext
        handler.removeCallbacksAndMessages(token)
        handler.postAtTime({ post(app, SubNotifier.current(app), silent = false) }, token, android.os.SystemClock.uptimeMillis() + DELAY_MS)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "刷卡待確認", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "讀到刷卡通知、有消費等你確認時，由吉祥物提醒"
            }
        )
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTI_ID)

    private fun isShown(ctx: Context): Boolean = try {
        ctx.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == NOTI_ID } == true
    } catch (_: Exception) {
        false
    }

    /** 通知還掛在手機上時，照新的待確認更新（記好了就收掉）；不會再響一次 */
    fun refreshShown(ctx: Context) {
        if (isShown(ctx)) post(ctx, SubNotifier.current(ctx), silent = true)
    }

    private fun mascotIcon(ctx: Context, d: AppData): Bitmap? {
        if (d.prefs.mascot == "none") return null
        return try {
            val b = BitmapFactory.decodeResource(ctx.resources, mascotRes(d.prefs.mascot, Mood.HAPPY)) ?: return null
            Bitmap.createScaledBitmap(b, 192, 192, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun action(ctx: Context, code: Int, act: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, CardPendReceiver::class.java).setAction(act).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 照現在的待確認發（或更新、收掉）通知；silent＝更新時不再響 */
    fun post(ctx: Context, d: AppData, silent: Boolean) {
        val n = if (d.prefs.cardNotify) d.pendNotice(CardPendLog.load(ctx), d.prefs.mascotTitle(::mascotDefaultName)) else null
        if (n == null || !SubNotifier.allowed(ctx)) {
            cancel(ctx)
            return
        }
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 3,
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
        mascotIcon(ctx, d)?.let { b.setLargeIcon(it) }
        if (n.lines.isNotEmpty()) {
            b.setStyle(NotificationCompat.InboxStyle().also { st -> n.lines.forEach { st.addLine(it) }; st.setSummaryText("點一下打開記帳本") })
        }
        n.single?.let {
            b.addAction(0, "記好了", action(ctx, 20, ACTION_RECORD, it))
            b.addAction(0, "不是消費", action(ctx, 21, ACTION_NOT, it))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(NOTI_ID, b.build())
        } catch (_: SecurityException) {
            // 沒有通知權限（Android 13 以上被拒絕）：不發
        }
    }
}

/** 通知上的「記好了」「不是消費」（只收自己 App 發的，不對外開放） */
class CardPendReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        val record = intent.action == CardPendNotifier.ACTION_RECORD
        if (id == 0L || (!record && intent.action != CardPendNotifier.ACTION_NOT)) return
        val vm = MoneyViewModel.live?.get()
        if (vm != null) {
            // App 開著：交給畫面那邊記（有「復原」）
            vm.handlePendNotice(id, record)
            CardPendNotifier.refreshShown(ctx)
            return
        }
        val p = CardPendLog.load(ctx).firstOrNull { it.id == id && it.state == PendState.WAIT }
        if (p == null) {
            CardPendNotifier.refreshShown(ctx)
            return
        }
        if (!record) {
            CardPendLog.update(ctx) { list -> list.map { if (it.id == id) it.copy(state = PendState.NOT) else it } }
            CardPendNotifier.refreshShown(ctx)
            return
        }
        val store = Store(ctx.filesDir)
        var d = store.load()
        val card = d.resolveCard(p) ?: return
        val sm = d.pendSub(p, card)?.takeIf { !it.done && it.exact }
        // 退款記成不算收入（連到原本那筆）、分期記成好幾期、預先授權記成預估（跟按「記」一樣）
        val list: List<Txn> = when {
            p.kind == PendKind.REFUND -> {
                val (d2, cat) = d.ensureRefundCat()
                d = d2
                listOfNotNull(d.pendRefundTxn(p, d.nextId, d.currentBook.id, card, cat, d.pendRefundOf(p, card)?.id ?: 0L))
            }
            sm != null -> listOfNotNull(d.pendSubTxn(p, sm, d.nextId))
            p.isInst -> d.pendInstTxns(p, d.nextId, d.currentBook.id, card, d.guessCat(p.merchant, card))
            else -> listOfNotNull(d.pendTxn(p, d.nextId, d.currentBook.id, card, d.guessCat(p.merchant, card))?.let { if (p.kind == PendKind.PREAUTH) it.copy(est = true) else it })
        }
        val t = list.firstOrNull() ?: return
        d = d.copy(
            txns = sortTxns(d.txns + list), nextId = d.nextId + list.size,
            subs = if (sm != null) d.subs.map { if (it.id == sm.sub.id) it.markDone(sm.day) else it } else d.subs,
        )
        store.save(d)
        CardPendLog.update(ctx) { list -> list.map { if (it.id == id) it.copy(state = PendState.DONE, txnId = t.id) else it } }
        CardPendNotifier.post(ctx, d, silent = true)
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
