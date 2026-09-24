package com.github.woodsmarshes.chat.core.database.room.di

import org.koin.dsl.module

/**
 * The Room stack is staged to replace SQLDelight (see CLAUDE.md) but is NOT
 * wired into the app graph yet. Loading this module without providing real
 * platform bindings must fail fast with a clear message instead of silently
 * resolving to null and exploding with a cryptic NPE at first use.
 */
val roomDatabaseModule = module {
    single<Any> { error("core:database-room is not wired up yet: provide a platform Room driver first") }
}
