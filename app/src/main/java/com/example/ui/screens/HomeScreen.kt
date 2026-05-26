package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RecentFile
import com.example.utils.HighlightTheme
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    theme: HighlightTheme,
    recentFiles: List<RecentFile>,
    onOpenFile: (Uri) -> Unit,
    onDeleteRecent: (RecentFile) -> Unit,
    onClearAllRecents: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onOpenFile(it) }
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = theme.accent
                        )
                        Text(
                            text = "PureText",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = theme.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background
                )
            )
        },
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Bento Grid Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Bento Tile: App Stats & Code Languages
                    Card(
                        modifier = Modifier
                            .weight(1.7f)
                            .height(134.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "PureText 极速解析",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "完美支持高阶文本与源码阅读",
                                    fontSize = 11.sp,
                                    color = theme.textPrimary.copy(alpha = 0.6f)
                                )
                            }
                            
                            // Visual horizontal ribbon of supported languages in bento pill styling
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val langs = listOf("KT", "PY", "RS", "TS", "MD", "JSON")
                                langs.forEach { lang ->
                                    Box(
                                        modifier = Modifier
                                            .background(theme.background.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = lang,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Bento Tile: Quick Theme Preview & Settings shortcut
                    Card(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(134.dp)
                            .clickable(onClick = onNavigateToSettings),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "阅读主题",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = theme.textPrimary.copy(0.7f),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            // Visual bento style themes preview dot row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(14.dp).background(theme.accent, CircleShape))
                                Box(modifier = Modifier.size(10.dp).background(theme.comment.copy(alpha = 0.6f), CircleShape))
                                Box(modifier = Modifier.size(7.dp).background(theme.string.copy(alpha = 0.6f), CircleShape))
                            }

                            Text(
                                text = "去切换风格 →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.accent
                            )
                        }
                    }
                }

                // Row 2: File Opener
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    docLauncher.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    docLauncher.launch(arrayOf("text/*", "application/*"))
                                }
                            }
                            .padding(18.dp)
                            .testTag("open_file_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(theme.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "选择本地文件",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = theme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SAF极速加载 • 极致流畅无卡顿",
                                fontSize = 11.sp,
                                color = theme.textPrimary.copy(alpha = 0.5f)
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = theme.textPrimary.copy(0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recents Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近打开的文件",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    )

                    if (recentFiles.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("clear_recents_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = theme.accent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清空历史", color = theme.accent)
                        }
                    }
                }

                // Recents List
                if (recentFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = theme.textPrimary.copy(alpha = 0.25f)
                            )
                            Text(
                                text = "暂无最近阅读历史",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = theme.textPrimary.copy(alpha = 0.5f)
                                )
                            )
                            Text(
                                text = "点击上方按钮选择一个文件，开启极简悦读体验",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = theme.textPrimary.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentFiles, key = { it.uriString }) { file ->
                            RecentFileItem(
                                recentFile = file,
                                theme = theme,
                                onClick = { onOpenFile(Uri.parse(file.uriString)) },
                                onDelete = { onDeleteRecent(file) }
                            )
                        }
                    }
                }
            }

            // Confirm clear dialog
            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    title = { Text("清空历史记录", color = theme.textPrimary) },
                    text = { Text("确定要清除所有最近查看的文件历史吗？此操作不会删除文件本身。", color = theme.textPrimary.copy(0.8f)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onClearAllRecents()
                                showClearConfirm = false
                            }
                        ) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) {
                            Text("取消", color = theme.textPrimary)
                        }
                    },
                    containerColor = theme.surface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileItem(
    recentFile: RecentFile,
    theme: HighlightTheme,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val dateStr = remember(recentFile.lastOpened) { formatter.format(Date(recentFile.lastOpened)) }
    val sizeStr = remember(recentFile.size) { formatFileSize(recentFile.size) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (recentFile.language.lowercase()) {
                        "python", "rust", "typescript", "javascript", "kotlin", "java" -> Icons.Default.Code
                        "json" -> Icons.Default.DataObject
                        "markdown", "epub" -> Icons.Default.MenuBook
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recentFile.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$sizeStr  •  ${recentFile.language}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = theme.textPrimary.copy(alpha = 0.5f)
                        )
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = theme.textPrimary.copy(alpha = 0.4f)
                        )
                    )
                }

                if (recentFile.scrollIndex > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已读至第 ${recentFile.scrollIndex + 1} 行",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = theme.accent,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove recent",
                    tint = theme.textPrimary.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
