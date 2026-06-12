package com.github.woodsmarshes.chat.core.database.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.github.woodsmarshes.chat.core.common.di.AndroidContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>,
    context: PlatformContext,
    dbName: String,
): SqlDriver {
    val androidContext = (context as AndroidContext).context
    val schema = schema.synchronous()
    return AndroidSqliteDriver(
        schema = schema,
        context = androidContext,
        name = dbName,
        callback = object : AndroidSqliteDriver.Callback(schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
    )
}
