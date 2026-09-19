package com.youven.quranaccessible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.QuranMetadata
import com.youven.quranaccessible.data.SurahInfo

sealed interface SearchResultItem {
    data class PageMatch(val page: Int) : SearchResultItem
    data class SurahMatch(val surah: SurahInfo) : SearchResultItem
    data class RubMatch(
        val snippet: String,
        val surahNumber: Int,
        val surahName: String,
        val verse: Int,
        val page: Int,
        val rubNumber: Int
    ) : SearchResultItem
    data class VerseMatch(
        val surahNumber: Int,
        val surahName: String,
        val verseNumber: Int,
        val page: Int,
        val verseKey: String
    ) : SearchResultItem
}

private data class PendingVerseLaunch(
    val page: Int,
    val verseKey: String,
    val verseNumber: Int,
    val surahName: String
)

@Composable
fun SearchScreen(
    onClose: () -> Unit,
    onOpenPage: (page: Int, targetVerse: String?, autoPlay: Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var pendingVerse by remember { mutableStateOf<PendingVerseLaunch?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val trimmed = query.trim()
    val results = remember(trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else {
            val list = mutableListOf<SearchResultItem>()
            // 1. Page match
            val parsedPage = MadaniPage.parse(trimmed)
            if (parsedPage != null) {
                list.add(SearchResultItem.PageMatch(parsedPage))
            }

            // 2. Verse match by number (e.g. "2:255" or "2 255")
            val colonMatch = Regex("""^(\d{1,3})[:\s]+(\d{1,3})$""").matchEntire(normalizeDigits(trimmed))
            if (colonMatch != null) {
                val ch = colonMatch.groupValues[1].toIntOrNull()
                val v = colonMatch.groupValues[2].toIntOrNull()
                if (ch != null && v != null && ch in 1..114) {
                    val surah = QuranMetadata.SURAHS.find { it.number == ch }
                    if (surah != null && v in 1..surah.versesCount) {
                        val page = MadaniPage.pageForVerse(ch, v)
                        list.add(SearchResultItem.VerseMatch(ch, surah.nameArabic, v, page, "$ch:$v"))
                    }
                }
            }

            // 3. Verse match by surah name + verse number (e.g. "البقرة 255" or "سورة البقرة 255")
            val parts = trimmed.split(Regex("""[\s:]+"""))
            if (parts.size >= 2) {
                val verseDigits = normalizeDigits(parts.last())
                val v = verseDigits.toIntOrNull()
                if (v != null) {
                    val surahPart = parts.dropLast(1).joinToString(" ")
                        .replace("سورة", "")
                        .replace("سوره", "")
                        .trim()
                    val normSurah = normalizeArabic(surahPart)
                    if (normSurah.isNotEmpty()) {
                        val matched = QuranMetadata.SURAHS.filter {
                            normalizeArabic(it.nameArabic).contains(normSurah) ||
                            it.nameEnglish.lowercase().contains(surahPart.lowercase())
                        }
                        for (s in matched) {
                            if (v in 1..s.versesCount) {
                                val page = MadaniPage.pageForVerse(s.number, v)
                                list.add(SearchResultItem.VerseMatch(s.number, s.nameArabic, v, page, "${s.number}:$v"))
                            }
                        }
                    }
                }
            }

            // 4. Surah matches
            val normQuery = normalizeArabic(trimmed.lowercase())
            val matchedSurahs = QuranMetadata.SURAHS.filter { s ->
                normalizeArabic(s.nameArabic).contains(normQuery) ||
                s.nameEnglish.lowercase().contains(trimmed.lowercase())
            }
            matchedSurahs.forEach { list.add(SearchResultItem.SurahMatch(it)) }

            // 5. Rub matches (snippets)
            if (trimmed.length >= 2) {
                val matchedRubs = QuranMetadata.RUBS.filter { r ->
                    normalizeArabic(r.snippet).contains(normQuery) ||
                    normalizeArabic(r.surahName).contains(normQuery)
                }.take(20)
                matchedRubs.forEach { r ->
                    val surahNum = QuranMetadata.SURAHS.find { it.nameArabic == r.surahName }?.number ?: 1
                    list.add(SearchResultItem.RubMatch(r.snippet, surahNum, r.surahName, r.verseNumber, r.pageNumber, r.rubNumber))
                }
            }

            list
        }
    }

    // Dialog when user selects a verse not at the page start
    pendingVerse?.let { pv ->
        val pageSpec = MadaniPage.spec(pv.page)
        AlertDialog(
            onDismissRequest = { pendingVerse = null },
            title = {
                Text(
                    text = "بدء القراءة والاستماع",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1C2520)
                )
            },
            text = {
                Text(
                    text = "الآية المحددة (سورة ${pv.surahName}، آية ${QuranMetadata.toArabicDigits(pv.verseNumber)}) ليست في بداية الصفحة (صفحة ${QuranMetadata.toArabicDigits(pv.page)}).\n\nمن أين تفضل أن تبدأ القراءة والاستماع؟",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF374151)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pv
                        pendingVerse = null
                        onOpenPage(target.page, target.verseKey, true)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D8A74))
                ) {
                    Text("من الآية المحددة (آية ${QuranMetadata.toArabicDigits(pv.verseNumber)})")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val target = pv
                        pendingVerse = null
                        onOpenPage(target.page, pageSpec.firstVerse, true)
                        onClose()
                    }
                ) {
                    Text("من بداية الصفحة")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF8F5))
            .safeDrawingPadding()
    ) {
        // Search Header
        Surface(
            shadowElevation = 2.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "رجوع",
                        tint = Color(0xFF1C2520)
                    )
                }

                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "ابحث في القرآن (سورة، آية، صفحة)",
                            color = Color(0xFF8F9B94),
                            fontSize = 17.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF0D8A74)
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "مسح",
                            tint = Color(0xFF757575)
                        )
                    }
                }
            }
        }

        // Results List
        if (trimmed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFB0BEC5),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "ابحث بالسورة (الفاتحة)، أو بالآية (البقرة 255)، أو برقم الصفحة",
                        color = Color(0xFF8F9B94),
                        fontSize = 15.sp
                    )
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لم يتم العثور على نتائج لـ \"$trimmed\"",
                    color = Color(0xFF8F9B94),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(results) { item ->
                    when (item) {
                        is SearchResultItem.VerseMatch -> {
                            val isFirstOnPage = MadaniPage.spec(item.page).firstVerse == item.verseKey
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "سورة ${item.surahName} · آية ${QuranMetadata.toArabicDigits(item.verseNumber)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1C2520)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "صفحة ${QuranMetadata.toArabicDigits(item.page)}${if (isFirstOnPage) " (بداية الصفحة)" else ""}",
                                        color = Color(0xFF6B7770),
                                        fontSize = 13.sp
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEDE0D0),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = QuranMetadata.toArabicDigits(item.verseNumber),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF725B2A)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (isFirstOnPage) {
                                        onOpenPage(item.page, item.verseKey, true)
                                        onClose()
                                    } else {
                                        pendingVerse = PendingVerseLaunch(
                                            page = item.page,
                                            verseKey = item.verseKey,
                                            verseNumber = item.verseNumber,
                                            surahName = item.surahName
                                        )
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0xFFE8E5DD), thickness = 0.5.dp)
                        }
                        is SearchResultItem.PageMatch -> {
                            ListItem(
                                headlineContent = { Text("الانتقال إلى صفحة ${QuranMetadata.toArabicDigits(item.page)}", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(QuranMetadata.surahForPage(item.page).let { "سورة ${it.nameArabic} · جزء ${QuranMetadata.toArabicDigits(QuranMetadata.juzForPage(item.page))}" }) },
                                leadingContent = {
                                    Surface(shape = CircleShape, color = Color(0xFFDDECE2), modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF0D8A74))
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    onOpenPage(item.page, null, false)
                                    onClose()
                                }
                            )
                            HorizontalDivider(color = Color(0xFFE8E5DD), thickness = 0.5.dp)
                        }
                        is SearchResultItem.SurahMatch -> {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "سورةُ ${item.surah.nameArabic} (${item.surah.nameEnglish})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Color(0xFF1C2520)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${item.surah.type} · ${QuranMetadata.toArabicDigits(item.surah.versesCount)} آيات · صفحة ${QuranMetadata.toArabicDigits(item.surah.startPage)}",
                                        color = Color(0xFF6B7770),
                                        fontSize = 13.sp
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFDDECE2),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = QuranMetadata.toArabicDigits(item.surah.number),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0D8A74)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    onOpenPage(item.surah.startPage, null, false)
                                    onClose()
                                }
                            )
                            HorizontalDivider(color = Color(0xFFE8E5DD), thickness = 0.5.dp)
                        }
                        is SearchResultItem.RubMatch -> {
                            val verseKey = "${item.surahNumber}:${item.verse}"
                            val isFirstOnPage = MadaniPage.spec(item.page).firstVerse == verseKey
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = item.snippet,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1C2520)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${item.surahName}، آية ${QuranMetadata.toArabicDigits(item.verse)} · صفحة ${QuranMetadata.toArabicDigits(item.page)}",
                                        color = Color(0xFF6B7770),
                                        fontSize = 13.sp
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF3EFE6),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = QuranMetadata.toArabicDigits(item.rubNumber),
                                                fontSize = 12.sp,
                                                color = Color(0xFF725B2A)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (isFirstOnPage) {
                                        onOpenPage(item.page, verseKey, true)
                                        onClose()
                                    } else {
                                        pendingVerse = PendingVerseLaunch(
                                            page = item.page,
                                            verseKey = verseKey,
                                            verseNumber = item.verse,
                                            surahName = item.surahName
                                        )
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0xFFE8E5DD), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeDigits(text: String): String {
    return text.map { ch ->
        when (ch) {
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> ch
        }
    }.joinToString("")
}

private fun normalizeArabic(text: String): String {
    return text
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ة", "ه")
        .replace("ى", "ي")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // remove tashkeel
}
