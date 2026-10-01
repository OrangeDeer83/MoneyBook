package tw.moneybook.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.moneybook.app.ui.MoneyApp
import tw.moneybook.app.ui.MoneyTheme

class MainActivity : ComponentActivity() {
    /** 桌面小工具點下去要開哪一頁：add（記一筆）、reimb（報銷） */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = intent.getStringExtra("open")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = intent?.getStringExtra("open")
        setContent {
            val vm: MoneyViewModel = viewModel()
            val prefs = vm.data.prefs
            val systemDark = isSystemInDarkTheme()
            val dark = when (prefs.dark) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            // 讓狀態列圖示的深淺跟著 App 的深色設定走
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MoneyTheme(prefs.palette, dark) {
                MoneyApp(vm, openRequest.value) { openRequest.value = null }
            }
        }
    }
}
