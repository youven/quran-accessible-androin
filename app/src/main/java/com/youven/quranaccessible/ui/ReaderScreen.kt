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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface PageState {
    data object Loading : PageState
    data class Failed(val part: PageLoadException.Part?) : PageState
    data class Ready(val result: LoadedPage) : PageState
}

@Composable
fun ReaderScreen(
    page: Int,
    initialVerse: String? = null,
    autoPlay: Boolean = false,
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
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { AudioRecitationManager(context) }

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

    // Audio recitation state
    var recitersList by remember { mutableStateOf(AudioRecitationManager.DEFAULT_RECITERS) }
    var selectedReciterObj by remember { mutableStateOf(AudioRecitationManager.DEFAULT_RECITERS.first()) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var activeVerseKey by remember { mutableStateOf<String?>(initialVerse) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Overlay auto-toggling bars state (Single tap on page toggles both)
    var barsVisible by rememberSaveable { mutableStateOf(true) }

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

    // Horizontal Pager state for smooth swiping between Quran pages (1..604)
    val pagerState = rememberPagerState(
        initialPage = (page - 1).coerceIn(0, 603),
        pageCount = { 604 }
    )

    val currentPage = pagerState.currentPage + 1

    // Load dynamic reciters from Quran.com API
    LaunchedEffect(Unit) {
        val fetched = audioManager.loadReciters()
        if (fetched.isNotEmpty()) {
            recitersList = fetched
            val matched = fetched.find { it.id == selectedReciterObj.id } ?: fetched.first()
            selectedReciterObj = matched
        }
    }

    // Setup audio manager event listeners
    LaunchedEffect(audioManager) {
        audioManager.onVerseChangeListener = { verse ->
            activeVerseKey = verse
        }
        audioManager.onPlaybackStateChangeListener = { playing ->
            isPlayingAudio = playing
        }
        audioManager.onPageChangeListener = { nextPage ->
            coroutineScope.launch {
                pagerState.scrollToPage((nextPage - 1).coerceIn(0, 603))
            }
        }
        audioManager.onErrorListener = { errorMsg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(errorMsg)
            }
        }
    }

    // Handle initial auto-play from search or external action
    LaunchedEffect(initialVerse, autoPlay) {
        if (autoPlay && !initialVerse.isNullOrBlank()) {
            activeVerseKey = initialVerse
            audioManager.startPageRecitation(
                reciterId = selectedReciterObj.id,
                page = page,
                startVerseKey = initialVerse
            )
        }
    }

    // Synchronize pager page change with app state
    LaunchedEffect(pagerState.currentPage) {
        val newPage = pagerState.currentPage + 1
        if (newPage != page) {
            onPageChange(newPage)
        }
        // If reciting and user swiped to another page, continue recitation on new page
        if (isPlayingAudio && audioManager.currentPage != newPage) {
            audioManager.startPageRecitation(selectedReciterObj.id, newPage, null)
        }
    }

    // Synchronize external page change (e.g. from jump dialog) with pager
    LaunchedEffect(page) {
        val target = (page - 1).coerceIn(0, 603)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioManager.release()
        }
    }

    val surahInfo = QuranMetadata.surahForPage(currentPage)
    val juzNumber = QuranMetadata.juzForPage(currentPage)
    val rubInfo = QuranMetadata.rubForPage(currentPage)
    val isBookmarked = currentPage in bookmarks

    // Full screen root Box with Quran page underneath and overlay bars on top
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F5))
    ) {
        // --- 1. Quran Pages Horizontal Pager (Fills 100% of the screen, Edge-to-Edge) ---
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it }
        ) { pageIndex ->
            val pageNum = pageIndex + 1
            QuranPageView(
                pageNumber = pageNum,
                repository = repository,
                accessible = accessible,
                textSize = textSize,
                activeVerse = if (pageNum == currentPage) activeVerseKey else null,
                onPageLoaded = { onPageLoaded(pageNum) },
                onTap = { barsVisible = !barsVisible },
                onFastScroll = { rubBadgeTriggerTime = System.currentTimeMillis() },
                onScrollDirection = { dy ->
                    if (dy > 14f) barsVisible = false
                    else if (dy < -14f) barsVisible = true
                },
                onRegisterView = { v ->
                    if (pageNum == currentPage) view = v
                }
            )
        }

        // --- 2. Top Bar Overlay (Z-Index above page, toggled on page click) ---
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xFFFAF8F5).copy(alpha = 0.96f),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right: Back Button
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "رجوع إلى الشاشة الرئيسية",
                            tint = Color(0xFF1C2520)
                        )
                    }

                    // Center: Surah Name & Page/Juz Info (Clickable for Quick Jump)
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
                            text = "صفحة ${QuranMetadata.toArabicDigits(currentPage)}، جزء ${QuranMetadata.toArabicDigits(juzNumber)}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    // Left: Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "إزالة الإشارة المرجعية" else "إضافة إشارة مرجعية",
                                tint = if (isBookmarked) Color(0xFF0D8A74) else Color(0xFF1C2520)
                            )
                        }

                        IconButton(onClick = { showDisplayDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "خيارات العرض والقراءة",
                                tint = Color(0xFF1C2520)
                            )
                        }

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
        }

        // --- 3. Bottom Bar Overlay (Z-Index above page, streamlined without previous/next buttons) ---
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xFFFAF8F5).copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Reciter selector
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
                                text = selectedReciterObj.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1C2520),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Page indicator badge (Clickable to jump)
                        Surface(
                            color = Color(0xFFE8F5F2),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clickable { showJumpDialog = true }
                        ) {
                            Text(
                                text = "${QuranMetadata.toArabicDigits(currentPage)} / ٦٠٤",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D8A74),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        // Audio Play / Pause Button
                        FilledIconButton(
                            onClick = {
                                if (isPlayingAudio) {
                                    audioManager.pause()
                                } else {
                                    if (audioManager.currentPage == currentPage && audioManager.currentVerseKey != null) {
                                        audioManager.resume()
                                    } else {
                                        audioManager.startPageRecitation(
                                            reciterId = selectedReciterObj.id,
                                            page = currentPage,
                                            startVerseKey = activeVerseKey
                                        )
                                    }
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF0D8A74)
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "إيقاف مؤقت" else "تشغيل التلاوة",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Floating Rub' el Hizb Badge on Fast Scroll ---
        AnimatedVisibility(
            visible = showRubBadge,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (barsVisible) 80.dp else 24.dp)
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

        // --- 5. Snackbar Host for audio notifications and error messages ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (barsVisible) 84.dp else 20.dp)
        )
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

    // Reciter Selection Dialog with Quran.com Reciters & Live Search
    if (showReciterDialog) {
        var filterText by remember { mutableStateOf("") }
        val filteredReciters = remember(filterText, recitersList) {
            if (filterText.isBlank()) recitersList
            else {
                val q = filterText.trim().lowercase()
                recitersList.filter {
                    it.nameArabic.contains(q) ||
                    it.englishName.lowercase().contains(q) ||
                    (it.style?.contains(q) == true)
                }
            }
        }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("ابحث عن قارئ…", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredReciters, key = { it.id }) { reciter ->
                            val isSelected = reciter.id == selectedReciterObj.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFE8F5F2) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedReciterObj = reciter
                                        showReciterDialog = false
                                        if (isPlayingAudio) {
                                            audioManager.startPageRecitation(
                                                reciterId = reciter.id,
                                                page = currentPage,
                                                startVerseKey = activeVerseKey
                                            )
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reciter.nameArabic,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF0D8A74) else Color(0xFF1C2520)
                                        )
                                        if (!reciter.style.isNullOrBlank() && reciter.style != "None") {
                                            Text(
                                                text = reciter.style,
                                                fontSize = 12.sp,
                                                color = Color(0xFF6B7280)
                                            )
                                        }
                                    }
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showReciterDialog = false }) {
                    Text("إغلاق")
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

@Composable
private fun QuranPageView(
    pageNumber: Int,
    repository: PageRepository,
    accessible: Boolean,
    textSize: Int,
    activeVerse: String? = null,
    onPageLoaded: () -> Unit,
    onTap: () -> Unit,
    onFastScroll: () -> Unit,
    onScrollDirection: (Float) -> Unit,
    onRegisterView: (MushafTextView) -> Unit
) {
    var retry by remember(pageNumber) { mutableIntStateOf(0) }
    val state by produceState<PageState>(PageState.Loading, pageNumber, retry) {
        value = PageState.Loading
        value = try {
            PageState.Ready(repository.load(pageNumber))
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
        if (state is PageState.Ready) onLoaded()
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
                            .clickable { onTap() }
                            .padding(horizontal = 14.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        current.result.page.starts.forEach { start ->
                            val surahName = current.result.chapterNames[start.chapter] ?: ""
                            Surface(
                                color = Color(0xFFF7EED3),
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "سورة $surahName",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1C2520)
                                    )
                                    Text(
                                        text = "بداية السورة",
                                        fontSize = 13.sp,
                                        color = Color(0xFF8A6218)
                                    )
                                }
                            }
                        }

                        current.result.page.words.groupBy { it.verse }.forEach { (verse, words) ->
                            val isVerseActive = (activeVerse == verse)
                            Surface(
                                color = if (isVerseActive) Color(0xFFEDE0D0) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(if (isVerseActive) 8.dp else 0.dp)
                                ) {
                                    Text(
                                        words.joinToString(" ") { it.text },
                                        fontSize = textSize.sp,
                                        lineHeight = (textSize * 1.85).sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${current.result.chapterNames[verse.substringBefore(':').toInt()]} · آية ${verse.substringAfter(':')}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isVerseActive) Color(0xFF725B2A) else Color(0xFF0D8A74),
                                        fontWeight = if (isVerseActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            MushafTextView(ctx).also { v ->
                                onRegisterView(v)
                                v.activeVerse = activeVerse
                                v.onScrollListener = { dx, dy ->
                                    if (abs(dy) > 15f || abs(dx) > 15f) {
                                        onFastScroll()
                                    }
                                    onScrollDirection(dy)
                                }
                                v.onTapListener = onTap
                                v.bind(current.result)
                            }
                        },
                        update = { v ->
                            v.bind(current.result)
                            v.activeVerse = activeVerse
                            onRegisterView(v)
                            v.onScrollListener = { dx, dy ->
                                if (abs(dy) > 15f || abs(dx) > 15f) {
                                    onFastScroll()
                                }
                                onScrollDirection(dy)
                            }
                            v.onTapListener = onTap
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

