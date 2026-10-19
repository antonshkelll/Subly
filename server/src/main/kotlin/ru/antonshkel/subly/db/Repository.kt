package ru.antonshkel.subly.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.antonshkel.subly.domain.CATEGORIES
import ru.antonshkel.subly.domain.FREE_LIMIT
import ru.antonshkel.subly.domain.Period
import ru.antonshkel.subly.domain.SubscriptionDto
import ru.antonshkel.subly.domain.SubscriptionInput
import ru.antonshkel.subly.domain.UserDto
import ru.antonshkel.subly.domain.badRequest
import ru.antonshkel.subly.domain.nextCharge
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class UserRow(val id: Long, val email: String, val passwordHash: String, val createdAt: Instant, val proUntil: Instant?) {
    fun isPro(now: Instant = Instant.now()) = proUntil?.isAfter(now) == true
    fun dto(admin: Boolean) = UserDto(
        id = id, email = email, plan = if (isPro()) "pro" else "free",
        proUntil = proUntil?.takeIf { isPro() }?.toString(), isAdmin = admin,
        subscriptionLimit = if (isPro()) null else FREE_LIMIT,
    )
}

private fun ResultRow.toUser() = UserRow(this[Users.id].value, this[Users.email], this[Users.passwordHash], this[Users.createdAt], this[Users.proUntil])

private fun ResultRow.toSubscription(today: LocalDate): SubscriptionDto {
    val period = Period.valueOf(this[Subscriptions.period])
    val anchor = this[Subscriptions.anchorDate]
    return SubscriptionDto(
        id = this[Subscriptions.id].value,
        name = this[Subscriptions.name],
        priceKopecks = this[Subscriptions.priceKopecks],
        period = period,
        anchorDate = anchor.toString(),
        nextCharge = nextCharge(anchor, period, today).toString(),
        monthlyKopecks = period.monthly(this[Subscriptions.priceKopecks]),
        category = this[Subscriptions.category],
        color = this[Subscriptions.color],
        remindDaysBefore = this[Subscriptions.remindDays],
        isTrial = this[Subscriptions.isTrial],
        notes = this[Subscriptions.notes],
    )
}

private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/** Проверка ввода. Возвращает нормализованную копию или бросает понятную ошибку 400. */
fun SubscriptionInput.validated(): Pair<SubscriptionInput, LocalDate> {
    val n = name.trim()
    if (n.isEmpty() || n.length > 60) badRequest("Название — от 1 до 60 символов")
    if (priceKopecks !in 0..1_000_000_00L) badRequest("Цена должна быть от 0 до 1 000 000 ₽")
    if (remindDaysBefore !in 0..30) badRequest("Напоминание — от 0 до 30 дней до списания")
    if (notes.length > 500) badRequest("Заметка — не длиннее 500 символов")
    if (!COLOR.matches(color)) badRequest("Цвет в формате #RRGGBB")
    val date = try { LocalDate.parse(anchorDate) } catch (_: DateTimeParseException) { badRequest("Дата в формате ГГГГ-ММ-ДД") }
    if (date.year !in 2000..2100) badRequest("Проверьте дату списания")
    return copy(name = n, category = category.takeIf { it in CATEGORIES } ?: "other", color = color.uppercase(), notes = notes.trim()) to date
}

object Repo {
    fun userByEmail(email: String) = Users.selectAll().where { Users.email eq email }.singleOrNull()?.toUser()
    fun user(id: Long) = Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()

    fun createUser(email: String, hash: String): UserRow {
        val now = Instant.now()
        val id = Users.insertAndGetId {
            it[Users.email] = email; it[passwordHash] = hash; it[createdAt] = now
        }.value
        return UserRow(id, email, hash, now, null)
    }

    fun deleteUser(id: Long) = Users.deleteWhere { Users.id eq id } > 0

    fun extendPro(userId: Long, months: Long, now: Instant = Instant.now()): Instant {
        val current = user(userId)?.proUntil?.takeIf { it.isAfter(now) } ?: now
        val until = current.atZone(java.time.ZoneOffset.UTC).plusMonths(months).toInstant()
        Users.update({ Users.id eq userId }) { it[proUntil] = until }
        return until
    }

    fun setProDays(userId: Long, days: Long) {
        Users.update({ Users.id eq userId }) { it[proUntil] = if (days <= 0) null else Instant.now().plusSeconds(days * 86400) }
    }

    fun subscriptions(userId: Long, today: LocalDate = LocalDate.now()) =
        Subscriptions.selectAll().where { Subscriptions.userId eq userId }
            .orderBy(Subscriptions.id to SortOrder.ASC)
            .map { it.toSubscription(today) }
            .sortedBy { it.nextCharge }

