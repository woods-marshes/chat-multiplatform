package com.github.woodsmarshes.chat.feature.search.di

import com.github.woodsmarshes.chat.feature.search.ui.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchModule = module {
    viewModelOf(::SearchViewModel)
}
