package ru.antonshkel.subly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antonshkel.subly.SublyApp
import ru.antonshkel.subly.data.ApiError
import ru.antonshkel.subly.data.Credentials
import ru.antonshkel.subly.data.Insight
import ru.antonshkel.subly.data.PlansResponse
import ru.antonshkel.subly.data.Prefs
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.data.SubscriptionInput
import ru.antonshkel.subly.data.ThemeMode
import ru.antonshkel.subly.data.User

enum class Phase { LOADING, ONBOARDING, AUTH, MAIN }

data class UiState(
    val phase: Phase = Phase.LOADING,
    val prefs: Prefs = Prefs(),
    val user: User? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val insights: List<Insight>? = null,
    val insightsError: String? = null,
    val plans: PlansResponse? = null,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val authError: String? = null,
    val message: String? = null,
    val checkoutPending: Boolean = false,
)

sealed interface Effect {
    data class OpenUrl(val url: String) : Effect
    data class ShareCsv(val csv: String) : Effect
    data object AskNotifications : Effect
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val sublyApp = app as SublyApp
    private val repo = sublyApp.repository
    private val api = sublyApp.api
    private val settings = sublyApp.settings

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val effects = Channel<Effect>(Channel.BUFFERED)
    val effectFlow = effects.receiveAsFlow()

    private var messageJob: Job? = null
    private var billingCheckAfterStart = false

    init {
        viewModelScope.launch {
            val prefs = settings.current()
            val cache = repo.loadCache()
            _state.update {
                it.copy(
                    prefs = prefs, user = cache.user, subscriptions = cache.subscriptions,
                    phase = when {
                        !prefs.onboardingDone -> Phase.ONBOARDING
                        prefs.token == null -> Phase.AUTH
                        else -> Phase.MAIN
                    },
                )
            }
            if (prefs.token != null) refresh(silent = true)
            // Приложение открыли ссылкой subly://billing до того, как прочитались настройки.
            if (billingCheckAfterStart) checkBilling(force = true)
        }
        viewModelScope.launch {
            repo.cache.collect { c -> _state.update { it.copy(user = c.user ?: it.user, subscriptions = c.subscriptions) } }
        }
    }

    fun message(text: String) {
        messageJob?.cancel()
        _state.update { it.copy(message = text) }
        messageJob = viewModelScope.launch { delay(3200); _state.update { it.copy(message = null) } }
    }

    private fun handle(e: Throwable) {
        if (e is ApiError && e.status == 401) { logout(); message("Сессия истекла, войдите снова"); return }
        message(e.message ?: "Что-то пошло не так")
    }

    fun finishOnboarding() = viewModelScope.launch {
        settings.setOnboardingDone()
        _state.update { it.copy(phase = Phase.AUTH, prefs = it.prefs.copy(onboardingDone = true)) }
    }

