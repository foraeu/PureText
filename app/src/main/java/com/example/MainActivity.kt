package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.HighlightTheme
import com.example.viewmodel.PureTextViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PureTextViewModel by viewModels()
    private var globalNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.userSettings.collectAsStateWithLifecycle()
            val theme = remember(settings.themeName) { HighlightTheme.fromName(settings.themeName) }
            val navController = rememberNavController()

            // Handle incoming document intent on initial startup
            LaunchedEffect(navController) {
                globalNavController = navController
                intent?.let { handleIncomingIntent(it) }
            }

            MyApplicationTheme(theme = theme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            val recents by viewModel.recentFiles.collectAsStateWithLifecycle()
                            HomeScreen(
                                theme = theme,
                                recentFiles = recents,
                                onOpenFile = { uri ->
                                    viewModel.openFile(uri)
                                    navController.navigate("reader")
                                },
                                onDeleteRecent = { viewModel.deleteRecent(it) },
                                onClearAllRecents = { viewModel.clearAllRecents() },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("reader") {
                            val readerState by viewModel.readerState.collectAsStateWithLifecycle()
                            val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
                            val editableText by viewModel.editableText.collectAsStateWithLifecycle()
                            val hasUnsavedEdits by viewModel.hasUnsavedEdits.collectAsStateWithLifecycle()
                            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                            val caseSensitive by viewModel.searchCaseSensitive.collectAsStateWithLifecycle()
                            val idxRegex by viewModel.searchRegex.collectAsStateWithLifecycle()
                            val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                            val matchIndex by viewModel.currentSearchMatchIndex.collectAsStateWithLifecycle()
                            val editStartLineIndex by viewModel.editStartLineIndex.collectAsStateWithLifecycle()
                            val editEndLineIndex by viewModel.editEndLineIndex.collectAsStateWithLifecycle()

                            ReaderScreen(
                                theme = theme,
                                settings = settings,
                                readerState = readerState,
                                isEditMode = isEditMode,
                                editableText = editableText,
                                hasUnsavedEdits = hasUnsavedEdits,
                                editStartLineIndex = editStartLineIndex,
                                editEndLineIndex = editEndLineIndex,
                                searchQuery = searchQuery,
                                searchCaseSensitive = caseSensitive,
                                searchRegex = idxRegex,
                                searchResults = searchResults,
                                currentSearchMatchIndex = matchIndex,
                                scrollRequest = viewModel.scrollRequest,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onToggleEditMode = { viewModel.toggleEditMode(it) },
                                onNavigateEditWindow = { viewModel.navigateEditWindow(it) },
                                onUpdateEditableText = { viewModel.updateEditableText(it) },
                                onSaveEdits = { viewModel.saveEdits() },
                                onUpdateSearchQuery = { viewModel.updateSearchQuery(it) },
                                onToggleSearchCaseSensitive = { viewModel.toggleSearchCaseSensitive() },
                                onToggleSearchRegex = { viewModel.toggleSearchRegex() },
                                onGoToNextSearchMatch = { viewModel.goToNextSearchMatch() },
                                onGoToPrevSearchMatch = { viewModel.goToPrevSearchMatch() },
                                onClearSearch = { viewModel.clearSearch() },
                                onUpdateScrollCoordinates = { index, offset ->
                                    viewModel.updateScrollCoordinates(index, offset)
                                },
                                onNavigateBack = {
                                    viewModel.clearSearch()
                                    navController.navigateUp()
                                },
                                onOpenFile = { viewModel.openFile(it) }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                theme = theme,
                                settings = settings,
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onNavigateBack = { navController.navigateUp() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val action = intent.action
        val dataUri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                val streamValue = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val type = intent.type
                if (streamValue != null && (type != null && (type.startsWith("text/") || type == "application/octet-stream"))) {
                    streamValue
                } else null
            }
            else -> null
        }

        dataUri?.let { uri ->
            viewModel.openFile(uri)
            globalNavController?.let { controller ->
                if (controller.currentDestination?.route != "reader") {
                    controller.navigate("reader") {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
