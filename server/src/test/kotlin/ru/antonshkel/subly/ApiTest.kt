package ru.antonshkel.subly

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ru.antonshkel.subly.domain.AuthResponse
import ru.antonshkel.subly.domain.CheckoutRequest
import ru.antonshkel.subly.domain.CheckoutResponse
import ru.antonshkel.subly.domain.Credentials
import ru.antonshkel.subly.domain.Insight
import ru.antonshkel.subly.domain.Period
import ru.antonshkel.subly.domain.SubscriptionDto
import ru.antonshkel.subly.domain.SubscriptionInput
import ru.antonshkel.subly.domain.UserDto
import ru.antonshkel.subly.payments.Amount
import ru.antonshkel.subly.payments.Confirmation
import ru.antonshkel.subly.payments.PaymentGateway
import ru.antonshkel.subly.payments.YooPayment
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeGateway : PaymentGateway {
    val payments = mutableMapOf<String, YooPayment>()
    override suspend fun create(amountKopecks: Long, description: String, returnUrl: String, metadata: Map<String, String>): YooPayment {
        val id = "pay-${payments.size + 1}"
        val amount = Amount("%d.%02d".format(amountKopecks / 100, amountKopecks % 100))
        return YooPayment(id, "pending", amount = amount, confirmation = Confirmation(confirmation_url = "https://yoomoney.test/$id"), metadata = metadata)
            .also { payments[id] = it }
    }
    override suspend fun get(id: String) = payments.getValue(id)
    fun succeed(id: String) { payments[id] = payments.getValue(id).copy(status = "succeeded", paid = true) }
}

class ApiTest {
    private fun cfg(demo: Boolean = false) = Config(
        databaseUrl = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        bcryptCost = 4, authRateLimit = 1000, adminEmails = setOf("admin@subly.test"), paymentsDemo = demo,
    )

    private fun ApplicationTestBuilder.json() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun HttpClient.register(email: String = "anna@example.com"): AuthResponse =
        post("/api/auth/register") { contentType(ContentType.Application.Json); setBody(Credentials(email, "password123")) }.body()

    private fun sub(name: String, price: Long, period: Period = Period.MONTH, category: String = "video", date: LocalDate = LocalDate.now().plusDays(3), trial: Boolean = false) =
        SubscriptionInput(name, price, period, date.toString(), category, "#FF3B30", 1, trial)

