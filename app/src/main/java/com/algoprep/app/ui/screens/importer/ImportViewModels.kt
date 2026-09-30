package com.algoprep.app.ui.screens.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoprep.app.domain.importer.CandidateDraft
import com.algoprep.app.domain.importer.DuplicateChoice
import com.algoprep.app.domain.importer.ImportParser
import com.algoprep.app.domain.importer.ImportSource
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.ImportBatch
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.domain.repository.CatalogRepository
import com.algoprep.app.domain.repository.ImportRepository
import com.algoprep.app.domain.usecase.ParseImport
import com.algoprep.app.domain.usecase.SaveImport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ImportError { EMPTY_INPUT, UNREADABLE_FILE, TOO_LARGE, NOTHING_FOUND, FAILED }

data class ImportHomeUiState(
    val pastedText: String = "",
    val fileName: String? = null,
    val fileText: String? = null,
    val parsing: Boolean = false,
    val error: ImportError? = null,
    val history: List<ImportBatch> = emptyList(),
) {
    val hasInput: Boolean get() = pastedText.isNotBlank() || !fileText.isNullOrBlank()
}

@HiltViewModel
class ImportHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseImport: ParseImport,
    private val store: ImportDraftStore,
    private val imports: ImportRepository,
) : ViewModel() {
    private val form = MutableStateFlow(ImportHomeUiState())

    val uiState: StateFlow<ImportHomeUiState> = combine(form, imports.observeBatches()) { f, batches ->
        f.copy(history = batches)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportHomeUiState())

    private val _toReview = Channel<Unit>(Channel.BUFFERED)
    val toReview = _toReview.receiveAsFlow()

    init {
        // After a successful save the inputs are no longer needed.
        viewModelScope.launch { store.saved.collect { reset() } }
    }

    fun setPastedText(text: String) = form.update { it.copy(pastedText = text, error = null) }

    fun clearFile() = form.update { it.copy(fileName = null, fileText = null, error = null) }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { readFile(uri) }
            form.update {
                when (result) {
                    is FileResult.Ok -> it.copy(fileName = result.name, fileText = result.text, error = null)
                    is FileResult.Failed -> it.copy(error = result.error)
                }
            }
        }
    }

    fun parse() {
        val s = form.value
        if (s.parsing) return
        val fromFile = !s.fileText.isNullOrBlank()
        val text = if (fromFile) s.fileText.orEmpty() else s.pastedText
        if (text.isBlank()) {
            form.update { it.copy(error = ImportError.EMPTY_INPUT) }
            return
        }
        val name = if (fromFile) s.fileName.orEmpty() else PASTED_NAME
        form.update { it.copy(parsing = true, error = null) }
        viewModelScope.launch {
            try {
                val source = ImportSource(name, text, ImportParser.detectFormat(name, text))
                val parsed = parseImport(source)
                if (parsed.candidates.isEmpty()) {
                    form.update { it.copy(parsing = false, error = ImportError.NOTHING_FOUND) }
                } else {
                    store.start(parsed)
                    form.update { it.copy(parsing = false) }
                    _toReview.send(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(parsing = false, error = ImportError.FAILED) }
            }
        }
    }

    /** Clears the inputs once their import has been saved. */
    fun reset() {
        form.value = ImportHomeUiState()
    }

    fun rollback(batchId: Long) {
        viewModelScope.launch { imports.rollback(batchId) }
    }

    private sealed interface FileResult {
        data class Ok(val name: String, val text: String) : FileResult
        data class Failed(val error: ImportError) : FileResult
    }

    private fun readFile(uri: Uri): FileResult = try {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_BYTES + 1) }
        when {
            bytes == null -> FileResult.Failed(ImportError.UNREADABLE_FILE)
            bytes.size > MAX_BYTES -> FileResult.Failed(ImportError.TOO_LARGE)
            else -> FileResult.Ok(name, bytes.toString(Charsets.UTF_8))
        }
    } catch (e: Exception) {
        FileResult.Failed(ImportError.UNREADABLE_FILE)
    }

    /** InputStream.readNBytes needs API 33; minSdk is 26. */
    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER = 8 * 1024
        const val PASTED_NAME = "pasted"
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}

data class ImportReviewUiState(
    val loading: Boolean = true,
    val session: ImportSession? = null,
    val topics: List<Topic> = emptyList(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

@HiltViewModel
class ImportReviewViewModel @Inject constructor(
    private val store: ImportDraftStore,
    catalog: CatalogRepository,
    private val saveImport: SaveImport,
) : ViewModel() {
    private val transient = MutableStateFlow(ImportReviewUiState())

    val uiState: StateFlow<ImportReviewUiState> = combine(store.session, catalog.observeTopics(), transient) { session, topics, t ->
        t.copy(loading = false, session = session, topics = topics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportReviewUiState())

    private val _saved = Channel<ImportBatch>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    fun setSelected(id: String, selected: Boolean) = store.update(id) { it.copy(selected = selected) }
    fun selectAll() = store.setSelected { true }
    fun selectNone() = store.setSelected { false }
    fun selectHighConfidence() = store.setSelected(ImportDraftStore.HIGH_CONFIDENCE)

    fun setTitle(id: String, v: String) = store.update(id) { it.copy(title = v) }
    fun setText(id: String, v: String) = store.update(id) { it.copy(text = v) }
    fun setSource(id: String, v: String) = store.update(id) { it.copy(source = v.ifBlank { null }) }
    fun setCompany(id: String, v: String) = store.update(id) { it.copy(companyTag = v.ifBlank { null }) }
    fun setDifficulty(id: String, d: Difficulty?) = store.update(id) { it.copy(difficulty = d) }
    fun toggleTopic(id: String, topicId: String) =
        store.update(id) { it.copy(topics = if (topicId in it.topics) it.topics - topicId else it.topics + topicId) }
    fun setChoice(id: String, c: DuplicateChoice) = store.update(id) { it.copy(choice = c) }

    fun cancel() = store.clear()

    fun save() {
        val session = store.session.value ?: return
        if (transient.value.saving || session.selectedCount == 0) return
        transient.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val batch = saveImport(session.source, session.drafts)
                store.finishSaved()
                transient.update { it.copy(saving = false) }
                _saved.send(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
