package pl.parafia.kancelaria.data

import android.net.Uri
import pl.parafia.kancelaria.ocr.Suggestion

data class Doc(val id: String, val title: String, val createdAt: Long, val text: String)

enum class Screen { LIST, EDITOR, ANALYSIS }

/** Stan ekranu edycji: świeżo zeskanowany tekst albo otwarty zapisany dokument. */
data class EditorState(
    val id: String?,
    val title: String,
    val text: String,
    val uncertain: Set<String>,
    val suggestions: List<Suggestion>,
    val images: List<Uri>,
    val createdAt: Long,
)
