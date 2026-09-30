package com.algoprep.app.domain.importer

import com.algoprep.app.domain.model.Difficulty
import java.time.LocalDate

enum class ImportFormat { JSON, CSV, MARKDOWN, TEXT }

enum class ParserConfidence { HIGH, MEDIUM, LOW }

data class ImportSource(val name: String, val text: String, val format: ImportFormat)

/**
 * What a parser found in the raw material. Everything the source did not say stays null:
 * the importer never invents metadata.
 */
data class RawCandidate(
    val title: String?,
    val text: String,
    val source: String? = null,
    val sourceUrl: String? = null,
    val companyTag: String? = null,
    val interviewStage: String? = null,
    val roleLevel: String? = null,
    val reportedDate: LocalDate? = null,
    val difficultyHint: String? = null,
    val topicHints: List<String> = emptyList(),
    val patternHints: List<String> = emptyList(),
    val notes: String? = null,
    val confidence: ParserConfidence,
    val titleGuessed: Boolean = false,
)

sealed interface DuplicateTarget {
    data class Bank(val taskId: Long, val title: String) : DuplicateTarget
    data class Batch(val tempId: String, val title: String) : DuplicateTarget
}

data class DuplicateMatch(val target: DuplicateTarget, val score: Double)

enum class DuplicateChoice { UNDECIDED, MERGE, KEEP_SEPARATE }

/** A parsed task proposal on the review screen; every field is editable by the user. */
data class CandidateDraft(
    val tempId: String,
    val selected: Boolean,
    val title: String,
    val text: String,
    val topics: Set<String>,
    val patterns: Set<String>,
    val difficulty: Difficulty?,
    val source: String?,
    val sourceUrl: String?,
    val companyTag: String?,
    val interviewStage: String?,
    val roleLevel: String?,
    val reportedDate: LocalDate?,
    val notes: String?,
    val confidence: ParserConfidence,
    val titleGuessed: Boolean,
    val duplicate: DuplicateMatch? = null,
    val choice: DuplicateChoice = DuplicateChoice.UNDECIDED,
)

data class ParsedImport(val source: ImportSource, val candidates: List<CandidateDraft>)
