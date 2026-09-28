package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.base.DatabaseConfig
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager

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
        // READ_COMMITTED: SERIALIZABLE aborts concurrent OLTP writes with
        // serialization failures that nothing retries.
        isReadOnly = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
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