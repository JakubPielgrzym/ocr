package pl.parafia.kancelaria

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.parafia.kancelaria.data.Doc
import pl.parafia.kancelaria.data.EditorState
import pl.parafia.kancelaria.data.Screen
import pl.parafia.kancelaria.data.SecureStore
import pl.parafia.kancelaria.ocr.OcrEngine
import pl.parafia.kancelaria.ocr.Suggestion
import pl.parafia.kancelaria.ocr.Suggestions
import pl.parafia.kancelaria.util.PL
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val ocr = OcrEngine(app)

    var locked by mutableStateOf(true); private set
    var authError by mutableStateOf<String?>(null)
    var screen by mutableStateOf(Screen.LIST); private set
    var docs by mutableStateOf<List<Doc>>(emptyList()); private set
    var editor by mutableStateOf<EditorState?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    init {
        // Resztki po skanerze (zdjęcia stron) nie powinny zalegać w pamięci aplikacji.
        File(app.filesDir, "mlkit_docscan_ui_client").deleteRecursively()
    }

    // ---------- blokada ----------

    fun unlock() {
        locked = false
        authError = null
        refreshDocs()
    }

    fun lock() {
        locked = true
        docs = emptyList() // odszyfrowane treści nie zostają w pamięci stanu UI
    }

    // ---------- dokumenty ----------

    fun refreshDocs() {
        viewModelScope.launch { docs = withContext(Dispatchers.IO) { store.list() } }
    }

    fun deleteDoc(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refreshDocs()
        }
    }

    fun openDoc(doc: Doc) {
        editor = EditorState(doc.id, doc.title, doc.text, emptySet(), emptyList(), emptyList(), doc.createdAt)
        screen = Screen.EDITOR
    }

    fun goAnalysis() { screen = Screen.ANALYSIS }
    fun goList() { screen = Screen.LIST }

    fun exportAllText(): String = docs.joinToString("\n\n") { d ->
        "=== ${d.title} (${formatDate(d.createdAt)}) ===\n${d.text}"
    }

    // ---------- skanowanie i OCR ----------

    fun onScanResult(uris: List<Uri>) {
        viewModelScope.launch {
            busy = true
            try {
                val res = ocr.recognize(uris)
                val dictionary = withContext(Dispatchers.IO) { loadDictionary() }
                editor = EditorState(
                    id = null,
                    title = "",
                    text = res.text,
                    uncertain = res.uncertainWords,
                    suggestions = Suggestions.suggest(res.uncertainWords, dictionary),
                    images = uris,
                    createdAt = System.currentTimeMillis(),
                )
                screen = Screen.EDITOR
            } catch (e: Exception) {
                deleteImages(uris)
                message = "Nie udało się rozpoznać tekstu: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun loadDictionary(): List<String> {
        val builtIn = getApplication<Application>().assets.open("slownik.txt")
            .bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        return (builtIn + store.loadUserWords()).distinct()
    }

    // ---------- edycja ----------

    fun onTitleChange(t: String) { editor = editor?.copy(title = t) }
    fun onTextChange(t: String) { editor = editor?.copy(text = t) }

    fun applySuggestion(s: Suggestion) {
        val e = editor ?: return
        val rx = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(s.original) + "(?![\\p{L}\\p{N}])")
        editor = e.copy(
            text = rx.replace(e.text, Regex.escapeReplacement(s.replacement)),
            suggestions = e.suggestions - s,
            uncertain = e.uncertain - s.original,
        )
    }

    fun saveEditor() {
        val e = editor ?: return
        viewModelScope.launch {
            val title = e.title.ifBlank { "Zapis z ${formatDate(e.createdAt)}" }
            val doc = Doc(e.id ?: UUID.randomUUID().toString(), title, e.createdAt, e.text)
            try {
                withContext(Dispatchers.IO) {
                    store.save(doc)
                    store.addUserWords(Suggestions.learnableWords(e.text))
                }
            } catch (ex: Exception) {
                message = "Nie udało się zapisać: ${ex.message}"
                return@launch
            }
            deleteImages(e.images)
            editor = null
            screen = Screen.LIST
            refreshDocs()
        }
    }

    fun cancelEditor() {
        editor?.let { deleteImages(it.images) }
        editor = null
        screen = Screen.LIST
    }

    private fun deleteImages(uris: List<Uri>) {
        uris.forEach { u -> if (u.scheme == "file") u.path?.let { File(it).delete() } }
    }

    private fun formatDate(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", PL).format(Date(ms))

    override fun onCleared() {
        ocr.close()
    }
}
