package ru.antonshkel.subly.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import ru.antonshkel.subly.Services
import ru.antonshkel.subly.auth.isValidEmail
import ru.antonshkel.subly.auth.normalizeEmail
import ru.antonshkel.subly.db.Repo
import ru.antonshkel.subly.db.UserRow
import ru.antonshkel.subly.db.dbQuery
import ru.antonshkel.subly.db.validated
import ru.antonshkel.subly.domain.ApiException
import ru.antonshkel.subly.domain.AuthResponse
import ru.antonshkel.subly.domain.CATEGORIES
import ru.antonshkel.subly.domain.Credentials
import ru.antonshkel.subly.domain.FREE_LIMIT
import ru.antonshkel.subly.domain.SubscriptionInput
import ru.antonshkel.subly.domain.buildInsights
import java.time.LocalDate

val AUTH_LIMIT = RateLimitName("auth")

data class UserPrincipal(val userId: Long)

/** Текущий пользователь из токена. Если аккаунт удалён, токен больше не действует. */
suspend fun ApplicationCall.currentUser(): UserRow {
    val id = principal<UserPrincipal>()?.userId ?: throw ApiException(401, "unauthorized", "Войдите в аккаунт заново")
    return dbQuery { Repo.user(id) } ?: throw ApiException(401, "unauthorized", "Аккаунт не найден, войдите заново")
}

fun ApplicationCall.idParam(): Long = parameters["id"]?.toLongOrNull() ?: throw ApiException(404, "not_found", "Не найдено")

fun Route.authRoutes(s: Services) {
    rateLimit(AUTH_LIMIT) {
        post("/api/auth/register") {
            val c = call.receive<Credentials>()
            val email = normalizeEmail(c.email)
            if (!isValidEmail(email)) throw ApiException(400, "bad_email", "Проверьте адрес почты")
            if (c.password.length !in 8..128) throw ApiException(400, "weak_password", "Пароль — от 8 символов")
            val hash = s.hasher.hash(c.password)
            val user = call.dbQuery {
                if (Repo.userByEmail(email) != null) throw ApiException(409, "email_taken", "Этот адрес уже зарегистрирован. Войдите.")
                Repo.createUser(email, hash)
            }
            call.respond(HttpStatusCode.Created, AuthResponse(s.tokens.issue(user.id), user.dto(email in s.cfg.adminEmails)))
        }
        post("/api/auth/login") {
            val c = call.receive<Credentials>()
            val email = normalizeEmail(c.email)
            val user = call.dbQuery { Repo.userByEmail(email) }
            if (!s.hasher.verify(c.password, user?.passwordHash) || user == null)
                throw ApiException(401, "bad_credentials", "Неверная почта или пароль")
            call.respond(AuthResponse(s.tokens.issue(user.id), user.dto(email in s.cfg.adminEmails)))
        }
    }

    authenticate("user") {
        get("/api/me") {
            val u = call.currentUser()
            call.respond(u.dto(u.email in s.cfg.adminEmails))
        }
        delete("/api/me") {
            val u = call.currentUser()
            call.dbQuery { Repo.deleteUser(u.id) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.subscriptionRoutes() {
    get("/api/categories") { call.respond(CATEGORIES) }

    authenticate("user") {
        route("/api/subscriptions") {
            get {
                val u = call.currentUser()
                call.respond(call.dbQuery { Repo.subscriptions(u.id) })
            }
            post {
                val u = call.currentUser()
                val (input, anchor) = call.receive<SubscriptionInput>().validated()
                val created = call.dbQuery {
                    if (!u.isPro() && Repo.countSubscriptions(u.id) >= FREE_LIMIT)
                        throw ApiException(402, "limit", "В бесплатной версии — до $FREE_LIMIT подписок. В Pro ограничений нет.")
                    val id = Repo.createSubscription(u.id, input, anchor)
                    Repo.subscription(u.id, id)!!
                }
                call.respond(HttpStatusCode.Created, created)
            }
            put("/{id}") {
                val u = call.currentUser()
                val id = call.idParam()
                val (input, anchor) = call.receive<SubscriptionInput>().validated()
                val updated = call.dbQuery {
                    if (!Repo.updateSubscription(u.id, id, input, anchor)) throw ApiException(404, "not_found", "Подписка не найдена")
                    Repo.subscription(u.id, id)!!
                }
                call.respond(updated)
            }
            delete("/{id}") {
                val u = call.currentUser()
                val id = call.idParam()
                if (!call.dbQuery { Repo.deleteSubscription(u.id, id) }) throw ApiException(404, "not_found", "Подписка не найдена")
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/api/insights") {
            val u = call.currentUser()
            if (!u.isPro()) throw ApiException(402, "pro_required", "Советы доступны в Subly Pro")
            call.respond(buildInsights(call.dbQuery { Repo.subscriptions(u.id) }, LocalDate.now()))
        }

        get("/api/export.csv") {
            val u = call.currentUser()
            if (!u.isPro()) throw ApiException(402, "pro_required", "Экспорт доступен в Subly Pro")
            val subs = call.dbQuery { Repo.subscriptions(u.id) }
            val csv = buildString {
                append("name;price_rub;period;next_charge;monthly_rub;category;trial;notes\n")
                subs.forEach {
                    fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                    append(listOf(q(it.name), "%.2f".format(it.priceKopecks / 100.0), it.period.name, it.nextCharge,
                        "%.2f".format(it.monthlyKopecks / 100.0), CATEGORIES[it.category] ?: it.category,
                        if (it.isTrial) "yes" else "no", q(it.notes)).joinToString(";")).append('\n')
                }
            }
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"subly.csv\"")
            // BOM, чтобы Excel сразу открыл кириллицу правильно
            call.respondText("﻿" + csv, ContentType.Text.CSV.withParameter("charset", "utf-8"))
        }
    }
}
