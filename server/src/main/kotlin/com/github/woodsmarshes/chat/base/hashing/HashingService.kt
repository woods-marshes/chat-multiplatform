package com.github.woodsmarshes.chat.base.hashing

import at.favre.lib.crypto.bcrypt.BCrypt

interface HashingService {
    fun generateSaltedHash(value: String, cost: Int = 12): SaltedHash
    fun verify(value: String, saltedHash: SaltedHash): Boolean
}

class HashingServiceImpl : HashingService {

    override fun generateSaltedHash(value: String, cost: Int): SaltedHash {
        val hash = BCrypt.withDefaults().hashToString(cost, value.toCharArray())
        return SaltedHash(hash = hash, salt = "")
    }

    override fun verify(value: String, saltedHash: SaltedHash): Boolean {
        val result = BCrypt.verifyer().verify(value.toCharArray(), saltedHash.hash)
        return result.verified
    }
}
