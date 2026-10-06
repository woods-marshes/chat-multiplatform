package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tripwire tests for the Exposed migration diff engine
 * (MigrationUtils.statementsRequiredForDatabaseMigration) against the
 * schema the server actually ships, run on the H2 dialect that
 * development mode and the test suite boot on.
 *
 * These pins were measured on exposed-migration 1.5.0 after evaluating it
 * to replace configureSchema's hand-written ADD COLUMN IF NOT EXISTS
 * block. The evaluation REJECTED boot-time adoption because the diff on a
 * fully synced database is not clean:
 *
 * - it emits DROP INDEX for all 17 FK-support indices H2 auto-creates for
 *   foreign keys (its metadata scan does not recognize them) — auto-apply
 *   would silently strip every FK index from the database;
 * - it re-issues YJS_DOCUMENTS_ARTICLE_ID_UNIQUE, the same
 *   named-unique-constraint readback failure that sank the older
 *   SchemaUtils.createMissingTablesAndColumns API;
 * - it includes DROP COLUMN for unmapped columns, i.e. data loss is part
 *   of the default output.
 *
 * The tests assert these defects exist. When one fails because Exposed
 * fixed its H2 metadata readback, re-run the adoption evaluation — at
 * that point configureSchema's hand-written patch block can finally be
 * replaced by a diff-based check. PostgreSQL was NOT measured (no local
 * instance); re-measure both dialects before adopting.
 */
class SchemaDiffTest {

    private fun freshH2(): Database =
        Database.connect("jdbc:h2:mem:spike-${Uuid.random()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

    private fun diff(db: Database): List<String> = transaction(db) {
        MigrationUtils.statementsRequiredForDatabaseMigration(
            *ALL_SCHEMA_TABLES.toTypedArray(),
            withLogs = false,
        )
    }

    private fun createAll(db: Database) = transaction(db) {
        SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
    }

    @Test
    fun `fresh h2 database gets create statements without drops`() {
        val db = freshH2()
        val statements = diff(db)
        assertTrue(statements.isNotEmpty())
        assertTrue(
            statements.none { it.trimStart().startsWith("DROP", ignoreCase = true) },
            "fresh-database diff should never drop anything:\n${statements.joinToString("\n")}"
        )
    }

    @Test
    fun `synced h2 database diff still carries the known 1_5_0 metadata noise`() {
        val db = freshH2()
        createAll(db)
        val statements = diff(db)
        val drops = statements.filter { it.trimStart().startsWith("DROP", ignoreCase = true) }
        val reissuedConstraints = statements.filter {
            it.contains("ADD CONSTRAINT", ignoreCase = true) && it.contains("UNIQUE", ignoreCase = true)
        }
        assertTrue(
            drops.isNotEmpty(),
            "Exposed 1.5.0 no longer wants to drop H2's auto-created FK indices on a synced " +
                "database — its metadata readback was fixed; re-evaluate diff-based schema " +
                "maintenance for configureSchema."
        )
        assertTrue(
            reissuedConstraints.isNotEmpty(),
            "Exposed 1.5.0 no longer re-issues existing named unique constraints on a synced " +
                "database — its metadata readback was fixed; re-evaluate diff-based schema " +
                "maintenance for configureSchema."
        )
    }

    @Test
    fun `column-add detection itself is correct even though output is polluted`() {
        val db = freshH2()
        transaction(db) {
            SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
            // Simulate a database from before the seq / next_seq /
            // client_request_id era (the columns configureSchema today
            // back-fills with hand-written ADD COLUMN IF NOT EXISTS).
            // Every statement is idempotent: Exposed retries the whole
            // transaction block on failure, and a half-applied drop set
            // must not poison the retry.
            exec("ALTER TABLE messages DROP CONSTRAINT IF EXISTS MESSAGES_USER_ID_CONVERSATION_ID_CLIENT_REQUEST_ID_UNIQUE")
            exec("ALTER TABLE messages DROP COLUMN IF EXISTS seq")
            exec("ALTER TABLE messages DROP COLUMN IF EXISTS client_request_id")
            exec("ALTER TABLE conversations DROP COLUMN IF EXISTS next_seq")
        }
        val statements = diff(db)
        val adds = statements.filter { it.trimStart().startsWith("ALTER TABLE", ignoreCase = true) && it.contains(" ADD ", ignoreCase = true) }
        assertTrue(adds.any { it.contains("MESSAGES") && it.contains("SEQ") })
        assertTrue(adds.any { it.contains("MESSAGES") && it.contains("CLIENT_REQUEST_ID") })
        assertTrue(adds.any { it.contains("CONVERSATIONS") && it.contains("NEXT_SEQ") })
    }

    @Test
    fun `unmapped columns are dropped by the default diff`() {
        val db = freshH2()
        transaction(db) {
            SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
            exec("ALTER TABLE users ADD COLUMN legacy_note VARCHAR(64)")
        }
        val statements = diff(db)
        assertTrue(
            statements.any { it.contains("DROP COLUMN", ignoreCase = true) && it.contains("LEGACY_NOTE", ignoreCase = true) },
            "Unmapped columns are no longer dropped by the default diff — the data-loss " +
                "warning attached to boot-time auto-apply no longer applies; re-evaluate."
        )
    }
}
