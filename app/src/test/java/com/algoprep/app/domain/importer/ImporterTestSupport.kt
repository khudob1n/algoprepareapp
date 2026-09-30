package com.algoprep.app.domain.importer

import com.algoprep.app.data.seed.SeedParser
import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.Topic
import java.io.File

/** Real topic and pattern tables from the bundled seed, so classifier tests use production ids. */
object ImporterFixtures {
    private val seed = SeedParser.parse(
        File("src/main/assets/seed/topics.json").readText(),
        File("src/main/assets/seed/patterns.json").readText(),
        File("src/main/assets/seed/roadmap.json").readText(),
        File("src/main/assets/seed/tasks_seed.json").readText(),
    )
    val topics = seed.topics.mapIndexed { i, t -> Topic(t.id, t.title, t.parentId, i) }
    val patterns = seed.patterns.map { Pattern(it.id, it.title, it.topicHint) }
    val classifier = TaskClassifier(topics, patterns)
    val parser = ImportParser(classifier)
}
