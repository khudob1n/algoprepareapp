package com.algoprep.app.domain.importer

import com.algoprep.app.domain.util.TitleNormalizer

/**
 * Finds possible duplicates by comparing normalised titles (exact key, token overlap) and
 * statements (overlap of 3-word shingles). It only reports; it never merges or deletes.
 * An inverted index keeps it fast for imports of several hundred entries.
 */
class DuplicateIndex {
    data class Match(val id: String, val score: Double)

    private class Doc(val id: String, val canonical: String, val titleTokens: Set<String>, val shingles: Set<String>, val wordCount: Int)

    private val docs = ArrayList<Doc>()
    private val tokenPostings = HashMap<String, MutableList<Int>>()
    private val shinglePostings = HashMap<String, MutableList<Int>>()

    fun add(id: String, title: String, text: String) {
        val doc = analyse(id, title, text)
        val index = docs.size
        docs += doc
        doc.titleTokens.forEach { tokenPostings.getOrPut(it) { ArrayList() } += index }
        doc.shingles.forEach { shinglePostings.getOrPut(it) { ArrayList() } += index }
    }

    /** Best matches first. */
    fun matches(title: String, text: String): List<Match> {
        val query = analyse("", title, text)
        val candidates = HashSet<Int>()
        query.titleTokens.forEach { tokenPostings[it]?.let(candidates::addAll) }
        val shingleHits = HashMap<Int, Int>()
        query.shingles.forEach { s -> shinglePostings[s]?.forEach { shingleHits[it] = (shingleHits[it] ?: 0) + 1 } }
        shingleHits.filterValues { it >= 2 }.keys.let(candidates::addAll)

        return candidates.mapNotNull { idx ->
            val doc = docs[idx]
            val score = score(query, doc)
            if (score != null) Match(doc.id, score) else null
        }.sortedByDescending { it.score }
    }

    private fun score(q: Doc, d: Doc): Double? {
        if (q.canonical.isNotEmpty() && q.canonical == d.canonical) return 1.0
        val titleSim = if (q.titleTokens.size >= 2 && d.titleTokens.size >= 2) jaccard(q.titleTokens, d.titleTokens) else 0.0
        val textSim = if (q.wordCount >= MIN_WORDS && d.wordCount >= MIN_WORDS) jaccard(q.shingles, d.shingles) else 0.0
        return if (titleSim >= TITLE_THRESHOLD || textSim >= TEXT_THRESHOLD) maxOf(titleSim, textSim) else null
    }

    private fun analyse(id: String, title: String, text: String): Doc {
        val textTokens = tokens(text)
        val shingles = if (textTokens.size >= SHINGLE) {
            textTokens.windowed(SHINGLE).map { it.joinToString(" ") }.toSet()
        } else emptySet()
        return Doc(id, TitleNormalizer.canonicalKey(title), tokens(title).toSet(), shingles, textTokens.size)
    }

    private fun tokens(s: String): List<String> = TitleNormalizer.canonicalKey(s).split(' ').filter { it.isNotEmpty() && it !in STOP_WORDS }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    private companion object {
        const val TITLE_THRESHOLD = 0.8
        const val TEXT_THRESHOLD = 0.6
        const val MIN_WORDS = 12
        const val SHINGLE = 3
        val STOP_WORDS = setOf(
            "the", "a", "an", "of", "in", "to", "and", "or", "for", "with", "is", "are", "on", "at", "by",
            "и", "в", "на", "с", "по", "для", "из", "к", "о", "от", "за",
        )
    }
}
