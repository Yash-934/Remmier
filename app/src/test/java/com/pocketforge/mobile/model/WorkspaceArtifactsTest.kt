package com.pocketforge.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceArtifactsTest {

    @Test
    fun generatedArtifactsAreCorrectlyIdentified() {
        val artifactFiles = listOf(
            "app-debug.apk",
            "release-signed.apk",
            "project-export.zip",
            "archive.tar.gz",
            "library.aar",
        )
        val nonArtifactFiles = listOf(
            "MainActivity.kt",
            "build.gradle.kts",
            "package.json",
            "index.html",
            "README.md",
        )

        for (name in artifactFiles) {
            val isArtifact = name.endsWith(".apk", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true) ||
                name.endsWith(".tar.gz", ignoreCase = true) ||
                name.endsWith(".aar", ignoreCase = true)
            assertTrue("Expected $name to be recognized as an artifact", isArtifact)
        }

        for (name in nonArtifactFiles) {
            val isArtifact = name.endsWith(".apk", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true) ||
                name.endsWith(".tar.gz", ignoreCase = true) ||
                name.endsWith(".aar", ignoreCase = true)
            assertFalse("Expected $name NOT to be recognized as an artifact", isArtifact)
        }
    }

    @Test
    fun intermediateDirectoriesAreCorrectlyDetected() {
        val intermediateDirs = listOf(
            "intermediates",
            "tmp",
            ".transforms",
            "kotlin-classes",
            ".gradle",
            ".cache",
            "__pycache__",
        )

        for (dir in intermediateDirs) {
            val isCache = dir.lowercase() in setOf(".git", ".gradle", ".idea", ".cache", "__pycache__", ".pytest_cache", ".cargo") ||
                dir.lowercase() in setOf("intermediates", "tmp", ".transforms", "kotlin-classes", "extracted-include-protos", "incremental")
            assertTrue("Expected $dir to be flagged as cache/intermediate", isCache)
        }
    }

    @Test
    fun parentDirectoryHierarchyGeneration() {
        val artifactPath = "app/build/outputs/apk/debug/app-debug.apk"
        val parts = artifactPath.split('/')
        val parentPaths = (1 until parts.size).map { depth -> parts.take(depth).joinToString("/") }

        assertEquals(
            listOf(
                "app",
                "app/build",
                "app/build/outputs",
                "app/build/outputs/apk",
                "app/build/outputs/apk/debug",
            ),
            parentPaths
        )
    }
}
