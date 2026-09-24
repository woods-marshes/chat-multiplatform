package com.github.woodsmarshes.chat.core.database.di

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import java.util.Properties

actual suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
    context: PlatformContext,
    dbName: String,
): SqlDriver {
    val databaseUrl = "jdbc:sqlite:$dbName"
    val driver = JdbcSqliteDriver(
        url = databaseUrl,
        properties = Properties().apply {
            put("foreign_keys", "true")
        }
    )

    val isDatabaseEmpty = try {
        driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type='table'",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) == 0L else false)
            },
            parameters = 0
        ).value

    } catch (e: Exception) {
        true // 如果查询出错，假设是空的
    }

    if (isDatabaseEmpty) {
        schema.create(driver).await()
    } else {
        // Migrate only from the version SQLite actually records in
        // user_version; the previous code always "migrated" from
        // version - 1 even when the DB was already current.
        val userVersion = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0
        ).value ?: 0L

        when {
            userVersion < schema.version.toLong() ->
                schema.migrate(driver, userVersion, schema.version.toLong()).await()
            else -> Unit // already current
        }
    }
    return driver
}
