package com.revosleap.text.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumbersTest {
    @Test fun iranianFormatsShareOneCanonicalNumber() {
        listOf("+989121234567", "00989121234567", "989121234567", "09121234567",
            "9121234567", "۰۹۱۲۱۲۳۴۵۶۷", "٠٩١٢١٢٣٤٥٦٧", " (0912) 123-4567 ").forEach {
            assertEquals(NormalizedNumber("+989121234567", true), Numbers.normalize(it))
        }
    }
    @Test fun rejectShortCodesPremiumAndLetters() {
        listOf("123", "+981234", "0912hello", "abc", "+9891212345678", "02112345678", "*123#").forEach {
            assertFalse(it, Numbers.normalize(it).valid)
        }
    }
    @Test fun delimitersAndDeduplication() {
        val rows = Numbers.parse("09121234567,۰۹۱۲۱۲۳۴۵۶۷;00989121234567\n9121234567 123")
        assertEquals(5, rows.size)
        assertEquals(2, Numbers.deduplicate(rows).size)
        assertEquals(1, rows.count { !it.valid })
    }
    @Test fun namesArePreservedWhenDeduplicating() {
        val result = Numbers.deduplicate(listOf(Recipient("+989121234567"),
            Recipient("+989121234567", "Contact")))
        assertEquals("Contact", result.single().name)
    }
    @Test fun internationalE164Accepted() {
        assertTrue(Numbers.normalize("+447700900123").valid)
        assertFalse(Numbers.normalize("447700900123").valid)
    }
}
