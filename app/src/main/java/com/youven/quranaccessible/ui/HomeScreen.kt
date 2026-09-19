package com.youven.quranaccessible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youven.quranaccessible.data.QuranMetadata
import com.youven.quranaccessible.data.RubInfo
import com.youven.quranaccessible.data.SurahInfo

@Composable
fun HomeScreen(
    lastReadPage: Int,
    bookmarks: Set<Int>,
    recents: List<Int>,
    onOpenPage: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onQuickJump: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }

    // Preserve scroll states across tabs
    val surahsListState = rememberLazyListState()
    val juzsListState = rememberLazyListState()
    val bookmarksListState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F5))
            .safeDrawingPadding()
    ) {
        // --- Top Header ---
        Surface(
            color = Color(0xFFFAF8F5),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Far Right: Title "قرآن" (in RTL, this is start)
                Text(
                    text = "قرآن",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C2520)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Middle Action Icons (change based on active tab)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (selectedTab) {
                        0 -> { // سورة
                            IconButton(onClick = onOpenSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "بحث",
                                    tint = Color(0xFF1C2520)
                                )
                            }
                            IconButton(onClick = { onOpenPage(lastReadPage) }) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "متابعة القراءة",
                                    tint = Color(0xFF0D8A74)
                                )
                            }
                        }
                        1 -> { // الجزء
                            IconButton(onClick = { onOpenPage(lastReadPage) }) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "متابعة القراءة",
                                    tint = Color(0xFF0D8A74)
                                )
                            }
                            IconButton(onClick = { sortDescending = !sortDescending }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "ترتيب",
                                    tint = if (sortDescending) Color(0xFF0D8A74) else Color(0xFF1C2520)
                                )
                            }
                        }
                        2 -> { // المرجعيات
                            IconButton(onClick = { onOpenPage(lastReadPage) }) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "متابعة القراءة",
                                    tint = Color(0xFF0D8A74)
                                )
                            }
                            IconButton(onClick = { sortDescending = !sortDescending }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "ترتيب",
                                    tint = if (sortDescending) Color(0xFF0D8A74) else Color(0xFF1C2520)
                                )
                            }
                        }
                    }

                    // Far Left: Overflow Menu (⋮)
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
                            onQuickJump = onQuickJump,
                            onSettings = onOpenSettings,
                            onHelp = onOpenHelp,
                            onAbout = onOpenAbout,
                            onOtherApps = onOpenAbout
                        )
                    }
                }
            }
        }

        // --- Tabs (3 horizontal tabs) ---
        val tabs = listOf("سورة", "الجزء", "المرجعيات")
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFFFAF8F5),
            contentColor = Color(0xFF0D8A74),
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF0D8A74),
                        height = 3.dp
                    )
                }
            },
            divider = {
                HorizontalDivider(color = Color(0xFFE8E5DD), thickness = 1.dp)
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) Color(0xFF0D8A74) else Color(0xFF757575)
                        )
                    }
                )
            }
        }

        // --- Tab Contents ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> SurahsTabContent(
                    listState = surahsListState,
                    onOpenPage = onOpenPage
                )
                1 -> JuzsTabContent(
                    listState = juzsListState,
                    lastReadPage = lastReadPage,
                    sortDescending = sortDescending,
                    onOpenPage = onOpenPage
                )
                2 -> BookmarksTabContent(
                    listState = bookmarksListState,
                    bookmarks = bookmarks,
                    recents = recents,
                    onOpenPage = onOpenPage,
                    onToggleBookmark = onToggleBookmark
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 1: Surahs Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun SurahsTabContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenPage: (Int) -> Unit
) {
    val surahs = QuranMetadata.SURAHS

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(surahs) { surah ->
            // If this Surah starts on a new Juz, display Juz separator
            val juzNumber = surah.startJuz
            val isFirstSurahInJuz = surahs.firstOrNull { it.startJuz == juzNumber } == surah

            if (isFirstSurahInJuz) {
                JuzSectionHeader(
                    juzNumber = juzNumber,
                    pageNumber = surah.startPage
                )
            }

            SurahRowItem(
                surah = surah,
                onClick = { onOpenPage(surah.startPage) }
            )
            HorizontalDivider(color = Color(0xFFECE8DF), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun JuzSectionHeader(
    juzNumber: Int,
    pageNumber: Int
) {
    Surface(
        color = Color(0xFFF3EFE6),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الجزء ${QuranMetadata.toArabicDigits(juzNumber)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF4A554E)
            )
            Text(
                text = "صفحة ${QuranMetadata.toArabicDigits(pageNumber)}",
                fontSize = 14.sp,
                color = Color(0xFF725B2A)
            )
        }
    }
}

@Composable
private fun SurahRowItem(
    surah: SurahInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text Content (Right in RTL)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "سورةُ ${surah.nameArabic}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C2520)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(${surah.nameEnglish})",
                    fontSize = 14.sp,
                    color = Color(0xFF8F9B94)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${surah.type} – ${QuranMetadata.toArabicDigits(surah.versesCount)} آيات",
                fontSize = 13.sp,
                color = Color(0xFF6B7770)
            )
        }

        // Left in RTL: Surah Number Circle
        Surface(
            shape = CircleShape,
            color = Color(0xFFF0EDE4),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = QuranMetadata.toArabicDigits(surah.number),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D8A74)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 2: Juzs / Rub' el Hizb Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun JuzsTabContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    lastReadPage: Int,
    sortDescending: Boolean,
    onOpenPage: (Int) -> Unit
) {
    val rubs = remember(sortDescending) {
        if (sortDescending) QuranMetadata.RUBS.reversed() else QuranMetadata.RUBS
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        var currentJuz = -1
        items(rubs) { rub ->
            if (rub.juzNumber != currentJuz) {
                currentJuz = rub.juzNumber
                Surface(
                    color = Color(0xFFF3EFE6),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "الجزء ${QuranMetadata.toArabicDigits(rub.juzNumber)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF4A554E),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            RubRowItem(
                rub = rub,
                isCompleted = rub.pageNumber <= lastReadPage,
                onClick = { onOpenPage(rub.pageNumber) }
            )
            HorizontalDivider(color = Color(0xFFECE8DF), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun RubRowItem(
    rub: RubInfo,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress Donut on right
        ProgressDonut(
            progress = if (isCompleted) 1f else 0f,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(14.dp))

        // Text Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rub.snippet,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C2520)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${rub.surahName}، آية ${QuranMetadata.toArabicDigits(rub.verseNumber)} · صفحة ${QuranMetadata.toArabicDigits(rub.pageNumber)}",
                fontSize = 13.sp,
                color = Color(0xFF6B7770)
            )
        }

        // Left: Rub' Number
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF0EDE4),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = QuranMetadata.toArabicDigits(rub.rubNumber),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF725B2A)
                )
            }
        }
    }
}

