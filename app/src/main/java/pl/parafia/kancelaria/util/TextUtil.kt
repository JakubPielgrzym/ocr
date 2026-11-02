package pl.parafia.kancelaria.util

import java.text.Normalizer
import java.util.Locale

val PL: Locale = Locale("pl", "PL")

private val combiningMarks = Regex("\\p{M}+")

/** Usuwa znaki diakrytyczne (ą→a, ś→s, ł→l itd.), żeby porównywać "Łuków" i "Lukow". */
fun String.foldDiacritics(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace('ł', 'l')
        .replace('Ł', 'L')

/** Małe litery po polsku, opcjonalnie bez ogonków. */
fun String.normalizeForSearch(ignoreDiacritics: Boolean): String {
    val lower = this.lowercase(PL)
    return if (ignoreDiacritics) lower.foldDiacritics() else lower
}

/** Słowo: litery/cyfry, ewentualnie połączone myślnikiem lub apostrofem ("Nowak-Kowalska"). */
val WORD_REGEX = Regex("[\\p{L}\\p{N}]+(?:[-'’][\\p{L}\\p{N}]+)*")

fun cleanWord(raw: String): String = raw.trim { !it.isLetterOrDigit() }
