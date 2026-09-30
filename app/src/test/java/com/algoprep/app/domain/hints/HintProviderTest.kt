package com.algoprep.app.domain.hints

import com.algoprep.app.core.AppJson
import com.algoprep.app.data.seed.SeedParser
import com.algoprep.app.domain.model.SessionPhase
import com.algoprep.app.domain.model.SessionType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.phase
import com.algoprep.app.domain.planning.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class HintProviderTest {
    private val templates: PatternHintTemplates = mapOf(
        "dfs" to mapOf("en" to listOf("e1", "e2", "e3"), "ru" to listOf("r1", "r2", "r3")),
        "trie" to mapOf("en" to listOf("t1")),
    )

    private fun withPatterns(vararg p: String, hints: List<String> = emptyList()) =
        task(1).copy(patterns = p.toSet(), hints = hints)

    @Test fun taskOwnHintsWin() {
        val provider = TemplateHintProvider(templates) { "en" }
        assertEquals(listOf("own"), provider.hintsFor(withPatterns("dfs", hints = listOf("own"))))
    }

    @Test fun fallsBackToPatternTemplatesInUserLanguage() {
        assertEquals(listOf("r1", "r2", "r3"), TemplateHintProvider(templates) { "ru" }.hintsFor(withPatterns("dfs")))
        assertEquals(listOf("e1", "e2", "e3"), TemplateHintProvider(templates) { "en" }.hintsFor(withPatterns("dfs")))
    }

    @Test fun unknownLanguageFallsBackToEnglish() {
        assertEquals(listOf("t1"), TemplateHintProvider(templates) { "ru" }.hintsFor(withPatterns("trie")))
    }

    @Test fun noPatternAndNoHintsMeansNoHints() {
        assertTrue(TemplateHintProvider(templates) { "en" }.hintsFor(withPatterns()).isEmpty())
        assertTrue(TemplateHintProvider(templates) { "en" }.hintsFor(withPatterns("unknown")).isEmpty())
    }

    @Test fun bundledTemplatesCoverEveryPatternInBothLanguages() {
        val json = File("src/main/assets/hints/pattern_hints.json").readText()
        val bundled: PatternHintTemplates = AppJson.decodeFromString(json)
        val patterns = SeedParser.parse(
            File("src/main/assets/seed/topics.json").readText(),
            File("src/main/assets/seed/patterns.json").readText(),
            File("src/main/assets/seed/roadmap.json").readText(),
            File("src/main/assets/seed/tasks_seed.json").readText(),
        ).patterns
        for (p in patterns) {
            for (lang in listOf("en", "ru")) {
                assertEquals("${p.id}/$lang", 3, bundled[p.id]?.get(lang)?.size)
            }
        }
    }

    @Test fun sessionPhaseIsDerivedFromDurationAndFinishTime() {
        val base = SolveSession(1, 1, null, SessionType.PRACTICE, Instant.EPOCH, null, 0, 0, null, null, "", LocalDate.EPOCH)
        assertEquals(SessionPhase.RUNNING, base.phase)
        assertEquals(SessionPhase.AWAITING_RESULT, base.copy(durationSec = 90).phase)
        assertEquals(
            SessionPhase.FINISHED,
            base.copy(durationSec = 90, finishedAt = Instant.EPOCH, outcome = SolveOutcome.INDEPENDENT).phase,
        )
    }
}
