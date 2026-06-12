package com.github.woodsmarshes.chat.feature.contacts.di

import com.github.woodsmarshes.chat.feature.contacts.ui.ContactsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val contactsModule = module {
    viewModelOf(::ContactsViewModel)
}