@Composable
private fun ProgressDonut(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        // Background circle
        drawCircle(
            color = Color(0xFFE2DDD3),
            style = Stroke(width = strokeWidth)
        )
        // Foreground progress arc
        if (progress > 0f) {
            drawArc(
                color = Color(0xFF0D8A74),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tab 3: Bookmarks Content
// -------------------------------------------------------------------------------------------------
@Composable
private fun BookmarksTabContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    bookmarks: Set<Int>,
    recents: List<Int>,
    onOpenPage: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Section 1: آخر المتصفحات ---
        item {
            SectionHeaderTitle("آخر المتصفحات")
        }

        if (recents.isEmpty()) {
            item {
                Text(
                    text = "لا توجد صفحات سابقة بعد.",
                    color = Color(0xFF8F9B94),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            items(recents) { page ->
                val surah = QuranMetadata.surahForPage(page)
                val juz = QuranMetadata.juzForPage(page)

                ListItem(
                    headlineContent = {
                        Text(
                            text = "سورة ${surah.nameArabic}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2520)
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "صفحة ${QuranMetadata.toArabicDigits(page)}، جزء ${QuranMetadata.toArabicDigits(juz)}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7770)
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF0D8A74),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.clickable { onOpenPage(page) }
                )
                HorizontalDivider(color = Color(0xFFECE8DF), thickness = 0.5.dp)
            }
        }

        // --- Section 2: مرجعيات الصفحات ---
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeaderTitle("مرجعيات الصفحات")
        }

        if (bookmarks.isEmpty()) {
            item {
                Text(
                    text = "لم تقم بحفظ أي علامات مرجعية حتى الآن. اضغط على أيقونة الإشارة المرجعية أثناء القراءة لحفظ الصفحة.",
                    color = Color(0xFF8F9B94),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            items(bookmarks.sorted()) { page ->
                val surah = QuranMetadata.surahForPage(page)
                val juz = QuranMetadata.juzForPage(page)

                ListItem(
                    headlineContent = {
                        Text(
                            text = "سورة ${surah.nameArabic}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1C2520)
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "صفحة ${QuranMetadata.toArabicDigits(page)}، جزء ${QuranMetadata.toArabicDigits(juz)}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7770)
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFF1C2520),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onToggleBookmark(page) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إزالة العلامة",
                                tint = Color(0xFF8F9B94)
                            )
                        }
                    },
                    modifier = Modifier.clickable { onOpenPage(page) }
                )
                HorizontalDivider(color = Color(0xFFECE8DF), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun SectionHeaderTitle(title: String) {
    Surface(
        color = Color(0xFFF3EFE6),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF6B7770),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
