package pl.parafia.kancelaria.analysis

import pl.parafia.kancelaria.util.PL
import pl.parafia.kancelaria.util.normalizeForSearch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Dokument w postaci potrzebnej do analizy. */
data class DocText(val id: String, val title: String, val text: String)

data class TermCount(val term: String, val total: Int, val perDoc: List<Pair<String, Int>>)

data class NumberHit(val docTitle: String, val value: BigDecimal, val line: String)

data class SumResult(
    val sum: BigDecimal,
    val hits: List<NumberHit>,
) {
    val count: Int get() = hits.size
    val min: BigDecimal? get() = hits.minOfOrNull { it.value }
    val max: BigDecimal? get() = hits.maxOfOrNull { it.value }
    val average: BigDecimal?
        get() = if (hits.isEmpty()) null else sum.divide(BigDecimal(hits.size), 2, RoundingMode.HALF_UP)
}

object Analyzer {

    // ---------- Zliczanie wystąpień ----------

    fun countIn(text: String, term: String, wholeWord: Boolean, ignoreDiacritics: Boolean): Int {
        val needle = term.trim().normalizeForSearch(ignoreDiacritics)
        if (needle.isEmpty()) return 0
        val hay = text.normalizeForSearch(ignoreDiacritics)
        var idx = 0
        var count = 0
        while (true) {
            val i = hay.indexOf(needle, idx)
            if (i < 0) break
            val end = i + needle.length
            val ok = !wholeWord ||
                ((i == 0 || !hay[i - 1].isLetterOrDigit()) &&
                    (end >= hay.length || !hay[end].isLetterOrDigit()))
            if (ok) count++
            idx = end
        }
        return count
    }

    fun countTerms(
        docs: List<DocText>,
        terms: List<String>,
        wholeWord: Boolean,
        ignoreDiacritics: Boolean,
    ): List<TermCount> = terms.map { it.trim() }.filter { it.isNotEmpty() }.map { term ->
        val per = docs.map { it.title to countIn(it.text, term, wholeWord, ignoreDiacritics) }
        TermCount(term, per.sumOf { it.second }, per.filter { it.second > 0 })
    }

    // ---------- Sumowanie liczb ----------

    private val datePattern = Regex("""\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b""")

    // 1 200,50 | 1.200,50 | 1200 | 12,5 | 12.50
    private val numberPattern =
        Regex("""(?<![\d.,])(\d{1,3}(?:[ .]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)""")

    fun extractNumbers(line: String): List<BigDecimal> {
        val cleaned = line.replace('\u00A0', ' ').replace(datePattern, " ")
        return numberPattern.findAll(cleaned).mapNotNull { m ->
            val intPart = m.groupValues[1].replace(" ", "").replace(".", "")
            val frac = m.groupValues[2]
            runCatching { BigDecimal(if (frac.isEmpty()) intPart else "$intPart.$frac") }.getOrNull()
        }.toList()
    }

    /** Sumuje liczby z linii zawierających [lineFilter] (pusty filtr = wszystkie linie). */
    fun sumNumbers(docs: List<DocText>, lineFilter: String, ignoreDiacritics: Boolean): SumResult {
        val filter = lineFilter.trim().normalizeForSearch(ignoreDiacritics)
        val hits = mutableListOf<NumberHit>()
        for (doc in docs) {
            for (line in doc.text.lines()) {
                if (filter.isNotEmpty() && !line.normalizeForSearch(ignoreDiacritics).contains(filter)) continue
                for (n in extractNumbers(line)) hits += NumberHit(doc.title, n, line.trim())
            }
        }
        return SumResult(hits.fold(BigDecimal.ZERO) { a, h -> a + h.value }, hits)
    }

    // ---------- Najczęstsze słowa ----------

    fun topWords(docs: List<DocText>, limit: Int = 20, minLen: Int = 3): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        val rx = Regex("\\p{L}{$minLen,}")
        for (d in docs) for (m in rx.findAll(d.text)) {
            val w = m.value.lowercase(PL)
            counts[w] = (counts[w] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
    }

    // ---------- Raporty tekstowe (do ekranu i eksportu) ----------

    private fun money(v: BigDecimal): String = String.format(PL, "%,.2f", v)

    fun reportCounts(docCount: Int, results: List<TermCount>): String = buildString {
        appendLine("Zliczanie wystąpień (dokumentów: $docCount)")
        if (results.isEmpty()) appendLine("Nie podano szukanych wyrażeń.")
        for (r in results) {
            appendLine()
            appendLine("„${r.term}”: ${r.total}")
            for ((title, n) in r.perDoc) appendLine("   • $title: $n")
        }
    }.trimEnd()

    fun reportSum(docCount: Int, filter: String, r: SumResult): String = buildString {
        appendLine("Suma liczb (dokumentów: $docCount)")
        if (filter.isNotBlank()) appendLine("Tylko linie zawierające: „${filter.trim()}”")
        appendLine()
        if (r.count == 0) {
            appendLine("Nie znaleziono żadnych liczb.")
        } else {
            appendLine("Suma: ${money(r.sum)}")
            appendLine("Liczba wartości: ${r.count}")
            appendLine("Średnia: ${money(r.average!!)}")
            appendLine("Min: ${money(r.min!!)}, max: ${money(r.max!!)}")
            appendLine()
            appendLine("Uwzględnione wartości (sprawdź, czy OCR nie pomylił cyfr):")
            for (h in r.hits) appendLine("   • ${money(h.value)}  ←  ${h.line.take(80)}  [${h.docTitle}]")
        }
    }.trimEnd()

    fun reportTopWords(docCount: Int, words: List<Pair<String, Int>>): String = buildString {
        appendLine("Najczęstsze słowa (dokumentów: $docCount)")
        appendLine()
        for ((w, n) in words) appendLine("   $n × $w")
    }.trimEnd()
}
