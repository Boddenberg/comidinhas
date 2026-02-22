package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import br.com.boddenb.comidinhas.data.correction.TermCorrectionService
import br.com.boddenb.comidinhas.data.image.DallEImageGenerator
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoResult
import br.com.boddenb.comidinhas.data.util.fixEncoding
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse
import javax.inject.Inject

/**
 * Orquestra o fluxo completo de busca de receitas.
 *
 * Ordem de prioridade:
 * 1. Cache em memória
 * 2. Supabase (receitas já salvas)
 * 3. Correção de termo + nova busca no Supabase
 * 4. TudoGostoso scraping
 * 5. Validação do termo (GPT-4o-mini)
 * 6. Geração via OpenAI GPT-4o
 *
 * O [OpenAiClient] passa a ser apenas um cliente HTTP para a OpenAI.
 */
class SearchRecipesUseCase @Inject constructor(
    private val openAiClient: OpenAiClient,
    private val recipeRepository: RecipeRepository,
    private val cacheManager: RecipeCacheManager,
    private val termCorrectionService: TermCorrectionService,
    private val imageGenerator: DallEImageGenerator,
    private val tudoGostosoUseCase: SearchAndFetchTudoGostosoUseCase
) {
    suspend operator fun invoke(query: String): RecipeSearchResponse {
        AppLogger.searchStart(query)

        val normalizedQuery = query.lowercase().trim()

        // 1. Cache em memória
        cacheManager.get(normalizedQuery)?.let { cached ->
            AppLogger.cacheHit(normalizedQuery)
            AppLogger.searchEnd(normalizedQuery, "Cache em memória", cached.results.size)
            return cached
        }
        AppLogger.cacheMiss(normalizedQuery)

        // 2. Supabase + correção de termos
        val (foundRecipes, resolvedQuery) = searchSupabaseWithCorrection(normalizedQuery)

        if (foundRecipes.isNotEmpty()) {
            val response = buildResponseFromEntities(foundRecipes, resolvedQuery)
            cacheManager.put(resolvedQuery, response)
            AppLogger.cacheSave(resolvedQuery)
            AppLogger.searchEnd(resolvedQuery, "Supabase", response.results.size)
            return response
        }

        // 3. TudoGostoso
        val tgResult = runCatching { tudoGostosoUseCase.execute(resolvedQuery) }.getOrNull()
        when (tgResult) {
            is TudoGostosoResult.Success -> {
                val details = tgResult.details
                AppLogger.tgSuccess(details.title, details.imageUrl, details.ingredients.size, details.steps.size)
                val item = tudoGostosoUseCase.toRecipeItem(details).let { base ->
                    if (base.imageUrl.isNullOrEmpty()) {
                        AppLogger.imageStart(base.name, "fallback TudoGostoso")
                        val url = imageGenerator.generateRecipeImage(base.name)
                        AppLogger.imageFound("fallback", url.orEmpty().ifEmpty { "null" })
                        base.copy(imageUrl = url)
                    } else base
                }
                val response = RecipeSearchResponse(query = resolvedQuery, results = listOf(item))
                cacheManager.put(resolvedQuery, response)
                AppLogger.searchEnd(resolvedQuery, "TudoGostoso", 1)
                return response
            }
            is TudoGostosoResult.NotFound -> AppLogger.tgNotFound(resolvedQuery)
            is TudoGostosoResult.Error    -> AppLogger.tgError(tgResult.message)
            null                          -> AppLogger.tgError("Exceção inesperada no TudoGostoso")
        }

        // 4. Validação do termo antes de chamar OpenAI
        AppLogger.validationStart(resolvedQuery)
        val validationResult = openAiClient.validateTerm(resolvedQuery)
        if (validationResult is OpenAiClient.TermValidationResult.Invalid) {
            AppLogger.validationInvalid(resolvedQuery, validationResult.reason)
            AppLogger.searchError(query, validationResult.reason)
            return RecipeSearchResponse(
                query = resolvedQuery,
                results = emptyList(),
                errorMessage = "Não foi possível encontrar receitas para '$query'. ${validationResult.reason}"
            )
        }
        AppLogger.validationValid(resolvedQuery)

        // 5. Geração via OpenAI
        val response = openAiClient.generateRecipes(resolvedQuery)
        cacheManager.put(resolvedQuery, response)
        AppLogger.cacheSave(resolvedQuery)
        AppLogger.searchEnd(resolvedQuery, "OpenAI", response.results.size)
        return response
    }

    private suspend fun searchSupabaseWithCorrection(
        normalizedQuery: String
    ): Pair<List<RecipeEntity>, String> {
        // Busca direta
        val direct = recipeRepository.getRecipesByQuery(normalizedQuery).getOrNull().orEmpty()
        if (direct.isNotEmpty()) {
            AppLogger.supabaseSearchFound(normalizedQuery, direct.size)
            return Pair(direct, normalizedQuery)
        }
        AppLogger.supabaseSearchEmpty(normalizedQuery)

        // Tenta correção de termo
        AppLogger.correctionStart(normalizedQuery)
        val corrected = termCorrectionService.correctTerm(normalizedQuery)
        if (corrected == normalizedQuery) return Pair(emptyList(), normalizedQuery)

        val correctedResult = recipeRepository.getRecipesByQuery(corrected).getOrNull().orEmpty()
        if (correctedResult.isNotEmpty()) {
            AppLogger.supabaseSearchFound(corrected, correctedResult.size)
            return Pair(correctedResult, corrected)
        }
        AppLogger.supabaseSearchEmpty(corrected)
        return Pair(emptyList(), corrected)
    }

    private suspend fun buildResponseFromEntities(
        entities: List<RecipeEntity>,
        query: String
    ): RecipeSearchResponse {
        val unique = entities
            .distinctBy { it.id }
            .distinctBy { it.name.lowercase().trim() }

        AppLogger.d(AppLogger.SUPABASE, "🔄 ${unique.size} receita(s) únicas (de ${entities.size} recuperadas)")

        val items = unique.map { entity ->
            val imageUrl = resolveImageUrl(entity)
            RecipeItem(
                id = entity.id,
                name = fixEncoding(entity.name),
                ingredients = fixEncoding(entity.ingredients),
                instructions = fixEncoding(entity.instructions),
                imageUrl = imageUrl,
                cookingTime = entity.cookingTime,
                servings = entity.servings
            )
        }
        return RecipeSearchResponse(query = query, results = items)
    }

    private suspend fun resolveImageUrl(entity: RecipeEntity): String? {
        val url = entity.imageUrl
        if (!url.isNullOrEmpty() && isValidStorageUrl(url)) {
            AppLogger.d(AppLogger.SUPABASE, "   ✅ ${entity.name} → imagem já disponível")
            return url
        }
        AppLogger.imageStart(entity.name, "fallback")
        val generated = imageGenerator.generateRecipeImage(entity.name)
        AppLogger.imageFound("fallback", generated.orEmpty().ifEmpty { "null" })
        return generated
    }

    private fun isValidStorageUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.contains("oaidalleapiprodscus.blob.core.windows.net")) return false
        val validDomains = listOf("supabase.co", "unsplash.com", "images.unsplash.com")
        return validDomains.any { url.contains(it) }
    }
}

