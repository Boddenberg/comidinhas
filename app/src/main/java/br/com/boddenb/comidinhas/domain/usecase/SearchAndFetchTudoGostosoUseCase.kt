package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.domain.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.scraper.CandidateRanker
import br.com.boddenb.comidinhas.data.scraper.RecipeDetails
import br.com.boddenb.comidinhas.data.scraper.RecipeLinkDiscovery
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoResult
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject

class SearchAndFetchTudoGostosoUseCase @Inject constructor(
    private val discovery: RecipeLinkDiscovery,
    private val extractor: br.com.boddenb.comidinhas.data.scraper.RecipeDetailExtractor,
    private val recipeRepository: RecipeRepository
) {

    suspend fun execute(query: String): TudoGostosoResult {
        val normalized = query.lowercase().trim()

        val candidates = discovery.discover(normalized)
        if (candidates.isEmpty()) {
            AppLogger.tgNotFound(normalized)
            return TudoGostosoResult.NotFound(normalized)
        }

        val ranked = CandidateRanker.rank(candidates, topN = 3)
        AppLogger.tgCandidates(ranked.map { "'${it.candidate.title}' | ${it.reason}" })

        val top = ranked.first()
        AppLogger.tgExtracting(top.candidate.url)
        val details = extractor.extract(top.candidate.url)
            ?: run {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Top 1 falhou, tentando top 2...")
                ranked.getOrNull(1)?.let { extractor.extract(it.candidate.url) }
            }

        if (details == null) {
            AppLogger.tgError("Não foi possível extrair detalhes de nenhum candidato")
            return TudoGostosoResult.Error("Nao foi possivel extrair a receita do TudoGostoso")
        }

        AppLogger.tgSuccess(details.title, details.imageUrl, details.ingredients.size, details.steps.size)
        saveToRepository(details, normalized)

        return TudoGostosoResult.Success(details = details, topCandidates = ranked)
    }

    /**
     * Busca múltiplas variações em paralelo e retorna todas as que tiveram sucesso,
     * deduplicando por nome normalizado.
     */
    suspend fun executeMultiple(variations: List<String>): List<RecipeDetails> = coroutineScope {
        AppLogger.d(AppLogger.TUDO_GOSTOSO, "┌─ TG MULTI: ${variations.size} variações → ${variations.joinToString(", ")}")

        val deferred = variations.map { variation ->
            async {
                runCatching { execute(variation) }.getOrNull()
            }
        }

        val results = deferred.awaitAll()
            .filterIsInstance<TudoGostosoResult.Success>()
            .map { it.details }
            .distinctBy { it.title.lowercase().trim() }

        AppLogger.d(AppLogger.TUDO_GOSTOSO, "└─ TG MULTI: ${results.size} receitas encontradas de ${variations.size} variações")
        results
    }

    fun toRecipeItem(details: RecipeDetails): RecipeItem {
        return RecipeItem(
            id = UUID.randomUUID().toString(),
            name = details.title,
            ingredients = details.ingredients,
            instructions = details.steps,
            imageUrl = details.imageUrl,
            cookingTime = details.timeText,
            servings = details.yieldText
        )
    }

    private suspend fun saveToRepository(details: RecipeDetails, searchQuery: String) {
        try {
            val entity = RecipeEntity(
                id = UUID.randomUUID().toString(),
                name = details.title,
                ingredients = details.ingredients,
                instructions = details.steps,
                imageUrl = details.imageUrl,
                cookingTime = details.timeText,
                servings = details.yieldText,
                searchQuery = searchQuery,
                source = "TudoGostoso",
                createdAt = System.currentTimeMillis()
            )
            val result = recipeRepository.saveRecipe(entity)
            if (result.isSuccess) {
                AppLogger.supabaseSaveRecipe(details.title, entity.id)
            } else {
                AppLogger.supabaseError("saveRecipe(TG:${details.title})", result.exceptionOrNull()?.message ?: "Erro desconhecido")
            }
        } catch (e: Exception) {
            AppLogger.supabaseError("saveToRepository(TG)", e.message ?: "Exceção")
        }
    }
}





