package com.algoprep.app.domain.importer

import com.algoprep.app.domain.ai.AiAssistant
import com.algoprep.app.domain.ai.NoAiAssistant

/** Parse step of Import -> Parse -> Review -> Save. Produces editable drafts; nothing is saved here. */
class ImportParser(
    private val classifier: TaskClassifier,
    private val ai: AiAssistant = NoAiAssistant(),
) {
    suspend fun parse(source: ImportSource): List<CandidateDraft> {
        val raw = ai.extractTasks(source) ?: parseLocally(source)
        return raw.mapIndexed { index, r -> toDraft("c$index", r) }
    }

    private fun parseLocally(source: ImportSource): List<RawCandidate> = when (source.format) {
        ImportFormat.JSON -> JsonImportParser.parse(source.text) ?: TextImportParser.parse(source.text)
        ImportFormat.CSV -> CsvImportParser.parse(source.text)
        ImportFormat.MARKDOWN, ImportFormat.TEXT -> TextImportParser.parse(source.text)
    }

    private fun toDraft(tempId: String, r: RawCandidate): CandidateDraft {
        var title = r.title
        var text = r.text
        var guessed = r.titleGuessed
        if (title.isNullOrBlank()) {
            val (t, rest) = TextImportParser.guessTitle(text.trim(), r.sourceUrl)
            title = t
            text = rest.ifEmpty { text }
            guessed = true
        }
        val classification = classifier.classify(title, text, r.topicHints, r.patternHints)
        return CandidateDraft(
            tempId = tempId,
            selected = r.confidence != ParserConfidence.LOW,
            title = title,
            text = text,
            topics = classification.topics,
            patterns = classification.patterns,
            difficulty = TaskClassifier.parseDifficulty(r.difficultyHint) ?: TaskClassifier.difficultyFromText(text),
            source = r.source,
            sourceUrl = r.sourceUrl,
            companyTag = r.companyTag,
            interviewStage = r.interviewStage,
            roleLevel = r.roleLevel,
            reportedDate = r.reportedDate,
            notes = r.notes,
            confidence = r.confidence,
            titleGuessed = guessed,
        )
    }

    companion object {
        fun detectFormat(fileName: String, text: String): ImportFormat {
            val name = fileName.lowercase()
            return when {
                name.endsWith(".json") -> ImportFormat.JSON
                name.endsWith(".csv") || name.endsWith(".tsv") -> ImportFormat.CSV
                name.endsWith(".md") || name.endsWith(".markdown") -> ImportFormat.MARKDOWN
                name.endsWith(".txt") -> ImportFormat.TEXT
                else -> sniff(text)
            }
        }

        private fun sniff(text: String): ImportFormat {
            val t = text.trim().removePrefix("﻿")
            if ((t.startsWith("[") || t.startsWith("{")) && JsonImportParser.parse(t) != null) return ImportFormat.JSON
            val header = CsvImportParser.readRows(t.lineSequence().take(1).joinToString("\n")).firstOrNull().orEmpty()
            if (header.size >= 2 && header.count { KeywordDictionary.fieldFor(it) != null } >= 2) return ImportFormat.CSV
            return if (t.lineSequence().any { it.trimStart().startsWith("#") }) ImportFormat.MARKDOWN else ImportFormat.TEXT
        }
    }
}
