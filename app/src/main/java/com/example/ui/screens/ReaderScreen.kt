package com.example.ui.screens

import android.net.Uri
import android.widget.Space
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserSettings
import com.example.viewmodel.ReaderState
import com.example.viewmodel.SearchResult
import com.example.utils.HighlightTheme
import com.example.utils.SyntaxHighlighter
import com.example.utils.OutlineSymbol
import com.example.utils.SymbolType
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    theme: HighlightTheme,
    settings: UserSettings,
    readerState: ReaderState,
    isEditMode: Boolean,
    editableText: String,
    hasUnsavedEdits: Boolean,
    editStartLineIndex: Int = 0,
    editEndLineIndex: Int = 0,
    searchQuery: String,
    searchCaseSensitive: Boolean,
    searchRegex: Boolean,
    searchResults: List<SearchResult>,
    currentSearchMatchIndex: Int,
    scrollRequest: SharedFlow<Int>,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onToggleEditMode: (Int) -> Unit,
    onNavigateEditWindow: (Int) -> Unit = {},
    onUpdateEditableText: (String) -> Unit,
    onSaveEdits: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleSearchCaseSensitive: () -> Unit,
    onToggleSearchRegex: () -> Unit,
    onGoToNextSearchMatch: () -> Unit,
    onGoToPrevSearchMatch: () -> Unit,
    onClearSearch: () -> Unit,
    onUpdateScrollCoordinates: (index: Int, offset: Int) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenFile: (Uri) -> Unit = {},
    outlineSymbols: List<OutlineSymbol> = emptyList(),
    tabs: List<Pair<String, String>> = emptyList(),
    activeTabUri: String? = null,
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerState.initialScrollIndex,
        initialFirstVisibleItemScrollOffset = readerState.initialScrollOffset
    )

    // Bars display state (toggled by tapping body)
    var showUIBars by remember { mutableStateOf(!settings.immersiveReadEnabled) }
    var showQuickSettingsMenu by remember { mutableStateOf(false) }
    var showSearchPanel by remember { mutableStateOf(false) }

    // Alert for massive file editing
    var showLargeFileEditAlert by remember { mutableStateOf(false) }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onOpenFile(it) }
    }

    val resolvedFontFamily = remember(settings.fontFamilyName) {
        when (settings.fontFamilyName) {
            "Monospace" -> FontFamily.Monospace
            "Serif" -> FontFamily.Serif
            "Sans-Serif" -> FontFamily.SansSerif
            else -> FontFamily.Default
        }
    }

    // Scroll Coordinates Recorder (debounced/triggered on screen index adjustments)
    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        if (!readerState.isLoading && readerState.uriString != null) {
            delay(500L)
            onUpdateScrollCoordinates(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset
            )
        }
    }

    val currentReaderState by rememberUpdatedState(readerState)

    // Subscribe to view model scroll request jumps
    LaunchedEffect(scrollRequest) {
        scrollRequest.collect { targetLine ->
            val latestState = currentReaderState
            if (targetLine in latestState.lines.indices) {
                // Ensurebars are visible on search selection
                showUIBars = true
                val currentIndex = lazyListState.firstVisibleItemIndex
                if (kotlin.math.abs(targetLine - currentIndex) < 200) {
                    lazyListState.animateScrollToItem(targetLine)
                } else {
                    lazyListState.scrollToItem(targetLine)
                }
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = theme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (readerState.language == "markdown") "文档大纲" else "代码符号大纲",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (outlineSymbols.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("未检测到大纲结构", color = theme.textPrimary.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(outlineSymbols) { symbol ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                drawerState.close()
                                                val currentIndex = lazyListState.firstVisibleItemIndex
                                                if (kotlin.math.abs(symbol.lineIndex - currentIndex) < 200) {
                                                    lazyListState.animateScrollToItem(symbol.lineIndex)
                                                } else {
                                                    lazyListState.scrollToItem(symbol.lineIndex)
                                                }
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val icon = when (symbol.type) {
                                        SymbolType.CLASS -> "🅒"
                                        SymbolType.FUNCTION -> "🅵"
                                        SymbolType.HEADER -> "📌"
                                    }
                                    Text(
                                        text = icon,
                                        color = theme.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = symbol.label,
                                        color = theme.textPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier,
        topBar = {
            AnimatedVisibility(
                visible = showUIBars,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = readerState.fileName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary
                                    ),
                                    maxLines = 1
                                )
                                if (readerState.lines.isNotEmpty()) {
                                    Text(
                                        text = "共 ${readerState.lines.size} 行 • ${readerState.language}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = theme.textPrimary.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("reader_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go Back",
                                    tint = theme.textPrimary
                                )
                            }
                        },
                        actions = {
                            // Outline Button
                            if (outlineSymbols.isNotEmpty()) {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        drawerState.open()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Toc,
                                        contentDescription = "Outline List",
                                        tint = theme.textPrimary
                                    )
                                }
                            }

                            // Search Button
                            IconButton(onClick = {
                                showSearchPanel = !showSearchPanel
                                if (!showSearchPanel) onClearSearch()
                            }) {
                                Icon(
                                    imageVector = if (showSearchPanel) Icons.Default.SearchOff else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (showSearchPanel) theme.accent else theme.textPrimary
                                )
                            }

                            // Quick Settings Popover
                            IconButton(onClick = { showQuickSettingsMenu = !showQuickSettingsMenu }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Quick Adjust",
                                    tint = theme.textPrimary
                                )
                            }

                            // Edit Toggle
                            if (readerState.language != "epub") {
                                IconButton(
                                    onClick = {
                                        if (!isEditMode && readerState.size > 250_000) {
                                            showLargeFileEditAlert = true
                                        } else {
                                            onToggleEditMode(lazyListState.firstVisibleItemIndex)
                                        }
                                    },
                                    modifier = Modifier.testTag("toggle_edit_mode_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Default.Save else Icons.Default.EditNote,
                                        contentDescription = "Edit Mode",
                                        tint = if (isEditMode) theme.accent else theme.textPrimary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = theme.surface
                        )
                    )

                    // Bento Capsule Tabs Row
                    if (tabs.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.surface)
                                .border(width = 0.5.dp, color = theme.textPrimary.copy(alpha = 0.08f))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(tabs) { (uriStr, fileName) ->
                                val isActive = uriStr == activeTabUri
                                val language = SyntaxHighlighter.detectLanguage(fileName)
                                val icon = when (language.lowercase()) {
                                    "python", "rust", "typescript", "javascript", "kotlin", "java", "r" -> Icons.Default.Code
                                    "json" -> Icons.Default.DataObject
                                    "markdown", "epub" -> Icons.Default.MenuBook
                                    else -> Icons.Default.Description
                                }
                                
                                val displayName = if (fileName.length <= 14) {
                                    fileName
                                } else {
                                    val dotIndex = fileName.lastIndexOf('.')
                                    if (dotIndex <= 0) {
                                        fileName.take(11) + "..."
                                    } else {
                                        val ext = fileName.substring(dotIndex)
                                        val base = fileName.substring(0, dotIndex)
                                        val maxBase = (14 - ext.length - 3).coerceAtLeast(3)
                                        base.take(maxBase) + "..." + ext
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isActive) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isActive) theme.accent else theme.textPrimary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectTab(uriStr) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isActive) theme.accent else theme.textPrimary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) theme.accent else theme.textPrimary.copy(alpha = 0.8f)
                                    )
                                    IconButton(
                                        onClick = { onCloseTab(uriStr) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close tab",
                                            tint = if (isActive) theme.accent.copy(alpha = 0.7f) else theme.textPrimary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (showUIBars) innerPadding.calculateTopPadding() else 0.dp,
                    bottom = if (showUIBars) innerPadding.calculateBottomPadding() else 0.dp
                )
        ) {
            if (readerState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            } else if (readerState.error != null) {
                val isPermissionError = remember(readerState.error, readerState.uriString) {
                    val err = readerState.error ?: ""
                    val isContentUri = readerState.uriString?.startsWith("content://") == true
                    err.isNotEmpty() && (
                        isContentUri ||
                        err.contains("Permission Denial", ignoreCase = true) ||
                        err.contains("ACTION_OPEN_DOCUMENT", ignoreCase = true) ||
                        err.contains("SecurityException", ignoreCase = true) ||
                        err.contains("access using", ignoreCase = true) ||
                        err.contains("timeout", ignoreCase = true)
                    )
                }

                if (isPermissionError) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(theme.accent.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = theme.accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Text(
                                    text = "文件防失效保护到期",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = "Android 系统的安全访问限制机制规定：如果该文件是此前跨应用（如微信、QQ、外部文件管理器等第三方 App）「发送/分享」到 PureText 打开的，系统默认仅授权一次性临时预览权限。应用关闭重启或手机清理内存后，该临时秘钥会被系统强制自动收回。",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = theme.textPrimary.copy(alpha = 0.7f),
                                        lineHeight = 22.sp
                                    ),
                                    textAlign = TextAlign.Justify
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(theme.background, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("💡", fontSize = 16.sp)
                                        Text(
                                            text = "温馨提醒：点击下方「重新打开文件」并由您授权，便可获取永久读取记录权限，在“最近打开”历史中随时点击并即刻加载。",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = theme.textPrimary.copy(alpha = 0.6f),
                                                lineHeight = 16.sp
                                            )
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                docLauncher.launch(arrayOf("*/*"))
                                            } catch (e: Exception) {
                                                docLauncher.launch(arrayOf("text/*", "application/*"))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.FileOpen, null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("重新打开文件", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    TextButton(
                                        onClick = onNavigateBack,
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Text("返回历史首页", color = theme.textPrimary.copy(0.6f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text(
                                text = readerState.error,
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = theme.accent)) {
                                Text("返回历史页", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Main reading body or active editing view
                if (isEditMode) {
                    // Raw Edit editor
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.background)
                            .padding(horizontal = settings.horizontalPadding.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).background(theme.accent, CircleShape))
                                Text(
                                    "极简编辑模式开启中",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }

                            if (hasUnsavedEdits) {
                                TextButton(
                                    onClick = onSaveEdits,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = theme.accent)
                                ) {
                                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("保存并同步", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    "已自动同步",
                                    fontSize = 11.sp,
                                    color = theme.textPrimary.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sliding Edit Viewport Controls (Editor Viewport Lazy Loading Controls)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                            border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onNavigateEditWindow(-1) },
                                        enabled = editStartLineIndex > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft,
                                            contentDescription = "前一分块",
                                            tint = if (editStartLineIndex > 0) theme.textPrimary else theme.textPrimary.copy(alpha = 0.25f)
                                        )
                                    }
                                    Text(
                                        text = "前一页",
                                        fontSize = 11.sp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (editStartLineIndex > 0) theme.textPrimary.copy(alpha = 0.8f) else theme.textPrimary.copy(alpha = 0.3f)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "智能分块编辑视窗",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.accent
                                    )
                                    Text(
                                        text = "第 ${editStartLineIndex + 1} - ${editEndLineIndex + 1} 行 (共 ${readerState.lines.size} 行)",
                                        fontSize = 11.sp,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "后一页",
                                        fontSize = 11.sp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (editEndLineIndex < readerState.lines.size - 1) theme.textPrimary.copy(alpha = 0.8f) else theme.textPrimary.copy(alpha = 0.3f)
                                    )
                                    IconButton(
                                        onClick = { onNavigateEditWindow(1) },
                                        enabled = editEndLineIndex < readerState.lines.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "后一分块",
                                            tint = if (editEndLineIndex < readerState.lines.size - 1) theme.textPrimary else theme.textPrimary.copy(alpha = 0.25f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        BasicTextField(
                            value = editableText,
                            onValueChange = onUpdateEditableText,
                            textStyle = TextStyle(
                                fontFamily = resolvedFontFamily,
                                fontSize = settings.fontSize.sp,
                                lineHeight = (settings.fontSize * settings.lineHeightMultiplier).sp,
                                color = theme.textPrimary
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(theme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, theme.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            keyboardOptions = KeyboardOptions.Default
                        )
                    }
                } else {
                    // High Performance Lazy Reader view
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.background)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) {
                                        onUpdateSettings { it.copy(fontSize = (it.fontSize * zoom).coerceIn(12f, 30f)) }
                                    }
                                }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Toggle status UI bars
                                showUIBars = !showUIBars
                            }
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 12.dp,
                                bottom = 96.dp
                            )
                        ) {
                            itemsIndexed(readerState.lines) { lineIdx, lineText ->
                                ReaderLineRow(
                                    lineIdx = lineIdx,
                                    lineText = lineText,
                                    language = readerState.language,
                                    theme = theme,
                                    settings = settings,
                                    resolvedFontFamily = resolvedFontFamily,
                                    searchResults = searchResults
                                )
                            }
                        }

                        // Minimal Floating Bottom Info Tab when bars are closed
                        AnimatedVisibility(
                            visible = !showUIBars,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(theme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                    .border(0.5.dp, theme.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                val currentLine = lazyListState.firstVisibleItemIndex + 1
                                val percent = if (readerState.lines.isNotEmpty()) {
                                    (currentLine.toFloat() / readerState.lines.size.toFloat() * 100).toInt()
                                } else 0
                                Text(
                                    text = "$percent%  •  $currentLine/${readerState.lines.size} 行",
                                    fontSize = 11.sp,
                                    color = theme.textPrimary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Expanding Quick Settings Panel Overlay
            AnimatedVisibility(
                visible = showQuickSettingsMenu,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Title Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Tune, null, tint = theme.accent, modifier = Modifier.size(18.dp))
                                Text("快速面板", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                            }

                            IconButton(
                                onClick = { showQuickSettingsMenu = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = theme.textPrimary.copy(0.4f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Grid Row 1: Font Size (weight 2f) + Theme shortcuts (weight 1f each)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Column 1: Font Size Changer Card
                            Card(
                                modifier = Modifier
                                    .weight(2f)
                                    .height(96.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.background),
                                border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "字号大小",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary.copy(alpha = 0.4f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledIconButton(
                                            onClick = {
                                                onUpdateSettings { it.copy(fontSize = (it.fontSize - 1f).coerceAtLeast(12f)) }
                                            },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = theme.surface)
                                        ) {
                                            Icon(Icons.Default.Remove, null, tint = theme.textPrimary, modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            "${settings.fontSize.toInt()}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )

                                        FilledIconButton(
                                            onClick = {
                                                onUpdateSettings { it.copy(fontSize = (it.fontSize + 1f).coerceAtMost(30f)) }
                                            },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = theme.surface)
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = theme.textPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Theme Shortcut 1: Paper Light Preset
                            val isPaperSelected = settings.themeName == "Paper Light"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp)
                                    .clickable {
                                        onUpdateSettings { it.copy(themeName = "Paper Light") }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.background),
                                border = if (isPaperSelected) BorderStroke(1.5.dp, theme.accent) else BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFFFDF8F6), CircleShape)
                                            .border(1.dp, Color(0xFF1D1B20).copy(0.1f), CircleShape)
                                    )
                                    Text(
                                        "经典浅色",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Theme Shortcut 2: Soft Dark Preset
                            val isDarkSelected = settings.themeName == "Soft Dark"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp)
                                    .clickable {
                                        onUpdateSettings { it.copy(themeName = "Soft Dark") }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = theme.background),
                                border = if (isDarkSelected) BorderStroke(1.5.dp, theme.accent) else BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFF121417), CircleShape)
                                            .border(1.dp, Color.White.copy(0.1f), CircleShape)
                                    ) {
                                        if (isDarkSelected) {
                                            Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape).align(Alignment.Center))
                                        }
                                    }
                                    Text(
                                        "柔和暗色",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Grid Row 2: Switches and action toggles in grid tiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Code Wrap Toggle Tile
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateSettings { it.copy(wordWrapEnabled = !it.wordWrapEnabled) }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (settings.wordWrapEnabled) theme.accent.copy(alpha = 0.08f) else theme.background
                                ),
                                border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("自动折行", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                        Text(if (settings.wordWrapEnabled) "已开启" else "已禁用", fontSize = 9.sp, color = theme.textPrimary.copy(0.5f))
                                    }
                                    Switch(
                                        checked = settings.wordWrapEnabled,
                                        onCheckedChange = { value ->
                                            onUpdateSettings { it.copy(wordWrapEnabled = value) }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = theme.accent
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            }

                            // Show Lines Toggle Tile
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onUpdateSettings { it.copy(showLineNumbers = !it.showLineNumbers) }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (settings.showLineNumbers) theme.accent.copy(alpha = 0.08f) else theme.background
                                ),
                                border = BorderStroke(1.dp, theme.textPrimary.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("显示行号", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                        Text(if (settings.showLineNumbers) "已开启" else "已隐藏", fontSize = 9.sp, color = theme.textPrimary.copy(0.5f))
                                    }
                                    Switch(
                                        checked = settings.showLineNumbers,
                                        onCheckedChange = { value ->
                                            onUpdateSettings { it.copy(showLineNumbers = value) }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = theme.accent
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            }
                        }

                        // Bottom Actions: Full-bleed primary Bento Edit Mode selector
                        Button(
                            onClick = {
                                showQuickSettingsMenu = false
                                if (!isEditMode && readerState.size > 250_000) {
                                    showLargeFileEditAlert = true
                                } else {
                                    onToggleEditMode(lazyListState.firstVisibleItemIndex)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Save else Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEditMode) "保存当前编辑" else "极简编辑模式",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Overlay Bar
            AnimatedVisibility(
                visible = showSearchPanel,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search Field
                            TextField(
                                value = searchQuery,
                                onValueChange = onUpdateSearchQuery,
                                placeholder = { Text("搜索文本或表达式...", color = theme.textPrimary.copy(0.4f), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textPrimary.copy(0.5f)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                textStyle = TextStyle(color = theme.textPrimary, fontSize = 13.sp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = theme.background,
                                    unfocusedContainerColor = theme.background,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Clear / Close
                            IconButton(
                                onClick = {
                                    onClearSearch()
                                    showSearchPanel = false
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = theme.textPrimary.copy(0.5f))
                            }
                        }

                        // Option Toggles & Navigators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search filters
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = searchCaseSensitive,
                                    onClick = onToggleSearchCaseSensitive,
                                    label = { Text("大小写", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.accent.copy(alpha = 0.15f),
                                        selectedLabelColor = theme.accent
                                    )
                                )

                                FilterChip(
                                    selected = searchRegex,
                                    onClick = onToggleSearchRegex,
                                    label = { Text("正则", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = theme.accent.copy(alpha = 0.15f),
                                        selectedLabelColor = theme.accent
                                    )
                                )
                            }

                            // Match statistics and Navigation keys
                            if (searchQuery.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (searchResults.isEmpty()) "无匹配" else "${currentSearchMatchIndex + 1}/${searchResults.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (searchResults.isEmpty()) MaterialTheme.colorScheme.error else theme.textPrimary
                                    )

                                    FilledIconButton(
                                        onClick = onGoToPrevSearchMatch,
                                        enabled = searchResults.isNotEmpty(),
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = theme.background
                                        )
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, null, tint = theme.textPrimary)
                                    }

                                    FilledIconButton(
                                        onClick = onGoToNextSearchMatch,
                                        enabled = searchResults.isNotEmpty(),
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = theme.background
                                        )
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = theme.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Large File Editing Alert
            if (showLargeFileEditAlert) {
                AlertDialog(
                    onDismissRequest = { showLargeFileEditAlert = false },
                    title = { Text("智能分块编辑提示", color = theme.textPrimary) },
                    text = {
                        Text(
                            text = "当前文件较大 (${formatFileSize(readerState.size)})。PureText 已经为您自动开启了「编辑视窗懒加载」模式：系统将自动锁定您当前阅读第 ${lazyListState.firstVisibleItemIndex + 1} 行附近的区间（默认 400 行）供您毫秒级极速编辑。在此过程中，您还可以通过上下翻页键快速切换编辑范围，极致流畅。确认开启吗？",
                            color = theme.textPrimary.copy(0.7f)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLargeFileEditAlert = false
                                onToggleEditMode(lazyListState.firstVisibleItemIndex)
                            }
                        ) {
                            Text("开启编辑", color = theme.accent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLargeFileEditAlert = false }) {
                            Text("取消", color = theme.textPrimary)
                        }
                    },
                    containerColor = theme.surface
                )
            }
        }
        }
    }
}

@Composable
fun ReaderLineRow(
    lineIdx: Int,
    lineText: String,
    language: String,
    theme: HighlightTheme,
    settings: UserSettings,
    resolvedFontFamily: FontFamily,
    searchResults: List<SearchResult>
) {
    val matchesOnThisLine = remember(searchResults, lineIdx) {
        searchResults.filter { it.lineIndex == lineIdx }
    }

    // Live Syntax-Highlighter with overlay Search Highlights
    val annotatedContent = remember(lineText, language, theme, matchesOnThisLine) {
        buildAnnotatedString {
            // Apply language highlighter Base
            append(SyntaxHighlighter.highlight(lineText, language, theme))
            // Overlay manual Search Highlighter
            matchesOnThisLine.forEach { match ->
                val start = match.charIndex.coerceIn(0, lineText.length)
                val end = (match.charIndex + match.length).coerceIn(0, lineText.length)
                if (start < end) {
                    addStyle(
                        SpanStyle(
                            background = Color(0x99FFA500), // Strong gold highlight overlay
                            color = Color.Black
                        ),
                        start,
                        end
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (matchesOnThisLine.isNotEmpty()) theme.accent.copy(alpha = 0.08f) else Color.Transparent
            )
            .padding(vertical = 1.dp)
    ) {
        // Line spacing columns (Line Numbers gutter)
        if (settings.showLineNumbers) {
            Text(
                text = "${lineIdx + 1}",
                style = TextStyle(
                    fontFamily = resolvedFontFamily,
                    fontSize = (settings.fontSize - 3f).sp,
                    color = theme.comment.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .width(42.dp)
                    .padding(end = 8.dp),
                textAlign = TextAlign.End
            )
        }

        // Sub-Line Text Reader
        val lineModifier = if (settings.wordWrapEnabled) {
            Modifier
                .weight(1f)
                .padding(end = settings.horizontalPadding.dp)
        } else {
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(end = settings.horizontalPadding.dp)
        }

        Text(
            text = annotatedContent,
            style = TextStyle(
                fontFamily = resolvedFontFamily,
                fontSize = settings.fontSize.sp,
                lineHeight = (settings.fontSize * settings.lineHeightMultiplier).sp,
                letterSpacing = settings.letterSpacing.sp,
                color = theme.textPrimary
            ),
            modifier = lineModifier
        )
    }
}

// Format File Size
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
