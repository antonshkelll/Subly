package ru.antonshkel.subly.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.antonshkel.subly.Services
import ru.antonshkel.subly.db.Repo
import ru.antonshkel.subly.db.dbQuery
import ru.antonshkel.subly.domain.ApiException
import ru.antonshkel.subly.domain.CheckoutRequest
import ru.antonshkel.subly.domain.CheckoutResponse
import ru.antonshkel.subly.domain.ProPlan
import ru.antonshkel.subly.payments.PaymentGateway

@Serializable data class PlanDto(val code: String, val title: String, val priceKopecks: Long, val months: Long)
@Serializable data class PlansResponse(val plans: List<PlanDto>, val paymentsAvailable: Boolean, val demo: Boolean)

/**
 * Применяет статус платежа из ЮKassa к нашей базе. Вызывается и из вебхука, и при ручной проверке —
 * поэтому идемпотентно: повторное «succeeded» Pro второй раз не продлевает.
 */
private suspend fun ApplicationCall.syncPayment(gateway: PaymentGateway, externalId: String): String? {
    val remote = gateway.get(externalId)
    return dbQuery {
        val local = Repo.paymentByExternalId(externalId) ?: return@dbQuery null
        if (local.status != "pending") return@dbQuery local.status
        when (remote.status) {
            "succeeded" -> {
                val plan = ProPlan.of(local.plan) ?: return@dbQuery null
                // Сумма должна совпасть с тем, что мы выставили: защита от подмены метаданных.
                val paid = remote.amount?.value?.replace(".", "")?.toLongOrNull()
                if (paid != local.amount) return@dbQuery "amount_mismatch"
                Repo.setPaymentStatus(local.id, "succeeded")
                Repo.extendPro(local.userId, plan.months)
                "succeeded"
            }
            "canceled" -> { Repo.setPaymentStatus(local.id, "canceled"); "canceled" }
            else -> "pending"
        }
    }
}

fun Route.billingRoutes(s: Services) {
    get("/api/billing/plans") {
        call.respond(PlansResponse(
            plans = ProPlan.entries.map { PlanDto(it.code, it.title, it.priceKopecks, it.months) },
            paymentsAvailable = s.payments != null || s.cfg.paymentsDemo,
            demo = s.payments == null && s.cfg.paymentsDemo,
        ))
    }

    authenticate("user") {
        post("/api/billing/checkout") {
            val u = call.currentUser()
            val plan = ProPlan.of(call.receive<CheckoutRequest>().plan) ?: throw ApiException(400, "bad_plan", "Неизвестный тариф")
            val gateway = s.payments
            when {
                gateway != null -> {
                    val paymentId = call.dbQuery { Repo.createPayment(u.id, plan.code, plan.priceKopecks) }
                    val remote = gateway.create(
                        amountKopecks = plan.priceKopecks,
                        description = plan.title,
                        returnUrl = "${s.cfg.publicUrl}/billing/return",
                        metadata = mapOf("user_id" to u.id.toString(), "payment_id" to paymentId.toString(), "plan" to plan.code),
                    )
                    call.dbQuery { Repo.attachExternalId(paymentId, remote.id) }
                    val url = remote.confirmation?.confirmation_url ?: throw ApiException(502, "gateway", "Платёжная система не ответила, попробуйте позже")
                    call.respond(CheckoutResponse(confirmationUrl = url))
                }
                s.cfg.paymentsDemo -> {
                    call.dbQuery {
                        val id = Repo.createPayment(u.id, plan.code, plan.priceKopecks)
                        Repo.setPaymentStatus(id, "succeeded")
                        Repo.extendPro(u.id, plan.months)
                    }
                    call.respond(CheckoutResponse(activated = true))
                }
                else -> throw ApiException(503, "payments_off", "Оплата пока не подключена")
            }
        }

        // Приложение вызывает это, когда пользователь вернулся из браузера после оплаты.
        // Работает, даже если вебхук ЮKassa ещё не дошёл или не настроен.
        post("/api/billing/refresh") {
            val u = call.currentUser()
            val gateway = s.payments
            if (gateway != null) {
                call.dbQuery { Repo.pendingPayments(u.id) }.forEach { p ->
                    runCatching { call.syncPayment(gateway, p.externalId!!) }
                        .onFailure { application.log.warn("Не удалось проверить платёж ${p.externalId}: ${it.message}") }
                }
            }
            val fresh = call.dbQuery { Repo.user(u.id)!! }
            call.respond(fresh.dto(fresh.email in s.cfg.adminEmails))
        }
    }

    // Уведомления ЮKassa. Телу не доверяем: берём только id и перепроверяем платёж через API.
    post("/api/billing/yookassa/webhook") {
        val gateway = s.payments
        val id = runCatching {
            Json.parseToJsonElement(call.receiveText()).jsonObject["object"]!!.jsonObject["id"]!!.jsonPrimitive.content
        }.getOrNull()
        if (gateway == null || id == null || id.length > 64) return@post call.respond(HttpStatusCode.OK)
        runCatching { call.syncPayment(gateway, id) }
            .onSuccess { application.log.info("Вебхук ЮKassa: платёж $id → $it"); call.respond(HttpStatusCode.OK) }
            .onFailure {
                // Не 200 — тогда ЮKassa повторит уведомление позже.
                application.log.warn("Вебхук ЮKassa: $id — ${it.message}")
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
    }
}
