package com.algoprep.app.ui.screens.importer

import com.algoprep.app.domain.importer.CandidateDraft
import com.algoprep.app.domain.importer.DuplicateChoice
import com.algoprep.app.domain.importer.ImportSource
import com.algoprep.app.domain.importer.ParsedImport
import com.algoprep.app.domain.importer.ParserConfidence
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSession(val source: ImportSource, val drafts: List<CandidateDraft>) {
    val selectedCount: Int get() = drafts.count { it.selected }
    val undecidedDuplicates: Int
        get() = drafts.count { it.selected && it.duplicate != null && it.choice == DuplicateChoice.UNDECIDED }
}

/**
 * Holds the parse result between the Import and Review screens. The drafts are user edits in
 * progress and are dropped after saving or cancelling; nothing is stored until Save.
 */
@Singleton
class ImportDraftStore @Inject constructor() {
    private val _session = MutableStateFlow<ImportSession?>(null)
    val session: StateFlow<ImportSession?> = _session.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once after a review session has been saved. */
    val saved: SharedFlow<Unit> = _saved

    fun finishSaved() {
        _session.value = null
        _saved.tryEmit(Unit)
    }

    fun start(parsed: ParsedImport) {
        _session.value = ImportSession(parsed.source, parsed.candidates)
    }

    fun clear() {
        _session.value = null
    }

    fun update(tempId: String, transform: (CandidateDraft) -> CandidateDraft) {
        _session.update { s -> s?.copy(drafts = s.drafts.map { if (it.tempId == tempId) transform(it) else it }) }
    }

    fun setSelected(predicate: (CandidateDraft) -> Boolean) {
        _session.update { s -> s?.copy(drafts = s.drafts.map { it.copy(selected = predicate(it)) }) }
    }

    companion object {
        val HIGH_CONFIDENCE: (CandidateDraft) -> Boolean = { it.confidence == ParserConfidence.HIGH }
    }
}
