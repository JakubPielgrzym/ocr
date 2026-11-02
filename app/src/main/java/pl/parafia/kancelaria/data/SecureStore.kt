package pl.parafia.kancelaria.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File

/**
 * Szyfrowany magazyn (AES-256-GCM, klucz w Android Keystore).
 * Pliki leżą w prywatnym katalogu aplikacji - inne aplikacje ich nie widzą,
 * a bez odblokowania telefonu nie da się ich odczytać.
 */
class SecureStore(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }
    private val docsDir: File get() = File(context.filesDir, "docs").apply { mkdirs() }
    private val dictFile: File get() = File(context.filesDir, "slownik_uzytkownika.enc")

    private fun enc(file: File) = EncryptedFile.Builder(
        context, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    private fun write(file: File, text: String) {
        if (file.exists()) file.delete() // EncryptedFile nie nadpisuje istniejącego pliku
        enc(file).openFileOutput().use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun read(file: File): String =
        enc(file).openFileInput().use { it.readBytes().toString(Charsets.UTF_8) }

    // ---------- dokumenty ----------

    fun save(doc: Doc) {
        val json = JSONObject()
            .put("id", doc.id)
            .put("title", doc.title)
            .put("createdAt", doc.createdAt)
            .put("text", doc.text)
        write(File(docsDir, "${doc.id}.enc"), json.toString())
    }

    fun list(): List<Doc> =
        (docsDir.listFiles { f -> f.extension == "enc" } ?: emptyArray())
            .mapNotNull { f ->
                runCatching {
                    val j = JSONObject(read(f))
                    Doc(j.getString("id"), j.getString("title"), j.getLong("createdAt"), j.getString("text"))
                }.getOrNull()
            }
            .sortedByDescending { it.createdAt }

    fun delete(id: String) {
        File(docsDir, "$id.enc").delete()
    }

    // ---------- słownik uczony z zatwierdzonych tekstów ----------

    fun loadUserWords(): Set<String> =
        if (dictFile.exists()) runCatching { read(dictFile).lines().filter { it.isNotBlank() }.toSet() }
            .getOrDefault(emptySet())
        else emptySet()

    fun addUserWords(words: Set<String>) {
        if (words.isEmpty()) return
        val all = loadUserWords() + words
        write(dictFile, all.sorted().joinToString("\n"))
    }
}
