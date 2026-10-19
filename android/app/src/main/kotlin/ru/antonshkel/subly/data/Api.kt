package ru.antonshkel.subly.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class ApiError(val code: String, override val message: String, val status: Int = 0) : Exception(message)

val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

/** Тонкий клиент к серверу Subly. Все ошибки превращаются в ApiError с понятным текстом. */
class Api(private val baseUrl: () -> String, private val token: () -> String?) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }
        expectSuccess = false
    }

    private fun url(path: String) = baseUrl().trimEnd('/') + path

    private suspend inline fun call(block: () -> HttpResponse): HttpResponse {
        val resp = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiError("network", "Нет связи с сервером. Проверьте интернет.")
        }
        if (!resp.status.isSuccess()) {
            val err = runCatching { AppJson.decodeFromString<ErrorDto>(resp.bodyAsText()) }.getOrNull()
            throw ApiError(err?.error ?: "http", err?.message ?: "Сервер ответил ошибкой ${resp.status.value}", resp.status.value)
        }
        return resp
    }

    private fun HttpRequestBuilder.auth() { token()?.let { bearerAuth(it) } }
    private fun HttpRequestBuilder.jsonBody(body: Any) { contentType(ContentType.Application.Json); setBody(body) }

    suspend fun register(c: Credentials): AuthResponse = call { client.post(url("/api/auth/register")) { jsonBody(c) } }.body()
    suspend fun login(c: Credentials): AuthResponse = call { client.post(url("/api/auth/login")) { jsonBody(c) } }.body()
    suspend fun me(): User = call { client.get(url("/api/me")) { auth() } }.body()
    suspend fun deleteAccount() { call { client.delete(url("/api/me")) { auth() } } }

    suspend fun subscriptions(): List<Subscription> = call { client.get(url("/api/subscriptions")) { auth() } }.body()
    suspend fun create(s: SubscriptionInput): Subscription = call { client.post(url("/api/subscriptions")) { auth(); jsonBody(s) } }.body()
    suspend fun update(id: Long, s: SubscriptionInput): Subscription = call { client.put(url("/api/subscriptions/$id")) { auth(); jsonBody(s) } }.body()
    suspend fun delete(id: Long) { call { client.delete(url("/api/subscriptions/$id")) { auth() } } }

    suspend fun insights(): List<Insight> = call { client.get(url("/api/insights")) { auth() } }.body()
    suspend fun exportCsv(): String = call { client.get(url("/api/export.csv")) { auth() } }.bodyAsText()

    suspend fun plans(): PlansResponse = call { client.get(url("/api/billing/plans")) }.body()
    suspend fun checkout(plan: String): CheckoutResponse = call { client.post(url("/api/billing/checkout")) { auth(); jsonBody(CheckoutRequest(plan)) } }.body()
    suspend fun refreshBilling(): User = call { client.post(url("/api/billing/refresh")) { auth() } }.body()
}
