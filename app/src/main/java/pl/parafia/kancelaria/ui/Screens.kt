@file:OptIn(ExperimentalMaterial3Api::class)

package pl.parafia.kancelaria.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.parafia.kancelaria.AppViewModel
import pl.parafia.kancelaria.analysis.Analyzer
import pl.parafia.kancelaria.analysis.DocText
import pl.parafia.kancelaria.data.Doc
import pl.parafia.kancelaria.data.EditorState
import pl.parafia.kancelaria.data.Screen
import pl.parafia.kancelaria.util.PL
import pl.parafia.kancelaria.util.WORD_REGEX
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun KancelariaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(), content = content)
}

private fun fmt(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", PL).format(Date(ms))

@Composable
fun App(
    vm: AppViewModel,
    onScan: () -> Unit,
    onExport: (String, String) -> Unit,
    onUnlock: () -> Unit,
) {
    if (vm.locked) {
        LockScreen(vm.authError, onUnlock)
        return
    }
    when (vm.screen) {
        Screen.LIST -> ListScreen(vm, onScan, onExport)
        Screen.EDITOR -> vm.editor?.let { EditorScreen(vm, it, onExport) }
        Screen.ANALYSIS -> AnalysisScreen(vm, onExport)
    }
    if (vm.busy) {
        Box(
            Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text("Rozpoznawanie tekstu…", color = Color.White, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
    vm.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}

// ---------------------------------------------------------------- blokada

@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Kancelaria OCR", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Aplikacja jest zablokowana.",
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
            }
            Button(onClick = onUnlock) { Text("Odblokuj") }
        }
    }
}

// ---------------------------------------------------------------- lista

