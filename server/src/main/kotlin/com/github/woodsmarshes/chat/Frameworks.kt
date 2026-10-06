package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.base.DatabaseConfig
import com.github.woodsmarshes.chat.base.ServerConfig
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.di.MainModule
import com.github.woodsmarshes.chat.di.repositoryModule
import com.github.woodsmarshes.chat.di.serviceModule
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.PrivateFiles
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.github.woodsmarshes.chat.repository.database.schema.backfillMessageSeq
import com.github.woodsmarshes.chat.service.FileService
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import com.github.woodsmarshes.chat.utils.connectToH2Database
import com.github.woodsmarshes.chat.utils.connectToPostgresDatabase
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.SortOrder
import kotlin.uuid.Uuid
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.fileProperties
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

private const val DEFAULT_JWT_EXPIRY_MS = 1L * 60 * 60 * 1000 // 1 hour access token
private const val EXPIRED_FILE_CLEANUP_MINUTES = 30L
private const val SESSION_SWEEP_MINUTES = 60L

@OptIn(KoinExperimentalAPI::class)
fun Application.configureFrameworks() {
    val appConfig = extractServerConfig()
    logEffectiveJwtConfig(appConfig)
    requireProductionConfiguration(appConfig)
    val dbType = environment.config.propertyOrNull("database.type")?.getString() ?: "h2"
    val database = configureDatabase(appConfig, dbType)
    configureSchema(database)
    backfillMessageSeq(database)
    configurePrivateFileBackfill(database, dbType)
    configureDependencyInjection(appConfig, environment.config, database, environment.log)
    configureUploadDirectories()
    configureSessionCleanup()
    configureFileCleanup()
}

/**
 * Refuses to boot with the repository's development defaults outside
 * development mode.
 *
 * The shipped defaults (a published JWT secret, the checked-in PostgreSQL
 * password and the in-memory H2 fallback) are convenient locally and are a
 * credential leak in production, where forgetting an environment variable
 * would otherwise start a publicly reachable server with known secrets.
 */
private fun Application.requireProductionConfiguration(config: ServerConfig) {
    if (config.development) return
    val databaseType = environment.config.propertyOrNull("database.type")?.getString() ?: "h2"
    check(databaseType == "postgres") {
        "database.type must be \"postgres\" outside development mode (was \"$databaseType\")"
    }
    check(config.tokenConfig.secret !in INSECURE_SECRETS) {
        "jwt.secret still uses the shipped development default; set JWT_SECRET"
    }
    val database = config.databaseConfig
    check(
        database != null &&
            !database.url.isBlank() &&
            !database.username.isBlank() &&
            !database.password.isBlank()
    ) {
        "postgres url/username/password must be configured outside development mode"
    }
    check(database.password !in INSECURE_SECRETS) {
        "postgres password still uses the shipped development default; set POSTGRES_PASSWORD"
    }
}

/**
 * Fingerprints the effective JWT settings at boot: a verifier/issuer
 * mismatch between environments then shows up here as a differing
 * fingerprint instead of as unexplained 401s on business routes. Only the
 * last 8 hex chars of the secret's SHA-256 are logged — never the secret.
 */
