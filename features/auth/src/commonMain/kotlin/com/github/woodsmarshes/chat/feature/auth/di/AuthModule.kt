package com.github.woodsmarshes.chat.feature.auth.di

import com.github.woodsmarshes.chat.feature.auth.ui.AuthViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    viewModelOf(::AuthViewModel)
}
