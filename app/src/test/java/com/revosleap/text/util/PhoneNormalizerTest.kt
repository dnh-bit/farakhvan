package com.revosleap.text.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {
    private val canonical = "+989121234567"

    @Test
    fun iranianFormatsShareOneCanonicalNumber() {
        listOf(
            "+989121234567", "00989121234567", "989121234567", "09121234567",
            "9121234567", "\u06f0\u06f9\u06f1\u06f2\u06f1\u06f2\u06f3\u06f4\u06f5\u06f6",
            "\u0660\u0669\u0661\u0662\u0661\u0662\u0663\u0664\u0665\u0666",
            " (0912) 123-4567 "
        ).forEach {
            val p = PhoneNormalizer.normalize(it)
            assertTrue("$it must be valid", p.valid)
            assertEquals("$it must canonicalize to one form", canonical, p.canonical)
        }
    }

    @Test
    fun rejectsShortCodesPremiumAndLetters() {
        listOf("123", "+981234", "+9891", "0912hello", "abc", "02112345678", "*123#").forEach {
            assertFalse(it, PhoneNormalizer.normalize(it).valid)
        }
    }

    @Test
    fun internationalE164AcceptedOnlyWithPrefix() {
        assertTrue(PhoneNormalizer.normalize("+447700900123").valid)
        assertFalse(PhoneNormalizer.normalize("447700900123").valid)
    }

    @Test
    fun parseBulkSplitsDedupesAndCountsInvalids() {
        val parsed = PhoneNormalizer.parseBulk(
            "09121234567,\u06f0\u06f9\u06f1\u06f2\u06f1\u06f2\u06f3\u06f4\u06f5\u06f6;00989121234567\n9121234567 123"
        )
        assertEquals(3, parsed.duplicates)
        assertEquals(2, parsed.numbers.size)
        assertEquals(1, parsed.validCount)
        assertEquals(1, parsed.invalidCount)
        assertEquals(canonical, parsed.numbers.first().canonical)
    }

    @Test
    fun spacesInsideOneNumberAreReassembled() {
        val parsed = PhoneNormalizer.parseBulk("0912 345 6789")
        assertEquals(1, parsed.numbers.size)
        assertEquals(canonical, parsed.numbers.first().canonical)
    }

    @Test
    fun persianAndArabicDigitsBecomeLatin() {
        assertEquals("0912", PhoneNormalizer.toLatinDigits("\u06f0\u06f9\u06f1\u06f2"))
        assertEquals("0912", PhoneNormalizer.toLatinDigits("\u0660\u0669\u0661\u0662"))
    }
}
