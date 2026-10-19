package ru.antonshkel.subly.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Единственный источник данных для экранов. Сначала показываем кеш с телефона (мгновенный старт
 * даже без сети), затем обновляем с сервера. Каждое изменение сразу сохраняется в кеш.
 */
class Repository(context: Context, private val api: Api) {
    private val file = File(context.filesDir, "cache.json")
    private val _cache = MutableStateFlow(Cache())
    val cache: StateFlow<Cache> = _cache.asStateFlow()

    suspend fun loadCache(): Cache = withContext(Dispatchers.IO) {
        val c = runCatching { AppJson.decodeFromString<Cache>(file.readText()) }.getOrDefault(Cache())
        _cache.value = c
        c
    }

    private suspend fun save(transform: (Cache) -> Cache) {
        _cache.update(transform)
        val snapshot = _cache.value
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "cache.json.tmp")
            tmp.writeText(AppJson.encodeToString(Cache.serializer(), snapshot))
            tmp.renameTo(file)            // атомарная замена: не останемся с половиной файла
        }
    }

    suspend fun signIn(resp: AuthResponse) = save { Cache(user = resp.user) }
    suspend fun clear() { _cache.value = Cache(); withContext(Dispatchers.IO) { file.delete() } }

    suspend fun refresh() {
        val user = api.me()
        val subs = api.subscriptions()
        save { Cache(user, subs) }
    }

    suspend fun setUser(user: User) = save { it.copy(user = user) }

    suspend fun create(input: SubscriptionInput): Subscription =
        api.create(input).also { s -> save { c -> c.copy(subscriptions = (c.subscriptions + s).sortedBy { it.nextCharge }) } }

    suspend fun update(id: Long, input: SubscriptionInput): Subscription =
        api.update(id, input).also { s -> save { c -> c.copy(subscriptions = c.subscriptions.map { if (it.id == id) s else it }.sortedBy { it.nextCharge }) } }

    suspend fun delete(id: Long) {
        api.delete(id)
        save { c -> c.copy(subscriptions = c.subscriptions.filterNot { it.id == id }) }
    }
}
