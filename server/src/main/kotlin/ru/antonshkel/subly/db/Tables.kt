package ru.antonshkel.subly.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import ru.antonshkel.subly.Config

object Users : LongIdTable("users") {
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = timestamp("created_at")
    val proUntil = timestamp("pro_until").nullable()
}

object Subscriptions : LongIdTable("subscriptions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 60)
    val priceKopecks = long("price_kopecks")
    val period = varchar("period", 10)
    val anchorDate = date("anchor_date")
    val category = varchar("category", 20)
    val color = varchar("color", 7)
    val remindDays = integer("remind_days")
    val isTrial = bool("is_trial")
    val notes = varchar("notes", 500)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Payments : LongIdTable("payments") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).index()
    val externalId = varchar("external_id", 64).nullable().uniqueIndex()
    val plan = varchar("plan", 16)
    val amountKopecks = long("amount_kopecks")
    val status = varchar("status", 16)
    val createdAt = timestamp("created_at")
    val paidAt = timestamp("paid_at").nullable()
}

fun connectDatabase(cfg: Config): Database {
    val ds = HikariDataSource(HikariConfig().apply {
        jdbcUrl = cfg.databaseUrl
        if (cfg.databaseUser.isNotEmpty()) username = cfg.databaseUser
        if (cfg.databasePassword.isNotEmpty()) password = cfg.databasePassword
        maximumPoolSize = 5           // небольшой VPS: больше соединений не нужно
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
    })
    val db = Database.connect(ds)
    transaction(db) {
        @Suppress("DEPRECATION")
        SchemaUtils.createMissingTablesAndColumns(Users, Subscriptions, Payments)
    }
    return db
}

val DbKey = AttributeKey<Database>("subly.db")

/** Транзакция в базе этого приложения. База передаётся явно, без глобального состояния Exposed. */
suspend fun <T> ApplicationCall.dbQuery(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, application.attributes[DbKey]) { block() }
