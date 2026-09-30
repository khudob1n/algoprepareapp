package com.algoprep.app.domain.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps loosely named fields (JSON keys, CSV headers) onto a RawCandidate. */
internal object FieldMapper {
    fun toCandidate(fields: Map<String, String>, multi: Map<String, List<String>> = emptyMap()): RawCandidate? {
        val byName = HashMap<String, String>()
        for ((key, value) in fields) {
            val field = KeywordDictionary.fieldFor(key) ?: continue
            val v = value.trim()
            if (v.isNotEmpty() && field !in byName) byName[field] = v
        }
        val multiByName = HashMap<String, List<String>>()
        for ((key, values) in multi) {
            val field = KeywordDictionary.fieldFor(key) ?: continue
            if (values.isNotEmpty() && field !in multiByName) multiByName[field] = values
        }
        val title = byName["title"]
        val text = byName["text"]
        if (title == null && text == null) return null
        val body = text ?: title!!
        return RawCandidate(
            title = title,
            text = body,
            source = byName["source"],
            sourceUrl = byName["url"],
            companyTag = byName["company"],
            interviewStage = byName["stage"],
            roleLevel = byName["level"],
            reportedDate = TaskClassifier.parseDate(byName["date"]),
            difficultyHint = byName["difficulty"],
            topicHints = multiByName["topics"] ?: splitList(byName["topics"]),
            patternHints = multiByName["patterns"] ?: splitList(byName["patterns"]),
            notes = byName["notes"],
            confidence = when {
                title != null && text != null && text.length >= MIN_STATEMENT -> ParserConfidence.HIGH
                title != null && text == null -> ParserConfidence.MEDIUM
                else -> ParserConfidence.MEDIUM
            },
            titleGuessed = false,
        )
    }

    fun splitList(raw: String?): List<String> =
        raw?.split(',', ';', '|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private const val MIN_STATEMENT = 20
}

object JsonImportParser {
    private val containerKeys = setOf("tasks", "problems", "items", "questions", "reports", "data", "entries", "задачи")

    /** @return candidates, or null when the text is not valid JSON of a supported shape. */
    fun parse(text: String): List<RawCandidate>? {
        val root = try {
            Json.parseToJsonElement(text.trim().removePrefix("﻿"))
        } catch (_: Exception) {
            return null
        }
        val items: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val container = root.entries.firstOrNull { (k, v) -> k.lowercase() in containerKeys && v is JsonArray }
                (container?.value as? JsonArray) ?: listOf(root)
            }
            else -> return null
        }
        return items.mapNotNull { fromElement(it) }
    }

    private fun fromElement(e: JsonElement): RawCandidate? = when (e) {
        is JsonPrimitive -> e.content.trim().takeIf { it.isNotEmpty() }?.let { s ->
            RawCandidate(title = null, text = s, confidence = ParserConfidence.LOW)
        }
        is JsonObject -> {
            val fields = LinkedHashMap<String, String>()
            val multi = LinkedHashMap<String, List<String>>()
            for ((k, v) in e) {
                when (v) {
                    is JsonPrimitive -> fields[k] = v.content
                    is JsonArray -> multi[k] = v.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }.filter { it.isNotEmpty() }
                    else -> Unit
                }
            }
            FieldMapper.toCandidate(fields, multi)
        }
        else -> null
    }
}

object CsvImportParser {
    /** Minimal RFC 4180 reader: quoted fields, doubled quotes, embedded newlines, `,` `;` or tab delimiters. */
    fun readRows(text: String): List<List<String>> {
        val input = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val delimiter = detectDelimiter(input)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> { row.add(field.toString()); field.setLength(0) }
                !inQuotes && c == '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    private fun detectDelimiter(input: String): Char {
        val firstLine = input.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (c in firstLine) {
            if (c == '"') inQuotes = !inQuotes
            else if (!inQuotes && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun parse(text: String): List<RawCandidate> {
        val rows = readRows(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first()
        val known = header.count { KeywordDictionary.fieldFor(it) != null }
        if (known == 0) {
            // No recognisable header: treat the first column of every row as task text.
            return rows.mapNotNull { r ->
                r.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    RawCandidate(title = null, text = it, confidence = ParserConfidence.LOW)
                }
            }
        }
        return rows.drop(1).mapNotNull { r ->
            val fields = LinkedHashMap<String, String>()
            header.forEachIndexed { i, name -> r.getOrNull(i)?.let { fields[name] = it } }
            FieldMapper.toCandidate(fields)
        }
    }
}
