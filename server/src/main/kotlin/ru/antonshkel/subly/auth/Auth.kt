package ru.antonshkel.subly.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class TokenService(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build()

    fun issue(userId: Long): String = JWT.create()
        .withIssuer(ISSUER).withAudience(AUDIENCE)
        .withClaim("uid", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + TTL_MS))
        .sign(algorithm)

    companion object {
        const val ISSUER = "subly"
        const val AUDIENCE = "subly-app"
        const val TTL_MS = 60L * 24 * 3600 * 1000   // 60 дней: на телефоне не нужно часто логиниться
    }
}

class PasswordHasher(private val cost: Int) {
    // Хеш-заглушка: при входе с несуществующим email тоже тратим время на bcrypt,
    // чтобы по времени ответа нельзя было понять, зарегистрирован ли адрес.
    private val dummy = hash("dummy-password-for-timing")

    fun hash(password: String): String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())
    fun verify(password: String, hash: String?): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash ?: dummy).verified && hash != null
}

private val EMAIL = Regex("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$")

fun normalizeEmail(raw: String): String = raw.trim().lowercase()
fun isValidEmail(email: String) = email.length <= 254 && EMAIL.matches(email)
