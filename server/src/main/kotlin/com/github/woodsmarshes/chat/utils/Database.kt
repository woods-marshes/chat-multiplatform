package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.base.DatabaseConfig
import com.github.woodsmarshes.chat.prometheusRegistry
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Every Hikari pool created since boot; observability binds them to Micrometer. */
private val hikariDataSources = CopyOnWriteArrayList<HikariDataSource>()

/** The pool behind each [Database] created via [connectToH2Database]/[connectToPostgresDatabase]. */
private val dataSourceByDatabase = ConcurrentHashMap<Database, HikariDataSource>()

fun registeredHikariDataSources(): List<HikariDataSource> = hikariDataSources.toList()

/**
 * Closes only the pool behind [database] and unregisters it. Owners stop
 * their own pool this way (ApplicationStopped) — a global "close everything"
 * would also kill pools belonging to other Database instances in the same
 * JVM, e.g. the shared in-memory database used by the test suite.
 */
fun closeHikariDataSource(database: Database) {
    dataSourceByDatabase.remove(database)?.let { dataSource ->
        runCatching { dataSource.close() }
        hikariDataSources.remove(dataSource)
    }
}

suspend fun clearDatabaseData(database: Database) {
    withContext(Dispatchers.IO) {
        transaction(database) {
            // Children first: reverse of the create order in ALL_SCHEMA_TABLES.
            ALL_SCHEMA_TABLES.asReversed().forEach { table ->
                exec("DROP TABLE IF EXISTS ${table.tableName} CASCADE")
            }
        }
    }
}

fun connectToH2Database(): Database {
    // Connecting to H2 embedded database
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
        driverClassName = "org.h2.Driver"
        username = "root"
        password = ""
        maximumPoolSize = 6
        // as of version 0.46.0, if these options are set here, they do not need to be duplicated in DatabaseConfig
        isReadOnly = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    }
    return connectThroughHikari(HikariDataSource(hikariConfig))
}

fun connectToPostgresDatabase(config: DatabaseConfig): Database {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        driverClassName = "org.postgresql.Driver"
        username = config.username
        password = config.password
        maximumPoolSize = 10
        // READ_COMMITTED: SERIALIZABLE aborts concurrent OLTP writes with
        // serialization failures that nothing retries.
        isReadOnly = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        // Pool saturation/latency straight into the Prometheus registry.
        metricsTrackerFactory = MicrometerMetricsTrackerFactory(prometheusRegistry)
    }
    return connectThroughHikari(HikariDataSource(hikariConfig))
}

private fun connectThroughHikari(dataSource: HikariDataSource): Database {
    hikariDataSources.add(dataSource)
    val database = Database.connect(datasource = dataSource)
    dataSourceByDatabase[database] = dataSource
    return database
}

fun connectToH2(): Connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")

fun connectToPostgres(config: DatabaseConfig): Connection {
    Class.forName("org.postgresql.Driver")
    // 连接到实际的 PostgreSQL 数据库
    return DriverManager.getConnection(config.url, config.username, config.password)
}