    fun countSubscriptions(userId: Long) = Subscriptions.selectAll().where { Subscriptions.userId eq userId }.count()

    fun subscription(userId: Long, id: Long, today: LocalDate = LocalDate.now()) =
        Subscriptions.selectAll().where { (Subscriptions.id eq id) and (Subscriptions.userId eq userId) }
            .singleOrNull()?.toSubscription(today)

    fun createSubscription(userId: Long, input: SubscriptionInput, anchor: LocalDate): Long {
        val now = Instant.now()
        return Subscriptions.insertAndGetId {
            it[Subscriptions.userId] = userId
            it[name] = input.name; it[priceKopecks] = input.priceKopecks; it[period] = input.period.name
            it[anchorDate] = anchor; it[category] = input.category; it[color] = input.color
            it[remindDays] = input.remindDaysBefore; it[isTrial] = input.isTrial; it[notes] = input.notes
            it[createdAt] = now; it[updatedAt] = now
        }.value
    }

    fun updateSubscription(userId: Long, id: Long, input: SubscriptionInput, anchor: LocalDate): Boolean =
        Subscriptions.update({ (Subscriptions.id eq id) and (Subscriptions.userId eq userId) }) {
            it[name] = input.name; it[priceKopecks] = input.priceKopecks; it[period] = input.period.name
            it[anchorDate] = anchor; it[category] = input.category; it[color] = input.color
            it[remindDays] = input.remindDaysBefore; it[isTrial] = input.isTrial; it[notes] = input.notes
            it[updatedAt] = Instant.now()
        } > 0

    fun deleteSubscription(userId: Long, id: Long): Boolean =
        Subscriptions.deleteWhere { (Subscriptions.id eq id) and (Subscriptions.userId eq userId) } > 0

    // --- платежи ---

    fun createPayment(userId: Long, plan: String, amount: Long): Long = Payments.insertAndGetId {
        it[Payments.userId] = userId; it[Payments.plan] = plan; it[amountKopecks] = amount
        it[status] = "pending"; it[createdAt] = Instant.now()
    }.value

    fun attachExternalId(paymentId: Long, externalId: String) {
        Payments.update({ Payments.id eq paymentId }) { it[Payments.externalId] = externalId }
    }

    data class PaymentRow(val id: Long, val userId: Long, val externalId: String?, val plan: String, val amount: Long, val status: String, val createdAt: Instant, val paidAt: Instant?)

    private fun ResultRow.toPayment() = PaymentRow(this[Payments.id].value, this[Payments.userId].value, this[Payments.externalId],
        this[Payments.plan], this[Payments.amountKopecks], this[Payments.status], this[Payments.createdAt], this[Payments.paidAt])

    fun paymentByExternalId(externalId: String) = Payments.selectAll().where { Payments.externalId eq externalId }.singleOrNull()?.toPayment()

    fun pendingPayments(userId: Long) = Payments.selectAll()
        .where { (Payments.userId eq userId) and (Payments.status eq "pending") }
        .map { it.toPayment() }.filter { it.externalId != null }

    fun setPaymentStatus(id: Long, status: String) {
        Payments.update({ Payments.id eq id }) {
            it[Payments.status] = status
            if (status == "succeeded") it[paidAt] = Instant.now()
        }
    }

    fun recentPayments(limit: Int) = Payments.selectAll().orderBy(Payments.id to SortOrder.DESC).limit(limit).map { it.toPayment() }
    fun succeededSince(since: Instant) = Payments.selectAll()
        .where { (Payments.status eq "succeeded") and (Payments.paidAt greaterEq since) }.map { it.toPayment() }
    fun allSucceeded() = Payments.selectAll().where { Payments.status eq "succeeded" }.map { it.toPayment() }

    // --- для админки ---

    fun allUsers() = Users.selectAll().map { it.toUser() }
    fun searchUsers(q: String, limit: Int, offset: Long) = Users.selectAll()
        .apply { if (q.isNotBlank()) where { Users.email like "%${q.lowercase().replace("%", "").replace("_", "")}%" } }
        .orderBy(Users.id to SortOrder.DESC).limit(limit).offset(offset).map { it.toUser() }
    fun subscriptionStats() = Subscriptions.selectAll().map {
        Triple(it[Subscriptions.userId].value, it[Subscriptions.name], Period.valueOf(it[Subscriptions.period]).monthly(it[Subscriptions.priceKopecks]))
    }
}
