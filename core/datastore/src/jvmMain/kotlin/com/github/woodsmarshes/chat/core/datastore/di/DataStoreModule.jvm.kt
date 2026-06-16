package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

//actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> = createDataStore(
//    producePath = {
//        val homeDir = System.getProperty("user.home")
//        val appName = ".chat-multiplatform"
//
//        val dataDir = File(homeDir, appName)
//        if (!dataDir.exists()) {
//            dataDir.mkdirs()
//        }
//
//        val file = File(dataDir, DATA_STORE_FILE_NAME)
//        file.absolutePath
//    }
//)

actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> {
    val homeDir = System.getProperty("user.home")
    val appName = ".chat-multiplatform"

    val dataDir = File(homeDir, appName)
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    val file = File(dataDir, DATA_STORE_FILE_NAME)
    return createDataStore(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            producePath = { file.toOkioPath() }
        )
    )
}