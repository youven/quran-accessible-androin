package com.youven.quranaccessible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youven.quranaccessible.R
import com.youven.quranaccessible.data.LoadedPage
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.PageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Ready(val result: LoadedPage) : PageState
}

@Composable
fun ReaderScreen(
    page: Int, onPageChange: (Int) -> Unit, repository: PageRepository,
    easyMode: Boolean, bookmarks: Set<Int>, onToggleBookmark: () -> Unit,
    onPageLoaded: (Int) -> Unit, onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var jumpDialog by rememberSaveable { mutableStateOf(false) }
    var bookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.home)) }
            TextButton(onClick = { jumpDialog = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.page_number, page, MadaniPage.COUNT), fontSize = if (easyMode) 22.sp else 18.sp)
            }
            TextButton(onClick = { controls = !controls }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(if (controls) R.string.hide_tools else R.string.show_tools))
            }
        }
        // Discard the previous image immediately; late downloads cannot appear under a different page number.
        key(page) { PageContent(page, repository, controls, onPageLoaded, Modifier.weight(1f)) }
        if (controls) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleBookmark, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(if (page in bookmarks) R.string.remove_bookmark else R.string.add_bookmark))
                }
                TextButton(onClick = { bookmarkDialog = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.bookmarks))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onPageChange(page - 1) }, enabled = page > 1,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Text(stringResource(R.string.previous), fontSize = if (easyMode) 22.sp else 18.sp)
            }
            Button(onClick = { onPageChange(page + 1) }, enabled = page < MadaniPage.COUNT,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Text(stringResource(R.string.next), fontSize = if (easyMode) 22.sp else 18.sp)
            }
        }
    }
    if (jumpDialog) JumpDialog(page, onDismiss = { jumpDialog = false }, onJump = { onPageChange(it); jumpDialog = false })
    if (bookmarkDialog) {
        AlertDialog(onDismissRequest = { bookmarkDialog = false }, title = { Text(stringResource(R.string.bookmarks)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (bookmarks.isEmpty()) Text(stringResource(R.string.no_bookmarks))
                    bookmarks.sorted().forEach { saved ->
                        TextButton(onClick = { onPageChange(saved); bookmarkDialog = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.bookmarked_page, saved), fontSize = 22.sp)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { bookmarkDialog = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun PageContent(page: Int, repository: PageRepository, controls: Boolean, onLoaded: (Int) -> Unit, modifier: Modifier) {
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<PageState>(PageState.Loading, page, retry) {
        value = PageState.Loading
        value = try { PageState.Ready(repository.load(page)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PageState.Failed }
    }
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    LaunchedEffect(state) { if (state is PageState.Ready) latestOnLoaded(page) }
    Column(modifier) {
        when (val current = state) {
            PageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading_page), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            PageState.Failed -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.load_error), fontSize = 20.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = { retry++ }, modifier = Modifier.padding(top = 16.dp).heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            is PageState.Ready -> {
                if (controls) Text(stringResource(if (current.result.savedOffline) R.string.saved_offline else R.string.not_saved),
                    modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                ZoomablePage(current.result, page, controls, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ZoomablePage(result: LoadedPage, page: Int, controls: Boolean, modifier: Modifier) {
    var zoom by rememberSaveable(page) { mutableFloatStateOf(1f) }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.White)) {
            val width = maxWidth * zoom
            val height = maxHeight * zoom
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.horizontalScroll(horizontal).verticalScroll(vertical)) {
                    Image(result.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.page_image_description, page),
                        modifier = Modifier.width(width).height(height), contentScale = ContentScale.Fit)
                }
            }
        }
        if (controls) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.zoom_out)) }
                TextButton(onClick = { zoom = 1f; scope.launch { horizontal.scrollTo(0); vertical.scrollTo(0) } },
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.fit_page)) }
                TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.zoom_in)) }
                Text(stringResource(R.string.zoom_percent, (zoom * 100).toInt()))
            }
            if (zoom > 1f) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { scope.launch { vertical.animateScrollTo((vertical.value - 240).coerceAtLeast(0)) } }) { Text(stringResource(R.string.pan_up)) }
                    TextButton(onClick = { scope.launch { vertical.animateScrollTo((vertical.value + 240).coerceAtMost(vertical.maxValue)) } }) { Text(stringResource(R.string.pan_down)) }
                    TextButton(onClick = { scope.launch { horizontal.animateScrollTo((horizontal.value + 160).coerceAtMost(horizontal.maxValue)) } }) { Text(stringResource(R.string.pan_right)) }
                    TextButton(onClick = { scope.launch { horizontal.animateScrollTo((horizontal.value - 160).coerceAtLeast(0)) } }) { Text(stringResource(R.string.pan_left)) }
                }
            }
        }
    }
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
