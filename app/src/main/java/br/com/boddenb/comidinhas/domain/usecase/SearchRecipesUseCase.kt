package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import br.com.boddenb.comidinhas.data.correction.TermCorrectionService
import br.com.boddenb.comidinhas.data.image.DallEImageGenerator
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.domain.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.util.fixEncoding
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse
import javax.inject.Inject

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

        cacheManager.get(normalizedQuery)?.let { cached ->
            AppLogger.cacheHit(normalizedQuery)
            AppLogger.searchEnd(normalizedQuery, "Cache em memória", cached.results.size)
            return cached
        }
        AppLogger.cacheMiss(normalizedQuery)

        AppLogger.d(AppLogger.OPENAI, "┌─ EXPANSÃO DE QUERY: \"$normalizedQuery\"")
        val expansion = openAiClient.expandQuery(normalizedQuery)
        AppLogger.d(AppLogger.OPENAI, "│  genérico=${expansion.isGeneric} raiz=${expansion.genericTerm} variações=${expansion.variations}")

        val (supabaseRecipes, resolvedQuery) = searchSupabaseWithExpansion(normalizedQuery, expansion)

        if (supabaseRecipes.isNotEmpty()) {
            val response = buildResponseFromEntities(
                entities = supabaseRecipes,
                query = resolvedQuery,
                isGeneric = expansion.isGeneric,
                featuredName = if (!expansion.isGeneric) normalizedQuery else null
            )
            cacheManager.put(normalizedQuery, response)
            AppLogger.cacheSave(normalizedQuery)
            AppLogger.searchEnd(resolvedQuery, "Supabase", response.results.size)
            return response
        }

        AppLogger.d(AppLogger.TUDO_GOSTOSO, "┌─ TG MULTI início: ${expansion.variations.size} variações")
        val tgDetails = tudoGostosoUseCase.executeMultiple(expansion.variations)

        if (tgDetails.isNotEmpty()) {
            val items = tgDetails.map { details ->
                val item = tudoGostosoUseCase.toRecipeItem(details)
                if (item.imageUrl.isNullOrEmpty()) {
                    AppLogger.imageStart(item.name, "fallback TudoGostoso")
                    val url = imageGenerator.generateRecipeImage(item.name)
                    AppLogger.imageFound("fallback", url.orEmpty().ifEmpty { "null" })
                    item.copy(imageUrl = url)
                } else item
            }

            val featuredId = if (!expansion.isGeneric) {
                items.firstOrNull { it.name.lowercase().contains(normalizedQuery) }?.id
                    ?: items.firstOrNull()?.id
            } else null

            val response = RecipeSearchResponse(
                query = resolvedQuery,
                results = items,
                isGeneric = expansion.isGeneric,
                featuredRecipeId = featuredId
            )
            cacheManager.put(normalizedQuery, response)
            AppLogger.cacheSave(normalizedQuery)
            AppLogger.searchEnd(resolvedQuery, "TudoGostoso", items.size)
            return response
        }

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

        val response = openAiClient.generateRecipes(resolvedQuery)
        val finalResponse = response.copy(isGeneric = expansion.isGeneric)
        cacheManager.put(normalizedQuery, finalResponse)
        AppLogger.cacheSave(normalizedQuery)
        AppLogger.searchEnd(resolvedQuery, "OpenAI", finalResponse.results.size)
        return finalResponse
    }

    private suspend fun searchSupabaseWithExpansion(
        normalizedQuery: String,
        expansion: OpenAiClient.QueryExpansion
    ): Pair<List<RecipeEntity>, String> {
        val direct = recipeRepository.getRecipesByQuery(normalizedQuery).getOrNull().orEmpty()
        if (direct.isNotEmpty()) {
            AppLogger.supabaseSearchFound(normalizedQuery, direct.size)
            return Pair(direct, normalizedQuery)
        }
        AppLogger.supabaseSearchEmpty(normalizedQuery)

        val genericTerm = expansion.genericTerm?.lowercase()?.trim()
        if (!genericTerm.isNullOrBlank() && genericTerm != normalizedQuery) {
            AppLogger.d(AppLogger.SUPABASE, "│  🔎 Buscando pelo raiz genérico: \"$genericTerm\"")
            val genericResults = recipeRepository.getRecipesByQuery(genericTerm).getOrNull().orEmpty()
            if (genericResults.isNotEmpty()) {
                AppLogger.supabaseSearchFound(genericTerm, genericResults.size)
                return Pair(genericResults, genericTerm)
            }
            AppLogger.supabaseSearchEmpty(genericTerm)
        }

        AppLogger.correctionStart(normalizedQuery)
        val corrected = termCorrectionService.correctTerm(normalizedQuery)
        if (corrected != normalizedQuery) {
            val correctedResult = recipeRepository.getRecipesByQuery(corrected).getOrNull().orEmpty()
            if (correctedResult.isNotEmpty()) {
                AppLogger.supabaseSearchFound(corrected, correctedResult.size)
                return Pair(correctedResult, corrected)
            }
            AppLogger.supabaseSearchEmpty(corrected)
        }

        return Pair(emptyList(), normalizedQuery)
    }

    private suspend fun buildResponseFromEntities(
        entities: List<RecipeEntity>,
        query: String,
        isGeneric: Boolean,
        featuredName: String?
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

        val sorted = if (!isGeneric && featuredName != null) {
            val featured = items.firstOrNull { it.name.lowercase().contains(featuredName) }
            if (featured != null) listOf(featured) + items.filter { it.id != featured.id }
            else items
        } else items

        val featuredId = if (!isGeneric) sorted.firstOrNull()?.id else null

        return RecipeSearchResponse(
            query = query,
            results = sorted,
            isGeneric = isGeneric,
            featuredRecipeId = featuredId
        )
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
