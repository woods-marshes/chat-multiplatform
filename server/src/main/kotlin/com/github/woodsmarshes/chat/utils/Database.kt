package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.base.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager

// Reverse order of SchemaUtils.create() — children before parents
private val ALL_TABLES = listOf(
    "contact_requests",
    "contacts",
    "conversation_participants",
    "group_join_requests",
    "messages",
    "group_profiles",
    "user_settings",
    "conversations",
    "users",
)

suspend fun clearDatabaseData(database: Database) {
    withContext(Dispatchers.IO) {
        transaction(database) {
            ALL_TABLES.forEach { table ->
                exec("DROP TABLE IF EXISTS $table CASCADE")
            }
        }
    }
}

//suspend fun createDatabaseData(database: Database) {
//    withContext(Dispatchers.IO) {
//        transaction(database) {
//            SchemaUtils.create(Groups, Users, Sessions, Messages, UserSessions)
//        }
//    }
//}

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
    val dataSource = HikariDataSource(hikariConfig)
    return Database.connect(
        datasource = dataSource
    )
}

fun connectToPostgresDatabase(config: DatabaseConfig): Database {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        driverClassName = "org.postgresql.Driver"
        username = config.username
        password = config.password
        maximumPoolSize = 10
        // as of version 0.46.0, if these options are set here, they do not need to be duplicated in DatabaseConfig
        isReadOnly = false
        transactionIsolation = "TRANSACTION_SERIALIZABLE"
    }
    val dataSource = HikariDataSource(hikariConfig)
    return Database.connect(
        datasource = dataSource
    )
}

fun connectToH2(): Connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "root", "")

fun connectToPostgres(config: DatabaseConfig): Connection {
    Class.forName("org.postgresql.Driver")
    // 连接到实际的 PostgreSQL 数据库
    return DriverManager.getConnection(config.url, config.username, config.password)
}