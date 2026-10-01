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
    val LWallet: ImageVector = lineIcon("wallet", "M19,7V4a1,1 0 0 0 -1,-1H5a2,2 0 0 0 0,4h15a1,1 0 0 1 1,1v4h-3a2,2 0 0 0 0,4h3a1,1 0 0 0 1,-1v-2a1,1 0 0 0 -1,-1 M3,5v14a2,2 0 0 0 2,2h15a1,1 0 0 0 1,-1v-4")
    val LTag: ImageVector = lineIcon("tag", "M20.6,13.4l-7.2,7.2a2,2 0 0 1 -2.8,0L3,13V3h10l7.6,7.6a2,2 0 0 1 0,2.8z M7.5,7.5h.01")
    val LReceipt: ImageVector = lineIcon("receipt", "M5,3h14v18l-3,-2 -2,2 -2,-2 -2,2 -2,-2 -3,2z M9,8h6 M9,12h6 M9,16h3")
    val LCoin: ImageVector = lineIcon("coin", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M8.5,12h7")
    val LStar: ImageVector = lineIcon("star", "M12,3l2.7,5.6 6.1,0.9 -4.4,4.3 1,6.1L12,17l-5.5,2.9 1,-6.1L3.2,9.5l6.1,-0.9z")
    val LBook: ImageVector = lineIcon("book", "M5,3h13a1,1 0 0 1 1,1v16a1,1 0 0 1 -1,1H6a2,2 0 0 1 -2,-2V4a1,1 0 0 1 1,-1z M8,3v18 M11,8h5")
}
