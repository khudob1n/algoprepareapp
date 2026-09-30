package com.algoprep.app.domain.ai

import com.algoprep.app.domain.importer.ImportSource
import com.algoprep.app.domain.importer.RawCandidate

/**
 * Extension point for a future AI backend. The app is local-first and ships without one:
 * the default implementation returns null ("I can't"), and callers fall back to the local heuristics.
 * Nothing in the app pretends that a model is involved.
 */
interface AiAssistant {
    /** Extracts task candidates from raw material, or null when unavailable. */
    suspend fun extractTasks(source: ImportSource): List<RawCandidate>?
}

class NoAiAssistant : AiAssistant {
    override suspend fun extractTasks(source: ImportSource): List<RawCandidate>? = null
}
