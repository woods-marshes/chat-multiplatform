package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal const val DATA_STORE_FILE_NAME = "chat-multiplatform.preferences_pb"

val dataStoreModule = module {
    singleOf(::AuthTokenDataSource)
    singleOf(::UserSettingDataSource)
    single<DataStore<Preferences>> {
        createDataStore(get<PlatformContext>())
    }
}

//fun createDataStore(producePath: () -> String): DataStore<Preferences> =
//    PreferenceDataStoreFactory.createWithPath(
//        produceFile = { producePath().toPath() }
//    )

fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(storage = storage)

expect fun createDataStore(platformContext: PlatformContext): DataStore<Preferences>