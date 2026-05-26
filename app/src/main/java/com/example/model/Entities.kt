package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val uriString: String,
    val name: String,
    val size: Long,
    val lastOpened: Long = System.currentTimeMillis(),
    val language: String = "Text",
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1, // Single-row configuration
    val themeName: String = "Paper Light", // "Soft Dark", "Deep Black", "Sepia", "Paper Light", "High Contrast"
    val fontFamilyName: String = "Monospace", // "System Default", "Monospace", "Serif", "Sans-Serif"
    val fontSize: Float = 14f,
    val lineHeightMultiplier: Float = 1.4f,
    val letterSpacing: Float = 0.02f,
    val horizontalPadding: Int = 16, // in dp
    val immersiveReadEnabled: Boolean = false,
    val wordWrapEnabled: Boolean = true,
    val showLineNumbers: Boolean = true,
    val defaultEncoding: String = "UTF-8"
)
