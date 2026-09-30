package com.algoprep.app.domain.importer

import com.algoprep.app.domain.model.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParserTest {
    private val parser = ImporterFixtures.parser

    @Test fun classifiesTopicsAndPatternsFromKeywords() = runTest {
        val src = ImportSource("notes.md", "## Islands\nGiven a 2D grid of land and water, count islands using DFS or BFS.", ImportFormat.MARKDOWN)
        val d = parser.parse(src).single()
        assertTrue("graphs" in d.topics)
        assertTrue("dfs" in d.patterns && "bfs" in d.patterns)
        assertNull("difficulty is never guessed", d.difficulty)
    }

    @Test fun explicitHintsAreMappedToKnownIdsAndUnknownOnesAreDropped() = runTest {
        val json = """[{"title":"X","text":"Some statement of the task here.","topics":["Graphs","Динамическое программирование","nonsense"],"difficulty":"Hard"}]"""
        val d = parser.parse(ImportSource("a.json", json, ImportFormat.JSON)).single()
        assertTrue("graphs" in d.topics && "dp" in d.topics)
        assertEquals(Difficulty.HARD, d.difficulty)
        assertFalse(d.topics.any { it == "nonsense" })
    }

    @Test fun difficultyIsReadFromTextOnlyWhenStated() = runTest {
        val stated = parser.parse(ImportSource("a.md", "## T\nDifficulty: Hard\nDetect a cycle in a graph given as an edge list.", ImportFormat.MARKDOWN)).single()
        assertEquals(Difficulty.HARD, stated.difficulty)
        val silent = parser.parse(ImportSource("a.md", "## T\nDetect a cycle in a graph given as an edge list.", ImportFormat.MARKDOWN)).single()
        assertNull(silent.difficulty)
    }

    @Test fun lowConfidenceCandidatesStartUnselected() = runTest {
        val src = ImportSource("n.txt", "Lunch was good and the office was big and bright.\n\nThen they asked me to reverse a linked list.", ImportFormat.TEXT)
        val r = parser.parse(src)
        assertEquals(2, r.size)
        assertFalse(r[0].selected)
        assertTrue(r[1].selected)
    }

    @Test fun shortKeywordsRequireWholeWords() = runTest {
        val d = parser.parse(ImportSource("a.md", "## Tip\nThe dpi setting of the monitor was low and the bstrong light was on.", ImportFormat.MARKDOWN)).single()
        assertFalse("dp" in d.patterns)
        assertFalse("trees" in d.topics)
    }

    @Test fun jsonTextWithoutTitleGetsAGuessedTitle() = runTest {
        val d = parser.parse(ImportSource("a.json", """["Reverse a singly linked list in place. Do it iteratively."]""", ImportFormat.JSON)).single()
        assertTrue(d.titleGuessed)
        assertTrue(d.title.isNotBlank())
        assertTrue("linked_list" in d.topics)
    }

    @Test fun invalidJsonFallsBackToTextParsing() = runTest {
        val r = parser.parse(ImportSource("a.json", "Given an array, find the maximum subarray sum.", ImportFormat.JSON))
        assertEquals(1, r.size)
    }

    @Test fun formatDetection() {
        assertEquals(ImportFormat.JSON, ImportParser.detectFormat("x.json", ""))
        assertEquals(ImportFormat.CSV, ImportParser.detectFormat("x.CSV", ""))
        assertEquals(ImportFormat.MARKDOWN, ImportParser.detectFormat("x.md", ""))
        assertEquals(ImportFormat.TEXT, ImportParser.detectFormat("x.txt", ""))
        assertEquals(ImportFormat.JSON, ImportParser.detectFormat("pasted", "[{\"title\":\"a\"}]"))
        assertEquals(ImportFormat.CSV, ImportParser.detectFormat("pasted", "title,description\nA,B\n"))
        assertEquals(ImportFormat.MARKDOWN, ImportParser.detectFormat("pasted", "# Heading\ntext"))
        assertEquals(ImportFormat.TEXT, ImportParser.detectFormat("pasted", "just some text"))
    }

    @Test fun aHundredEntryDocumentIsSplitIntoAHundredCandidates() = runTest {
        val doc = (1..100).joinToString("\n\n") { "## Report $it\nCompany: C$it\nGiven an array of $it integers, find the pair with the largest sum number $it." }
        val r = parser.parse(ImportSource("big.md", doc, ImportFormat.MARKDOWN))
        assertEquals(100, r.size)
        assertEquals("C57", r[56].companyTag)
        assertEquals(100, r.map { it.tempId }.toSet().size)
    }
}
