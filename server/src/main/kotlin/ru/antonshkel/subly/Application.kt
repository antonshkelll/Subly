package ru.antonshkel.subly

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import ru.antonshkel.subly.auth.PasswordHasher
import ru.antonshkel.subly.auth.TokenService
import ru.antonshkel.subly.db.DbKey
import ru.antonshkel.subly.db.connectDatabase
import ru.antonshkel.subly.domain.ApiException
import ru.antonshkel.subly.domain.ErrorDto
import ru.antonshkel.subly.payments.PaymentGateway
import ru.antonshkel.subly.payments.YooKassaGateway
import ru.antonshkel.subly.routes.AUTH_LIMIT
import ru.antonshkel.subly.routes.UserPrincipal
import ru.antonshkel.subly.routes.adminRoutes
import ru.antonshkel.subly.routes.authRoutes
import ru.antonshkel.subly.routes.billingRoutes
import ru.antonshkel.subly.routes.subscriptionRoutes
import ru.antonshkel.subly.routes.webRoutes
import kotlin.time.Duration.Companion.minutes

fun main() {
    val cfg = Config.fromEnv()
    embeddedServer(Netty, port = cfg.port, host = "0.0.0.0") { module(cfg) }.start(wait = true)
}

class Services(val cfg: Config, val tokens: TokenService, val hasher: PasswordHasher, val payments: PaymentGateway?)

fun Application.module(
    cfg: Config,
    gateway: PaymentGateway? = if (cfg.paymentsEnabled) YooKassaGateway(cfg.yookassaShopId, cfg.yookassaSecretKey) else null,
) {
    attributes.put(DbKey, connectDatabase(cfg))
    val services = Services(cfg, TokenService(cfg.jwtSecret), PasswordHasher(cfg.bcryptCost), gateway)
    if (cfg.jwtSecret == Config.DEV_SECRET) log.warn("JWT_SECRET не задан — используется тестовый ключ. Для сервера обязательно задайте свой.")
    log.info("Платежи: ${when { gateway != null -> "ЮKassa"; cfg.paymentsDemo -> "демо-режим"; else -> "выключены" }}")

    // За nginx: берём последний адрес из X-Forwarded-For — его дописал сам nginx.
    // Первый адрес присылает клиент, и подделав его, можно было бы обойти лимит на вход.
    install(XForwardedHeaders) { useLastProxy() }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }
    install(Compression) { gzip { minimumSize(512) } }
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/api") }
        // В журнал попадает только метод, путь и статус — без тел запросов и паролей.
        format { "${it.request.local.method.value} ${it.request.path()} → ${it.response.status()?.value}" }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
    }
    install(RateLimit) {
        register(AUTH_LIMIT) {
            rateLimiter(limit = cfg.authRateLimit, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteAddress }
        }
    }
    install(Authentication) {
        jwt("user") {
            realm = "subly"
            verifier(services.tokens.verifier)
            validate { cred -> cred.payload.getClaim("uid").asLong()?.let { UserPrincipal(it) } }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorDto("unauthorized", "Войдите в аккаунт заново")) }
        }
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(HttpStatusCode.fromValue(e.status), ErrorDto(e.code, e.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Некорректный запрос")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Некорректный запрос")) }
        exception<Throwable> { call, e ->
            call.application.log.error("Ошибка ${call.request.path()}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal", "Что-то пошло не так. Попробуйте ещё раз."))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            if (call.request.path().startsWith("/api")) call.respond(HttpStatusCode.NotFound, ErrorDto("not_found", "Не найдено"))
        }
    }

    routing {
        authRoutes(services)
        subscriptionRoutes()
        billingRoutes(services)
        adminRoutes(services)
        webRoutes(services)
    }
}
