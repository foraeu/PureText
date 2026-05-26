package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PureTextDatabase
import com.example.data.PureTextRepository
import com.example.model.RecentFile
import com.example.model.UserSettings
import com.example.utils.EpubParser
import com.example.utils.FileUtils
import com.example.utils.OutlineParser
import com.example.utils.OutlineSymbol
import com.example.utils.SyntaxHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResult(
    val lineIndex: Int,
    val charIndex: Int,
    val length: Int
)

data class ReaderState(
    val uriString: String? = null,
    val fileName: String = "",
    val size: Long = 0,
    val language: String = "Text",
    val isLoading: Boolean = false,
    val error: String? = null,
    val lines: List<String> = emptyList(),
    val initialScrollIndex: Int = 0,
    val initialScrollOffset: Int = 0
)

@OptIn(FlowPreview::class)
class PureTextViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository: PureTextRepository

    // Settings State with in-memory Flow + debounced DB write
    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    // Outline Symbols State
    private val _outlineSymbols = MutableStateFlow<List<OutlineSymbol>>(emptyList())
    val outlineSymbols: StateFlow<List<OutlineSymbol>> = _outlineSymbols.asStateFlow()
    
    // Recent Files State
    val recentFiles: StateFlow<List<RecentFile>>

    // Session Reader State
    private val _readerState = MutableStateFlow(ReaderState())
    val readerState: StateFlow<ReaderState> = _readerState.asStateFlow()

    // Mode: Boolean value for Read-Only vs Edit
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // Live modified full text state when editing
    private val _editableText = MutableStateFlow("")
    val editableText: StateFlow<String> = _editableText.asStateFlow()

    // Highlight text change flags
    private val _hasUnsavedEdits = MutableStateFlow(false)
    val hasUnsavedEdits: StateFlow<Boolean> = _hasUnsavedEdits.asStateFlow()

    // Sliding Edit Viewport coordinates
    private val _editStartLineIndex = MutableStateFlow(0)
    val editStartLineIndex: StateFlow<Int> = _editStartLineIndex.asStateFlow()

    private val _editEndLineIndex = MutableStateFlow(0)
    val editEndLineIndex: StateFlow<Int> = _editEndLineIndex.asStateFlow()

    // Search Engine States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCaseSensitive = MutableStateFlow(false)
    val searchCaseSensitive: StateFlow<Boolean> = _searchCaseSensitive.asStateFlow()

    private val _searchRegex = MutableStateFlow(false)
    val searchRegex: StateFlow<Boolean> = _searchRegex.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _currentSearchMatchIndex = MutableStateFlow(-1)
    val currentSearchMatchIndex: StateFlow<Int> = _currentSearchMatchIndex.asStateFlow()

    // Navigation trigger for visual jumps to line index
    private val _scrollRequest = MutableSharedFlow<Int>(replay = 0)
    val scrollRequest = _scrollRequest.asSharedFlow()

    init {
        val database = PureTextDatabase.getDatabase(context)
        repository = PureTextRepository(database)

        recentFiles = repository.recentFiles
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initialize user config and collect DB settings
        viewModelScope.launch {
            repository.getUserSettingsSync()
            repository.userSettings.collect { dbSettings ->
                if (dbSettings != null && _userSettings.value != dbSettings) {
                    _userSettings.value = dbSettings
                }
            }
        }

        // Debounce database write operations for settings (optimizes pinch-to-zoom)
        _userSettings
            .debounce(500L)
            .onEach { settings ->
                repository.saveUserSettings(settings)
            }
            .launchIn(viewModelScope)

        // Asynchronous debounced search observer
        combine(
            _searchQuery,
            _searchCaseSensitive,
            _searchRegex,
            _readerState.map { it.lines }
        ) { query, caseSensitive, regex, lines ->
            SearchInput(query, caseSensitive, regex, lines)
        }
        .debounce(300L)
        .onEach { input ->
            performSearchOnInput(input)
        }
        .launchIn(viewModelScope)
    }

    // Load file inside standard coroutine with asynchronous streaming chunked loading
    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _readerState.update { it.copy(isLoading = true, error = null) }
            _isEditMode.value = false
            _hasUnsavedEdits.value = false
            clearSearch()

            try {
                // Try to persist the read & write permissions if it is a content URI
                if (uri.scheme == "content") {
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException) {
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e2: SecurityException) {
                            // Some sources might not support persistable permissions
                        }
                    }
                }

                val metadata = FileUtils.getMetadata(context, uri)
                val activeSettings = userSettings.value
                val language = SyntaxHighlighter.detectLanguage(metadata.name)

                val dbFile = withContext(Dispatchers.IO) {
                    repository.getRecentFileByUri(uri.toString())
                }

                // Initial quick settings load
                _readerState.value = ReaderState(
                    uriString = uri.toString(),
                    fileName = metadata.name,
                    size = metadata.size,
                    language = language,
                    isLoading = true,
                    lines = emptyList(),
                    initialScrollIndex = dbFile?.scrollIndex ?: 0,
                    initialScrollOffset = dbFile?.scrollOffset ?: 0
                )

                // Save to Room Recents
                repository.insertRecentFile(
                    RecentFile(
                        uriString = uri.toString(),
                        name = metadata.name,
                        size = metadata.size,
                        lastOpened = System.currentTimeMillis(),
                        language = language,
                        scrollIndex = dbFile?.scrollIndex ?: 0,
                        scrollOffset = dbFile?.scrollOffset ?: 0
                    )
                )

                // Stream load the file in an asynchronous IO task
                withContext(Dispatchers.IO) {
                    if (language == "epub") {
                        val epubLines = EpubParser.parseEpubToLines(context, uri)
                        withContext(Dispatchers.Main) {
                            _readerState.update {
                                it.copy(
                                    isLoading = false,
                                    lines = epubLines
                                )
                            }
                            _editableText.value = epubLines.take(1000).joinToString("\n")
                            _outlineSymbols.value = OutlineParser.parseSymbols(epubLines, language)
                        }
                    } else {
                        val charset = FileUtils.determineCharset(context, uri, activeSettings.defaultEncoding)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val reader = inputStream.bufferedReader(charset)
                            val initialLines = mutableListOf<String>()
                            
                            // First chunk: instantly load up to 1000 lines
                            for (i in 0 until 1000) {
                                val line = reader.readLine() ?: break
                                initialLines.add(line)
                            }

                            // Send first chunk to UI instantly (so loading overlay goes away immediately)
                            withContext(Dispatchers.Main) {
                                _readerState.update {
                                    it.copy(
                                        isLoading = false,
                                        lines = initialLines.toList()
                                    )
                                }
                                _editableText.value = initialLines.joinToString("\n")
                            }

                            // Background chunk streaming for larger files
                            val streamBuffer = mutableListOf<String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                streamBuffer.add(line)
                                if (streamBuffer.size >= 3000) {
                                    val chunk = streamBuffer.toList()
                                    streamBuffer.clear()
                                    withContext(Dispatchers.Main) {
                                        _readerState.update {
                                            it.copy(lines = it.lines + chunk)
                                        }
                                    }
                                }
                            }

                            // Flush remaining stream lines
                            if (streamBuffer.isNotEmpty()) {
                                val chunk = streamBuffer.toList()
                                withContext(Dispatchers.Main) {
                                    _readerState.update {
                                        it.copy(lines = it.lines + chunk)
                                    }
                                }
                            }

                            // Parse symbols after full file is loaded in memory
                            val finalLines = _readerState.value.lines
                            val symbols = OutlineParser.parseSymbols(finalLines, language)
                            withContext(Dispatchers.Main) {
                                _outlineSymbols.value = symbols
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                _readerState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to open document"
                    )
                }
            }
        }
    }

    // Toggle Edit Mode with Adaptive Viewport selection to prevent full-file memory rendering lag
    fun toggleEditMode(visibleLineIndex: Int = 0) {
        if (_readerState.value.language == "epub") return // Safe guard
        if (_isEditMode.value) {
            // Save edits
            saveEdits()
        } else {
            val totalLines = _readerState.value.lines.size
            if (totalLines == 0) {
                _editStartLineIndex.value = 0
                _editEndLineIndex.value = 0
                _editableText.value = ""
            } else {
                // Initialize Viewport Window around current visible line (span around 400 lines)
                val start = (visibleLineIndex - 100).coerceAtLeast(0)
                val end = (visibleLineIndex + 300).coerceAtMost(totalLines - 1)

                _editStartLineIndex.value = start
                _editEndLineIndex.value = end

                val windowLines = _readerState.value.lines.subList(start, end + 1)
                _editableText.value = windowLines.joinToString("\n")
            }
            _isEditMode.value = true
            _hasUnsavedEdits.value = false
        }
    }

    // Commit active viewport changes back to overall list in memory
    fun commitActiveViewportToLinesMemory() {
        val currentText = _editableText.value
        val newWindowLines = currentText.split(Regex("\\r?\\n"))
        val allLines = _readerState.value.lines.toMutableList()
        val start = _editStartLineIndex.value
        val end = _editEndLineIndex.value

        if (allLines.isNotEmpty() && start in allLines.indices && end in allLines.indices && start <= end) {
            val safeStart = start.coerceIn(0, allLines.lastIndex)
            val safeEnd = end.coerceIn(safeStart, allLines.lastIndex)
            // Single O(n) bulk shift via subList clear
            allLines.subList(safeStart, safeEnd + 1).clear()
            // Add new edited lines at start
            allLines.addAll(safeStart, newWindowLines)
            _editEndLineIndex.value = safeStart + newWindowLines.size - 1
        } else {
            allLines.clear()
            allLines.addAll(newWindowLines)
            _editEndLineIndex.value = newWindowLines.size - 1
        }

        _readerState.update { it.copy(lines = allLines) }
    }

    // Move sliding edit viewport window forwards/backwards
    fun navigateEditWindow(direction: Int) { // -1 for page back, +1 for page forward
        // First commit the current window edits to memory list
        commitActiveViewportToLinesMemory()

        val updatedLines = _readerState.value.lines
        val totalLines = updatedLines.size
        if (totalLines == 0) return

        val currentStart = _editStartLineIndex.value
        val currentEnd = _editEndLineIndex.value
        val windowSize = 300

        val (start, end) = if (direction < 0) {
            // Move backward: the new end is currentStart - 1
            val targetEnd = (currentStart - 1).coerceIn(0, totalLines - 1)
            val targetStart = (targetEnd - windowSize + 1).coerceAtLeast(0)
            Pair(targetStart, targetEnd)
        } else {
            // Move forward: the new start is currentEnd + 1
            val targetStart = (currentEnd + 1).coerceIn(0, totalLines - 1)
            val targetEnd = (targetStart + windowSize - 1).coerceAtMost(totalLines - 1)
            Pair(targetStart, targetEnd)
        }

        _editStartLineIndex.value = start
        _editEndLineIndex.value = end

        val windowLines = updatedLines.subList(start, end + 1)
        _editableText.value = windowLines.joinToString("\n")
        _hasUnsavedEdits.value = true
    }

    // Update Live Editor Text with optimized validation
    fun updateEditableText(newText: String) {
        _editableText.value = newText
        val start = _editStartLineIndex.value
        val end = _editEndLineIndex.value
        val lines = _readerState.value.lines
        val original = if (lines.isNotEmpty() && start in lines.indices && end in lines.indices && start <= end) {
            lines.subList(start, end + 1).joinToString("\n")
        } else ""
        _hasUnsavedEdits.value = newText != original
    }

    // Save Changes Back to Content Provider / File with full reconstruction
    fun saveEdits() {
        val uriStr = _readerState.value.uriString ?: return
        val activeSettings = userSettings.value
        val uri = Uri.parse(uriStr)

        viewModelScope.launch {
            _readerState.update { it.copy(isLoading = true) }
            
            // Commit current edited section to memory lines list
            commitActiveViewportToLinesMemory()

            val isSuccess = withContext(Dispatchers.IO) {
                FileUtils.writeLinesToUri(context, uri, _readerState.value.lines, activeSettings.defaultEncoding)
            }

            if (isSuccess) {
                _readerState.update {
                    it.copy(
                        isLoading = false
                    )
                }
                _hasUnsavedEdits.value = false
                _isEditMode.value = false

                // Update size metadata in database
                val metadata = FileUtils.getMetadata(context, uri)
                repository.insertRecentFile(
                    RecentFile(
                        uriString = uriStr,
                        name = metadata.name,
                        size = metadata.size,
                        lastOpened = System.currentTimeMillis(),
                        language = _readerState.value.language,
                        scrollIndex = 0,
                        scrollOffset = 0
                    )
                )

                // Re-parse outline symbols after saving edits
                val symbols = OutlineParser.parseSymbols(_readerState.value.lines, _readerState.value.language)
                _outlineSymbols.value = symbols
            } else {
                _readerState.update {
                    it.copy(
                        isLoading = false,
                        error = "更新文件内容失败"
                    )
                }
            }
        }
    }

    // Scroll Coordinates Tracking
    fun updateScrollCoordinates(index: Int, offset: Int) {
        val uriStr = _readerState.value.uriString ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateScrollPosition(uriStr, index, offset)
        }
    }

    // Preferences Modification
    fun updateSettings(reducer: (UserSettings) -> UserSettings) {
        val oldSettings = _userSettings.value
        val updated = reducer(oldSettings)
        _userSettings.value = updated

        // Re-read with fallback encoding if encoding name changed
        val uriStr = _readerState.value.uriString
        if (uriStr != null && updated.defaultEncoding != oldSettings.defaultEncoding) {
            viewModelScope.launch {
                openFile(Uri.parse(uriStr))
            }
        }
    }

    // Delete Recent
    fun deleteRecent(file: RecentFile) {
        viewModelScope.launch {
            repository.deleteRecentFile(file)
        }
    }

    // Clear Recents
    fun clearAllRecents() {
        viewModelScope.launch {
            repository.clearAllRecents()
        }
    }

    private data class SearchInput(
        val query: String,
        val caseSensitive: Boolean,
        val regex: Boolean,
        val lines: List<String>
    )

    // Search Engine Implementation
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            _currentSearchMatchIndex.value = -1
        }
    }

    fun toggleSearchCaseSensitive() {
        _searchCaseSensitive.value = !_searchCaseSensitive.value
    }

    fun toggleSearchRegex() {
        _searchRegex.value = !_searchRegex.value
    }

    private suspend fun performSearchOnInput(input: SearchInput) {
        val query = input.query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            _currentSearchMatchIndex.value = -1
            return
        }

        val matches = withContext(Dispatchers.Default) {
            val lines = input.lines
            val tempMatches = mutableListOf<SearchResult>()
            try {
                if (input.regex) {
                    // Regular Expression search
                    val options = if (input.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val regex = Regex(query, options)
                    lines.forEachIndexed { lineIdx, line ->
                        regex.findAll(line).forEach { match ->
                            tempMatches.add(SearchResult(lineIndex = lineIdx, charIndex = match.range.first, length = match.value.length))
                        }
                    }
                } else {
                    // Straight substring match
                    val sensitive = input.caseSensitive
                    lines.forEachIndexed { lineIdx, line ->
                        var startIdx = 0
                        while (true) {
                            val foundIdx = line.indexOf(query, startIdx, ignoreCase = !sensitive)
                            if (foundIdx == -1) break
                            tempMatches.add(SearchResult(lineIndex = lineIdx, charIndex = foundIdx, length = query.length))
                            startIdx = foundIdx + query.length
                            if (query.isEmpty()) break // Safe guard
                        }
                    }
                }
            } catch (e: Exception) {
                // Safe guard against invalid regex input
            }
            tempMatches
        }

        _searchResults.value = matches
        if (matches.isNotEmpty()) {
            _currentSearchMatchIndex.value = 0
            triggerScrollToSearchResult(0)
        } else {
            _currentSearchMatchIndex.value = -1
        }
    }

    fun goToNextSearchMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIdx = (_currentSearchMatchIndex.value + 1) % results.size
        _currentSearchMatchIndex.value = nextIdx
        triggerScrollToSearchResult(nextIdx)
    }

    fun goToPrevSearchMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIdx = (_currentSearchMatchIndex.value - 1 + results.size) % results.size
        _currentSearchMatchIndex.value = prevIdx
        triggerScrollToSearchResult(prevIdx)
    }

    private fun triggerScrollToSearchResult(index: Int) {
        val match = _searchResults.value.getOrNull(index) ?: return
        viewModelScope.launch {
            _scrollRequest.emit(match.lineIndex)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _currentSearchMatchIndex.value = -1
    }
}
