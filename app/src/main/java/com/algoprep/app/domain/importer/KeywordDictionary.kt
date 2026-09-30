package com.algoprep.app.domain.importer

/**
 * Keyword tables (English and Russian) used by the local heuristic classifier.
 * Short ASCII keywords (<= 4 chars, e.g. "dfs", "bst") are matched as whole words, longer ones as substrings.
 * The tables only propose tags; the user confirms them on the review screen.
 */
object KeywordDictionary {
    val topics: Map<String, List<String>> = mapOf(
        "arrays" to listOf("array", "subarray", "matrix", "массив", "подмассив", "матриц"),
        "hashing" to listOf("hash", "dictionary", "frequency", "anagram", "хеш", "хэш", "словар", "частот", "анаграмм"),
        "strings" to listOf("string", "substring", "palindrome", "character", "строк", "подстрок", "палиндром", "символ"),
        "two_pointers" to listOf("two pointer", "two-pointer", "два указател", "container with most water"),
        "sliding_window" to listOf("sliding window", "longest substring", "at most k", "скользящ", "окно"),
        "stack" to listOf("stack", "parenthes", "bracket", "стек", "скобк"),
        "binary_search" to listOf("binary search", "rotated", "sorted array", "бинарн", "двоичн", "отсортирован"),
        "linked_list" to listOf("linked list", "listnode", "singly", "связн", "односвязн"),
        "trees" to listOf("tree", "bst", "дерев", "бинарное дерев"),
        "graphs" to listOf("graph", "island", "grid", "course schedule", "граф", "вершин", "остров"),
        "heap" to listOf("heap", "priority queue", "kth largest", "k-th largest", "top k", "куча", "приоритетн"),
        "intervals" to listOf("interval", "meeting room", "overlapping", "интервал", "пересечени"),
        "greedy" to listOf("greedy", "жадн"),
        "dp" to listOf("dynamic programming", "dp", "subsequence", "knapsack", "coin change", "динамическ", "подпоследовательн", "рюкзак"),
        "backtracking" to listOf("backtrack", "permutation", "combination", "subsets", "n-queens", "перестановк", "сочетани", "подмножеств", "перебор"),
        "tries" to listOf("trie", "prefix tree", "autocomplete", "префиксн"),
    )

    val patterns: Map<String, List<String>> = mapOf(
        "hash_map" to listOf("hash map", "hashmap", "hash table", "frequency map", "хеш-таблиц", "хэш-таблиц"),
        "prefix_sum" to listOf("prefix sum", "cumulative sum", "префиксн сумм"),
        "two_pointers" to listOf("two pointer", "two-pointer", "два указател"),
        "sliding_window" to listOf("sliding window", "скользящее окно"),
        "monotonic_stack" to listOf("monotonic", "next greater", "next smaller", "монотонн"),
        "binary_search" to listOf("binary search", "бинарный поиск", "двоичный поиск"),
        "fast_slow" to listOf("fast and slow", "tortoise", "hare", "cycle detection", "быстрый и медленный"),
        "dfs" to listOf("dfs", "depth-first", "depth first", "в глубину"),
        "bfs" to listOf("bfs", "breadth-first", "breadth first", "level order", "в ширину"),
        "topological_sort" to listOf("topological", "prerequisite", "course schedule", "топологическ"),
        "union_find" to listOf("union find", "union-find", "disjoint set", "dsu", "непересекающихся множеств"),
        "top_k_heap" to listOf("top k", "kth largest", "k-th largest", "min-heap", "max-heap", "priority queue"),
        "merge_intervals" to listOf("merge intervals", "overlapping intervals", "слияние интервалов"),
        "greedy" to listOf("greedy", "жадный"),
        "dp_1d" to listOf("climbing stairs", "house robber", "coin change", "longest increasing subsequence"),
        "dp_2d" to listOf("edit distance", "longest common subsequence", "2d dp", "unique paths"),
        "backtracking" to listOf("backtrack", "перебор с возвратом"),
        "trie" to listOf("trie", "prefix tree", "префиксное дерево"),
    )

    /** Field-name aliases used by JSON keys, CSV headers and `Key: value` lines. */
    val fieldAliases: Map<String, Set<String>> = mapOf(
        "title" to setOf("title", "name", "problem", "question", "task", "название", "задача", "вопрос"),
        "text" to setOf("text", "description", "statement", "condition", "body", "content", "details", "описание", "условие", "текст"),
        "source" to setOf("source", "origin", "from", "report", "источник"),
        "url" to setOf("url", "link", "sourceurl", "source_url", "ссылка"),
        "company" to setOf("company", "companytag", "company_tag", "organization", "employer", "компания"),
        "stage" to setOf("stage", "round", "interviewstage", "interview_stage", "этап", "раунд"),
        "level" to setOf("level", "role", "position", "rolelevel", "role_level", "grade", "уровень", "роль", "должность"),
        "date" to setOf("date", "reporteddate", "reported_date", "reported", "when", "дата"),
        "difficulty" to setOf("difficulty", "сложность"),
        "topics" to setOf("topics", "topic", "tags", "category", "темы", "тема", "теги"),
        "patterns" to setOf("patterns", "pattern", "паттерны", "паттерн"),
        "notes" to setOf("notes", "note", "comment", "заметки", "заметка", "комментарий"),
    )

    fun fieldFor(key: String): String? {
        val k = key.trim().lowercase()
        return fieldAliases.entries.firstOrNull { k in it.value }?.key
    }
}
