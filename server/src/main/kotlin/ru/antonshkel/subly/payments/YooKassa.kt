package ru.antonshkel.subly.payments

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable data class Amount(val value: String, val currency: String = "RUB")
@Serializable data class Confirmation(val type: String = "redirect", val return_url: String? = null, val confirmation_url: String? = null)

@Serializable
data class CreatePayment(
    val amount: Amount,
    val capture: Boolean = true,
    val confirmation: Confirmation,
    val description: String,
    val metadata: Map<String, String>,
)

@Serializable
data class YooPayment(
    val id: String,
    val status: String,                  // pending, waiting_for_capture, succeeded, canceled
    val paid: Boolean = false,
    val amount: Amount? = null,
    val confirmation: Confirmation? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** Минимум API ЮKassa, который нужен Subly. Интерфейс — чтобы в тестах подменить на фейк. */
interface PaymentGateway {
    suspend fun create(amountKopecks: Long, description: String, returnUrl: String, metadata: Map<String, String>): YooPayment
    suspend fun get(id: String): YooPayment
}

class YooKassaGateway(private val shopId: String, private val secretKey: String) : PaymentGateway {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        expectSuccess = false
    }

    override suspend fun create(amountKopecks: Long, description: String, returnUrl: String, metadata: Map<String, String>): YooPayment {
        val resp = client.post("$API/payments") {
            basicAuth(shopId, secretKey)
            header("Idempotence-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody(CreatePayment(
                amount = Amount(formatAmount(amountKopecks)),
                confirmation = Confirmation(return_url = returnUrl),
                description = description,
                metadata = metadata,
            ))
        }
        if (!resp.status.isSuccess()) error("ЮKassa: ${resp.status} ${resp.body<String>().take(300)}")
        return resp.body()
    }

    override suspend fun get(id: String): YooPayment {
        val resp = client.get("$API/payments/$id") { basicAuth(shopId, secretKey) }
        if (!resp.status.isSuccess()) error("ЮKassa: ${resp.status}")
        return resp.body()
    }

    companion object {
        const val API = "https://api.yookassa.ru/v3"
        fun formatAmount(kopecks: Long) = "%d.%02d".format(kopecks / 100, kopecks % 100)
    }
}
