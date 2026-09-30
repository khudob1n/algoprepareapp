package com.algoprep.app.domain.stats

import com.algoprep.app.domain.model.Pattern
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.Topic

data class CountRow(val id: String, val title: String, val records: Int, val uniqueTasks: Int)

data class ProblemRow(val taskId: Long, val title: String, val mentions: Int, val sources: List<String?>)

/**
 * Statistics over the user's own imported material only: tasks that have at least one mention.
 * These are counts of what was imported, not a prediction of anything.
 */
data class InterviewData(
    val totalRecords: Int,
    val uniqueTasks: Int,
    val topics: List<CountRow>,
    val patterns: List<CountRow>,
    val companies: List<CountRow>,
    val topProblems: List<ProblemRow>,
    /** Records whose task has no topic at all. */
    val unclassifiedRecords: Int,
)

object InterviewDataCalculator {
    const val TOP_PROBLEMS = 10

    fun compute(tasks: List<Task>, topics: List<Topic>, patterns: List<Pattern>): InterviewData {
        val imported = tasks.filter { it.mentions.isNotEmpty() }
        return InterviewData(
            totalRecords = imported.sumOf { it.mentions.size },
            uniqueTasks = imported.size,
            topics = count(imported, topics.associate { it.id to it.title }) { it.topics },
            patterns = count(imported, patterns.associate { it.id to it.title }) { it.patterns },
            companies = companies(imported),
            topProblems = imported.filter { it.mentions.size >= 2 }
                .sortedWith(compareByDescending<Task> { it.mentions.size }.thenBy { it.title.lowercase() })
                .take(TOP_PROBLEMS)
                .map { ProblemRow(it.id, it.title, it.mentions.size, it.mentions.map { m -> m.source }) },
            unclassifiedRecords = imported.filter { it.topics.isEmpty() }.sumOf { it.mentions.size },
        )
    }

    private fun count(tasks: List<Task>, titles: Map<String, String>, ids: (Task) -> Set<String>): List<CountRow> {
        val records = HashMap<String, Int>()
        val unique = HashMap<String, Int>()
        for (t in tasks) for (id in ids(t)) {
            records[id] = (records[id] ?: 0) + t.mentions.size
            unique[id] = (unique[id] ?: 0) + 1
        }
        return records.map { (id, r) -> CountRow(id, titles[id] ?: id, r, unique.getValue(id)) }
            .sortedWith(compareByDescending<CountRow> { it.records }.thenBy { it.title })
    }

    private fun companies(tasks: List<Task>): List<CountRow> {
        val records = HashMap<String, Int>()
        val tasksPerCompany = HashMap<String, MutableSet<Long>>()
        for (t in tasks) for (m in t.mentions) {
            val name = m.companyTag?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            records[name] = (records[name] ?: 0) + 1
            tasksPerCompany.getOrPut(name) { HashSet() } += t.id
        }
        return records.map { (name, r) -> CountRow(name, name, r, tasksPerCompany.getValue(name).size) }
            .sortedWith(compareByDescending<CountRow> { it.records }.thenBy { it.title })
    }
}
