package com.revosleap.text.util

data class PhoneNumber(val canonical: String, val valid: Boolean, val raw: String)

data class ParsedList(val numbers: List<PhoneNumber>, val duplicates: Int) {
    val validCount: Int get() = numbers.count { it.valid }
    val invalidCount: Int get() = numbers.count { !it.valid }
}

/**
 * Normalizes phone numbers to one canonical form.
 * Iranian mobiles become "+98" + 10 digits; other international numbers keep "+" + digits.
 */
object PhoneNormalizer {

    private val separators = Regex("[\\s\\-()/\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val chunkSplit = Regex("[\\n\\r,;\\u060C\\u061B]+")
    private val spaceSplit = Regex("\\s+")

    /** Persian (۰-۹) and Arabic-Indic (٠-٩) digits to Latin. */
    fun toLatinDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Normalizes ONE number (all spaces / dashes / parentheses are ignored). */
    fun normalize(raw: String): PhoneNumber {
        val clean = separators.replace(toLatinDigits(raw), "")
        if (clean.isEmpty()) return PhoneNumber("", false, raw)

        val hasPlus = clean.startsWith("+")
        val body = if (hasPlus) clean.substring(1) else clean
        if (body.isEmpty() || body.any { it !in '0'..'9' }) {
            return PhoneNumber(clean, false, raw)
        }

        // International digits (without + / 00), or null for a local-looking number.
        val intl: String? = when {
            hasPlus -> body
            body.startsWith("00") -> body.substring(2)
            else -> null
        }

        if (intl != null) {
            if (intl.startsWith("98")) {
                var national = intl.substring(2)
                if (national.length == 11 && national.startsWith("0")) national = national.substring(1)
                val ok = national.length == 10 && national.startsWith("9")
                return PhoneNumber("+98$national", ok, raw)
            }
            val ok = intl.length in 8..15 && !intl.startsWith("0")
            return PhoneNumber("+$intl", ok, raw)
        }

        return when {
            body.length == 12 && body.startsWith("989") -> PhoneNumber("+" + body, true, raw)
            body.length == 11 && body.startsWith("09") -> PhoneNumber("+98" + body.substring(1), true, raw)
            body.length == 10 && body.startsWith("9") -> PhoneNumber("+98$body", true, raw)
            else -> PhoneNumber(body, false, raw)
        }
    }

    /**
     * Parses a pasted block of numbers separated by newline / comma / semicolon / space.
     * A chunk like "0912 345 6789" (spaces inside one number) is re-assembled greedily.
     * Duplicates (same canonical form) are removed and counted.
     */
    fun parseBulk(text: String): ParsedList {
        val out = LinkedHashMap<String, PhoneNumber>()
        var duplicates = 0

        fun add(p: PhoneNumber) {
            if (p.canonical.isEmpty()) return
            if (out.containsKey(p.canonical)) duplicates++ else out[p.canonical] = p
        }

        for (chunk in chunkSplit.split(text)) {
            val trimmed = chunk.trim()
            if (trimmed.isEmpty()) continue
            val tokens = trimmed.split(spaceSplit).filter { it.isNotEmpty() }
            if (tokens.size == 1) {
                add(normalize(tokens[0]))
                continue
            }
            val acc = StringBuilder()
            for (t in tokens) {
                acc.append(t)
                val candidate = normalize(acc.toString())
                if (candidate.valid) {
                    add(candidate)
                    acc.setLength(0)
                }
            }
            if (acc.isNotEmpty()) add(normalize(acc.toString()))
        }
        return ParsedList(out.values.toList(), duplicates)
    }
}
