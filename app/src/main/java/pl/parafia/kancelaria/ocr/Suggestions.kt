package pl.parafia.kancelaria.ocr

import pl.parafia.kancelaria.util.PL
import pl.parafia.kancelaria.util.WORD_REGEX
import pl.parafia.kancelaria.util.foldDiacritics

data class Suggestion(val original: String, val replacement: String)

/**
 * Podpowiedzi poprawek dla słów, których OCR nie był pewien:
 * szukamy w słowniku (imiona, nazwiska, miejscowości, słownictwo kancelaryjne)
 * najbliższego słowa. To tylko PODPOWIEDŹ - użytkownik zatwierdza ją kliknięciem.
 */
object Suggestions {

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun maxDistance(len: Int) = if (len <= 4) 1 else 2

    fun suggest(uncertain: Collection<String>, dictionary: Collection<String>): List<Suggestion> {
        if (uncertain.isEmpty() || dictionary.isEmpty()) return emptyList()
        val known = dictionary.map { it.lowercase(PL) }.toHashSet()
        val entries = dictionary.distinct().map { Triple(it, it.lowercase(PL), it.lowercase(PL).foldDiacritics()) }
        val result = mutableListOf<Suggestion>()

        for (word in uncertain) {
            if (word.length < 3) continue
            val lower = word.lowercase(PL)
            if (lower in known) continue // słowo już jest w słowniku - nie ruszamy
            val folded = lower.foldDiacritics()
            val limit = maxDistance(word.length)
            var best: Triple<String, Int, Int>? = null // (słowo, dystans bez ogonków, dystans dokładny)
            for ((orig, low, fold) in entries) {
                if (kotlin.math.abs(low.length - lower.length) > limit) continue
                val d = levenshtein(folded, fold)
                if (d > limit) continue
                val exact = levenshtein(lower, low)
                val b = best
                if (b == null || d < b.second || (d == b.second && exact < b.third)) {
                    best = Triple(orig, d, exact)
                }
            }
            best?.let {
                val replacement = adaptCase(word, it.first)
                if (replacement != word) result += Suggestion(word, replacement)
            }
        }
        return result
    }

    private fun adaptCase(original: String, candidate: String): String = when {
        original.all { !it.isLetter() || it.isUpperCase() } && original.count { it.isLetter() } > 1 -> candidate.uppercase(PL)
        original.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercase(PL) }
        else -> candidate.lowercase(PL)
    }

    /** Słowa pisane wielką literą (imiona, nazwiska, miejscowości) - uczymy się ich z zatwierdzonych tekstów. */
    fun learnableWords(text: String): Set<String> =
        WORD_REGEX.findAll(text).map { it.value }
            .filter { it.length >= 3 && it.first().isUpperCase() && it.all { c -> c.isLetter() || c == '-' } }
            .toSet()
}