private fun Application.logEffectiveJwtConfig(config: ServerConfig) {
    val tokenConfig = config.tokenConfig
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(tokenConfig.secret.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .takeLast(8)
    environment.log.info(
        "JWT config: issuer='${tokenConfig.issuer}', audience='${tokenConfig.audience}', " +
            "realm='${tokenConfig.realm}', expiresIn=${tokenConfig.expiresIn / 1000}s, " +
            "secretSha256=...$fingerprint"
    )
}

private val INSECURE_SECRETS = setOf(
    "my-local-test-secret-key-change-in-prod",
    "jghN7qJJq4vDmvHVg",
    // The demo values shipped in the checked-in .env that compose.yml feeds
    // the container: without these entries, `docker compose up` in
    // production passes the guard while running on published secrets.
    "demo-jwt-secret-must-be-changed-for-production-use",
    "demo-password-please-change-in-production",
)

private fun Application.configureDatabase(config: ServerConfig, dbType: String): Database {
    return when (dbType) {
        "postgres" -> {
            val dbConfig = config.databaseConfig
                ?: error("PostgreSQL config is required when database.type=postgres")
            connectToPostgresDatabase(dbConfig)
        }
        else -> connectToH2Database()
    }
}

private fun configurePrivateFileBackfill(database: Database, dbType: String) {
    // One-time backfill: attachments sent before download authorization was
    // introduced have no private_files rows. Runs only on PostgreSQL (the
    // production dialect — H2 dev/test data is disposable) and only while the
    // mapping table is still empty, so steady-state boots pay one COUNT.
    if (dbType != "postgres") return
    transaction(database) {
        if (PrivateFiles.selectAll().count() > 0) return@transaction
        exec(
            """
            INSERT INTO private_files (file_name, conversation_id, created_at)
            SELECT DISTINCT substring(m.content->>'url' from '[^/]+$'), m.conversation_id, now()
            FROM messages m
            WHERE m.content->>'type' = 'FILE' AND m.content->>'url' LIKE '/v1/files/content/%'
            ON CONFLICT DO NOTHING
            """.trimIndent()
        )
    }
}

private fun configureSchema(database: Database) {
    transaction(database) {
        // Creates tables that do not exist yet (e.g. private_files on a
        // fresh database). createMissingTablesAndColumns was considered for
        // widening this to existing tables, but it re-issues named unique
        // constraints that H2 has already created, so new columns on
        // existing databases are applied with explicit idempotent DDL below.
        // The same defects (FK-index drops, constraint re-issue, unmapped
        // column drops) were measured in the newer MigrationUtils diff on
        // exposed-migration 1.5.0 — see SchemaDiffTest, the tripwire that
        // flags when Exposed's H2 metadata readback is fixed and this block
        // can finally be diff-driven.
        SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
        // SchemaUtils.create skips existing tables, so databases created
        // before these additions never receive them from the schema objects.
        exec("CREATE INDEX IF NOT EXISTS idx_conversation_participants_user_id ON conversation_participants (user_id)")
        exec("ALTER TABLE messages ADD COLUMN IF NOT EXISTS seq BIGINT")
        exec("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS next_seq BIGINT DEFAULT 0 NOT NULL")
        exec("ALTER TABLE messages ADD COLUMN IF NOT EXISTS client_request_id UUID")
        // Unique across (sender, conversation, client_request_id): NULLs are
        // distinct in both H2 and PostgreSQL, so system messages coexist.
        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_client_request_id ON messages (user_id, conversation_id, client_request_id)"
        )
    }
}

@OptIn(KoinExperimentalAPI::class)
private fun Application.configureDependencyInjection(
    appConfig: ServerConfig,
    serverConfig: ApplicationConfig,
    database: Database,
    log: Logger,
) {
    // Background consumers (realtime event collectors, delivery dispatch,
    // typing debounce) all run here; cancelled together on shutdown so tests
    // and restarts never leak them.
    val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    dependencies {
        provide<Logger> { log }
        provide<Database> { database }
        provide<ApplicationConfig> { serverConfig }
        provide<ServerConfig> { appConfig }
        provide<CoroutineScope> { realtimeScope }
    }

    install(Koin) {
        slf4jLogger(level = org.koin.core.logger.Level.INFO)
        bridge { koinToKtor() }
        modules(MainModule, repositoryModule, serviceModule)
        createEagerInstances()
    }

    monitor.subscribe(ApplicationStopped) {
        realtimeScope.cancel()
        it.getKoin().close()
    }
}

private fun Application.configureUploadDirectories() {
    getKoin().get<FileService>().ensureUploadDirectories()
}

private fun Application.configureSessionCleanup() {
    launch(Dispatchers.IO) {
        val sessions = getKoin().get<AuthSessionRepository>()
        while (isActive) {
            sessions.deleteExpiredSessions()
            delay(SESSION_SWEEP_MINUTES.minutes)
        }
    }
}

private fun Application.configureFileCleanup() {
    launch(Dispatchers.IO) {
        val uploadStore = getKoin().get<TemporaryUploadStore>()
        while (isActive) {
            uploadStore.cleanExpiredFiles(EXPIRED_FILE_CLEANUP_MINUTES.toInt())
            delay(10.minutes)
        }
    }
}

private fun Application.extractServerConfig(): ServerConfig {
    val config = environment.config
    val development = config.propertyOrNull("ktor.development")?.getString() == "true"
    return ServerConfig(
        tokenConfig = TokenConfig(
            issuer = config.propertyOrNull("jwt.issuer")?.getString() ?: "chat-server",
            audience = config.propertyOrNull("jwt.audience")?.getString() ?: "chat-client",
            realm = config.propertyOrNull("jwt.realm")?.getString() ?: "chat",
            expiresIn = config.propertyOrNull("jwt.expiresInMs")?.getString()?.toLongOrNull()
                ?: DEFAULT_JWT_EXPIRY_MS,
            secret = config.propertyOrNull("jwt.secret")?.getString()
                ?: error("jwt.secret must be configured in application.conf"),
        ),
        development = development,
        databaseConfig = DatabaseConfig(
            url = config.propertyOrNull("postgres.url")?.getString() ?: "",
            username = config.propertyOrNull("postgres.username")?.getString() ?: "",
            password = config.propertyOrNull("postgres.password")?.getString() ?: "",
        ),
    )
}
