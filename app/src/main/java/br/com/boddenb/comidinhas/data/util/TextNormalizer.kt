package br.com.boddenb.comidinhas.data.util

import java.text.Normalizer

/**
 * Normalização centralizada de texto para buscas.
 *
 * Substitui as implementações duplicadas que existiam em
 * RecipeSupabaseRepository, RecipeCacheManager e TermCorrectionService.
 */
object TextNormalizer {

    /**
     * Converte para minúsculas e remove acentos/diacríticos.
     * Exemplo: "Lasanha à Bolonhesa" → "lasanha a bolonhesa"
     */
    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfd, "")
    }
}

