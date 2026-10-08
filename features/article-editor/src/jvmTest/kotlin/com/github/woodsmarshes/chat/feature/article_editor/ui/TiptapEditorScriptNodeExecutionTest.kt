package com.github.woodsmarshes.chat.feature.article_editor.ui

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiptapEditorScriptNodeExecutionTest {

    @Test
    fun generatedScriptsExecuteInNodeVmWithIdenticalDecodedPayloads() {
        val nodeAvailable = runCatching {
            val proc = ProcessBuilder("node", "--version").start()
            proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0
        }.getOrDefault(false)

        org.junit.Assume.assumeTrue("Node is required for JavaScript execution verification", nodeAvailable)

        val title = "中文标题 🚀 \"quotes\" 'single' \\backslash\nsecond line\r\nthird"
        val jsonStr = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hello 世界 🎉 \"quoted\"\nline 2"}]}]}"""
        val userName = "Wood \"The Reviewer\" \\ \n🌱"

        val nonCollabScript = buildEditorInitializeScript(
            initialTitle = title,
            initialJsonStr = jsonStr,
        )
        val collabScript = buildEditorInitializeScript(
            initialTitle = title,
            initialJsonStr = jsonStr,
            collabUrl = "ws://127.0.0.1:1234",
            roomId = "room-42",
            token = "secret-token",
            userInfoName = userName,
            userInfoColor = "#60a5fa",
        )

        val harness = """
            const vm = require('node:vm');
            const assert = require('node:assert/strict');

            function runScript(code) {
              const calls = [];
              const sandbox = {
                window: {
                  atob: (b64) => Buffer.from(b64, 'base64').toString('binary'),
                  __editorShell: {
                    initialize: (...args) => calls.push(args),
                  },
                },
                escape: globalThis.escape,
                decodeURIComponent: globalThis.decodeURIComponent,
              };
              vm.runInNewContext(code, sandbox);
              assert.equal(calls.length, 1);
              return calls[0];
            }

            const nonCollabArgs = runScript(${jsStringLiteral(nonCollabScript)});
            assert.equal(nonCollabArgs.length, 2);
            assert.equal(nonCollabArgs[0], ${jsStringLiteral(title)});
            assert.equal(nonCollabArgs[1], ${jsStringLiteral(jsonStr)});
            JSON.parse(nonCollabArgs[1]);

            const collabArgs = runScript(${jsStringLiteral(collabScript)});
            assert.equal(collabArgs.length, 3);
            assert.equal(collabArgs[0], ${jsStringLiteral(title)});
            assert.equal(collabArgs[1], ${jsStringLiteral(jsonStr)});
            const parsedCollab = JSON.parse(collabArgs[2]);
            assert.equal(parsedCollab.collabUrl, 'ws://127.0.0.1:1234');
            assert.equal(parsedCollab.roomId, 'room-42');
            assert.equal(parsedCollab.token, 'secret-token');
            assert.equal(parsedCollab.userInfo.name, ${jsStringLiteral(userName)});
            assert.equal(parsedCollab.userInfo.color, '#60a5fa');
            console.log('NODE_EXEC_OK');
        """.trimIndent()

        val process = ProcessBuilder("node", "-")
            .redirectErrorStream(true)
            .start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(harness) }
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        val output = process.inputStream.bufferedReader().readText()

        assertTrue(finished, "Node process timed out")
        assertEquals(0, process.exitValue(), "Node execution failed:\n$output")
        assertTrue(output.contains("NODE_EXEC_OK"), "Expected NODE_EXEC_OK in output:\n$output")
    }

    private fun jsStringLiteral(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
