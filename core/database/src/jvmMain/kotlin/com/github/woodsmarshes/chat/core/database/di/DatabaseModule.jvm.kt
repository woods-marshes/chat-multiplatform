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

    return initializeDatabaseDriver(driver, schema)
}

/** Failed initialization never leaks a driver or rewrites a newer schema version. */
internal suspend fun initializeDatabaseDriver(
    driver: SqlDriver,
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
): SqlDriver {
    try {
        val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0).value ?: 0L
        check(version <= schema.version) {
            "Database version $version is newer than supported version "+schema.version
        }
        val empty = driver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE type='table'", { cursor ->
            QueryResult.Value(cursor.next().value && cursor.getLong(0) == 0L)
        }, 0).value
        if (empty) schema.create(driver).await()
        else if (version < schema.version) schema.migrate(driver, version, schema.version).await()
        driver.execute(null, "PRAGMA user_version = "+schema.version, 0).await()
        return driver
    } catch (failure: Throwable) {
        try { driver.close() } catch (closeFailure: Throwable) {
            if (failure !== closeFailure) failure.addSuppressed(closeFailure)
        }
        throw failure
    }
}
