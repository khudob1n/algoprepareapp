package com.algoprep.app.domain.planning

import com.algoprep.app.domain.model.TopicSkill

/**
 * A topic counts as weak when there is evidence (>= [MIN_ATTEMPTS] attempts and a low score),
 * or, before any evidence exists, when the user explicitly rated it 1 ("new to me").
 */
object WeakTopics {
    const val MIN_ATTEMPTS = 3
    const val WEAK_SCORE = 0.55
    const val MAX_WEAK = 3

    fun isWeak(skill: TopicSkill): Boolean =
        if (skill.attempts >= MIN_ATTEMPTS) skill.score < WEAK_SCORE else skill.selfRating <= 1

    /** Weakest first; ties broken by topic id so the result is deterministic. */
    fun pick(skills: Collection<TopicSkill>, limit: Int = MAX_WEAK): List<TopicSkill> =
        skills.filter(::isWeak).sortedWith(compareBy({ it.score }, { it.topicId })).take(limit)
}
