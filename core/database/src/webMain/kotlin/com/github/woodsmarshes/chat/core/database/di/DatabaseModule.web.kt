package com.github.woodsmarshes.chat.core.database.di

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.createDefaultWebWorkerDriver
import com.github.woodsmarshes.chat.core.common.di.PlatformContext

actual suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
    context: PlatformContext,
    dbName: String,
): SqlDriver {
    return createDefaultWebWorkerDriver().also {
        it.execute(
            identifier = null,
            sql = "PRAGMA foreign_keys = ON;",
            parameters = 0
        ).await()
        schema.create(it).await()
    }
}
