package com.github.woodsmarshes.chat.feature.profile.di

import com.github.woodsmarshes.chat.feature.profile.ui.ProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profileModule = module {
    viewModelOf(::ProfileViewModel)
}
