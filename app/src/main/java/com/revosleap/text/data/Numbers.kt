package com.revosleap.text.data

data class NormalizedNumber(val number: String, val valid: Boolean)
data class Recipient(val number: String, val name: String = "", val valid: Boolean = true)

object Numbers {
    fun normalize(input: String): NormalizedNumber {
        val clean = buildString {
            input.trim().forEach { c ->
                when {
                    c in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
                    c in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
                    c.isWhitespace() || c in "-()\u200e\u200f\u202a\u202b\u202c\u2066\u2067\u2069" -> Unit
                    else -> append(c)
                }
            }
        }
        val number = when {
            clean.startsWith("0098") -> "+" + clean.drop(2)
            clean.startsWith("98") && clean.length == 12 -> "+$clean"
            clean.startsWith("09") && clean.length == 11 -> "+98" + clean.drop(1)
            clean.startsWith("9") && clean.length == 10 -> "+98$clean"
            clean.startsWith("00") -> "+" + clean.drop(2)
            else -> clean
        }
        // International E.164 mobile numbers only; no short codes, premium prefixes or landlines.
        val valid = if (number.startsWith("+98")) {
            Regex("^\\+989\\d{9}$").matches(number)
        } else {
            Regex("^\\+[1-9]\\d{7,14}$").matches(number)
        }
        return NormalizedNumber(number, valid)
    }

    fun parse(text: String): List<Recipient> =
        text.split(Regex("[\\s,;\u060C\u061B]+")).filter { it.isNotBlank() }.map {
            val normalized = normalize(it)
            Recipient(normalized.number, valid = normalized.valid)
        }

    fun deduplicate(recipients: List<Recipient>): List<Recipient> =
        recipients.groupBy { it.number }.map { (_, rows) ->
            rows.firstOrNull { it.name.isNotBlank() } ?: rows.first()
        }
}
