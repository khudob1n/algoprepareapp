package com.algoprep.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {
    @Test fun ignoresCasePunctuationAndSpacing() {
        assertEquals("two sum", TitleNormalizer.canonicalKey("  Two   Sum! "))
        assertEquals("two sum", TitleNormalizer.canonicalKey("two-sum"))
    }

    @Test fun keepsDigitsAndNonLatinLetters() {
        assertEquals("3sum", TitleNormalizer.canonicalKey("3Sum"))
        assertEquals("сумма двух", TitleNormalizer.canonicalKey("Сумма, двух"))
    }
}