    fun auth(email: String, password: String, register: Boolean) = viewModelScope.launch {
        _state.update { it.copy(busy = true, authError = null) }
        try {
            val c = Credentials(email.trim(), password)
            val resp = if (register) api.register(c) else api.login(c)
            settings.setToken(resp.token)
            sublyApp.updateSession(resp.token, _state.value.prefs.serverUrl)
            repo.signIn(resp)
            _state.update { it.copy(phase = Phase.MAIN, busy = false, prefs = it.prefs.copy(token = resp.token), insights = null) }
            refresh(silent = true)
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, authError = e.message ?: "Не получилось войти") }
        }
    }

    fun logout() = viewModelScope.launch {
        settings.setToken(null)
        sublyApp.updateSession(null, _state.value.prefs.serverUrl)
        repo.clear()
        // message сохраняем: handle() и deleteAccount() показывают его сразу после вызова logout().
        _state.update { UiState(phase = Phase.AUTH, prefs = it.prefs.copy(token = null), message = it.message) }
    }

    fun deleteAccount() = viewModelScope.launch {
        try {
            api.deleteAccount()
            logout()
            message("Аккаунт и все данные удалены")
        } catch (e: Exception) { handle(e) }
    }

    fun refresh(silent: Boolean = false) = viewModelScope.launch {
        if (!silent) _state.update { it.copy(refreshing = true) }
        try {
            repo.refresh()
            if (_state.value.user?.isPro == true) loadInsights()
        } catch (e: Exception) {
            // Без сети тихо остаёмся на кеше; при ручном обновлении — говорим, в чём дело.
            if (!silent || (e is ApiError && e.status == 401)) handle(e)
        } finally {
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun save(id: Long?, input: SubscriptionInput, onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            val firstOne = _state.value.subscriptions.isEmpty()
            if (id == null) repo.create(input) else repo.update(id, input)
            onDone()
            message(if (id == null) "«${input.name}» добавлена" else "Изменения сохранены")
            _state.update { it.copy(insights = null) }
            if (firstOne && _state.value.prefs.reminders) effects.send(Effect.AskNotifications)
            if (_state.value.user?.isPro == true) loadInsights()
        } catch (e: Exception) {
            handle(e)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch {
        try {
            repo.delete(id)
            onDone()
            message("Подписка удалена")
            if (_state.value.user?.isPro == true) loadInsights()
        } catch (e: Exception) { handle(e) }
    }

    fun loadInsights() = viewModelScope.launch {
        try {
            val list = api.insights()
            _state.update { it.copy(insights = list, insightsError = null) }
        } catch (e: Exception) {
            _state.update { it.copy(insightsError = e.message) }
        }
    }

    fun loadPlans() = viewModelScope.launch {
        runCatching { api.plans() }.onSuccess { p -> _state.update { it.copy(plans = p) } }
    }

    fun checkout(plan: String, onActivated: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            val r = api.checkout(plan)
            when {
                r.activated -> {
                    repo.setUser(api.me())
                    onActivated()
                    loadInsights()
                }
                r.confirmationUrl != null -> {
                    _state.update { it.copy(checkoutPending = true) }
                    effects.send(Effect.OpenUrl(r.confirmationUrl))
                }
            }
        } catch (e: Exception) {
            handle(e)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    /** Пользователь вернулся из браузера (или по ссылке subly://billing) — проверяем оплату. */
    fun checkBilling(force: Boolean = false) {
        if (!force && !_state.value.checkoutPending) return
        if (_state.value.phase == Phase.LOADING) { billingCheckAfterStart = true; return }
        if (_state.value.prefs.token == null) return
        viewModelScope.launch {
            try {
                val wasPro = _state.value.user?.isPro == true
                val user = api.refreshBilling()
                repo.setUser(user)
                if (user.isPro && !wasPro) {
                    _state.update { it.copy(checkoutPending = false) }
                    message("Добро пожаловать в Subly Pro!")
                    loadInsights()
                } else if (force) {
                    message(if (user.isPro) "Pro активен" else "Оплата пока не поступила")
                }
            } catch (e: Exception) { if (force) handle(e) }
        }
    }

    fun exportCsv() = viewModelScope.launch {
        try { effects.send(Effect.ShareCsv(api.exportCsv())) } catch (e: Exception) { handle(e) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch {
        settings.setTheme(mode); _state.update { it.copy(prefs = it.prefs.copy(theme = mode)) }
    }

    fun setReminders(on: Boolean) = viewModelScope.launch {
        settings.setReminders(on); _state.update { it.copy(prefs = it.prefs.copy(reminders = on)) }
        if (on) effects.send(Effect.AskNotifications)
    }

    fun setServer(url: String) = viewModelScope.launch {
        val clean = url.trim().trimEnd('/').let { if (it.startsWith("http")) it else "https://$it" }
        settings.setServer(clean)
        sublyApp.updateSession(_state.value.prefs.token, clean)
        _state.update { it.copy(prefs = it.prefs.copy(serverUrl = clean), plans = null) }
        message("Сервер: $clean")
    }
}
