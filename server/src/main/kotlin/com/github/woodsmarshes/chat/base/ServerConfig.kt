package com.github.woodsmarshes.chat.base

import com.github.woodsmarshes.chat.base.jwt.TokenConfig

data class ServerConfig(
    val tokenConfig: TokenConfig,
    val databaseConfig: DatabaseConfig?,
    val development: Boolean = false,
)