package com.github.woodsmarshes.chat.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.woodsmarshes.chat.core.common.di.PlatformContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual fun createDataStore(platformContext: PlatformContext): DataStore<Preferences> = createDataStore(
    producePath = {
        val homeDir = System.getProperty("user.home")
        val appName = ".chat-multiplatform"

        val dataDir = File(homeDir, appName)
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }

        val file = File(dataDir, DATA_STORE_FILE_NAME)
        file.absolutePath
    }
)