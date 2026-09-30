package com.algoprep.app.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StructuredParsersTest {
    @Test fun jsonArrayWithFlexibleKeys() {
        val json = """
            [
              {"name": "Two Sum", "description": "Return indices of two numbers adding up to target.", "company": "Acme",
               "round": "phone", "tags": ["arrays", "hashing"], "date": "2024-01-31", "difficulty": "easy"},
              {"problem": "LRU Cache", "statement": "Design an LRU cache with O(1) get and put.", "link": "https://x.test/lru"}
            ]
        """.trimIndent()
        val r = JsonImportParser.parse(json)!!
        assertEquals(2, r.size)
        assertEquals("Two Sum", r[0].title)
        assertEquals("Acme", r[0].companyTag)
        assertEquals("phone", r[0].interviewStage)
        assertEquals(listOf("arrays", "hashing"), r[0].topicHints)
        assertEquals(LocalDate.of(2024, 1, 31), r[0].reportedDate)
        assertEquals("easy", r[0].difficultyHint)
        assertEquals(ParserConfidence.HIGH, r[0].confidence)
        assertEquals("https://x.test/lru", r[1].sourceUrl)
        assertNull(r[1].companyTag)
        assertNull(r[1].reportedDate)
    }

    @Test fun jsonObjectWithContainerKeyAndStringItems() {
        assertEquals(2, JsonImportParser.parse("""{"problems": [{"title": "A", "text": "statement of A goes here"}, {"title": "B"}]}""")!!.size)
        val plain = JsonImportParser.parse("""["first task text", "second task text"]""")!!
        assertEquals(2, plain.size)
        assertTrue(plain.all { it.title == null && it.confidence == ParserConfidence.LOW })
    }

    @Test fun invalidJsonIsReportedAsNull() {
        assertNull(JsonImportParser.parse("not json at all"))
        assertNull(JsonImportParser.parse("{broken"))
    }

    @Test fun csvWithQuotesCommasAndNewlines() {
        val csv = "title,description,company\n\"Two Sum, variant\",\"Line one\nline two with \"\"quotes\"\"\",Acme\nLRU,Design a cache,\n"
        val r = CsvImportParser.parse(csv)
        assertEquals(2, r.size)
        assertEquals("Two Sum, variant", r[0].title)
        assertEquals("Line one\nline two with \"quotes\"", r[0].text)
        assertEquals("Acme", r[0].companyTag)
        assertNull(r[1].companyTag)
    }

    @Test fun csvSemicolonDelimiterAndRussianHeaders() {
        val csv = "Название;Описание;Компания;Дата\nДве суммы;Найти два числа с заданной суммой;Яндекс;12.05.2024\n"
        val r = CsvImportParser.parse(csv)
        assertEquals(1, r.size)
        assertEquals("Две суммы", r[0].title)
        assertEquals("Яндекс", r[0].companyTag)
        assertEquals(LocalDate.of(2024, 5, 12), r[0].reportedDate)
    }

    @Test fun csvWithoutHeaderUsesTheFirstColumn() {
        val r = CsvImportParser.parse("Reverse a string\nMerge two lists\n")
        assertEquals(2, r.size)
        assertTrue(r.all { it.confidence == ParserConfidence.LOW })
    }

    @Test fun unknownOrEmptyRowsAreDropped() {
        val r = CsvImportParser.parse("title,description\n,\nOnly title,\n")
        assertEquals(1, r.size)
        assertEquals("Only title", r[0].title)
    }
}
