package com.algoprep.app.domain.importer

/**
 * Parser for Markdown / plain text: splits a document into task-sized segments and pulls out
 * `Key: value` metadata lines and links. It proposes; the user decides on the review screen.
 *
 * Segmentation, first rule that applies wins:
 *  1. headings of the shallowest level that occurs at least twice (sections);
 *  2. horizontal rules;
 *  3. a numbered list of 3+ items;
 *  4. a bullet list of 3+ items;
 *  5. blank-line separated paragraphs.
 * A section that is itself a list of 2+ items is split into its items (the section heading becomes their source).
 */
object TextImportParser {
    private val headingRegex = Regex("^(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val ruleRegex = Regex("^\\s*([-*_=])\\1{2,}\\s*$")
    private val numberedRegex = Regex("^\\s*(\\d{1,3})[.)]\\s+(.*)$")
    private val bulletRegex = Regex("^\\s*[-*•]\\s+(.*)$")
    private val metaRegex = Regex("^\\s*(?:[-*•]\\s*)?\\**([\\p{L}_ ]{2,20}?)\\**\\s*[:：]\\s*(.+?)\\s*$")
    private val urlRegex = Regex("https?://[^\\s)>\\]]+")
    private val cueRegex = Regex(
        "(?iu)\\b(given|return|find|implement|design|write a|determine|check whether|compute|count|reverse|merge|detect|" +
            "validate|array|string|tree|graph|linked list|matrix|subarray|substring|asked|question)\\b|" +
            "дан[аоы]?\\b|найд|верни|реализ|напиш|определ|провер|вычисл|подсчит|массив|строк|дерев|граф|спросил|задач",
    )

    private const val MIN_LIST_ITEMS = 3
    private const val MIN_PREAMBLE = 80
    private const val MIN_PARAGRAPH = 15
    private const val MAX_TITLE = 90
    private const val HIGH_BODY = 40

    private class Segment(
        val heading: String?,
        val lines: List<String>,
        val contextHeading: String? = null,
        val fromList: Boolean = false,
    )

    fun parse(text: String): List<RawCandidate> {
        val lines = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').lines()
        return segment(lines).mapNotNull { toCandidate(it) }
    }

    // ---- segmentation -------------------------------------------------------------------------

    private fun segment(lines: List<String>): List<Segment> {
        val headings = lines.mapIndexedNotNull { i, l -> headingRegex.matchEntire(l)?.let { Triple(i, it.groupValues[1].length, it.groupValues[2]) } }
        if (headings.isNotEmpty()) {
            val splitLevel = headings.groupBy { it.second }.filter { it.value.size >= 2 }.keys.minOrNull()
            if (splitLevel != null) return splitByHeadings(lines, headings.filter { it.second == splitLevel })
            if (headings.size == 1) {
                val (idx, _, title) = headings.first()
                val before = lines.take(idx)
                val after = lines.drop(idx + 1)
                return listOfNotNull(before.takeIf { significant(it) }?.let { Segment(null, it) }) + Segment(title, after)
            }
        }
        splitByRules(lines)?.let { return it }
        splitByList(lines, numberedRegex)?.let { return it }
        splitByList(lines, bulletRegex)?.let { return it }
        return splitByParagraphs(lines)
    }

    private fun significant(lines: List<String>) = lines.joinToString("\n").trim().length >= MIN_PREAMBLE

    private fun splitByHeadings(lines: List<String>, splits: List<Triple<Int, Int, String>>): List<Segment> {
        val result = ArrayList<Segment>()
        val preamble = lines.take(splits.first().first)
        if (significant(preamble.filterNot { headingRegex.matches(it) })) result += Segment(null, preamble)
        splits.forEachIndexed { i, (idx, _, title) ->
            val end = splits.getOrNull(i + 1)?.first ?: lines.size
            val body = lines.subList(idx + 1, end)
            val items = listItems(body, numberedRegex)?.takeIf { it.size >= 2 } ?: listItems(body, bulletRegex)?.takeIf { it.size >= 2 }
            if (items != null) items.forEach { result += Segment(null, it, contextHeading = title, fromList = true) }
            else result += Segment(title, body)
        }
        return result
    }

    private fun splitByRules(lines: List<String>): List<Segment>? {
        if (lines.none { ruleRegex.matches(it) }) return null
        val parts = ArrayList<List<String>>()
        var current = ArrayList<String>()
        for (l in lines) {
            if (ruleRegex.matches(l)) { parts += current; current = ArrayList() } else current += l
        }
        parts += current
        val nonEmpty = parts.filter { p -> p.any { it.isNotBlank() } }
        return if (nonEmpty.size >= 2) nonEmpty.map { Segment(null, it) } else null
    }

    private fun splitByList(lines: List<String>, marker: Regex): List<Segment>? {
        val items = listItems(lines, marker) ?: return null
        if (items.size < MIN_LIST_ITEMS) return null
        val firstItem = lines.indexOfFirst { marker.matches(it) }
        val result = ArrayList<Segment>()
        val preamble = lines.take(firstItem)
        if (significant(preamble)) result += Segment(null, preamble)
        items.forEach { result += Segment(null, it, fromList = true) }
        return result
    }

    /** Items of a list: each marker line plus its continuation lines. Null if the text has no marker line. */
    private fun listItems(lines: List<String>, marker: Regex): List<List<String>>? {
        val items = ArrayList<MutableList<String>>()
        for (l in lines) {
            val m = marker.matchEntire(l)
            if (m != null) items += mutableListOf(m.groupValues.last())
            else if (items.isNotEmpty() && l.isNotBlank() && (l.startsWith(" ") || l.startsWith("\t"))) items.last() += l.trim()
            else if (items.isNotEmpty() && l.isNotBlank() && !headingRegex.matches(l)) items.last() += l.trim()
        }
        return items.takeIf { it.isNotEmpty() }
    }

    private fun splitByParagraphs(lines: List<String>): List<Segment> {
        val result = ArrayList<Segment>()
        var current = ArrayList<String>()
        fun flush() {
            if (current.joinToString(" ").trim().length >= MIN_PARAGRAPH) result += Segment(null, current)
            current = ArrayList()
        }
        for (l in lines) if (l.isBlank()) flush() else current += l
        flush()
        return result
    }

    // ---- segment -> candidate -----------------------------------------------------------------

    private fun toCandidate(segment: Segment): RawCandidate? {
        val meta = HashMap<String, String>()
        val body = ArrayList<String>()
        for (line in segment.lines) {
            val m = metaRegex.matchEntire(line)
            val field = m?.let { KeywordDictionary.fieldFor(it.groupValues[1]) }
            if (m != null && field != null && field !in meta) meta[field] = m.groupValues[2].trim().trim('*')
            else body += line
        }
        var text = body.joinToString("\n").trim()
        var sourceUrl = meta["url"]
        if (sourceUrl == null) {
            sourceUrl = urlRegex.find(text)?.value
        }
        // A line that only holds the link is metadata, not task text.
        if (sourceUrl != null) {
            text = text.lines().filterNot { it.trim().trimEnd('.', ',') == sourceUrl }.joinToString("\n").trim()
        }
        if (text.isEmpty() && meta["title"] == null && segment.heading == null && sourceUrl == null) return null

        var title = meta["title"] ?: segment.heading?.let(::cleanTitle)
        var titleGuessed = false
        if (title == null) {
            val guess = guessTitle(text, sourceUrl)
            title = guess.first
            text = guess.second
            titleGuessed = true
        }
        if (text.isEmpty()) text = sourceUrl ?: title

        val explicitTitle = !titleGuessed
        val confidence = when {
            explicitTitle && text.length >= HIGH_BODY -> ParserConfidence.HIGH
            cueRegex.containsMatchIn(text) || cueRegex.containsMatchIn(title) -> ParserConfidence.MEDIUM
            titleGuessed && sourceUrl != null -> ParserConfidence.MEDIUM
            explicitTitle -> ParserConfidence.MEDIUM
            segment.fromList && title.length <= MAX_TITLE && text == title -> ParserConfidence.MEDIUM // a bare list of task names
            else -> ParserConfidence.LOW
        }
        return RawCandidate(
            title = title,
            text = text,
            source = meta["source"] ?: segment.contextHeading?.let(::cleanTitle),
            sourceUrl = sourceUrl,
            companyTag = meta["company"],
            interviewStage = meta["stage"],
            roleLevel = meta["level"],
            reportedDate = TaskClassifier.parseDate(meta["date"]),
            difficultyHint = meta["difficulty"],
            topicHints = FieldMapper.splitList(meta["topics"]),
            patternHints = FieldMapper.splitList(meta["patterns"]),
            notes = meta["notes"],
            confidence = confidence,
            titleGuessed = titleGuessed,
        )
    }

    /** Title and remaining text for a segment that has no explicit title. */
    internal fun guessTitle(text: String, sourceUrl: String?): Pair<String, String> {
        if (text.isEmpty() && sourceUrl != null) return titleFromUrl(sourceUrl) to ""
        val lines = text.lines()
        val first = cleanTitle(lines.first())
        val dash = Regex("^(.{3,60}?)\\s[—–-]\\s(.+)$").matchEntire(first)
        if (dash != null && dash.groupValues[1].split(' ').size <= 6) {
            val rest = (listOf(dash.groupValues[2]) + lines.drop(1)).joinToString("\n").trim()
            return dash.groupValues[1].trim() to rest
        }
        if (first.length <= MAX_TITLE && lines.size > 1) return first to lines.drop(1).joinToString("\n").trim()
        if (first.length <= MAX_TITLE) return first to text
        val sentence = first.split(Regex("(?<=[.!?])\\s")).first()
        val short = if (sentence.length <= MAX_TITLE) sentence else sentence.take(MAX_TITLE - 1).trimEnd() + "…"
        return short to text
    }

    private fun cleanTitle(raw: String): String =
        raw.trim().replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^\\d{1,3}[.)]\\s+"), "").trim('*', '_', ' ').trim()

    /** "https://site/problems/two-sum/description" -> "Two Sum". Nothing is fetched. */
    internal fun titleFromUrl(url: String): String {
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#').trim('/')
        val parts = path.split('/').filter { it.isNotEmpty() }
        val idx = parts.indexOfFirst { it == "problems" || it == "problem" }
        val slug = if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else parts.lastOrNull() ?: url
        return slug.replace('-', ' ').replace('_', ' ').trim().split(' ')
            .filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifEmpty { url }
    }
}
