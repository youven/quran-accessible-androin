package com.youven.quranaccessible.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.youven.quranaccessible.R
import com.youven.quranaccessible.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

private sealed interface PageState {
    data object Loading : PageState
    data class Failed(val part: PageLoadException.Part?) : PageState
    data class Ready(val result: LoadedPage) : PageState
}

private val RECITERS = listOf(
    "أبو بكر الشاطري",
    "مشاري راشد العفاسي",
    "عبد الباسط عبد الصمد",
    "محمود خليل الحصري",
    "سعد الغامدي",
    "ماهر المعيقلي",
    "أحمد بن علي العجمي",
    "سعود الشريم"
)

@Composable
fun ReaderScreen(
    page: Int,
    onPageChange: (Int) -> Unit,
    repository: PageRepository,
    easyMode: Boolean,
    onToggleEasyMode: (Boolean) -> Unit,
    bookmarks: Set<Int>,
    onToggleBookmark: () -> Unit,
    onPageLoaded: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var accessible by rememberSaveable {
        mutableStateOf(easyMode || (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled)
    }

    var textSize by rememberSaveable { mutableIntStateOf(30) }
    var view by remember { mutableStateOf<MushafTextView?>(null) }

    // Dialog & Menu visibility states
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    var showDisplayDialog by remember { mutableStateOf(false) }
    var showReciterDialog by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    // Audio playback state
    var selectedReciter by rememberSaveable { mutableStateOf("أبو بكر الشاطري") }
    var isPlaying by rememberSaveable { mutableStateOf(false) }

    // Auto-hiding bottom bar state
    var bottomBarVisible by rememberSaveable { mutableStateOf(true) }

    // Fast scroll Rub' badge state
    var showRubBadge by remember { mutableStateOf(false) }
    var rubBadgeTriggerTime by remember { mutableLongStateOf(0L) }

    // Rub badge auto-dismiss effect
    LaunchedEffect(rubBadgeTriggerTime) {
        if (rubBadgeTriggerTime > 0) {
            showRubBadge = true
            delay(1600)
            showRubBadge = false
        }
    }

    val surahInfo = QuranMetadata.surahForPage(page)
    val juzNumber = QuranMetadata.juzForPage(page)
    val rubInfo = QuranMetadata.rubForPage(page)
    val isBookmarked = page in bookmarks

    Scaffold(
        containerColor = Color(0xFFFAF8F5),
        topBar = {
            // --- Top App Bar (RTL: Back on Right, Titles in Center, Actions on Left) ---
            Surface(
                color = Color(0xFFFAF8F5),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right: Back Button (points right in RTL)
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "رجوع إلى الشاشة الرئيسية",
                            tint = Color(0xFF1C2520)
                        )
                    }

                    // Center: Surah Name & Page/Juz Info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showJumpDialog = true },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "سورة ${surahInfo.nameArabic}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C2520)
                        )
                        Text(
                            text = "صفحة ${QuranMetadata.toArabicDigits(page)}، جزء ${QuranMetadata.toArabicDigits(juzNumber)}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    // Left Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Bookmark Toggle Icon
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "إزالة الإشارة المرجعية" else "إضافة إشارة مرجعية",
                                tint = if (isBookmarked) Color(0xFF0D8A74) else Color(0xFF1C2520)
                            )
                        }

                        // Display / Reading Mode Icon (Globe / Language)
                        IconButton(onClick = { showDisplayDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "خيارات العرض والقراءة",
                                tint = Color(0xFF1C2520)
                            )
                        }

                        // Overflow Menu Icon (⋮)
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "خيارات إضافية",
                                    tint = Color(0xFF1C2520)
                                )
                            }

                            AppOverflowMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                                onQuickJump = { showJumpDialog = true },
                                onSettings = onOpenSettings,
                                onHelp = onOpenHelp,
                                onAbout = onOpenAbout,
                                onOtherApps = onOpenAbout
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // --- Animated Auto-hiding Bottom Bar ---
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = Color(0xFFFAF8F5),
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Row: Reciter Name & Audio Play/Pause Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showReciterDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = Color(0xFF0D8A74),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = selectedReciter,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1C2520)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF0D8A74)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل التلاوة",
                                    tint = Color.White
                                )
                            }
                        }

                        // Bottom Row: Navigation Buttons (السابقة / التالية) & Page Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPageChange(page - 1) },
                                enabled = page > 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("السابقة", fontSize = 15.sp)
                            }

                            TextButton(
                                onClick = { showJumpDialog = true },
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(
                                    text = "${QuranMetadata.toArabicDigits(page)} / ٦٠٤",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D8A74)
                                )
                            }

                            Button(
                                onClick = { onPageChange(page + 1) },
                                enabled = page < 604,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D8A74))
                            ) {
                                Text("التالية", fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            key(page) {
                var retry by remember { mutableIntStateOf(0) }
                val state by produceState<PageState>(PageState.Loading, retry) {
                    value = PageState.Loading
                    value = try {
                        PageState.Ready(repository.load(page))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: PageLoadException) {
                        PageState.Failed(error.part)
                    } catch (_: Exception) {
                        PageState.Failed(null)
                    }
                }

                val onLoaded by rememberUpdatedState(onPageLoaded)
                LaunchedEffect(state) {
                    if (state is PageState.Ready) onLoaded(page)
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (val current = state) {
                        PageState.Loading -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF0D8A74))
                            Text(
                                "جارٍ تحميل نص الصفحة وخطها…",
                                color = Color(0xFF6B7280),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }

                        is PageState.Failed -> Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val detail = when (current.part) {
                                PageLoadException.Part.TEXT -> "تعذّر تحميل نص الصفحة."
                                PageLoadException.Part.PAGE_FONT -> "تم تحميل النص، لكن تعذّر تحميل خط هذه الصفحة."
                                PageLoadException.Part.PAGE_GLYPHS -> "تم تحميل الخط، لكن بعض رموز الصفحة غير متاحة فيه."
                                PageLoadException.Part.COMMON_FONT -> "تعذّر تحميل خط البسملة أو عناوين السور."
                                PageLoadException.Part.CHAPTERS -> "تعذّر تحميل أسماء السور."
                                null -> stringResource(R.string.load_error)
                            }
                            Text(
                                detail,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            Text(
                                "يمكنك الضغط على إعادة المحاولة. إذا استمر الخطأ، أرسل رقم الصفحة وصورة الرسالة.",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                            Button(
                                onClick = { retry++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D8A74))
                            ) {
                                Text("إعادة المحاولة")
                            }
                        }

                        is PageState.Ready -> {
                            if (accessible) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .clickable { bottomBarVisible = !bottomBarVisible }
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    current.result.page.words.groupBy { it.verse }.forEach { (verse, words) ->
                                        Text(
                                            words.joinToString(" ") { it.text },
                                            fontSize = textSize.sp,
                                            lineHeight = (textSize * 1.9).sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            "${current.result.chapterNames[verse.substringBefore(':').toInt()]} · آية ${verse.substringAfter(':')}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color(0xFF0D8A74)
                                        )
                                    }
                                }
                            } else {
                                AndroidView(
                                    factory = { ctx ->
                                        MushafTextView(ctx).also { v ->
                                            view = v
                                            v.onScrollListener = { dx, dy ->
                                                if (abs(dy) > 15f || abs(dx) > 15f) {
                                                    rubBadgeTriggerTime = System.currentTimeMillis()
                                                }
                                                if (dy > 12f) {
                                                    bottomBarVisible = false
                                                } else if (dy < -12f) {
                                                    bottomBarVisible = true
                                                }
                                            }
                                            v.onTapListener = {
                                                bottomBarVisible = !bottomBarVisible
                                            }
                                            v.bind(current.result)
                                        }
                                    },
                                    update = { v ->
                                        v.bind(current.result)
                                        view = v
                                        v.onScrollListener = { dx, dy ->
                                            if (abs(dy) > 15f || abs(dx) > 15f) {
                                                rubBadgeTriggerTime = System.currentTimeMillis()
                                            }
                                            if (dy > 12f) {
                                                bottomBarVisible = false
                                            } else if (dy < -12f) {
                                                bottomBarVisible = true
                                            }
                                        }
                                        v.onTapListener = {
                                            bottomBarVisible = !bottomBarVisible
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // --- Floating Rub' el Hizb Badge on Fast Scroll ---
            AnimatedVisibility(
                visible = showRubBadge,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    color = Color(0xF01C2520),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness7,
                            contentDescription = null,
                            tint = Color(0xFF0D8A74),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ربع الحزب ${QuranMetadata.toArabicDigits(rubInfo.rubNumber)} (${rubInfo.surahName})",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // Quick Jump Dialog
    if (showJumpDialog) {
        QuickJumpDialog(
            currentPage = page,
            onDismiss = { showJumpDialog = false },
            onJumpToPage = {
                onPageChange(it)
                showJumpDialog = false
            }
        )
    }

    // Reciter Selection Dialog
    if (showReciterDialog) {
        AlertDialog(
            onDismissRequest = { showReciterDialog = false },
            title = {
                Text(
                    text = "اختر القارئ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C2520)
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(RECITERS) { reciter ->
                        val isSelected = reciter == selectedReciter
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFE8F5F2) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedReciter = reciter
                                    showReciterDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = reciter,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF0D8A74) else Color(0xFF1C2520)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0D8A74),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReciterDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Display Options / Reading Mode Dialog
    if (showDisplayDialog) {
        AlertDialog(
            onDismissRequest = { showDisplayDialog = false },
            title = {
                Text(
                    text = "خيارات العرض والقراءة",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C2520)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mode selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (accessible) "النص الميسّر وقارئ الشاشة" else "رسم المصحف العثماني",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1C2520)
                            )
                            Text(
                                text = if (accessible) "عرض خط النظام القياسي مع إمكانية التكبير" else "رسم مصحف المدينة المنورة (QCF V2)",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        Switch(
                            checked = accessible,
                            onCheckedChange = {
                                accessible = it
                                onToggleEasyMode(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0D8A74)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE8E5DD))

                    // Zoom Controls
                    Text(
                        text = "التحكم في حجم العرض والتكبير",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C2520)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (accessible) {
                                    textSize = (textSize + 4).coerceAtMost(64)
                                } else {
                                    view?.zoomBy(1.25f)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("تكبير +", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {
                                if (accessible) {
                                    textSize = (textSize - 4).coerceAtLeast(22)
                                } else {
                                    view?.zoomBy(0.8f)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("تصغير −", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = {
                                view?.resetZoom()
                                textSize = 30
                            },
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("إعادة ضبط", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayDialog = false }) {
                    Text("تم")
                }
            }
        )
    }
}
