package com.github.woodsmarshes.chat.feature.profile.di

import com.github.woodsmarshes.chat.feature.profile.ui.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel { (userId: String) -> ProfileViewModel(userId, get(), get()) }
}
