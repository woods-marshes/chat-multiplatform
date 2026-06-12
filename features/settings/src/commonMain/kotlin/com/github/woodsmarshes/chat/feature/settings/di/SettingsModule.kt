package com.github.woodsmarshes.chat.feature.settings.di

import com.github.woodsmarshes.chat.feature.settings.ui.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
