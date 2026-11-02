package pl.parafia.kancelaria

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.parafia.kancelaria.analysis.Analyzer
import pl.parafia.kancelaria.analysis.DocText
import pl.parafia.kancelaria.ocr.Suggestion
import pl.parafia.kancelaria.ocr.Suggestions
import java.math.BigDecimal

class AnalyzerTest {

    private val docs = listOf(
        DocText("1", "Księga 1920", "Chrzest: Jan Kowalski, 12.03.1920\nChrzest: Maria Nowak\nPogrzeb: Józef Łukowski"),
        DocText("2", "Księga 1921", "chrzest Anna Wiśniewska\nofiara 1 200,50 zł\nofiara 300 zł"),
    )

    @Test fun countsIgnoringCaseAndDiacritics() {
        assertEquals(3, Analyzer.countIn(docs[0].text + "\n" + docs[1].text, "chrzest", false, false))
        assertEquals(1, Analyzer.countIn("Łukowski", "lukowski", false, true))
        assertEquals(0, Analyzer.countIn("Łukowski", "lukowski", false, false))
    }

    @Test fun wholeWordMatching() {
        assertEquals(1, Analyzer.countIn("Jan i Janina", "jan", true, false))
        assertEquals(2, Analyzer.countIn("Jan i Janina", "jan", false, false))
    }

    @Test fun countTermsPerDocument() {
        val r = Analyzer.countTerms(docs, listOf("chrzest", " pogrzeb "), true, true)
        assertEquals(3, r[0].total)
        assertEquals(listOf("Księga 1920" to 2, "Księga 1921" to 1), r[0].perDoc)
        assertEquals(1, r[1].total)
    }

    @Test fun extractsPolishNumbers() {
        assertEquals(listOf(BigDecimal("1200.50")), Analyzer.extractNumbers("ofiara 1 200,50 zł"))
        assertEquals(listOf(BigDecimal("3500")), Analyzer.extractNumbers("razem 3.500 zł"))
        assertEquals(listOf(BigDecimal("12.50")), Analyzer.extractNumbers("12.50"))
        assertEquals(listOf(BigDecimal("300")), Analyzer.extractNumbers("300 zł."))
    }

    @Test fun ignoresDatesWhenSumming() {
        assertEquals(listOf(BigDecimal("50")), Analyzer.extractNumbers("12.03.1920 wpłata 50"))
    }

    @Test fun sumsOnlyFilteredLines() {
        val r = Analyzer.sumNumbers(docs, "ofiara", true)
        assertEquals(0, BigDecimal("1500.50").compareTo(r.sum))
        assertEquals(2, r.count)
        assertEquals(0, BigDecimal("300").compareTo(r.min))
        assertEquals(0, BigDecimal("1200.50").compareTo(r.max))
    }

    @Test fun sumWithoutFilterSkipsDates() {
        val r = Analyzer.sumNumbers(listOf(docs[0]), "", true)
        assertEquals(0, r.count) // w pierwszym dokumencie jest tylko data
    }

    @Test fun topWordsAreOrdered() {
        val top = Analyzer.topWords(docs, 2)
        assertEquals("chrzest" to 3, top[0])
    }

    @Test fun suggestsDictionaryWordForUncertainOcr() {
        val dict = listOf("Kowalski", "Nowak", "Wiśniewska")
        val s = Suggestions.suggest(listOf("Kowaiski", "Nowak", "Wisniewska"), dict)
        assertEquals(listOf(Suggestion("Kowaiski", "Kowalski"), Suggestion("Wisniewska", "Wiśniewska")), s)
    }

    @Test fun doesNotSuggestForFarWordsOrShortOnes() {
        assertEquals(emptyList<Suggestion>(), Suggestions.suggest(listOf("Zxqwerty", "Ab"), listOf("Kowalski", "Jan")))
    }

    @Test fun learnsCapitalizedWords() {
        val w = Suggestions.learnableWords("Chrzest Jana Kowalskiego w Łukowie, ul. Długa 5, bez nazwy")
        assertEquals(setOf("Chrzest", "Jana", "Kowalskiego", "Łukowie", "Długa"), w)
    }
}
