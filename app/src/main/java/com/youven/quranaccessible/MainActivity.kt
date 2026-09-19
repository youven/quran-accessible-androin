package com.youven.quranaccessible

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.PageRepository
import com.youven.quranaccessible.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences("reading_settings", MODE_PRIVATE)
        val repository = ViewModelProvider(this)[ReaderModel::class.java].repository

        setContent {
            var easyMode by rememberSaveable {
                mutableStateOf(preferences.getBoolean("easy_mode", false))
            }
            var isReading by rememberSaveable { mutableStateOf(false) }
            var isSearching by rememberSaveable { mutableStateOf(false) }

            var lastRead by rememberSaveable {
                mutableIntStateOf(MadaniPage.restoredPage(preferences.getInt("last_page", 1)))
            }
            var page by rememberSaveable { mutableIntStateOf(lastRead) }

            var bookmarks by remember {
                mutableStateOf(
                    preferences.getStringSet("bookmarks", emptySet()).orEmpty()
                        .mapNotNull(MadaniPage::parse).toSet()
                )
            }

            var recents by remember {
                val raw = preferences.getString("recents", "") ?: ""
                val list = if (raw.isBlank()) {
                    listOf(lastRead)
                } else {
                    raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..604 }
                }
                mutableStateOf(list.ifEmpty { listOf(1) })
            }

            // Global Dialog States
            var showQuickJump by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showHelp by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }

            fun recordPageVisit(visitedPage: Int) {
                lastRead = visitedPage
                preferences.edit().putInt("last_page", visitedPage).apply()
                val updated = (listOf(visitedPage) + (recents - visitedPage)).take(20)
                recents = updated
                preferences.edit().putString("recents", updated.joinToString(",")).apply()
            }

            fun toggleBookmark(targetPage: Int) {
                bookmarks = if (targetPage in bookmarks) bookmarks - targetPage else bookmarks + targetPage
                preferences.edit().putStringSet("bookmarks", bookmarks.map(Int::toString).toSet()).apply()
            }

            // Force RTL across the entire application
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF0D8A74),
                        onPrimary = Color.White,
                        primaryContainer = Color(0xFFDDECE2),
                        onPrimaryContainer = Color(0xFF0A584B),
                        background = Color(0xFFFAF8F5),
                        surface = Color(0xFFFAF8F5),
                        onSurface = Color(0xFF1C2520),
                        secondary = Color(0xFF0D8A74)
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFFAF8F5)
                    ) {
                        when {
                            isSearching -> {
                                BackHandler { isSearching = false }
                                SearchScreen(
                                    onClose = { isSearching = false },
                                    onOpenPage = { targetPage ->
                                        page = targetPage
                                        recordPageVisit(targetPage)
                                        isSearching = false
                                        isReading = true
                                    }
                                )
                            }
                            isReading -> {
                                ReaderScreen(
                                    page = page,
                                    onPageChange = { targetPage ->
                                        page = targetPage
                                        recordPageVisit(targetPage)
                                    },
                                    repository = repository,
                                    easyMode = easyMode,
                                    onToggleEasyMode = {
                                        easyMode = it
                                        preferences.edit().putBoolean("easy_mode", it).apply()
                                    },
                                    bookmarks = bookmarks,
                                    onToggleBookmark = { toggleBookmark(page) },
                                    onPageLoaded = { loaded -> recordPageVisit(loaded) },
                                    onBack = { isReading = false },
                                    onOpenSettings = { showSettings = true },
                                    onOpenHelp = { showHelp = true },
                                    onOpenAbout = { showAbout = true }
                                )
                            }
                            else -> {
                                HomeScreen(
                                    lastReadPage = lastRead,
                                    bookmarks = bookmarks,
                                    recents = recents,
                                    onOpenPage = { targetPage ->
                                        page = targetPage
                                        recordPageVisit(targetPage)
                                        isReading = true
                                    },
                                    onOpenSearch = { isSearching = true },
                                    onToggleBookmark = { toggleBookmark(it) },
                                    onQuickJump = { showQuickJump = true },
                                    onOpenSettings = { showSettings = true },
                                    onOpenHelp = { showHelp = true },
                                    onOpenAbout = { showAbout = true }
                                )
                            }
                        }

                        // Dialogs accessible from anywhere
                        if (showQuickJump) {
                            QuickJumpDialog(
                                currentPage = page,
                                onDismiss = { showQuickJump = false },
                                onJumpToPage = { targetPage ->
                                    page = targetPage
                                    recordPageVisit(targetPage)
                                    isReading = true
                                    showQuickJump = false
                                }
                            )
                        }

                        if (showSettings) {
                            SettingsDialog(
                                easyMode = easyMode,
                                onToggleEasyMode = {
                                    easyMode = it
                                    preferences.edit().putBoolean("easy_mode", it).apply()
                                },
                                onDismiss = { showSettings = false }
                            )
                        }

                        if (showHelp) {
                            HelpDialog(onDismiss = { showHelp = false })
                        }

                        if (showAbout) {
                            AboutDialog(onDismiss = { showAbout = false })
                        }
                    }
                }
            }
        }
    }
}

/** Keeps page fonts and text across rotation without retaining an Activity. */
class ReaderModel(application: Application) : AndroidViewModel(application) {
    val repository = PageRepository(application.cacheDir)
}
