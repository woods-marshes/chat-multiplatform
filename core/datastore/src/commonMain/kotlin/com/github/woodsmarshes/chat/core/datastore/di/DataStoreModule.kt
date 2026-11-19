package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.ServerConfigDataSource
import com.github.woodsmarshes.chat.core.datastore.ServerConfigStore
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
    singleOf(::ServerConfigDataSource)
    single<ServerConfigStore> { get<ServerConfigDataSource>() }
}

data class PreferenceDataSources(
    val authTokenDataSource: AuthTokenDataSource,
    val userSettingDataSource: UserSettingDataSource,
    val serverConfigDataSource: ServerConfigDataSource,
)

fun createPreferenceDataSources(
    filePath: String,
    scope: CoroutineScope,
    json: Json,
): PreferenceDataSources {
    val dataStore = PreferenceDataStoreFactory.createWithPath(
        scope = scope,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { filePath.toPath() },
    )
    val userSettingDataSource = UserSettingDataSource(dataStore, json)
    val authTokenDataSource = AuthTokenDataSource(dataStore, userSettingDataSource)
    val serverConfigDataSource = ServerConfigDataSource(dataStore)
    return PreferenceDataSources(authTokenDataSource, userSettingDataSource, serverConfigDataSource)
}

// A corrupt preferences file (truncated by a process kill, disk-full, ...)
// must degrade to "empty preferences" instead of throwing at every collector —
// the worst case is a logged-out user with default settings, not a dead app.
fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(
        storage = storage,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )

expect fun createDataStore(platformContext: PlatformContext): DataStore<Preferences>
