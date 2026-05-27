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

data class TabSession(
    val uriString: String,
    val fileName: String,
    val readerState: ReaderState,
    val isEditMode: Boolean = false,
    val editableText: String = "",
    val hasUnsavedEdits: Boolean = false,
    val editStartLineIndex: Int = 0,
    val editEndLineIndex: Int = 0,
    val searchQuery: String = "",
    val searchCaseSensitive: Boolean = false,
    val searchRegex: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchMatchIndex: Int = -1,
    val outlineSymbols: List<OutlineSymbol> = emptyList()
)

@OptIn(FlowPreview::class)
class PureTextViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository: PureTextRepository

    // Settings State with in-memory Flow + debounced DB write
    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    // Recent Files State
    val recentFiles: StateFlow<List<RecentFile>>

    // Tabs Management State
    private val _tabs = MutableStateFlow<Map<String, TabSession>>(emptyMap())
    val tabs: StateFlow<List<Pair<String, String>>> = _tabs
        .map { map -> map.values.map { it.uriString to it.fileName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTabUri = MutableStateFlow<String?>(null)
    val activeTabUri: StateFlow<String?> = _activeTabUri.asStateFlow()

    // Active session flow mapping
    private val activeSession: Flow<TabSession?> = combine(_activeTabUri, _tabs) { activeUri, map ->
        activeUri?.let { map[it] }
    }

    // Reactively derived exposed StateFlows for compatibility with existing UI consumers
    val readerState: StateFlow<ReaderState> = activeSession
        .map { it?.readerState ?: ReaderState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderState())

    val isEditMode: StateFlow<Boolean> = activeSession
        .map { it?.isEditMode ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val editableText: StateFlow<String> = activeSession
        .map { it?.editableText ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val hasUnsavedEdits: StateFlow<Boolean> = activeSession
        .map { it?.hasUnsavedEdits ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val editStartLineIndex: StateFlow<Int> = activeSession
        .map { it?.editStartLineIndex ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val editEndLineIndex: StateFlow<Int> = activeSession
        .map { it?.editEndLineIndex ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val searchQuery: StateFlow<String> = activeSession
        .map { it?.searchQuery ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val searchCaseSensitive: StateFlow<Boolean> = activeSession
        .map { it?.searchCaseSensitive ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val searchRegex: StateFlow<Boolean> = activeSession
        .map { it?.searchRegex ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val searchResults: StateFlow<List<SearchResult>> = activeSession
        .map { it?.searchResults ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSearchMatchIndex: StateFlow<Int> = activeSession
        .map { it?.currentSearchMatchIndex ?: -1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    val outlineSymbols: StateFlow<List<OutlineSymbol>> = activeSession
        .map { it?.outlineSymbols ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            _activeTabUri,
            _tabs
        ) { activeUri, map ->
            val session = activeUri?.let { map[it] }
            SearchInput(
                activeUri = activeUri,
                query = session?.searchQuery ?: "",
                caseSensitive = session?.searchCaseSensitive ?: false,
                regex = session?.searchRegex ?: false,
                lines = session?.readerState?.lines ?: emptyList()
            )
        }
        .debounce(300L)
        .onEach { input ->
            performSearchOnInput(input)
        }
        .launchIn(viewModelScope)
    }

    // Load file inside standard coroutine with asynchronous streaming chunked loading
    fun openFile(uri: Uri) {
        val uriStr = uri.toString()
        if (_tabs.value.containsKey(uriStr)) {
            _activeTabUri.value = uriStr
            return
        }

        viewModelScope.launch {
            val metadata = FileUtils.getMetadata(context, uri)
            val activeSettings = userSettings.value
            val language = SyntaxHighlighter.detectLanguage(metadata.name)

            val dbFile = withContext(Dispatchers.IO) {
                repository.getRecentFileByUri(uriStr)
            }

            val initialSession = TabSession(
                uriString = uriStr,
                fileName = metadata.name,
                readerState = ReaderState(
                    uriString = uriStr,
                    fileName = metadata.name,
                    size = metadata.size,
                    language = language,
                    isLoading = true,
                    lines = emptyList(),
                    initialScrollIndex = dbFile?.scrollIndex ?: 0,
                    initialScrollOffset = dbFile?.scrollOffset ?: 0
                )
            )

            _tabs.update { it + (uriStr to initialSession) }
            _activeTabUri.value = uriStr

            // Save to Room Recents
            repository.insertRecentFile(
                RecentFile(
                    uriString = uriStr,
                    name = metadata.name,
                    size = metadata.size,
                    lastOpened = System.currentTimeMillis(),
                    language = language,
                    scrollIndex = dbFile?.scrollIndex ?: 0,
                    scrollOffset = dbFile?.scrollOffset ?: 0
                )
            )

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

                // Stream load the file in an asynchronous IO task
                withContext(Dispatchers.IO) {
                    if (language == "epub") {
                        val epubLines = EpubParser.parseEpubToLines(context, uri)
                        withContext(Dispatchers.Main) {
                            _tabs.update { map ->
                                val session = map[uriStr] ?: return@update map
                                val updated = session.copy(
                                    readerState = session.readerState.copy(
                                        isLoading = false,
                                        lines = epubLines
                                    ),
                                    editableText = epubLines.take(1000).joinToString("\n"),
                                    outlineSymbols = OutlineParser.parseSymbols(epubLines, language)
                                )
                                map + (uriStr to updated)
                            }
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

                            // Send first chunk to UI instantly
                            withContext(Dispatchers.Main) {
                                _tabs.update { map ->
                                    val session = map[uriStr] ?: return@update map
                                    val updated = session.copy(
                                        readerState = session.readerState.copy(
                                            isLoading = false,
                                            lines = initialLines.toList()
                                        ),
                                        editableText = initialLines.joinToString("\n")
                                    )
                                    map + (uriStr to updated)
                                }
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
                                        _tabs.update { map ->
                                            val session = map[uriStr] ?: return@update map
                                            val updated = session.copy(
                                                readerState = session.readerState.copy(
                                                    lines = session.readerState.lines + chunk
                                                )
                                            )
                                            map + (uriStr to updated)
                                        }
                                    }
                                }
                            }

                            // Flush remaining stream lines
                            if (streamBuffer.isNotEmpty()) {
                                val chunk = streamBuffer.toList()
                                withContext(Dispatchers.Main) {
                                    _tabs.update { map ->
                                        val session = map[uriStr] ?: return@update map
                                        val updated = session.copy(
                                            readerState = session.readerState.copy(
                                                lines = session.readerState.lines + chunk
                                            )
                                        )
                                        map + (uriStr to updated)
                                    }
                                }
                            }

                            // Parse symbols after full file is loaded in memory
                            val finalLines = _tabs.value[uriStr]?.readerState?.lines ?: emptyList()
                            val symbols = OutlineParser.parseSymbols(finalLines, language)
                            withContext(Dispatchers.Main) {
                                _tabs.update { map ->
                                    val session = map[uriStr] ?: return@update map
                                    val updated = session.copy(outlineSymbols = symbols)
                                    map + (uriStr to updated)
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                _tabs.update { map ->
                    val session = map[uriStr] ?: return@update map
                    val updated = session.copy(
                        readerState = session.readerState.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Failed to open document"
                        )
                    )
                    map + (uriStr to updated)
                }
            }
        }
    }

    // Toggle Edit Mode with Adaptive Viewport selection
    fun toggleEditMode(visibleLineIndex: Int = 0) {
        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        if (session.readerState.language == "epub") return // Safe guard
        
        if (session.isEditMode) {
            saveEdits()
        } else {
            val totalLines = session.readerState.lines.size
            val start: Int
            val end: Int
            val editText: String

            if (totalLines == 0) {
                start = 0
                end = 0
                editText = ""
            } else {
                val sVal = (visibleLineIndex - 100).coerceAtLeast(0)
                val eVal = (visibleLineIndex + 300).coerceAtMost(totalLines - 1)
                start = sVal
                end = eVal
                val windowLines = session.readerState.lines.subList(sVal, eVal + 1)
                editText = windowLines.joinToString("\n")
            }

            _tabs.update { map ->
                val s = map[activeUri] ?: return@update map
                val updated = s.copy(
                    isEditMode = true,
                    editableText = editText,
                    editStartLineIndex = start,
                    editEndLineIndex = end,
                    hasUnsavedEdits = false
                )
                map + (activeUri to updated)
            }
        }
    }

    // Commit active viewport changes back to overall list in memory
    fun commitActiveViewportToLinesMemory() {
        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        val currentText = session.editableText
        val newWindowLines = currentText.split(Regex("\\r?\\n"))
        val allLines = session.readerState.lines.toMutableList()
        val start = session.editStartLineIndex
        val end = session.editEndLineIndex

        val newEnd: Int
        if (allLines.isNotEmpty() && start in allLines.indices && end in allLines.indices && start <= end) {
            val safeStart = start.coerceIn(0, allLines.lastIndex)
            val safeEnd = end.coerceIn(safeStart, allLines.lastIndex)
            // Single O(n) bulk shift
            allLines.subList(safeStart, safeEnd + 1).clear()
            allLines.addAll(safeStart, newWindowLines)
            newEnd = safeStart + newWindowLines.size - 1
        } else {
            allLines.clear()
            allLines.addAll(newWindowLines)
            newEnd = newWindowLines.size - 1
        }

        _tabs.update { map ->
            val s = map[activeUri] ?: return@update map
            val updated = s.copy(
                readerState = s.readerState.copy(lines = allLines),
                editEndLineIndex = newEnd
            )
            map + (activeUri to updated)
        }
    }

    // Move sliding edit viewport window forwards/backwards
    fun navigateEditWindow(direction: Int) { // -1 for page back, +1 for page forward
        commitActiveViewportToLinesMemory()

        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        val updatedLines = session.readerState.lines
        val totalLines = updatedLines.size
        if (totalLines == 0) return

        val currentStart = session.editStartLineIndex
        val currentEnd = session.editEndLineIndex
        val windowSize = 300

        val (start, end) = if (direction < 0) {
            val targetEnd = (currentStart - 1).coerceIn(0, totalLines - 1)
            val targetStart = (targetEnd - windowSize + 1).coerceAtLeast(0)
            Pair(targetStart, targetEnd)
        } else {
            val targetStart = (currentEnd + 1).coerceIn(0, totalLines - 1)
            val targetEnd = (targetStart + windowSize - 1).coerceAtMost(totalLines - 1)
            Pair(targetStart, targetEnd)
        }

        val windowLines = updatedLines.subList(start, end + 1)
        _tabs.update { map ->
            val s = map[activeUri] ?: return@update map
            val updated = s.copy(
                editStartLineIndex = start,
                editEndLineIndex = end,
                editableText = windowLines.joinToString("\n"),
                hasUnsavedEdits = true
            )
            map + (activeUri to updated)
        }
    }

    // Update Live Editor Text
    fun updateEditableText(newText: String) {
        val activeUri = _activeTabUri.value ?: return
        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val start = session.editStartLineIndex
            val end = session.editEndLineIndex
            val lines = session.readerState.lines
            val original = if (lines.isNotEmpty() && start in lines.indices && end in lines.indices && start <= end) {
                lines.subList(start, end + 1).joinToString("\n")
            } else ""
            val updated = session.copy(
                editableText = newText,
                hasUnsavedEdits = newText != original
            )
            map + (activeUri to updated)
        }
    }

    // Save Changes Back to Content Provider / File
    fun saveEdits() {
        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        val activeSettings = userSettings.value
        val uri = Uri.parse(activeUri)

        viewModelScope.launch {
            _tabs.update { map ->
                val s = map[activeUri] ?: return@update map
                val updated = s.copy(readerState = s.readerState.copy(isLoading = true))
                map + (activeUri to updated)
            }
            
            commitActiveViewportToLinesMemory()

            val latestSession = _tabs.value[activeUri] ?: return@launch
            val isSuccess = withContext(Dispatchers.IO) {
                FileUtils.writeLinesToUri(context, uri, latestSession.readerState.lines, activeSettings.defaultEncoding)
            }

            if (isSuccess) {
                val metadata = FileUtils.getMetadata(context, uri)
                repository.insertRecentFile(
                    RecentFile(
                        uriString = activeUri,
                        name = metadata.name,
                        size = metadata.size,
                        lastOpened = System.currentTimeMillis(),
                        language = latestSession.readerState.language,
                        scrollIndex = 0,
                        scrollOffset = 0
                    )
                )

                val symbols = OutlineParser.parseSymbols(latestSession.readerState.lines, latestSession.readerState.language)
                _tabs.update { map ->
                    val s = map[activeUri] ?: return@update map
                    val updated = s.copy(
                        readerState = s.readerState.copy(isLoading = false),
                        hasUnsavedEdits = false,
                        isEditMode = false,
                        outlineSymbols = symbols
                    )
                    map + (activeUri to updated)
                }
            } else {
                _tabs.update { map ->
                    val s = map[activeUri] ?: return@update map
                    val updated = s.copy(
                        readerState = s.readerState.copy(
                            isLoading = false,
                            error = "更新文件内容失败"
                        )
                    )
                    map + (activeUri to updated)
                }
            }
        }
    }

    // Scroll Coordinates Tracking
    fun updateScrollCoordinates(index: Int, offset: Int) {
        val activeUri = _activeTabUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateScrollPosition(activeUri, index, offset)
        }
    }

    // Preferences Modification
    fun updateSettings(reducer: (UserSettings) -> UserSettings) {
        val oldSettings = _userSettings.value
        val updated = reducer(oldSettings)
        _userSettings.value = updated

        val activeUri = _activeTabUri.value
        if (activeUri != null && updated.defaultEncoding != oldSettings.defaultEncoding) {
            // Remove from tabs to force re-reading with new encoding
            _tabs.update { it - activeUri }
            viewModelScope.launch {
                openFile(Uri.parse(activeUri))
            }
        }
    }

    // Tab Tab Operations
    fun selectTab(uriStr: String) {
        if (_tabs.value.containsKey(uriStr)) {
            _activeTabUri.value = uriStr
        }
    }

    fun closeTab(uriStr: String, onAllTabsClosed: () -> Unit) {
        _tabs.update { it - uriStr }
        if (_activeTabUri.value == uriStr) {
            val remaining = _tabs.value.keys.toList()
            if (remaining.isNotEmpty()) {
                _activeTabUri.value = remaining.last()
            } else {
                _activeTabUri.value = null
                onAllTabsClosed()
            }
        }
    }

    // Delete Recent
    fun deleteRecent(file: RecentFile) {
        viewModelScope.launch {
            repository.deleteRecentFile(file)
            // Close tab as well if open
            if (_tabs.value.containsKey(file.uriString)) {
                closeTab(file.uriString) {}
            }
        }
    }

    // Clear Recents
    fun clearAllRecents() {
        viewModelScope.launch {
            repository.clearAllRecents()
            _tabs.value = emptyMap()
            _activeTabUri.value = null
        }
    }

    private data class SearchInput(
        val activeUri: String?,
        val query: String,
        val caseSensitive: Boolean,
        val regex: Boolean,
        val lines: List<String>
    )

    // Search Engine Implementation
    fun updateSearchQuery(query: String) {
        val activeUri = _activeTabUri.value ?: return
        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val updated = session.copy(
                searchQuery = query,
                searchResults = if (query.isEmpty()) emptyList() else session.searchResults,
                currentSearchMatchIndex = if (query.isEmpty()) -1 else session.currentSearchMatchIndex
            )
            map + (activeUri to updated)
        }
    }

    fun toggleSearchCaseSensitive() {
        val activeUri = _activeTabUri.value ?: return
        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val updated = session.copy(searchCaseSensitive = !session.searchCaseSensitive)
            map + (activeUri to updated)
        }
    }

    fun toggleSearchRegex() {
        val activeUri = _activeTabUri.value ?: return
        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val updated = session.copy(searchRegex = !session.searchRegex)
            map + (activeUri to updated)
        }
    }

    private suspend fun performSearchOnInput(input: SearchInput) {
        val activeUri = input.activeUri ?: return
        val query = input.query
        if (query.isEmpty()) {
            _tabs.update { map ->
                val session = map[activeUri] ?: return@update map
                val updated = session.copy(searchResults = emptyList(), currentSearchMatchIndex = -1)
                map + (activeUri to updated)
            }
            return
        }

        val matches = withContext(Dispatchers.Default) {
            val lines = input.lines
            val tempMatches = mutableListOf<SearchResult>()
            try {
                if (input.regex) {
                    val options = if (input.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val regex = Regex(query, options)
                    lines.forEachIndexed { lineIdx, line ->
                        regex.findAll(line).forEach { match ->
                            tempMatches.add(SearchResult(lineIndex = lineIdx, charIndex = match.range.first, length = match.value.length))
                        }
                    }
                } else {
                    val sensitive = input.caseSensitive
                    lines.forEachIndexed { lineIdx, line ->
                        var startIdx = 0
                        while (true) {
                            val foundIdx = line.indexOf(query, startIdx, ignoreCase = !sensitive)
                            if (foundIdx == -1) break
                            tempMatches.add(SearchResult(lineIndex = lineIdx, charIndex = foundIdx, length = query.length))
                            startIdx = foundIdx + query.length
                            if (query.isEmpty()) break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore invalid regex
            }
            tempMatches
        }

        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val matchIndex = if (matches.isNotEmpty()) 0 else -1
            val updated = session.copy(searchResults = matches, currentSearchMatchIndex = matchIndex)
            map + (activeUri to updated)
        }

        if (matches.isNotEmpty()) {
            triggerScrollToSearchResult(activeUri, 0)
        }
    }

    fun goToNextSearchMatch() {
        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        val results = session.searchResults
        if (results.isEmpty()) return
        val nextIdx = (session.currentSearchMatchIndex + 1) % results.size
        _tabs.update { map ->
            val s = map[activeUri] ?: return@update map
            val updated = s.copy(currentSearchMatchIndex = nextIdx)
            map + (activeUri to updated)
        }
        triggerScrollToSearchResult(activeUri, nextIdx)
    }

    fun goToPrevSearchMatch() {
        val activeUri = _activeTabUri.value ?: return
        val session = _tabs.value[activeUri] ?: return
        val results = session.searchResults
        if (results.isEmpty()) return
        val prevIdx = (session.currentSearchMatchIndex - 1 + results.size) % results.size
        _tabs.update { map ->
            val s = map[activeUri] ?: return@update map
            val updated = s.copy(currentSearchMatchIndex = prevIdx)
            map + (activeUri to updated)
        }
        triggerScrollToSearchResult(activeUri, prevIdx)
    }

    private fun triggerScrollToSearchResult(index: Int) {
        // Fallback for older code compatibility, does nothing as we use the activeUri version
    }

    fun clearSearch() {
        val activeUri = _activeTabUri.value ?: return
        _tabs.update { map ->
            val session = map[activeUri] ?: return@update map
            val updated = session.copy(
                searchQuery = "",
                searchResults = emptyList(),
                currentSearchMatchIndex = -1
            )
            map + (activeUri to updated)
        }
    }
}
