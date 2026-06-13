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
        schema.migrate(driver, schema.version - 1, schema.version).await()
    }
    return driver
}
