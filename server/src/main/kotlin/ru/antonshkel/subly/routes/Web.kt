package ru.antonshkel.subly.routes

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import ru.antonshkel.subly.Services
import ru.antonshkel.subly.db.Users
import ru.antonshkel.subly.db.dbQuery

@Serializable data class Health(val status: String, val db: Boolean)

private fun resource(name: String) = Services::class.java.classLoader.getResource("web/$name")!!.readText()

/** Лендинг, админка, страница возврата после оплаты, robots/sitemap. PUBLIC_URL подставляется при старте. */
fun Route.webRoutes(s: Services) {
    val pages = listOf("index.html", "admin.html", "return.html", "privacy.html", "robots.txt", "sitemap.xml")
        .associateWith { resource(it).replace("{{PUBLIC_URL}}", s.cfg.publicUrl) }

    fun page(path: String, file: String, type: ContentType) {
        get(path) {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=300")
            call.respondText(pages.getValue(file), type)
        }
    }

    page("/", "index.html", ContentType.Text.Html)
    page("/admin", "admin.html", ContentType.Text.Html)
    page("/billing/return", "return.html", ContentType.Text.Html)
    page("/privacy", "privacy.html", ContentType.Text.Html)
    page("/robots.txt", "robots.txt", ContentType.Text.Plain)
    page("/sitemap.xml", "sitemap.xml", ContentType.Application.Xml)

    staticResources("/static", "web/static") {
        cacheControl { listOf(CacheControl.MaxAge(maxAgeSeconds = 7 * 86400)) }
    }

    get("/health") {
        val ok = runCatching { call.dbQuery { Users.selectAll().limit(1).count() } }.isSuccess
        call.respond(if (ok) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, Health(if (ok) "ok" else "degraded", ok))
    }
}
