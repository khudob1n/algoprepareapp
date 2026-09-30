package com.algoprep.app.domain.importer

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.Topic
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class Classification(val topics: Set<String>, val patterns: Set<String>)

/**
 * Local heuristic classifier. It only proposes tags from explicit hints and keywords;
 * difficulty and dates are read only when the material states them.
 */
class TaskClassifier(
    private val topics: List<Topic>,
    private val patterns: List<Pattern>,
) {
    fun classify(title: String, text: String, topicHints: List<String>, patternHints: List<String>): Classification {
        val explicitTopics = topicHints.mapNotNull { resolveTopic(it) }.toSet()
        val explicitPatterns = patternHints.mapNotNull { resolvePattern(it) }.toSet()
        val inferredTopics = infer(KeywordDictionary.topics, title, text).filter { it in knownTopics }
        val inferredPatterns = infer(KeywordDictionary.patterns, title, text).filter { it in knownPatterns }
        return Classification(
            topics = explicitTopics + inferredTopics.take(MAX_INFERRED),
            patterns = explicitPatterns + inferredPatterns.take(MAX_INFERRED),
        )
    }

    private val knownTopics = topics.map { it.id }.toSet()
    private val knownPatterns = patterns.map { it.id }.toSet()

    private fun resolveTopic(hint: String): String? {
        val h = hint.trim().lowercase()
        if (h.isEmpty()) return null
        topics.firstOrNull { it.id == h || it.title.lowercase() == h }?.let { return it.id }
        return KeywordDictionary.topics.entries
            .firstOrNull { (id, words) -> id in knownTopics && words.any { matches(h, it) } }?.key
    }

    private fun resolvePattern(hint: String): String? {
        val h = hint.trim().lowercase()
        if (h.isEmpty()) return null
        patterns.firstOrNull { it.id == h || it.title.lowercase() == h }?.let { return it.id }
        return KeywordDictionary.patterns.entries
            .firstOrNull { (id, words) -> id in knownPatterns && words.any { matches(h, it) } }?.key
    }

    /** Ids ordered by score (title hits count double), best first. */
    private fun infer(dictionary: Map<String, List<String>>, title: String, text: String): List<String> {
        val t = title.lowercase()
        val body = text.lowercase()
        return dictionary.mapNotNull { (id, words) ->
            val score = words.sumOf { w -> (if (matches(t, w)) 2 else 0) + (if (matches(body, w)) 1 else 0) }
            if (score > 0) id to score else null
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).map { it.first }
    }

    private fun matches(haystack: String, keyword: String): Boolean =
        if (keyword.length <= SHORT_KEYWORD && keyword.all { it.code < 128 }) {
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(keyword) + "(?![\\p{L}\\p{N}])").containsMatchIn(haystack)
        } else {
            haystack.contains(keyword)
        }

    companion object {
        private const val MAX_INFERRED = 3
        private const val SHORT_KEYWORD = 4

        private val DIFFICULTY_LINE = Regex("(?i)difficulty\\s*[:\\-]\\s*(\\p{L}+)|сложность\\s*[:\\-]\\s*(\\p{L}+)")

        fun parseDifficulty(raw: String?): Difficulty? {
            val v = raw?.trim()?.lowercase() ?: return null
            return when {
                v.startsWith("easy") || v.startsWith("лёг") || v.startsWith("лег") || v == "простая" || v == "простой" -> Difficulty.EASY
                v.startsWith("medium") || v.startsWith("средн") -> Difficulty.MEDIUM
                v.startsWith("hard") || v.startsWith("слож") || v.startsWith("тяж") -> Difficulty.HARD
                else -> null
            }
        }

        /** Difficulty stated inside the text itself ("Difficulty: Hard"), or null. */
        fun difficultyFromText(text: String): Difficulty? {
            val m = DIFFICULTY_LINE.find(text) ?: return null
            return parseDifficulty(m.groupValues[1].ifEmpty { m.groupValues[2] })
        }

        private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
        private val DOTTED = DateTimeFormatter.ofPattern("dd.MM.uuuu")
        private val SLASHED = DateTimeFormatter.ofPattern("uuuu/MM/dd")

        /** Only unambiguous formats are accepted; anything else stays unknown. */
        fun parseDate(raw: String?): LocalDate? {
            val v = raw?.trim() ?: return null
            for (f in listOf(ISO, DOTTED, SLASHED)) {
                try {
                    return LocalDate.parse(v, f)
                } catch (_: DateTimeParseException) {
                    // try next
                }
            }
            return null
        }
    }
}
