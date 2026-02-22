package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.scraper.CandidateRanker
import br.com.boddenb.comidinhas.data.scraper.RecipeDetails
import br.com.boddenb.comidinhas.data.scraper.RecipeLinkDiscovery
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoResult
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import java.util.UUID
import javax.inject.Inject

/**
 * Fachada que orquestra:
 * 1. Busca de candidatos via discovery (Modo A ou B)
 * 2. Ranqueamento
 * 3. Extracao de detalhes do top candidato
 * 4. Persistencia no repositorio (cache Supabase)
 * 5. Retorno do RecipeItem para o ViewModel
 *
 * O discovery e injetado como interface, permitindo trocar Modo A/B sem alterar esta classe.
 */
class SearchAndFetchTudoGostosoUseCase @Inject constructor(
    private val discovery: RecipeLinkDiscovery,
    private val extractor: br.com.boddenb.comidinhas.data.scraper.RecipeDetailExtractor,
    private val recipeRepository: RecipeRepository
) {
    companion object {
        private const val TAG = "TGUseCase"
    }

    suspend fun execute(query: String): TudoGostosoResult {
        val normalized = query.lowercase().trim()

        // 1. Descobre candidatos
        val candidates = discovery.discover(normalized)
        if (candidates.isEmpty()) {
            AppLogger.tgNotFound(normalized)
            return TudoGostosoResult.NotFound(normalized)
        }

        // 2. Ranqueia e pega top 3
        val ranked = CandidateRanker.rank(candidates, topN = 3)
        AppLogger.tgCandidates(ranked.map { "'${it.candidate.title}' | ${it.reason}" })

        // 3. Extrai detalhes do top 1
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

        // 4. Persiste no repositório em background
        saveToRepository(details, normalized)

        return TudoGostosoResult.Success(details = details, topCandidates = ranked)
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

