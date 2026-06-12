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
    return JdbcSqliteDriver(
        url = databaseUrl,
        properties = Properties().apply { put("foreign_keys", "true") }
    )
        .also { schema.create(it).await() }
}
