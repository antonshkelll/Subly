package ru.antonshkel.subly

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.antonshkel.subly.data.AppJson
import ru.antonshkel.subly.data.Cache
import ru.antonshkel.subly.data.Period
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.work.ReminderWorker
import java.io.File
import java.time.LocalDate

/** Настоящее приложение, только WorkManager — тестовый. */
class TestApp : SublyApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        super.onCreate()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class AppTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun appStartsAndShowsOnboarding() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(!activity.isFinishing)
    }

    @Test fun reminderIsSentOnceForTomorrowsCharge() = runBlocking {
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val later = LocalDate.now().plusDays(10).toString()
        val subs = listOf(
            Subscription(7, "Кинопоиск", 299_00, Period.MONTH, tomorrow, tomorrow, 299_00, "video", "#FF6A00", 1, false),
            Subscription(8, "Облако", 149_00, Period.MONTH, later, later, 149_00, "cloud", "#007AFF", 1, false),
        )
        File(context.filesDir, "cache.json").writeText(AppJson.encodeToString(Cache.serializer(), Cache(subscriptions = subs)))
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val nm = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, nm.allNotifications.size)
        assertTrue(shadowOf(nm.allNotifications[0]).contentTitle.contains("Кинопоиск"))

        // Повторный запуск в тот же день не дублирует уведомление
        TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork()
        assertEquals(1, nm.allNotifications.size)
    }
}
