package tw.moneybook.app.ui

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 自己畫的幾個 Material 圖示，避免引入很大的 icons-extended 套件 */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black),
        ).build()

    val ListAlt: ImageVector = icon(
        "list",
        "M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z",
    )
    val PieChart: ImageVector = icon(
        "pie",
        "M11,2v20c-5.07,-0.5 -9,-4.79 -9,-10s3.93,-9.5 9,-10zM13.03,2v8.99H22c-0.47,-4.74 -4.24,-8.52 -8.97,-8.99zM13.03,13.01V22c4.74,-0.47 8.5,-4.25 8.97,-8.99h-8.97z",
    )
    val Calendar: ImageVector = icon(
        "calendar",
        "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4 3.01,4.9 3.01,6L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM19,20H5V10h14V20zM9,14H7v-2h2V14zM13,14h-2v-2h2V14zM17,14h-2v-2h2V14zM9,18H7v-2h2V18zM13,18h-2v-2h2V18zM17,18h-2v-2h2V18z",
    )
    val Person: ImageVector = icon(
        "person",
        "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
    )
    val Wallet: ImageVector = icon(
        "wallet",
        "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    )
    val ChevronLeft: ImageVector = icon("left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z")
    val ChevronRight: ImageVector = icon("right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z")
    val ChevronUp: ImageVector = icon("up", "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z")
    val ChevronDown: ImageVector = icon("down", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6z")

    /** 單色線條圖示（線寬 2、圓角），顏色跟著 Icon 的 tint */
    private fun lineIcon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    val LCalendar: ImageVector = lineIcon("calendar", "M6,5h12a3,3 0 0 1 3,3v10a3,3 0 0 1 -3,3H6a3,3 0 0 1 -3,-3V8a3,3 0 0 1 3,-3z M3,10h18 M8,3v4 M16,3v4")
    val LTicket: ImageVector = lineIcon("ticket", "M4,6h16a1,1 0 0 1 1,1v2.5a2.5,2.5 0 0 0 0,5V17a1,1 0 0 1 -1,1H4a1,1 0 0 1 -1,-1v-2.5a2.5,2.5 0 0 0 0,-5V7a1,1 0 0 1 1,-1z M14,8.5v1 M14,11.5v1 M14,14.5v1")
    val LCheck: ImageVector = lineIcon("check", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M8,12.5l3,3 5,-6")
    val LRepeat: ImageVector = lineIcon("repeat", "M17,2l4,4 -4,4 M3,11V9a4,4 0 0 1 4,-4h14 M7,22l-4,-4 4,-4 M21,13v2a4,4 0 0 1 -4,4H3")
    val LPencil: ImageVector = lineIcon("pencil", "M4,20l1,-4L16.5,4.5a2,2 0 0 1 3,3L8,19z M14,7l3,3")
    val LArrowDown: ImageVector = lineIcon("down", "M12,4v16 M6,14l6,6 6,-6")
    val LFolder: ImageVector = lineIcon("folder", "M3,7a2,2 0 0 1 2,-2h4l2,2h8a2,2 0 0 1 2,2v9a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2z")
    val LTarget: ImageVector = lineIcon("target", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M12,7a5,5 0 1,0 0,10a5,5 0 1,0 0,-10z M12,10.8a1.2,1.2 0 1,0 0,2.4a1.2,1.2 0 1,0 0,-2.4z")
    val LPalette: ImageVector = lineIcon("palette", "M12,3a9,9 0 1,0 0,18c1.5,0 2,-1 1.5,-2.2 -0.6,-1.4 0.3,-2.8 1.8,-2.8H17a4,4 0 0 0 4,-4C21,6.5 17,3 12,3z M7.5,11h.01 M10,7.5h.01 M14.5,7.5h.01 M17,11h.01")
    val LSave: ImageVector = lineIcon("save", "M5,3h11l4,4v13a1,1 0 0 1 -1,1H5a1,1 0 0 1 -1,-1V4a1,1 0 0 1 1,-1z M8,3v6h8V3 M8,21v-7h8v7")
    val LRestore: ImageVector = lineIcon("restore", "M3,12a9,9 0 1,0 3,-6.7 M3,4v5h5 M12,8v4l3,2")
    val LExport: ImageVector = lineIcon("export", "M12,15V3 M7,8l5,-5 5,5 M5,15v4a2,2 0 0 0 2,2h10a2,2 0 0 0 2,-2v-4")
    val LImport: ImageVector = lineIcon("import", "M12,3v12 M7,10l5,5 5,-5 M5,15v4a2,2 0 0 0 2,2h10a2,2 0 0 0 2,-2v-4")
    val LSiren: ImageVector = lineIcon("siren", "M7,18v-6a5,5 0 0 1 10,0v6 M5,21h14v-3H5z M12,3v2 M4.5,6.5L6,8 M19.5,6.5L18,8")
    val LWarning: ImageVector = lineIcon("warning", "M12,3l10,18H2z M12,10v5 M12,18h.01")
    val LSearch: ImageVector = lineIcon("search", "M11,4a7,7 0 1,0 0,14a7,7 0 1,0 0,-14z M16.5,16.5L21,21")
    val LClock: ImageVector = lineIcon("clock", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M12,7v5l3,2")
    val LWallet: ImageVector = lineIcon("wallet", "M19,7V4a1,1 0 0 0 -1,-1H5a2,2 0 0 0 0,4h15a1,1 0 0 1 1,1v4h-3a2,2 0 0 0 0,4h3a1,1 0 0 0 1,-1v-2a1,1 0 0 0 -1,-1 M3,5v14a2,2 0 0 0 2,2h15a1,1 0 0 0 1,-1v-4")
    val LTag: ImageVector = lineIcon("tag", "M20.6,13.4l-7.2,7.2a2,2 0 0 1 -2.8,0L3,13V3h10l7.6,7.6a2,2 0 0 1 0,2.8z M7.5,7.5h.01")
    val LReceipt: ImageVector = lineIcon("receipt", "M5,3h14v18l-3,-2 -2,2 -2,-2 -2,2 -2,-2 -3,2z M9,8h6 M9,12h6 M9,16h3")
    val LCoin: ImageVector = lineIcon("coin", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M8.5,12h7")
    val LStar: ImageVector = lineIcon("star", "M12,3l2.7,5.6 6.1,0.9 -4.4,4.3 1,6.1L12,17l-5.5,2.9 1,-6.1L3.2,9.5l6.1,-0.9z")
    // 外幣：硬幣裡放各幣別的符號（見 fxCoinIcon）；手續費／優惠：手托著 % 硬幣；待請款：兩枚硬幣
    val LCoinYen: ImageVector = lineIcon("coin_yen", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M9.6,7.7 L12,12 L14.4,7.7 M12,12 V16.6 M9.8,12.6 H14.2 M9.8,14.6 H14.2")
    val LCoinDollar: ImageVector = lineIcon("coin_dollar", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M14.2,9.3 C14.1,8.2 13.2,7.6 12,7.6 C10.7,7.6 9.8,8.3 9.8,9.4 C9.8,10.6 10.9,11 12,11.4 C13.2,11.8 14.3,12.2 14.3,13.5 C14.3,14.7 13.3,15.4 12,15.4 C10.7,15.4 9.8,14.8 9.7,13.6 M12,6.2 V7.6 M12,15.4 V16.8")
    val LCoinEuro: ImageVector = lineIcon("coin_euro", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M14.6,9 C14,8.1 13.1,7.6 12,7.6 C10,7.6 8.8,9.5 8.8,12 C8.8,14.5 10,16.4 12,16.4 C13.1,16.4 14,15.9 14.6,15 M7.6,11 H12.8 M7.6,13 H12.8")
    val LCoinPound: ImageVector = lineIcon("coin_pound", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M14.2,8.4 C13.7,7.8 12.9,7.5 12,7.5 C10.6,7.5 9.8,8.5 9.8,9.8 V14.8 C9.8,15.6 9.3,16.1 8.5,16.4 H14.6 M8.4,12.2 H12.6")
    val LCoinWon: ImageVector = lineIcon("coin_won", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M7.6,8 L9.5,16 L12,9.4 L14.5,16 L16.4,8 M7.8,11 H16.2 M8.6,13.4 H15.4")
    val LCoinBaht: ImageVector = lineIcon("coin_baht", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M10.4,7.7 V16.3 M10.4,7.7 H12.7 C13.8,7.7 14.3,8.5 14.3,9.4 C14.3,10.4 13.7,11.1 12.6,11.1 H10.4 M12.6,11.1 C13.9,11.1 14.6,11.9 14.6,13.2 C14.6,14.5 13.8,16.3 12.5,16.3 H10.4 M11.7,6.3 V7.7 M11.7,16.3 V17.7")
    val LCoinOther: ImageVector = lineIcon("coin_other", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M12,9.6a2.4,2.4 0 1,0 0.01,0z M9,9L10.4,10.4 M15,9L13.6,10.4 M9,15L10.4,13.6 M15,15L13.6,13.6")
    val LFee: ImageVector = lineIcon("fee", "M2.5,13H5.5V20H2.5z M5.5,14.5L9,12.8H13L14.6,14.2L21.5,9.6L21.8,11L16.6,16.6L9.5,19.6H5.5 M14.5,2a4.2,4.2 0 1,0 0,8.4a4.2,4.2 0 1,0 0,-8.4z M12.7,8L16.3,4.4 M12.8,4.9h.01 M16.2,7.5h.01")
    val LCoinTwo: ImageVector = lineIcon("coin_two", "M8.8,3.2a5.6,5.6 0 1,0 0,11.2a5.6,5.6 0 1,0 0,-11.2z M15.6,9.8a5.6,5.6 0 1,0 0,11.2a5.6,5.6 0 1,0 0,-11.2z M10.10,7.30 C9.80,6.85 9.35,6.60 8.80,6.60 C7.80,6.60 7.20,7.55 7.20,8.80 C7.20,10.05 7.80,11.00 8.80,11.00 C9.35,11.00 9.80,10.75 10.10,10.30 M6.60,8.30 H9.20 M6.60,9.30 H9.20 M14.40,13.25 L15.60,15.40 L16.80,13.25 M15.60,15.40 V17.70 M14.50,15.70 H16.70 M14.50,16.70 H16.70")
    val LBook: ImageVector = lineIcon("book", "M5,3h13a1,1 0 0 1 1,1v16a1,1 0 0 1 -1,1H6a2,2 0 0 1 -2,-2V4a1,1 0 0 1 1,-1z M8,3v18 M11,8h5")
}
