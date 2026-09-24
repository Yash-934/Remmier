package com.pocketforge.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNamingTest {
    @Test
    fun projectSlugFormatsNamesCorrectly() {
        assertEquals("school", projectSlug("School"))
        assertEquals("my-school-app", projectSlug("My School App"))
        assertEquals("school-project", projectSlug("School_Project"))
        assertEquals("react-web", projectSlug("react-web"))
    }

    @Test
    fun zipArchiveBaseNameSanitization() {
        val archiveName = "School.zip"
        val baseName = archiveName
            .replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.tar\\.gz$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
        assertEquals("School", baseName)
        assertEquals("school", projectSlug(baseName))
    }

    @Test
    fun projectSlugDeduplicationSequence() {
        val baseSlug = projectSlug("School")
        val usedSlugs = setOf("school", "school-2")
        val uniqueSlug = generateSequence(1) { it + 1 }
            .map { number -> if (number == 1) baseSlug else "$baseSlug-$number" }
            .first { it !in usedSlugs }
        assertEquals("school-3", uniqueSlug)
    }
}