    private suspend fun HttpClient.add(token: String, input: SubscriptionInput) =
        post("/api/subscriptions") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(input) }

    @Test
    fun `register, login and crud`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        val reg = c.register("  Anna@Example.com ")
        assertEquals("anna@example.com", reg.user.email)
        assertEquals("free", reg.user.plan)

        val dup = c.post("/api/auth/register") { contentType(ContentType.Application.Json); setBody(Credentials("anna@example.com", "password123")) }
        assertEquals(HttpStatusCode.Conflict, dup.status)

        val bad = c.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(Credentials("anna@example.com", "wrong-pass")) }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        val login: AuthResponse = c.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody(Credentials("anna@example.com", "password123")) }.body()
        val t = login.token

        val created: SubscriptionDto = c.add(t, sub("Кинопоиск", 399_00)).body()
        assertEquals(399_00, created.monthlyKopecks)

        val yearly: SubscriptionDto = c.add(t, sub("Облако", 1200_00, Period.YEAR, "cloud")).body()
        assertEquals(100_00, yearly.monthlyKopecks)

        val upd: SubscriptionDto = c.put("/api/subscriptions/${created.id}") {
            bearerAuth(t); contentType(ContentType.Application.Json); setBody(sub("Кинопоиск HD", 449_00))
        }.body()
        assertEquals("Кинопоиск HD", upd.name)

        val list: List<SubscriptionDto> = c.get("/api/subscriptions") { bearerAuth(t) }.body()
        assertEquals(2, list.size)

        assertEquals(HttpStatusCode.NoContent, c.delete("/api/subscriptions/${created.id}") { bearerAuth(t) }.status)
        assertEquals(HttpStatusCode.NotFound, c.delete("/api/subscriptions/${created.id}") { bearerAuth(t) }.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/subscriptions").status)
    }

    @Test
    fun `users cannot see each other's data`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        val a = c.register("a@example.com").token
        val b = c.register("b@example.com").token
        val s: SubscriptionDto = c.add(a, sub("Музыка", 299_00, category = "music")).body()
        assertEquals(HttpStatusCode.NotFound, c.put("/api/subscriptions/${s.id}") {
            bearerAuth(b); contentType(ContentType.Application.Json); setBody(sub("hack", 1))
        }.status)
        assertEquals(HttpStatusCode.NotFound, c.delete("/api/subscriptions/${s.id}") { bearerAuth(b) }.status)
        assertEquals(0, c.get("/api/subscriptions") { bearerAuth(b) }.body<List<SubscriptionDto>>().size)
    }

    @Test
    fun `validation errors are readable`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        val t = c.register().token
        val r = c.add(t, sub("", 100))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue("Название" in r.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, c.add(t, sub("x", -1)).status)
        assertEquals(HttpStatusCode.BadRequest, c.post("/api/subscriptions") {
            bearerAuth(t); contentType(ContentType.Application.Json); setBody("{\"name\":1}")
        }.status)
        val weak = c.post("/api/auth/register") { contentType(ContentType.Application.Json); setBody(Credentials("x@example.com", "123")) }
        assertEquals(HttpStatusCode.BadRequest, weak.status)
    }

    @Test
    fun `free plan limit and pro gating`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        val t = c.register().token
        repeat(5) { assertEquals(HttpStatusCode.Created, c.add(t, sub("S$it", 100_00)).status) }
        assertEquals(HttpStatusCode.PaymentRequired, c.add(t, sub("S6", 100_00)).status)
        assertEquals(HttpStatusCode.PaymentRequired, c.get("/api/insights") { bearerAuth(t) }.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, c.post("/api/billing/checkout") {
            bearerAuth(t); contentType(ContentType.Application.Json); setBody(CheckoutRequest("pro_month"))
        }.status)
    }

    @Test
    fun `yookassa payment flow upgrades to pro exactly once`() = testApplication {
        val gw = FakeGateway()
        application { module(cfg(), gateway = gw) }
        val c = json()
        val t = c.register().token
        val checkout: CheckoutResponse = c.post("/api/billing/checkout") {
            bearerAuth(t); contentType(ContentType.Application.Json); setBody(CheckoutRequest("pro_month"))
        }.body()
        assertEquals("https://yoomoney.test/pay-1", checkout.confirmationUrl)

        // пока не оплачено — всё ещё free
        assertEquals("free", c.post("/api/billing/refresh") { bearerAuth(t) }.body<UserDto>().plan)

        gw.succeed("pay-1")
        val hook = """{"type":"notification","event":"payment.succeeded","object":{"id":"pay-1","status":"succeeded"}}"""
        assertEquals(HttpStatusCode.OK, c.post("/api/billing/yookassa/webhook") { contentType(ContentType.Application.Json); setBody(hook) }.status)
        val me1: UserDto = c.get("/api/me") { bearerAuth(t) }.body()
        assertEquals("pro", me1.plan)
        assertNull(me1.subscriptionLimit)

        // повторный вебхук не продлевает Pro ещё раз
        c.post("/api/billing/yookassa/webhook") { contentType(ContentType.Application.Json); setBody(hook) }
        val me2: UserDto = c.post("/api/billing/refresh") { bearerAuth(t) }.body()
        assertEquals(me1.proUntil, me2.proUntil)

        // подделанный вебхук с чужим id ничего не ломает
        assertEquals(HttpStatusCode.OK, c.post("/api/billing/yookassa/webhook") { setBody("not json") }.status)

        repeat(7) { c.add(t, sub("S$it", 100_00)) }
        assertEquals(7, c.get("/api/subscriptions") { bearerAuth(t) }.body<List<SubscriptionDto>>().size)
    }

    @Test
    fun `demo payments and insights`() = testApplication {
        application { module(cfg(demo = true), gateway = null) }
        val c = json()
        val t = c.register().token
        val r: CheckoutResponse = c.post("/api/billing/checkout") {
            bearerAuth(t); contentType(ContentType.Application.Json); setBody(CheckoutRequest("pro_year"))
        }.body()
        assertTrue(r.activated)
        c.add(t, sub("Кино 1", 399_00))
        c.add(t, sub("Кино 2", 299_00))
        assertEquals(HttpStatusCode.Created, c.add(t, sub("Пробный", 199_00, category = "music", date = LocalDate.now().plusDays(2), trial = true)).status)
        val insights: List<Insight> = c.get("/api/insights") { bearerAuth(t) }.body()
        val kinds = insights.map { it.kind }
        assertTrue("total" in kinds && "duplicate" in kinds && "trial" in kinds && "upcoming" in kinds, kinds.toString())
        assertEquals(299_00L * 12, insights.first { it.kind == "duplicate" }.savingKopecks)

        val csv = c.get("/api/export.csv") { bearerAuth(t) }
        assertTrue(csv.bodyAsText().contains("\"Кино 1\";399.00;MONTH"))
    }

    @Test
    fun `admin endpoints are protected`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        val user = c.register("user@example.com").token
        val admin = c.register("admin@subly.test")
        assertTrue(admin.user.isAdmin)
        assertEquals(HttpStatusCode.Forbidden, c.get("/api/admin/stats") { bearerAuth(user) }.status)
        val stats = c.get("/api/admin/stats") { bearerAuth(admin.token) }.bodyAsText()
        assertTrue("\"users\":2" in stats, stats)
        val users = c.get("/api/admin/users?q=user") { bearerAuth(admin.token) }.bodyAsText()
        assertTrue("user@example.com" in users)
    }

    @Test
    fun `login rate limit cannot be bypassed with a forged X-Forwarded-For`() = testApplication {
        application { module(cfg().copy(authRateLimit = 3), gateway = null) }
        val c = json()
        // Клиент подставляет разный «свой» IP, а nginx дописывает настоящий последним.
        val codes = (1..5).map { i ->
            c.post("/api/auth/login") {
                header("X-Forwarded-For", "10.0.0.$i, 203.0.113.7")
                contentType(ContentType.Application.Json); setBody(Credentials("x@example.com", "password123"))
            }.status
        }
        assertEquals(HttpStatusCode.TooManyRequests, codes.last(), codes.toString())
    }

    @Test
    fun `pages and health`() = testApplication {
        application { module(cfg(), gateway = null) }
        val c = json()
        assertEquals(HttpStatusCode.OK, c.get("/health").status)
        assertEquals(HttpStatusCode.OK, c.get("/").status)
        assertEquals(HttpStatusCode.OK, c.get("/admin").status)
        assertNotNull(c.get("/sitemap.xml").bodyAsText().takeIf { "localhost" in it })
        assertEquals(HttpStatusCode.NotFound, c.get("/api/nope").status)
    }
}
