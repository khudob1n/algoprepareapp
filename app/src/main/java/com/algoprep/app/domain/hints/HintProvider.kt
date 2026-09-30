package com.algoprep.app.domain.hints

import com.algoprep.app.domain.model.Task

/**
 * Progressive hints for a task, from the gentlest to the most revealing.
 * Local-only for now; a future AI backend can implement the same interface.
 */
interface HintProvider {
    fun hintsFor(task: Task): List<String>
}

/** pattern id -> language code -> hints (gentle to revealing). */
typealias PatternHintTemplates = Map<String, Map<String, List<String>>>

/**
 * Uses the hints stored with the task when there are any; otherwise falls back to generic
 * per-pattern hints, and to nothing when the task has no known pattern (never invents hints).
 */
class TemplateHintProvider(
    private val templates: PatternHintTemplates,
    private val language: () -> String,
) : HintProvider {
    override fun hintsFor(task: Task): List<String> {
        if (task.hints.isNotEmpty()) return task.hints
        val lang = language()
        for (pattern in task.patterns) {
            val byLang = templates[pattern] ?: continue
            val hints = byLang[lang] ?: byLang[FALLBACK_LANGUAGE]
            if (!hints.isNullOrEmpty()) return hints
        }
        return emptyList()
    }

    private companion object {
        const val FALLBACK_LANGUAGE = "en"
    }
}
