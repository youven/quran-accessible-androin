package com.youven.quranaccessible

import android.os.Bundle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.PageRepository
import com.youven.quranaccessible.ui.ReaderScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("reading_settings", MODE_PRIVATE)
        val repository = ViewModelProvider(this)[ReaderModel::class.java].repository
        setContent {
            var easyMode by rememberSaveable { mutableStateOf(preferences.getBoolean("easy_mode", true)) }
            var reading by rememberSaveable { mutableStateOf(false) }
            var lastRead by rememberSaveable {
                mutableIntStateOf(MadaniPage.restoredPage(preferences.getInt("last_page", 1)))
            }
            var page by rememberSaveable { mutableIntStateOf(lastRead) }
            var bookmarks by remember {
                mutableStateOf(preferences.getStringSet("bookmarks", emptySet()).orEmpty()
                    .mapNotNull(MadaniPage::parse).toSet())
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = lightColorScheme(
                    primary = Color(0xFF185B49), onPrimary = Color.White,
                    primaryContainer = Color(0xFFDDECE2), onPrimaryContainer = Color(0xFF12392E),
                    background = Color(0xFFFAF7EF), surface = Color(0xFFFAF7EF),
                    onSurface = Color(0xFF202D26), secondary = Color(0xFF725B2A)
                )) {
                    Surface(Modifier.fillMaxSize()) {
                        if (reading) {
                            ReaderScreen(
                                page = page, onPageChange = { page = it },
                                repository = repository, easyMode = easyMode,
                                bookmarks = bookmarks,
                                onToggleBookmark = {
                                    bookmarks = if (page in bookmarks) bookmarks - page else bookmarks + page
                                    preferences.edit().putStringSet("bookmarks", bookmarks.map(Int::toString).toSet()).apply()
                                },
                                onPageLoaded = { loaded ->
                                    lastRead = loaded
                                    preferences.edit().putInt("last_page", loaded).apply()
                                },
                                onBack = { reading = false }
                            )
                        } else {
                            Column(
                                Modifier.fillMaxSize().safeDrawingPadding()
                                    .verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineSmall)
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(stringResource(R.string.edition_title), fontSize = if (easyMode) 26.sp else 22.sp)
                                        Text(stringResource(R.string.edition_detail), fontSize = 18.sp)
                                        Button(onClick = { page = lastRead; reading = true },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                            Text(stringResource(R.string.resume_page, lastRead), fontSize = 20.sp)
                                        }
                                        OutlinedButton(onClick = { page = 1; reading = true },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                            Text(stringResource(R.string.start_reading))
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().toggleable(value = easyMode, role = Role.Switch,
                                    onValueChange = {
                                        easyMode = it
                                        preferences.edit().putBoolean("easy_mode", it).apply()
                                    }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.easy_mode), fontSize = 22.sp)
                                        Text(stringResource(R.string.easy_description))
                                    }
                                    Switch(checked = easyMode, onCheckedChange = null)
                                }
                                Text(stringResource(R.string.text_reader_notice), fontSize = 18.sp, lineHeight = 28.sp)
                                Text(stringResource(R.string.source_credit), style = MaterialTheme.typography.bodyMedium)
                            }
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
