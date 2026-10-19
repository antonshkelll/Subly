package ru.antonshkel.subly

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.antonshkel.subly.data.ThemeMode
import ru.antonshkel.subly.ui.AppViewModel
import ru.antonshkel.subly.ui.Effect
import ru.antonshkel.subly.ui.SublyRoot
import ru.antonshkel.subly.ui.theme.SublyTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = when (state.prefs.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            LaunchedEffect(Unit) {
                vm.effectFlow.collect { e ->
                    when (e) {
                        is Effect.OpenUrl -> openUrl(e.url)
                        is Effect.ShareCsv -> shareCsv(e.csv)
                        Effect.AskNotifications -> askNotifications()
                    }
                }
            }
            SublyTheme(dark) { SublyRoot(state, vm, ::openUrl) }
        }
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.checkBilling()          // вернулись из браузера после оплаты
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.data?.scheme == "subly" && intent.data?.host == "billing") vm.checkBilling(force = true)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            vm.message("Не нашлось браузера, чтобы открыть ссылку")
        }
    }

    private fun shareCsv(csv: String) {
        val dir = File(cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "subly.csv").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Экспорт подписок"))
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
