package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.woodsmarshes.chat.core.common.di.AndroidContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext

actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> {
    val context = (platformContext as AndroidContext).context
    return createDataStore(
        producePath = { context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath }
    )
}