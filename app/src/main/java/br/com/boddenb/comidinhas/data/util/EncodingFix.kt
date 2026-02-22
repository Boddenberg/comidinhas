package br.com.boddenb.comidinhas.data.util

import android.text.Html

/**
 * Corrige textos com:
 * 1. HTML entities  → &ccedil; → ç,  &uacute; → ú,  &eacute; → é, etc.
 * 2. Encoding corrompido (Latin-1 mal interpretado como UTF-8):
 *    "AdicioneÃ\u00A0a farinha" → "Adicione a farinha"
 *    "Ã§" → "ç"   "Ã£" → "ã"   "Ã©" → "é"   "Ãº" → "ú"
 */
fun fixEncoding(text: String): String {
    if (text.isBlank()) return text

    // 1. Decodifica HTML entities primeiro (&ccedil; &uacute; &#233; etc.)
    val decoded = if (text.contains('&') && text.contains(';')) {
        @Suppress("DEPRECATION")
        Html.fromHtml(text).toString()
    } else {
        text
    }

    // 2. Corrige encoding corrompido (Latin-1 interpretado como UTF-8)
    val needsFix = decoded.any { it == 'Ã' } ||
                   decoded.contains('\u00C3') ||
                   decoded.contains("Â·") ||
                   decoded.contains("â€") ||
                   decoded.contains("Ã\u00A0")

    if (!needsFix) return decoded

    return try {
        String(decoded.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
    } catch (_: Exception) {
        decoded
    }
}

fun fixEncoding(list: List<String>): List<String> = list.map { fixEncoding(it) }

