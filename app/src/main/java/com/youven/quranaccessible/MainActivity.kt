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
import androidx.lifecycle.viewModelScope
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.PageRepository
import com.youven.quranaccessible.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences("reading_settings", MODE_PRIVATE)
        val readerModel = ViewModelProvider(this)[ReaderModel::class.java]
        val repository = readerModel.repository

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

            // First app launch download prompt state
            var showFirstLaunchPrompt by remember {
                val alreadyShown = preferences.getBoolean("first_launch_prompt_shown", false)
                mutableStateOf(!alreadyShown && !readerModel.isFullyDownloaded)
            }

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
                        if (showFirstLaunchPrompt) {
                            FirstLaunchDownloadDialog(
                                downloadedPagesCount = readerModel.downloadedCount,
                                isDownloading = readerModel.isDownloading,
                                downloadPercentage = readerModel.downloadPercentage,
                                onStartDownload = {
                                    preferences.edit().putBoolean("first_launch_prompt_shown", true).apply()
                                    readerModel.startFullDownload()
                                },
                                onCancelDownload = {
                                    readerModel.cancelFullDownload()
                                },
                                onDismiss = {
                                    preferences.edit().putBoolean("first_launch_prompt_shown", true).apply()
                                    showFirstLaunchPrompt = false
                                }
                            )
                        }

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
                                downloadedPagesCount = readerModel.downloadedCount,
                                isDownloading = readerModel.isDownloading,
                                downloadPercentage = readerModel.downloadPercentage,
                                onStartDownload = {
                                    readerModel.startFullDownload()
                                },
                                onCancelDownload = {
                                    readerModel.cancelFullDownload()
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

/**
 * Manages persistent storage and full offline downloading of the Holy Quran across configuration changes.
 */
class ReaderModel(application: Application) : AndroidViewModel(application) {
    val repository: PageRepository = run {
        val targetDir = File(application.filesDir, "quran_pages").also { it.mkdirs() }
        // Automatically migrate any files from previous cacheDir
        try {
            application.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.startsWith("page-") || file.name.startsWith("qcf-") || file.name.startsWith("chapters-") || file.name.startsWith("surah-names-"))) {
                    val dest = File(targetDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                }
            }
        } catch (_: Exception) {}
        PageRepository(targetDir)
    }

    private var downloadJob: Job? = null

    var downloadedCount by mutableIntStateOf(repository.getCachedPagesCount())
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var currentDownloadingPage by mutableIntStateOf(1)
        private set

    val downloadPercentage: Int
        get() = (downloadedCount * 100) / 604

    val isFullyDownloaded: Boolean
        get() = downloadedCount >= 604

    fun startFullDownload() {
        if (isDownloading) return
        isDownloading = true
        downloadJob = viewModelScope.launch {
            try {
                repository.downloadAllPages { count, _, pageNum ->
                    downloadedCount = count
                    currentDownloadingPage = pageNum
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User cancelled or paused
            } catch (e: Exception) {
                android.util.Log.w("QuranDownloader", "Download error", e)
            } finally {
                isDownloading = false
                downloadedCount = repository.getCachedPagesCount()
            }
        }
    }

    fun cancelFullDownload() {
        downloadJob?.cancel()
        downloadJob = null
        isDownloading = false
        downloadedCount = repository.getCachedPagesCount()
    }
}
