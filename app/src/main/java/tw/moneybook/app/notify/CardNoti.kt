package tw.moneybook.app.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tw.moneybook.app.CapturedCodec
import tw.moneybook.app.CapturedNoti
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Store
import tw.moneybook.app.addCaptured
import tw.moneybook.app.pruneCaptured
import java.io.File

/**
 * 收到的通知（只有勾選的 App 的）：存在手機自己的檔案裡（不跟著備份），原文留 7 天。
 * 第一步只收集、給使用者看，還不會記帳；之後讀欄位、教範本都用這裡的原文。
 */
object CardNotiLog {
    private const val FILE = "card_notis.json"
    private val lock = Any()
    private val _version = MutableStateFlow(0)

    /** 每次有新的通知或清空就加 1，畫面看到變了就重新讀 */
    val version: StateFlow<Int> = _version

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun load(ctx: Context): List<CapturedNoti> = synchronized(lock) {
        val f = file(ctx)
        val list = if (f.exists()) CapturedCodec.decode(f.readText()) else emptyList()
        pruneCaptured(list, System.currentTimeMillis())
    }

    fun add(ctx: Context, n: CapturedNoti) {
        synchronized(lock) {
            val f = file(ctx)
            val list = if (f.exists()) CapturedCodec.decode(f.readText()) else emptyList()
            f.writeText(CapturedCodec.encode(addCaptured(list, n, System.currentTimeMillis())))
        }
        _version.value = _version.value + 1
    }

    fun clear(ctx: Context) {
        synchronized(lock) { file(ctx).delete() }
        _version.value = _version.value + 1
    }
}

/** 使用者有沒有打開記帳本的通知存取權 */
fun cardNotiEnabled(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

fun cardNotiComponent(ctx: Context) = ComponentName(ctx, CardNotiListener::class.java)

/** 勾選要讀的 App（套件名稱 → 名稱）：App 開著用畫面上的資料，沒開就讀存檔（檔案沒變就用上次讀的） */
internal object CardNotiApps {
    @Volatile private var cache: Map<String, String>? = null
    @Volatile private var stamp = -1L

    fun selected(ctx: Context): Map<String, String> {
        MoneyViewModel.live?.get()?.let { vm -> return vm.data.notiApps.associate { it.pkg to it.label } }
        val f = File(ctx.filesDir, "moneybook_v2.json")
        val m = f.lastModified()
        val c = cache
        if (c != null && m == stamp) return c
        val fresh = Store(ctx.filesDir).load().notiApps.associate { it.pkg to it.label }
        cache = fresh
        stamp = m
        return fresh
    }
}

/**
 * 讀通知的服務（使用者在系統設定打開「通知存取權」後由系統啟動）。
 * 只看勾選的 App：其他 App 的通知直接丟掉、不讀內容、不存。不連網。
 */
class CardNotiListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: CardNotiListener? = null
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val apps = CardNotiApps.selected(this)
            val label = apps[sbn.packageName] ?: return
            val n = sbn.notification ?: return
            // 一組通知的摘要那則（「3 則新通知」）沒有內容，跳過
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            // 展開後的長內容優先（國泰的通知有好幾行），沒有就用一般內容或多行清單
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
                ?: "").trim()
            if (title.isEmpty() && text.isEmpty()) return
            CardNotiLog.add(this, CapturedNoti(sbn.postTime * 1000 + (text.hashCode() and 0x3ff), sbn.packageName, label, title, text, sbn.postTime))
        } catch (_: Exception) {
        }
    }

    /** 通知欄裡現在有通知的 App（選 App 時排前面；只拿套件名稱，不讀內容） */
    fun activePackages(): List<String> = try {
        activeNotifications?.map { it.packageName }?.distinct().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}
