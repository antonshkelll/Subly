package ru.antonshkel.subly.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.antonshkel.subly.MainActivity
import ru.antonshkel.subly.R
import ru.antonshkel.subly.SublyApp
import ru.antonshkel.subly.domain.daysLeft
import ru.antonshkel.subly.domain.next
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.domain.whenText
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Раз в день проверяет подписки из кеша и напоминает о списаниях. Работает без сети:
 * все даты считаются на телефоне. WorkManager сам учитывает режим экономии батареи.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SublyApp
        if (!app.settings.current().reminders || !canNotify()) return Result.success()
        val subs = app.repository.loadCache().subscriptions
        val today = LocalDate.now()
        val already = app.settings.notified()

        val due = subs.filter { s ->
            val left = s.daysLeft(today)
            left in 0..s.remindDaysBefore.toLong() && "${s.id}:${s.next(today)}" !in already
        }
        if (due.isEmpty()) return Result.success()

        val nm = NotificationManagerCompat.from(applicationContext)
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        due.forEach { s ->
            val left = s.daysLeft(today)
            val title = if (s.isTrial) "Заканчивается пробный период ${s.name}" else "${s.name}: списание ${whenText(left)}"
            val text = if (s.isTrial) "${whenText(left).replaceFirstChar { it.uppercase() }} спишут ${rub(s.priceKopecks)}. Отмените, если сервис не нужен."
            else "Спишется ${rub(s.priceKopecks)}. Проверьте, хватает ли денег на карте."
            val n = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            nm.notify(s.id.toInt(), n)
        }
        app.settings.addNotified(due.map { "${it.id}:${it.next(today)}" })
        return Result.success()
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL = "reminders"
        private const val NAME = "daily-reminders"

        /** Ежедневная проверка, первая — около 10:00, чтобы не будить человека ночью. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(10, 0)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
