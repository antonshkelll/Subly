package ru.antonshkel.subly

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.runBlocking
import ru.antonshkel.subly.data.Api
import ru.antonshkel.subly.data.Repository
import ru.antonshkel.subly.data.Settings
import ru.antonshkel.subly.work.ReminderWorker

/** Простейший DI: все зависимости создаются один раз здесь. Для приложения такого размера Hilt не нужен. */
class SublyApp : Application() {
    lateinit var settings: Settings
    lateinit var api: Api
    lateinit var repository: Repository

    @Volatile private var token: String? = null
    @Volatile private var server: String = BuildConfig.API_URL

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        // Читаем настройки один раз при старте — это быстрый локальный файл.
        runBlocking { settings.current() }.let { token = it.token; server = it.serverUrl }
        api = Api(baseUrl = { server }, token = { token })
        repository = Repository(this, api)
        createChannel()
        ReminderWorker.schedule(this)
    }

    fun updateSession(token: String?, server: String) {
        this.token = token
        this.server = server
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(ReminderWorker.CHANNEL, getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
