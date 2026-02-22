package br.com.boddenb.comidinhas.data.scraper

import android.util.Log
import kotlin.math.log10

/**
 * Responsavel por ranquear candidatos de receita.
 *
 * Formula: score = rating * log10(reviewsCount + 1)
 * Se rating ou reviewsCount forem nulos, score = 0.
 */
object CandidateRanker {

    private const val TAG = "CandidateRanker"

    /**
     * Ranqueia a lista de candidatos e retorna os top [topN].
     */
    fun rank(candidates: List<RecipeCandidate>, topN: Int = 3): List<RankedCandidate> {
        return candidates
            .map { candidate -> score(candidate) }
            .sortedByDescending { it.score }
            .take(topN)
            .also { ranked ->
                ranked.forEachIndexed { i, r ->
                    Log.d(TAG, "#${i + 1} '${r.candidate.title}' score=${r.score} | ${r.reason}")
                }
            }
    }

    private fun score(candidate: RecipeCandidate): RankedCandidate {
        val rating = candidate.rating
        val reviews = candidate.reviewsCount

        if (rating == null || reviews == null) {
            return RankedCandidate(
                candidate = candidate,
                score = 0.0,
                reason = "sem avaliacao ou contagem (score=0)"
            )
        }

        val score = rating * log10(reviews + 1.0)
        val reason = "nota $rating com $reviews avaliacoes, score ${"%.2f".format(score)}"

        return RankedCandidate(candidate = candidate, score = score, reason = reason)
    }

    /**
     * Converte strings de contagem como "1,7 mil", "2 mil", "15" para inteiro.
     */
    fun parseReviewCount(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return try {
            val cleaned = raw.trim().lowercase()
            when {
                cleaned.contains("mil") -> {
                    val number = cleaned
                        .replace("mil", "")
                        .replace(",", ".")
                        .trim()
                        .toDoubleOrNull() ?: return null
                    (number * 1000).toInt()
                }
                else -> {
                    cleaned
                        .replace(".", "")
                        .replace(",", "")
                        .trim()
                        .toIntOrNull()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel converter contagem '$raw': ${e.message}")
            null
        }
    }

    /**
     * Converte string de nota como "4,9" ou "4.9" para Double.
     */
    fun parseRating(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().replace(",", ".").toDoubleOrNull()
    }
}

