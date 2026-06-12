package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> = createDataStore {
    DATA_STORE_FILE_NAME
}