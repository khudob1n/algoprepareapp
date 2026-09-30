package com.algoprep.app.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TextImportParserTest {
    @Test fun markdownSectionsBecomeCandidatesWithMetadata() {
        val doc = """
            # My interview reports

            ## Two Sum variant
            Company: Acme
            Stage: onsite
            Level: senior
            Date: 2024-05-12
            Difficulty: Medium
            Given an array of integers and a target, return indices of two numbers that add up to the target.

            ## Serialize a tree
            Source: Reddit thread
            Implement serialize and deserialize for a binary tree. Follow up: handle very deep trees.
        """.trimIndent()
        val result = TextImportParser.parse(doc)
        assertEquals(2, result.size)
        val first = result[0]
        assertEquals("Two Sum variant", first.title)
        assertEquals("Acme", first.companyTag)
        assertEquals("onsite", first.interviewStage)
        assertEquals("senior", first.roleLevel)
        assertEquals(LocalDate.of(2024, 5, 12), first.reportedDate)
        assertEquals("Medium", first.difficultyHint)
        assertEquals(ParserConfidence.HIGH, first.confidence)
        assertFalse(first.text.contains("Company:"))
        assertTrue(first.text.startsWith("Given an array"))
        assertEquals("Reddit thread", result[1].source)
        assertNull(result[1].companyTag)
    }

    @Test fun numberedListItemsAreSeparateTasks() {
        val doc = """
            Tasks I was asked:
            1. Reverse a linked list in place.
            2. Find the longest substring without repeating characters.
               Follow-up: do it in O(n).
            3) Given a graph, detect a cycle.
        """.trimIndent()
        val result = TextImportParser.parse(doc)
        assertEquals(3, result.size)
        assertTrue(result[1].text.contains("Follow-up") || result[1].title.orEmpty().contains("Follow-up"))
        assertTrue(result.all { !it.title.isNullOrBlank() })
    }

    @Test fun bulletListOfBareTitlesIsKeptWithoutInventingStatements() {
        val doc = "- Two Sum\n- Valid Parentheses\n- Merge Intervals\n"
        val result = TextImportParser.parse(doc)
        assertEquals(listOf("Two Sum", "Valid Parentheses", "Merge Intervals"), result.map { it.title })
        assertTrue(result.all { it.text == it.title && it.confidence == ParserConfidence.MEDIUM })
    }

    @Test fun horizontalRulesSeparateEntries() {
        val doc = "Reverse a string without extra memory.\n\n---\n\nImplement an LRU cache with O(1) operations.\n"
        val result = TextImportParser.parse(doc)
        assertEquals(2, result.size)
        assertTrue(result[1].text.contains("LRU") || result[1].title.orEmpty().contains("LRU"))
    }

    @Test fun paragraphsAreTheLastResort() {
        val doc = "Они спросили, как найти цикл в связном списке.\n\nПотом дали задачу на динамическое программирование: рюкзак.\n"
        val result = TextImportParser.parse(doc)
        assertEquals(2, result.size)
        assertTrue(result.all { it.confidence != ParserConfidence.HIGH })
    }

    @Test fun linkOnlyEntryGetsATitleFromTheUrlWithoutFetching() {
        val doc = "- https://leetcode.com/problems/longest-substring-without-repeating-characters/\n- https://example.com/problems/two-sum\n- https://example.com/problems/valid-anagram\n"
        val result = TextImportParser.parse(doc)
        assertEquals(3, result.size)
        assertEquals("Longest Substring Without Repeating Characters", result[0].title)
        assertEquals("https://example.com/problems/two-sum", result[1].sourceUrl)
        assertTrue(result[0].titleGuessed)
    }

    @Test fun sectionThatIsAListIsSplitAndKeepsTheHeadingAsSource() {
        val doc = """
            ## Google phone screen
            - Given a sorted array, find the first occurrence of a value.
            - Merge two sorted linked lists.

            ## Meta onsite
            - Number of islands in a grid.
            - Clone a graph.
        """.trimIndent()
        val result = TextImportParser.parse(doc)
        assertEquals(4, result.size)
        assertEquals("Google phone screen", result[0].source)
        assertEquals("Meta onsite", result[3].source)
    }

    @Test fun russianMetadataKeysAreUnderstood() {
        val doc = "## Задача про скобки\nКомпания: Яндекс\nЭтап: секция алгоритмов\nДата: 12.05.2024\nСложность: средняя\nПроверь, правильно ли расставлены скобки трёх типов в строке.\n\n## Другая задача\nНайти медиану двух отсортированных массивов за O(log n)."
        val first = TextImportParser.parse(doc).first()
        assertEquals("Яндекс", first.companyTag)
        assertEquals("секция алгоритмов", first.interviewStage)
        assertEquals(LocalDate.of(2024, 5, 12), first.reportedDate)
        assertEquals("средняя", first.difficultyHint)
    }

    @Test fun emptyInputGivesNothing() {
        assertTrue(TextImportParser.parse("").isEmpty())
        assertTrue(TextImportParser.parse("\n\n   \n").isEmpty())
    }
}
