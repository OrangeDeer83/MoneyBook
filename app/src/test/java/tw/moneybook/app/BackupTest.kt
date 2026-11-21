package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 備份檔：記帳資料＋刷卡待確認（還沒按記的那幾筆也跟著換手機） */
class BackupTest {
    private val base = Defaults.create()

    private fun pend(id: Long, merchant: String, amount: Long, state: Int = PendState.WAIT) =
        CardPend(id, listOf(NotiSrc(id, "esun", "玉山Wallet", "", "")), amount, "", merchant, merchant.isNotEmpty(), "", 20406L, 600, 1_000L + id, state = state)

    @Test
    fun pendingRidesAlongWithTheBackup() {
        val pends = listOf(pend(1, "全聯", 350), pend(2, "", 450), pend(3, "台灣中油", 1, PendState.NOT))
        val text = BackupCodec.encode(base, pends)
        // 記帳資料照樣讀得出來
        assertEquals(Codec.decode(Codec.encode(base)), Codec.decode(text))
        // 待確認一筆不少
        assertEquals(pends, BackupCodec.pends(text))
        // 沒有待確認也寫空的：還原時清掉手機上的
        assertEquals(emptyList<CardPend>(), BackupCodec.pends(BackupCodec.encode(base, emptyList())))
        // 舊的備份檔沒有這個欄位：不動手機上的
        assertNull(BackupCodec.pends(Codec.encode(base)))
        assertNull(BackupCodec.pends("not json"))
        assertTrue(text.contains("\"cardPending\""))
    }
}
