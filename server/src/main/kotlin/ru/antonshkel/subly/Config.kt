package ru.antonshkel.subly

/** Все настройки сервера берутся из переменных окружения (на VPS — из файла .env). */
data class Config(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:h2:file:./data/subly;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    val databaseUser: String = "",
    val databasePassword: String = "",
    val jwtSecret: String = DEV_SECRET,
    val publicUrl: String = "http://localhost:8080",
    val adminEmails: Set<String> = emptySet(),
    val yookassaShopId: String = "",
    val yookassaSecretKey: String = "",
    val paymentsDemo: Boolean = false,
    val bcryptCost: Int = 12,
    val authRateLimit: Int = 10,
) {
    val paymentsEnabled get() = yookassaShopId.isNotBlank() && yookassaSecretKey.isNotBlank()

    companion object {
        const val DEV_SECRET = "dev-only-secret-change-me"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            val cfg = Config(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["DATABASE_URL"]?.takeIf { it.isNotBlank() } ?: Config().databaseUrl,
                databaseUser = env["DATABASE_USER"].orEmpty(),
                databasePassword = env["DATABASE_PASSWORD"].orEmpty(),
                jwtSecret = env["JWT_SECRET"]?.takeIf { it.isNotBlank() } ?: DEV_SECRET,
                publicUrl = env["PUBLIC_URL"]?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "http://localhost:8080",
                adminEmails = env["ADMIN_EMAILS"].orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
                yookassaShopId = env["YOOKASSA_SHOP_ID"].orEmpty().trim(),
                yookassaSecretKey = env["YOOKASSA_SECRET_KEY"].orEmpty().trim(),
                paymentsDemo = env["PAYMENTS_DEMO"]?.lowercase() in setOf("1", "true", "yes"),
            )
            if (env["APP_ENV"] == "prod") {
                require(cfg.jwtSecret != DEV_SECRET && cfg.jwtSecret.length >= 32) {
                    "APP_ENV=prod: задайте JWT_SECRET длиной от 32 символов (openssl rand -hex 32)"
                }
                require(!cfg.paymentsDemo) { "APP_ENV=prod: PAYMENTS_DEMO включает Pro без оплаты, выключите его" }
            }
            return cfg
        }
    }
}
