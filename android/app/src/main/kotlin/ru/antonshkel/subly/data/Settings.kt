package ru.antonshkel.subly.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.antonshkel.subly.BuildConfig

private val Context.store: DataStore<Preferences> by preferencesDataStore("subly")

enum class ThemeMode(val title: String) { SYSTEM("Авто"), LIGHT("Светлая"), DARK("Тёмная") }

data class Prefs(
    val token: String? = null,
    val serverUrl: String = BuildConfig.API_URL,
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminders: Boolean = true,
)

class Settings(private val context: Context) {
    private object K {
        val token = stringPreferencesKey("token")
        val server = stringPreferencesKey("server")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val reminders = booleanPreferencesKey("reminders")
        val notified = stringSetPreferencesKey("notified")
    }

    val prefs: Flow<Prefs> = context.store.data.map { p ->
        Prefs(
            token = p[K.token],
            serverUrl = p[K.server] ?: BuildConfig.API_URL,
            onboardingDone = p[K.onboarding] ?: false,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            reminders = p[K.reminders] ?: true,
        )
    }

    suspend fun current() = prefs.first()

    suspend fun setToken(token: String?) = context.store.edit { if (token == null) it.remove(K.token) else it[K.token] = token }
    suspend fun setServer(url: String) = context.store.edit { it[K.server] = url.trim().trimEnd('/') }
    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }
    suspend fun setTheme(mode: ThemeMode) = context.store.edit { it[K.theme] = mode.name }
    suspend fun setReminders(on: Boolean) = context.store.edit { it[K.reminders] = on }

    /** Ключи «подписка+дата», о которых уже напомнили: одно напоминание на одно списание. */
    suspend fun notified(): Set<String> = context.store.data.first()[K.notified] ?: emptySet()
    suspend fun addNotified(keys: Collection<String>) = context.store.edit {
        // Ключ — «id:дата списания». Записи о списаниях старше недели больше не нужны:
        // так набор не растёт, а свежие напоминания не теряются.
        val weekAgo = java.time.LocalDate.now().minusDays(7).toString()
        it[K.notified] = ((it[K.notified] ?: emptySet()) + keys).filter { k -> k.substringAfter(':') >= weekAgo }.toSet()
    }
}
