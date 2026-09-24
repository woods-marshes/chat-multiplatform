package com.github.woodsmarshes.chat.core.common.webview

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopWebViewResourcesTest {
    @Test
    fun encodedUriRoundTripsAndProfilesAreShared() = withRoot { root ->
        val content = "<html>Hello</html>".encodeToByteArray()
        val viewer = prepareDesktopWebViewResources(root, "viewer", content)
        val editor = prepareDesktopWebViewResources(root, "editor", content)
        assertEquals(viewer.dataDirectory, editor.dataDirectory)
        assertNotEquals(viewer.shellUrl, editor.shellUrl)
        assertEquals(content.decodeToString(), Files.readString(Path.of(URI(viewer.shellUrl))))
        assertTrue(viewer.shellUrl.contains("%23"))
        assertTrue(viewer.shellUrl.contains("%25"))
        assertTrue(viewer.shellUrl.contains("%20"))
    }

    @Test
    fun identicalContentReusesFileAndChangedContentGetsNewUrl() = withRoot { root ->
        val first = prepareDesktopWebViewResources(root, "viewer", "first".encodeToByteArray())
        val file = Path.of(URI(first.shellUrl))
        val modified = Files.getLastModifiedTime(file)
        val second = prepareDesktopWebViewResources(root, "viewer", "first".encodeToByteArray())
        val changed = prepareDesktopWebViewResources(root, "viewer", "second".encodeToByteArray())
        assertEquals(first, second)
        assertEquals(modified, Files.getLastModifiedTime(file))
        assertNotEquals(first.shellUrl, changed.shellUrl)
        assertEquals("first", Files.readString(file))
    }

    @Test
    fun rejectsPathTraversal() = withRoot { root ->
        assertFailsWith<IllegalArgumentException> {
            prepareDesktopWebViewResources(root, "../viewer", byteArrayOf())
        }
    }

    private fun withRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("webview test # % ")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
