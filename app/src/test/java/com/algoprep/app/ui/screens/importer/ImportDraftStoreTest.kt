package com.algoprep.app.ui.screens.importer

import com.algoprep.app.domain.importer.CandidateDraft
import com.algoprep.app.domain.importer.DuplicateChoice
import com.algoprep.app.domain.importer.DuplicateMatch
import com.algoprep.app.domain.importer.DuplicateTarget
import com.algoprep.app.domain.importer.ImportFormat
import com.algoprep.app.domain.importer.ImportSource
import com.algoprep.app.domain.importer.ParsedImport
import com.algoprep.app.domain.importer.ParserConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportDraftStoreTest {
    private fun draft(id: String, conf: ParserConfidence, selected: Boolean = true, dup: Boolean = false) = CandidateDraft(
        tempId = id, selected = selected, title = id, text = id, topics = emptySet(), patterns = emptySet(), difficulty = null,
        source = null, sourceUrl = null, companyTag = null, interviewStage = null, roleLevel = null, reportedDate = null,
        notes = null, confidence = conf, titleGuessed = false,
        duplicate = if (dup) DuplicateMatch(DuplicateTarget.Bank(1, "x"), 0.9) else null,
    )

    private fun store(vararg drafts: CandidateDraft) = ImportDraftStore().apply {
        start(ParsedImport(ImportSource("f", "t", ImportFormat.TEXT), drafts.toList()))
    }

    @Test fun updatesOnlyTheTargetDraft() {
        val s = store(draft("a", ParserConfidence.HIGH), draft("b", ParserConfidence.LOW))
        s.update("b") { it.copy(title = "edited") }
        assertEquals(listOf("a", "edited"), s.session.value!!.drafts.map { it.title })
    }

    @Test fun bulkSelectionByConfidence() {
        val s = store(draft("a", ParserConfidence.HIGH), draft("b", ParserConfidence.MEDIUM), draft("c", ParserConfidence.LOW))
        s.setSelected(ImportDraftStore.HIGH_CONFIDENCE)
        assertEquals(listOf(true, false, false), s.session.value!!.drafts.map { it.selected })
        s.setSelected { true }
        assertEquals(3, s.session.value!!.selectedCount)
        s.setSelected { false }
        assertEquals(0, s.session.value!!.selectedCount)
    }

    @Test fun undecidedDuplicatesCountOnlySelectedOnes() {
        val s = store(draft("a", ParserConfidence.HIGH, dup = true), draft("b", ParserConfidence.HIGH, selected = false, dup = true), draft("c", ParserConfidence.HIGH))
        assertEquals(1, s.session.value!!.undecidedDuplicates)
        s.update("a") { it.copy(choice = DuplicateChoice.KEEP_SEPARATE) }
        assertEquals(0, s.session.value!!.undecidedDuplicates)
    }

    @Test fun clearDropsTheSession() {
        val s = store(draft("a", ParserConfidence.HIGH))
        s.clear()
        assertNull(s.session.value)
    }
}
