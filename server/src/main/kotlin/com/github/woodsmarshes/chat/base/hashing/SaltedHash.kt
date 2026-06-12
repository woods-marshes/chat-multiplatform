package com.github.woodsmarshes.chat.base.hashing

data class SaltedHash(
    val hash: String,
    val salt: String,
)
