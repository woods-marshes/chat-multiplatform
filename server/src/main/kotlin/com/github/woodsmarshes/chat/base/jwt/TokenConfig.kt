package com.github.woodsmarshes.chat.base.jwt

data class TokenConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val expiresIn: Long,
    val secret: String
)
