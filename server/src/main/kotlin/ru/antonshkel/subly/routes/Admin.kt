package ru.antonshkel.subly.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import ru.antonshkel.subly.Services
import ru.antonshkel.subly.db.Repo
import ru.antonshkel.subly.db.dbQuery
import ru.antonshkel.subly.domain.ApiException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable data class DayCount(val date: String, val count: Int)
@Serializable data class NameCount(val name: String, val count: Int)
@Serializable data class PaymentDto(val id: Long, val userId: Long, val plan: String, val amountKopecks: Long, val status: String, val createdAt: String)

@Serializable
data class AdminStats(
    val users: Int,
    val proUsers: Int,
    val newUsers7d: Int,
    val activeTrackers: Int,
    val revenue30dKopecks: Long,
    val revenueTotalKopecks: Long,
    val subscriptionsTracked: Int,
    val avgSubscriptionsPerUser: Double,
    val avgMonthlySpendKopecks: Long,
    val conversionToPro: Double,
    val signups: List<DayCount>,
    val topServices: List<NameCount>,
    val payments: List<PaymentDto>,
)

@Serializable
data class AdminUser(val id: Long, val email: String, val createdAt: String, val plan: String, val proUntil: String?, val subscriptions: Int, val monthlyKopecks: Long)

@Serializable data class GrantPro(val days: Long)

private val MSK = ZoneId.of("Europe/Moscow")

private suspend fun ApplicationCall.requireAdmin(s: Services) {
    val u = currentUser()
    if (u.email !in s.cfg.adminEmails) throw ApiException(403, "forbidden", "Нет доступа")
}

fun Route.adminRoutes(s: Services) {
    authenticate("user") {
        route("/api/admin") {
            get("/stats") {
                call.requireAdmin(s)
                val now = Instant.now()
                val stats = call.dbQuery {
                    val users = Repo.allUsers()
                    val subs = Repo.subscriptionStats()
                    val perUser = subs.groupBy { it.first }
                    val today = LocalDate.now(MSK)
                    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
                    val byDay = users.groupingBy { it.createdAt.atZone(MSK).toLocalDate() }.eachCount()
                    AdminStats(
                        users = users.size,
                        proUsers = users.count { it.isPro(now) },
                        newUsers7d = users.count { it.createdAt.isAfter(now.minusSeconds(7 * 86400)) },
                        activeTrackers = perUser.size,
                        revenue30dKopecks = Repo.succeededSince(now.minusSeconds(30L * 86400)).sumOf { it.amount },
                        revenueTotalKopecks = Repo.allSucceeded().sumOf { it.amount },
                        subscriptionsTracked = subs.size,
                        avgSubscriptionsPerUser = if (perUser.isEmpty()) 0.0 else subs.size.toDouble() / perUser.size,
                        avgMonthlySpendKopecks = if (perUser.isEmpty()) 0 else perUser.values.sumOf { l -> l.sumOf { it.third } } / perUser.size,
                        conversionToPro = if (users.isEmpty()) 0.0 else users.count { it.isPro(now) }.toDouble() / users.size,
                        signups = days.map { DayCount(it.toString(), byDay[it] ?: 0) },
                        topServices = subs.groupingBy { it.second.trim().lowercase() }.eachCount()
                            .entries.sortedByDescending { it.value }.take(10)
                            .map { e -> NameCount(subs.first { it.second.trim().lowercase() == e.key }.second.trim(), e.value) },
                        payments = Repo.recentPayments(20).map { PaymentDto(it.id, it.userId, it.plan, it.amount, it.status, it.createdAt.toString()) },
                    )
                }
                call.respond(stats)
            }

            get("/users") {
                call.requireAdmin(s)
                val q = call.request.queryParameters["q"].orEmpty().take(100)
                val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                val list = call.dbQuery {
                    val subs = Repo.subscriptionStats().groupBy { it.first }
                    Repo.searchUsers(q, 50, offset).map { u ->
                        AdminUser(u.id, u.email, u.createdAt.toString(), if (u.isPro()) "pro" else "free", u.proUntil?.toString(),
                            subs[u.id]?.size ?: 0, subs[u.id]?.sumOf { it.third } ?: 0)
                    }
                }
                call.respond(list)
            }

            post("/users/{id}/pro") {
                call.requireAdmin(s)
                val id = call.idParam()
                val days = call.receive<GrantPro>().days.coerceIn(0, 3650)
                call.dbQuery {
                    Repo.user(id) ?: throw ApiException(404, "not_found", "Пользователь не найден")
                    Repo.setProDays(id, days)
                }
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/users/{id}") {
                call.requireAdmin(s)
                val id = call.idParam()
                if (!call.dbQuery { Repo.deleteUser(id) }) throw ApiException(404, "not_found", "Пользователь не найден")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
