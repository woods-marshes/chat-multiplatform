package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.core.okio.WebSessionStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import com.github.woodsmarshes.chat.core.common.utils.error
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.module.Module
import org.koin.dsl.module

//actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> = createDataStore {
//    DATA_STORE_FILE_NAME
//}

val log = KotlinLogging.logger {}
actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> {
    return try {
        createDataStore (
            storage = WebLocalStorage(
                serializer = PreferencesSerializer,
                name = DATA_STORE_FILE_NAME
            )
        )
    } catch (e: Exception) {
        log.error("WebLocalStorage failed, using memory fallback", throwable = e)
        throw e
    }
}