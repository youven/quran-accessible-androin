package com.youven.quranaccessible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.view.accessibility.AccessibilityManager
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.youven.quranaccessible.R
import com.youven.quranaccessible.data.*
import kotlinx.coroutines.CancellationException

private sealed interface PageState {
    data object Loading : PageState
    data class Failed(val part: PageLoadException.Part?) : PageState
    data class Ready(val result: LoadedPage) : PageState
}

@Composable
fun ReaderScreen(
    page: Int, onPageChange: (Int) -> Unit, repository: PageRepository,
    easyMode: Boolean, bookmarks: Set<Int>, onToggleBookmark: () -> Unit,
    onPageLoaded: (Int) -> Unit, onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var jump by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var accessible by rememberSaveable { mutableStateOf((context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled) }
    var textSize by rememberSaveable { mutableIntStateOf(30) }
    var view by remember { mutableStateOf<MushafTextView?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("الرئيسية") }
            TextButton(onClick = { jump = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("صفحة $page / ٦٠٤", fontSize = if (easyMode) 20.sp else 18.sp) }
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("أدوات القراءة") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (accessible) "رسم المصحف" else "النص الميسّر وقارئ الشاشة") }, onClick = { accessible = !accessible; menu = false })
                    DropdownMenuItem(text = { Text("تكبير") }, onClick = { if (accessible) textSize = (textSize + 4).coerceAtMost(64) else view?.zoomBy(1.25f) })
                    DropdownMenuItem(text = { Text("تصغير") }, onClick = { if (accessible) textSize = (textSize - 4).coerceAtLeast(22) else view?.zoomBy(0.8f) })
                    DropdownMenuItem(text = { Text("إعادة ضبط العرض") }, onClick = { view?.resetZoom(); textSize = 30; menu = false })
                    DropdownMenuItem(text = { Text(if (page in bookmarks) "إزالة العلامة" else "إضافة علامة") }, onClick = { onToggleBookmark(); menu = false })
                    DropdownMenuItem(text = { Text("العلامات المحفوظة") }, onClick = { saved = true; menu = false })
                }
            }
        }
        key(page) {
            var retry by remember { mutableIntStateOf(0) }
            val state by produceState<PageState>(PageState.Loading, retry) {
                value = PageState.Loading
                value = try { PageState.Ready(repository.load(page)) }
                catch (e: CancellationException) { throw e }
                catch (error: PageLoadException) { PageState.Failed(error.part) }
                catch (_: Exception) { PageState.Failed(null) }
            }
            val onLoaded by rememberUpdatedState(onPageLoaded)
            LaunchedEffect(state) { if (state is PageState.Ready) onLoaded(page) }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val current = state) {
                    PageState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("جارٍ تحميل نص الصفحة وخطها…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    is PageState.Failed -> Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                        val detail = when (current.part) {
                            PageLoadException.Part.TEXT -> "تعذّر تحميل نص الصفحة."
                            PageLoadException.Part.PAGE_FONT -> "تم تحميل النص، لكن تعذّر تحميل خط هذه الصفحة."
                            PageLoadException.Part.PAGE_GLYPHS -> "تم تحميل الخط، لكن بعض رموز الصفحة غير متاحة فيه."
                            PageLoadException.Part.COMMON_FONT -> "تعذّر تحميل خط البسملة أو عناوين السور."
                            PageLoadException.Part.CHAPTERS -> "تعذّر تحميل أسماء السور."
                            null -> stringResource(R.string.load_error)
                        }
                        Text(detail, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Text("يمكنك الضغط على إعادة المحاولة. إذا استمر الخطأ، أرسل رقم الصفحة وصورة الرسالة.", Modifier.padding(top = 8.dp))
                        Button(onClick = { retry++ }) { Text("إعادة المحاولة") }
                    }
                    is PageState.Ready -> if (accessible) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            current.result.page.words.groupBy { it.verse }.forEach { (verse, words) ->
                                Text(words.joinToString(" ") { it.text }, fontSize = textSize.sp, lineHeight = (textSize * 1.9).sp,
                                    modifier = Modifier.fillMaxWidth())
                                Text("${current.result.chapterNames[verse.substringBefore(':').toInt()]} · آية ${verse.substringAfter(':')}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        AndroidView(factory = { context -> MushafTextView(context).also { view = it; it.bind(current.result) } },
                            update = { it.bind(current.result); view = it }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onPageChange(page - 1) }, enabled = page > 1, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("السابقة") }
            Button(onClick = { onPageChange(page + 1) }, enabled = page < 604, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("التالية") }
        }
    }
    if (jump) JumpDialog(page, { jump = false }, { onPageChange(it); jump = false })
    if (saved) AlertDialog(onDismissRequest = { saved = false }, title = { Text("العلامات المحفوظة") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            if (bookmarks.isEmpty()) Text("لم تضف علامات بعد.")
            bookmarks.sorted().forEach { n -> TextButton(onClick = { onPageChange(n); saved = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("صفحة $n") } }
        }
    }, confirmButton = { TextButton(onClick = { saved = false }) { Text("إغلاق") } })
}

@Composable
private fun JumpDialog(page: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf(page.toString()) }
    val parsed = MadaniPage.parse(input)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.jump_title)) },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text(stringResource(R.string.page_input)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = parsed == null, supportingText = { Text(stringResource(R.string.page_range)) })
        }, confirmButton = { TextButton(onClick = { parsed?.let(onJump) }, enabled = parsed != null) { Text(stringResource(R.string.go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
