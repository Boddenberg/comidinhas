package br.com.boddenb.comidinhas.data.scraper

/**
 * Candidato de receita encontrado na busca (antes de extrair detalhes).
 */
data class RecipeCandidate(
    val title: String,
    val url: String,
    val rating: Double?,
    val reviewsCount: Int?,
    val timeText: String?
)

/**
 * Candidato ranqueado com score calculado e motivo para debug.
 */
data class RankedCandidate(
    val candidate: RecipeCandidate,
    val score: Double,
    val reason: String
)

/**
 * Detalhes completos de uma receita extraida de uma pagina do TudoGostoso.
 */
data class RecipeDetails(
    val title: String,
    val sourceUrl: String,
    val imageUrl: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val timeText: String?,
    val yieldText: String?,
    val difficultyText: String?,
    val rating: Double?,
    val reviewsCount: Int?
)

/**
 * Resultado encapsulado da busca e extracao no TudoGostoso.
 */
sealed class TudoGostosoResult {
    data class Success(
        val details: RecipeDetails,
        val topCandidates: List<RankedCandidate>
    ) : TudoGostosoResult()

    data class NotFound(val query: String) : TudoGostosoResult()

    data class Error(val message: String, val cause: Throwable? = null) : TudoGostosoResult()
}

