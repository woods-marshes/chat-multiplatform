package com.github.woodsmarshes.chat.feature.search.di

import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import com.github.woodsmarshes.chat.feature.search.ui.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val searchModule = module {
    viewModel { (type: SearchType) -> SearchViewModel(type, get(), get()) }
}
