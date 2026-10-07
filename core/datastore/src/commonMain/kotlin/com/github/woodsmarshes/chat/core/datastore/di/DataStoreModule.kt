package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal const val DATA_STORE_FILE_NAME = "chat-multiplatform.preferences_pb"

val dataStoreModule = module {
    single<DataStore<Preferences>> {
        createDataStore(get<PlatformContext>())
    }
    singleOf(::UserSettingDataSource)
    singleOf(::AuthTokenDataSource)
}

fun createPreferenceDataSources(
    filePath: String,
    scope: CoroutineScope,
    json: Json,
): Pair<AuthTokenDataSource, UserSettingDataSource> {
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        produceFile = { filePath.toPath() },
    )
    val userSettingDataSource = UserSettingDataSource(dataStore, json)
    val authTokenDataSource = AuthTokenDataSource(dataStore, userSettingDataSource)
    return authTokenDataSource to userSettingDataSource
}

fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(storage = storage)

expect fun createDataStore(platformContext: PlatformContext): DataStore<Preferences>