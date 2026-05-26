package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserSettings
import com.example.utils.HighlightTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: HighlightTheme,
    settings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "个性化设置",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Theme Section
            SettingsSectionHeader(title = "阅读主题配色", icon = Icons.Default.Palette, theme = theme)
            ThemeSelectorGrid(
                activeThemeName = settings.themeName,
                onSelected = { name ->
                    onUpdateSettings { it.copy(themeName = name) }
                },
                theme = theme
            )

            // Typography Section
            SettingsSectionHeader(title = "排版与字体", icon = Icons.Default.TextFields, theme = theme)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Family Choose
                    Text(
                        text = "字体样式",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textPrimary.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fonts = listOf("Monospace", "System Default", "Serif", "Sans-Serif")
                        fonts.forEach { font ->
                            val isSelected = settings.fontFamilyName == font
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.accent else theme.background)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else theme.textPrimary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onUpdateSettings { it.copy(fontFamilyName = font) }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (font) {
                                        "Monospace" -> "等宽"
                                        "System Default" -> "系统"
                                        "Serif" -> "衬线"
                                        else -> "非衬线"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else theme.textPrimary
                                )
                            }
                        }
                    }

                    // Font Size Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "字体大小 : ${settings.fontSize.toInt()}sp",
                                fontSize = 14.sp,
                                color = theme.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Slider(
                            value = settings.fontSize,
                            onValueChange = { value ->
                                onUpdateSettings { it.copy(fontSize = value) }
                            },
                            valueRange = 12f..30f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                activeTrackColor = theme.accent,
                                thumbColor = theme.accent
                            )
                        )
                    }

                    // Line Height Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "行高比例 : ${String.format("%.1f", settings.lineHeightMultiplier)}x",
                                fontSize = 14.sp,
                                color = theme.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Slider(
                            value = settings.lineHeightMultiplier,
                            onValueChange = { value ->
                                onUpdateSettings { it.copy(lineHeightMultiplier = value) }
                            },
                            valueRange = 1.1f..2.5f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                activeTrackColor = theme.accent,
                                thumbColor = theme.accent
                            )
                        )
                    }

                    // Horizontal Margin Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "左右页边距 : ${settings.horizontalPadding}dp",
                                fontSize = 14.sp,
                                color = theme.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Slider(
                            value = settings.horizontalPadding.toFloat(),
                            onValueChange = { value ->
                                onUpdateSettings { it.copy(horizontalPadding = value.toInt()) }
                            },
                            valueRange = 8f..36f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                activeTrackColor = theme.accent,
                                thumbColor = theme.accent
                            )
                        )
                    }
                }
            }

            // Reader Behavior Section
            SettingsSectionHeader(title = "功能与行为", icon = Icons.Default.SettingsSuggest, theme = theme)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Line numbers toggle
                    BehaviorToggleItem(
                        title = "显示行号",
                        subtitle = "在阅读器左侧显示源文件的行号",
                        checked = settings.showLineNumbers,
                        onCheckedChange = { value ->
                            onUpdateSettings { it.copy(showLineNumbers = value) }
                        },
                        theme = theme
                    )

                    // Word wrap toggle
                    BehaviorToggleItem(
                        title = "自动换行",
                        subtitle = "行宽超出屏幕时自动折行，关闭则启用横向滚动",
                        checked = settings.wordWrapEnabled,
                        onCheckedChange = { value ->
                            onUpdateSettings { it.copy(wordWrapEnabled = value) }
                        },
                        theme = theme
                    )

                    // Immersive reading mode toggle
                    BehaviorToggleItem(
                        title = "沉浸式阅读模式",
                        subtitle = "打开文件时，自动收起顶部及底部操作工具栏",
                        checked = settings.immersiveReadEnabled,
                        onCheckedChange = { value ->
                            onUpdateSettings { it.copy(immersiveReadEnabled = value) }
                        },
                        theme = theme
                    )

                    // Default Encoding picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "默认文本编码",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = theme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "打开新文本文件时自动解析的编码机制",
                                fontSize = 11.sp,
                                color = theme.textPrimary.copy(alpha = 0.5f)
                            )
                        }

                        var showEncodingMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(
                                onClick = { showEncodingMenu = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = theme.accent)
                            ) {
                                Text(settings.defaultEncoding)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = showEncodingMenu,
                                onDismissRequest = { showEncodingMenu = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                val encodings = listOf("UTF-8", "GBK", "UTF-16", "ISO-8859-1", "ASCII", "Auto")
                                encodings.forEach { enc ->
                                    DropdownMenuItem(
                                        text = { Text(enc, color = theme.textPrimary) },
                                        onClick = {
                                            onUpdateSettings { it.copy(defaultEncoding = enc) }
                                            showEncodingMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tech specs Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(theme.accent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = theme.accent)
                    }

                    Column {
                        Text(
                            "PureText v1.0 • 极致流畅的纯净阅读",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "采用先进的视口行级懒加载渲染技术，无卡顿流畅处理 100,000+ 行的大容量文件，内存极速启动。",
                            fontSize = 11.sp,
                            color = theme.textPrimary.copy(alpha = 0.6f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: HighlightTheme
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
        )
    }
}

@Composable
fun ThemeSelectorGrid(
    activeThemeName: String,
    onSelected: (String) -> Unit,
    theme: HighlightTheme
) {
    val themes = listOf(
        "Soft Dark" to "柔和暗色",
        "Deep Black" to "极夜深色",
        "Sepia" to "护眼复古",
        "Paper Light" to "经典浅色",
        "High Contrast" to "高敏调试"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        themes.forEach { (key, label) ->
            val isSelected = activeThemeName == key
            val sample = HighlightTheme.fromName(key)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) theme.accent else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelected(key) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = sample.background)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Radio dot
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) theme.accent else sample.textPrimary.copy(0.4f),
                                    shape = CircleShape
                                )
                                .background(
                                    color = if (isSelected) theme.accent else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }

                        Text(
                            text = label,
                            color = sample.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Colorful preview indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(sample.keyword, CircleShape))
                        Box(modifier = Modifier.size(10.dp).background(sample.string, CircleShape))
                        Box(modifier = Modifier.size(10.dp).background(sample.comment, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun BehaviorToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: HighlightTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = theme.textPrimary.copy(alpha = 0.5f),
                lineHeight = 14.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = theme.textPrimary.copy(0.5f),
                uncheckedTrackColor = theme.surface
            )
        )
    }
}
