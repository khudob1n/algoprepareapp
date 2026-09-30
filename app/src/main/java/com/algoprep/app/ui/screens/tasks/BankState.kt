package com.algoprep.app.ui.screens.tasks

import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus

data class BankFilter(
    val query: String = "",
    val status: TaskStatus? = null,
    val difficulty: Difficulty? = null,
    val topicId: String? = null,
    val importedOnly: Boolean = false,
) {
    val isActive: Boolean get() = query.isNotBlank() || status != null || difficulty != null || topicId != null || importedOnly
}

/** Case-insensitive search over title and statement plus exact filters; sorted by title. */
fun filterTasks(tasks: List<Task>, filter: BankFilter): List<Task> {
    val q = filter.query.trim().lowercase()
    return tasks.asSequence()
        .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.originalText.lowercase().contains(q) }
        .filter { filter.status == null || it.status == filter.status }
        .filter { filter.difficulty == null || it.difficulty == filter.difficulty }
        .filter { filter.topicId == null || filter.topicId in it.topics }
        .filter { !filter.importedOnly || it.mentions.isNotEmpty() }
        .sortedBy { it.title.lowercase() }
        .toList()
}