@Composable
fun ListScreen(vm: AppViewModel, onScan: () -> Unit, onExport: (String, String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Doc?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dokumenty (${vm.docs.size})") },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Analiza") }, onClick = { menu = false; vm.goAnalysis() })
                            DropdownMenuItem(
                                text = { Text("Eksportuj wszystko (.txt, bez szyfrowania)") },
                                onClick = { menu = false; onExport("kancelaria_wszystko.txt", vm.exportAllText()) },
                            )
                            DropdownMenuItem(text = { Text("Zablokuj") }, onClick = { menu = false; vm.lock() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScan) { Text("Skanuj dokument") }
        },
    ) { pad ->
        if (vm.docs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Brak zapisanych dokumentów.\nNaciśnij „Skanuj dokument”, aby zrobić zdjęcie kartki.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.docs, key = { it.id }) { d ->
                    Card(Modifier.fillMaxWidth().clickable { vm.openDoc(d) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium)
                            Text(fmt(d.createdAt), style = MaterialTheme.typography.bodySmall)
                            Text(
                                d.text.replace('\n', ' ').take(100),
                                maxLines = 2,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            TextButton(onClick = { toDelete = d }) { Text("Usuń") }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunąć dokument?") },
            text = { Text("„${d.title}” zostanie trwale usunięty.") },
            confirmButton = { TextButton(onClick = { vm.deleteDoc(d.id); toDelete = null }) { Text("Usuń") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }
}

// ---------------------------------------------------------------- edycja

private class UncertainTransformation(private val words: Set<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (words.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text.text)
        WORD_REGEX.findAll(text.text).forEach { m ->
            if (m.value in words) {
                builder.addStyle(SpanStyle(background = Color(0xFFFFE082)), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun EditorScreen(vm: AppViewModel, e: EditorState, onExport: (String, String) -> Unit) {
    val isScan = e.images.isNotEmpty()
    var confirmDiscard by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    val transformation = remember(e.uncertain) { UncertainTransformation(e.uncertain) }

    fun back() { if (isScan) confirmDiscard = true else vm.cancelEditor() }
    BackHandler { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isScan) "Nowy zapis" else "Dokument") },
                navigationIcon = { TextButton(onClick = ::back) { Text("Wróć") } },
                actions = { TextButton(onClick = vm::saveEditor) { Text("Zapisz") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp).imePadding()) {
            OutlinedTextField(
                value = e.title,
                onValueChange = vm::onTitleChange,
                label = { Text("Tytuł (np. Księga chrztów 1920)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isScan) {
                if (e.images.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page = max(0, page - 1) }) { Text("‹") }
                        Text("Strona ${page + 1} z ${e.images.size}")
                        TextButton(onClick = { page = minOf(e.images.size - 1, page + 1) }) { Text("›") }
                    }
                }
                ZoomableScan(e.images[page.coerceIn(0, e.images.size - 1)], Modifier.fillMaxWidth().weight(0.9f))
                if (e.suggestions.isNotEmpty()) {
                    Text("Podpowiedzi poprawek (dotknij, aby zastosować):", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(e.suggestions) { s ->
                            AssistChip(onClick = { vm.applySuggestion(s) }, label = { Text("${s.original} → ${s.replacement}") })
                        }
                    }
                }
                if (e.uncertain.isNotEmpty()) {
                    Text(
                        "Żółte słowa: OCR nie był pewien – porównaj ze zdjęciem.",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            OutlinedTextField(
                value = e.text,
                onValueChange = vm::onTextChange,
                label = { Text("Rozpoznany tekst (można poprawiać)") },
                visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
            )
            if (!isScan) {
                TextButton(onClick = { onExport("${e.title.ifBlank { "dokument" }}.txt", e.text) }) {
                    Text("Eksportuj do .txt (plik bez szyfrowania!)")
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Odrzucić zapis?") },
            text = { Text("Rozpoznany tekst nie został zapisany. Zdjęcie zostanie usunięte.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.cancelEditor() }) { Text("Odrzuć") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Wróć do edycji") } },
        )
    }
}

@Composable
private fun ZoomableScan(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadBitmap(context, uri, 1800) }
    }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    Box(modifier.clipToBounds().background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bmp,
                contentDescription = "Zeskanowana strona",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                    ),
            )
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?.asImageBitmap()
}

// ---------------------------------------------------------------- analiza

@Composable
fun AnalysisScreen(vm: AppViewModel, onExport: (String, String) -> Unit) {
    BackHandler { vm.goList() }
    val selected = remember { mutableStateListOf<String>().apply { addAll(vm.docs.map { it.id }) } }
    var terms by rememberSaveable { mutableStateOf("") }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var ignoreDiacritics by rememberSaveable { mutableStateOf(true) }
    var sumFilter by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }

    fun chosen(): List<DocText> =
        vm.docs.filter { it.id in selected }.map { DocText(it.id, it.title, it.text) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analiza") },
                navigationIcon = { TextButton(onClick = vm::goList) { Text("Wróć") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Dokumenty do analizy: ${selected.size} z ${vm.docs.size}", style = MaterialTheme.typography.titleSmall)
            Row {
                TextButton(onClick = { selected.clear(); selected.addAll(vm.docs.map { it.id }) }) { Text("Wszystkie") }
                TextButton(onClick = { selected.clear() }) { Text("Żadne") }
            }
            vm.docs.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = d.id in selected,
                        onCheckedChange = { on -> if (on) selected.add(d.id) else selected.remove(d.id) },
                    )
                    Text(d.title)
                }
            }

            Text("Zliczanie wystąpień", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = terms,
                onValueChange = { terms = it },
                label = { Text("Szukane wyrażenia (oddziel średnikiem)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wholeWord, onCheckedChange = { wholeWord = it })
                Text("Tylko całe słowa")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ignoreDiacritics, onCheckedChange = { ignoreDiacritics = it })
                Text("Ignoruj polskie znaki i wielkość liter")
            }
            Button(onClick = {
                val docs = chosen()
                result = Analyzer.reportCounts(
                    docs.size,
                    Analyzer.countTerms(docs, terms.split(';'), wholeWord, ignoreDiacritics),
                )
            }) { Text("Zlicz") }

            Text("Sumowanie liczb", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = sumFilter,
                onValueChange = { sumFilter = it },
                label = { Text("Tylko linie zawierające (puste = wszystkie)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val docs = chosen()
                    result = Analyzer.reportSum(docs.size, sumFilter, Analyzer.sumNumbers(docs, sumFilter, ignoreDiacritics))
                }) { Text("Sumuj") }
                OutlinedButton(onClick = {
                    val docs = chosen()
                    result = Analyzer.reportTopWords(docs.size, Analyzer.topWords(docs))
                }) { Text("Najczęstsze słowa") }
            }

            if (result.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SelectionContainer {
                        Text(result, modifier = Modifier.padding(12.dp))
                    }
                }
                OutlinedButton(onClick = { onExport("wynik_analizy.txt", result) }) {
                    Text("Eksportuj wynik (.txt, bez szyfrowania)")
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}
