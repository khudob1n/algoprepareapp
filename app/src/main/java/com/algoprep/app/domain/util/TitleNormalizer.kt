package com.algoprep.app.domain.util

/** Normalised title used as a cheap exact-duplicate key (fuzzy matching arrives with the importer). */
object TitleNormalizer {
    fun canonicalKey(title: String): String =
        title.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
