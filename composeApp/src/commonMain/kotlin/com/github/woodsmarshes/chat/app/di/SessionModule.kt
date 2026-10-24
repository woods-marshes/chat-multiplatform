package com.github.woodsmarshes.chat.app.di

import com.github.woodsmarshes.chat.app.session.SessionManager
import org.koin.dsl.module

val sessionModule = module {
    single(createdAtStart = true) {
        SessionManager(get(), get(), get(), get(), get(), get(), get())
    }
}
