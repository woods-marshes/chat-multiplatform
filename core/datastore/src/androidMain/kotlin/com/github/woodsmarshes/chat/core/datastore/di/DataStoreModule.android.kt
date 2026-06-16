package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.github.woodsmarshes.chat.core.common.di.AndroidContext
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath

//actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> {
//    val context = (platformContext as AndroidContext).context
//    return createDataStore(
//        producePath = { context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath }
//    )
//}

actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> {
    val context = (platformContext as AndroidContext).context
    val file = context.filesDir.resolve(DATA_STORE_FILE_NAME)
    return createDataStore(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            producePath = { file.toOkioPath() }
        )
    )
}