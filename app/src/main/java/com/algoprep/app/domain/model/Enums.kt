package com.algoprep.app.domain.model

enum class Difficulty { EASY, MEDIUM, HARD }
enum class TaskStatus { NEW, LEARNING, REVIEW, MASTERED, FAILED_RECENTLY }
enum class TaskOrigin { SEED, IMPORTED, MANUAL }
enum class SolveOutcome { INDEPENDENT, SMALL_HINT, BIG_HINT, SAW_SOLUTION, NOT_SOLVED }

/** Solved, possibly with help. Looking at the solution or giving up does not count as solved. */
val SolveOutcome.isSolved: Boolean
    get() = this == SolveOutcome.INDEPENDENT || this == SolveOutcome.SMALL_HINT || this == SolveOutcome.BIG_HINT
enum class ErrorType { PATTERN, IMPLEMENTATION, EDGE_CASES, COMPLEXITY, OTHER }
enum class PlannedKind { THEORY, WARMUP, MAIN, REVIEW, ERROR_REVIEW, MOCK }
enum class PlannedStatus { TODO, DONE, SKIPPED }
enum class Bucket { ROADMAP, WEAK, SPACED }
enum class PlanDayStatus { UPCOMING, TODAY, DONE, MISSED }
enum class SessionType { PRACTICE, REVIEW, MOCK, ERROR_REVIEW }
enum class DuplicateDecision { PENDING, MERGED, KEPT_SEPARATE }
enum class BatchStatus { PARSED, SAVED, ROLLED_BACK }
enum class UserLevel { BEGINNER, INTERMEDIATE, ADVANCED }

/** Machine-readable reason a task/day was planned; the UI turns it into localized text. */
enum class ReasonCode {
    WEAK_TOPIC, NOT_SOLVED_YET, REVIEW_DUE, RECENT_FAILURE,
    DIFFICULTY_FIT, FREQUENT_IN_DATASET, ROADMAP_TOPIC, MIXED, CARRIED_OVER, MOCK_DAY,
}
