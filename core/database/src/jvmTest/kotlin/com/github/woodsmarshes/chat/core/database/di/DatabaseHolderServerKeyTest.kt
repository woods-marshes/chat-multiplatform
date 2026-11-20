package com.github.woodsmarshes.chat.core.database.di

import com.github.woodsmarshes.chat.core.common.di.DesktopContext
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The same account id on two servers is two different people: the holder must
 * never hand back the previous server's driver, and a stale close call for the
 * other server must not tear down the current one.
 */
class DatabaseHolderServerKeyTest {
    @Test
    fun sameUserOnDifferentServersGetsSeparateDatabases() =
        runBlocking {
            val userId = Uuid.random()
            val keyA = "s0000aaa1"
            val keyB = "s0000bbb2"
            val files =
                listOf(
                    Path.of("chat_${keyA}_$userId.db"),
                    Path.of("chat_${keyB}_$userId.db"),
                )
            val holder = DatabaseHolder(DesktopContext())
            try {
                val first = holder.getOrCreateDatabase(userId, keyA)
                assertSame(first, holder.getOrCreateDatabase(userId, keyA), "same server must reuse the driver")
                assertTrue(Files.exists(files[0]), "database file must be namespaced by serverKey")

                val second = holder.getOrCreateDatabase(userId, keyB)
                assertNotSame(first, second, "another server must not reuse the previous driver")
                assertSame(second, holder.getActiveDatabase())
                assertTrue(Files.exists(files[1]), "each server must own its own database file")

                holder.closeDatabaseIfCurrent(userId, "s0000ccc3")
                assertSame(second, holder.getActiveDatabase(), "a close for another server must be ignored")

                holder.closeDatabaseIfCurrent(userId, keyB)
                assertFailsWith<IllegalStateException> { holder.getActiveDatabase() }
                Unit
            } finally {
                holder.closeDatabaseIfCurrent(userId, keyA)
                holder.closeDatabaseIfCurrent(userId, keyB)
                files.forEach { Files.deleteIfExists(it) }
            }
        }
}
