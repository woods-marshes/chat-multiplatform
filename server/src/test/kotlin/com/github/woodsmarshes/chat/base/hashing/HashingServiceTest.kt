package com.github.woodsmarshes.chat.base.hashing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HashingServiceTest {

    private val service: HashingService = HashingServiceImpl()

    @Test
    fun `generated hash verifies against the original value`() {
        val saltedHash = service.generateSaltedHash("s3cret-password")

        assertTrue(service.verify("s3cret-password", saltedHash))
    }

    @Test
    fun `verification fails for a wrong value`() {
        val saltedHash = service.generateSaltedHash("s3cret-password")

        assertFalse(service.verify("wrong-password", saltedHash))
    }

    @Test
    fun `verification fails for a value differing only by case`() {
        val saltedHash = service.generateSaltedHash("Password")

        assertFalse(service.verify("password", saltedHash))
    }

    @Test
    fun `empty value can be hashed and verified`() {
        val saltedHash = service.generateSaltedHash("")

        assertTrue(service.verify("", saltedHash))
        assertFalse(service.verify(" ", saltedHash))
    }

    @Test
    fun `unicode and whitespace values survive a hash-verify round trip`() {
        val value = "密码 P@ssw0rd! \t with spaces 中文 😀"

        val saltedHash = service.generateSaltedHash(value)

        assertTrue(service.verify(value, saltedHash))
    }

    @Test
    fun `two hashes of the same value differ because of the embedded salt`() {
        val first = service.generateSaltedHash("same-value")
        val second = service.generateSaltedHash("same-value")

        assertNotEquals(first.hash, second.hash)
        assertTrue(service.verify("same-value", first))
        assertTrue(service.verify("same-value", second))
    }

    @Test
    fun `salt field stays empty because BCrypt embeds the salt in the hash`() {
        val saltedHash = service.generateSaltedHash("any-value")

        // Documents the current design: BCrypt stores its own salt inside the
        // hash string, so the separate `salt` field is unused (always empty).
        assertEquals("", saltedHash.salt)
    }

    @Test
    fun `hash respects the requested cost factor`() {
        val hash = service.generateSaltedHash("value", cost = 4).hash

        assertTrue(hash.startsWith("\$2a\$04\$") || hash.startsWith("\$2y\$04\$") || hash.startsWith("\$2b\$04\$"))
    }

    @Test
    fun `verification fails for a hash produced from a different value`() {
        val saltedHash = service.generateSaltedHash("original")

        assertFalse(service.verify("originaL", saltedHash))
        assertFalse(service.verify("original ", saltedHash))
    }
}
