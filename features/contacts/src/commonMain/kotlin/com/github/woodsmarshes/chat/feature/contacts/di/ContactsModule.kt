package com.github.woodsmarshes.chat.feature.contacts.di

import com.github.woodsmarshes.chat.feature.contacts.ui.ContactsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val contactsModule = module {
    viewModel { ContactsViewModel(get(), get(), get()) }
}

