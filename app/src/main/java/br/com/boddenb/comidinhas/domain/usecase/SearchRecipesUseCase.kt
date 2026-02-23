package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import br.com.boddenb.comidinhas.data.correction.TermCorrectionService
import br.com.boddenb.comidinhas.data.image.DallEImageGenerator
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.data.scraper.RecipeDetails
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
            AppLogger.searchPanorama(
                query = normalizedQuery,
                source = "Cache em memória",
                included = toPanoramaRecipes(cached.results, "Cache"),
                isGeneric = cached.isGeneric,
                featuredId = cached.featuredRecipeId
            )
            return cached
        }
        AppLogger.cacheMiss(normalizedQuery)

        AppLogger.d(AppLogger.OPENAI, "┌─ EXPANSÃO DE QUERY: \"$normalizedQuery\"")
        val expansion = openAiClient.expandQuery(normalizedQuery)
        AppLogger.d(AppLogger.OPENAI, "│  genérico=${expansion.isGeneric} raiz=${expansion.genericTerm} variações=${expansion.variations}")

        val (supabaseRecipes, resolvedQuery) = searchSupabaseWithExpansion(normalizedQuery, expansion)

        val supabaseIsSufficient = supabaseRecipes.size >= 3
        if (supabaseIsSufficient) {
            val response = buildResponseFromEntities(
                entities = supabaseRecipes,
                query = resolvedQuery,
                isGeneric = expansion.isGeneric,
                featuredName = if (!expansion.isGeneric) normalizedQuery else null
            )
            cacheManager.put(normalizedQuery, response)
            AppLogger.cacheSave(normalizedQuery)
            AppLogger.searchEnd(resolvedQuery, "Supabase", response.results.size)
            AppLogger.searchPanorama(
                query = resolvedQuery,
                source = "Supabase",
                included = toPanoramaRecipes(response.results, "Supabase"),
                isGeneric = response.isGeneric,
                featuredId = response.featuredRecipeId
            )
            return response
        }

        if (supabaseRecipes.isNotEmpty()) {
            AppLogger.d(AppLogger.SUPABASE, "│  ⚡ ${supabaseRecipes.size} receita(s) no Supabase, buscando mais para complementar...")
        }

        AppLogger.d(AppLogger.TUDO_GOSTOSO, "┌─ TG MULTI início: ${expansion.variations.size} variações")
        var tgDetails = tudoGostosoUseCase.executeMultiple(expansion.variations)

        if (tgDetails.size < 2) {
            val genericTerm = expansion.genericTerm?.lowercase()?.trim()
            if (!genericTerm.isNullOrBlank() && genericTerm != normalizedQuery) {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  🔁 Fallback: apenas ${tgDetails.size} resultado(s) → buscando todos os candidatos do raiz \"$genericTerm\"")
                val fallbackDetails = tudoGostosoUseCase.executeAllCandidates(genericTerm)
                val existingTitles = tgDetails.map { it.title.lowercase().trim() }.toSet()
                val newEntries = fallbackDetails.filter { it.title.lowercase().trim() !in existingTitles }
                tgDetails = (tgDetails + newEntries).distinctBy { it.title.lowercase().trim() }
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  🔁 Após fallback raiz: ${tgDetails.size} receita(s) no total")
            }
        }

        val supabaseItems = if (supabaseRecipes.isNotEmpty()) {
            buildResponseFromEntities(
                entities = supabaseRecipes,
                query = resolvedQuery,
                isGeneric = expansion.isGeneric,
                featuredName = if (!expansion.isGeneric) normalizedQuery else null
            ).results
        } else emptyList()

        // Mapa de imageUrl por nome para saber a origem real da imagem no panorama
        val tgImageSourceMap = mutableMapOf<String, String>()
        val tgItems = tgDetails.map { details ->
            val item = tudoGostosoUseCase.toRecipeItem(details)
            if (item.imageUrl.isNullOrEmpty()) {
                AppLogger.imageStart(item.name, "fallback TudoGostoso")
                val url = imageGenerator.generateRecipeImage(item.name)
                AppLogger.imageFound("fallback", url.orEmpty().ifEmpty { "null" })
                tgImageSourceMap[item.name] = "Brave/Unsplash (fallback)"
                item.copy(imageUrl = url)
            } else {
                tgImageSourceMap[item.name] = "TudoGostoso (scraped)"
                item
            }
        }

        val supabaseNames = supabaseItems.map { it.name.lowercase().trim() }.toSet()
        val newTgItems = tgItems.filter { it.name.lowercase().trim() !in supabaseNames }
        val combinedItems = (supabaseItems + newTgItems).distinctBy { it.name.lowercase().trim() }

        if (combinedItems.size >= 3) {
            val source = when {
                supabaseItems.isNotEmpty() && newTgItems.isNotEmpty() -> "Supabase+TudoGostoso"
                newTgItems.isNotEmpty() -> "TudoGostoso"
                else -> "Supabase"
            }
            val featuredId = if (!expansion.isGeneric) {
                combinedItems.firstOrNull { it.name.lowercase().contains(normalizedQuery) }?.id
                    ?: combinedItems.firstOrNull()?.id
            } else null
            val response = RecipeSearchResponse(
                query = resolvedQuery,
                results = combinedItems,
                isGeneric = expansion.isGeneric,
                featuredRecipeId = featuredId
            )
            cacheManager.put(normalizedQuery, response)
            AppLogger.cacheSave(normalizedQuery)
            AppLogger.searchEnd(resolvedQuery, source, combinedItems.size)
            AppLogger.searchPanorama(
                query = resolvedQuery,
                source = source,
                included = toPanoramaRecipes(combinedItems, source, tgImageSourceMap, tgDetails),
                isGeneric = expansion.isGeneric,
                featuredId = featuredId
            )
            return response
        }

        AppLogger.validationStart(resolvedQuery)
        val validationResult = openAiClient.validateTerm(resolvedQuery)
        if (validationResult is OpenAiClient.TermValidationResult.Invalid) {
            if (combinedItems.isNotEmpty()) {
                AppLogger.d(AppLogger.OPENAI, "│  ⚠️ Termo inválido mas há ${combinedItems.size} resultado(s) existente(s), retornando...")
                val source = if (supabaseItems.isNotEmpty()) "Supabase" else "TudoGostoso"
                val featuredId = if (!expansion.isGeneric) combinedItems.firstOrNull()?.id else null
                val response = RecipeSearchResponse(
                    query = resolvedQuery,
                    results = combinedItems,
                    isGeneric = expansion.isGeneric,
                    featuredRecipeId = featuredId
                )
                cacheManager.put(normalizedQuery, response)
                AppLogger.cacheSave(normalizedQuery)
                AppLogger.searchEnd(resolvedQuery, source, combinedItems.size)
                AppLogger.searchPanorama(
                    query = resolvedQuery,
                    source = source,
                    included = toPanoramaRecipes(combinedItems, source, tgImageSourceMap, tgDetails),
                    isGeneric = expansion.isGeneric,
                    featuredId = featuredId
                )
                return response
            }
            AppLogger.validationInvalid(resolvedQuery, validationResult.reason)
            AppLogger.searchError(query, validationResult.reason)
            AppLogger.searchPanorama(
                query = resolvedQuery,
                source = "—",
                included = emptyList(),
                discarded = listOf(AppLogger.DiscardedRecipe(name = query, reason = "Termo inválido: ${validationResult.reason}")),
                isGeneric = expansion.isGeneric
            )
            return RecipeSearchResponse(
                query = resolvedQuery,
                results = emptyList(),
                errorMessage = "Não foi possível encontrar receitas para '$query'. ${validationResult.reason}"
            )
        }
        AppLogger.validationValid(resolvedQuery)

        val needed = maxOf(3, expansion.variations.size) - combinedItems.size
        val existingNames = combinedItems.map { it.name.lowercase().trim() }.toSet()
        val missingVariations = expansion.variations.filter { v ->
            existingNames.none { it.contains(v.lowercase().trim().take(10)) }
        }

        val openAiQuery: String
        val openAiMaxResults: Int
        if (missingVariations.isNotEmpty() && missingVariations.size > 1) {
            val variationsText = missingVariations.take(needed).joinToString(", ")
            AppLogger.d(AppLogger.OPENAI, "│  📤 Gerando para ${missingVariations.take(needed).size} variações faltantes: $variationsText")
            openAiQuery = "Gere 1 receita para cada uma dessas variações: $variationsText"
            openAiMaxResults = missingVariations.size
        } else {
            openAiQuery = resolvedQuery
            openAiMaxResults = needed
        }

        val aiResponse = openAiClient.generateRecipes(openAiQuery, openAiMaxResults)
        val aiImageSourceMap = mutableMapOf<String, String>()
        val aiItemsWithImages = aiResponse.results.map { item ->
            if (item.imageUrl.isNullOrEmpty()) {
                AppLogger.imageStart(item.name, "OpenAI fallback")
                val url = imageGenerator.generateRecipeImage(item.name)
                AppLogger.imageFound("OpenAI fallback", url.orEmpty().ifEmpty { "null" })
                aiImageSourceMap[item.name] = "Brave/Unsplash (fallback)"
                item.copy(imageUrl = url)
            } else {
                aiImageSourceMap[item.name] = "OpenAI (inclusa na geração)"
                item
            }
        }

        val aiNames = combinedItems.map { it.name.lowercase().trim() }.toSet()
        val newAiItems = aiItemsWithImages.filter { it.name.lowercase().trim() !in aiNames }
        val allFinalItems = combinedItems + newAiItems

        val source = when {
            combinedItems.isNotEmpty() -> "Supabase+OpenAI"
            else -> "OpenAI"
        }
        val featuredId = if (!expansion.isGeneric) {
            allFinalItems.firstOrNull { it.name.lowercase().contains(normalizedQuery) }?.id
                ?: allFinalItems.firstOrNull()?.id
        } else null

        val finalResponse = RecipeSearchResponse(
            query = resolvedQuery,
            results = allFinalItems,
            isGeneric = expansion.isGeneric,
            featuredRecipeId = featuredId
        )
        cacheManager.put(normalizedQuery, finalResponse)
        AppLogger.cacheSave(normalizedQuery)
        AppLogger.searchEnd(resolvedQuery, source, allFinalItems.size)
        AppLogger.searchPanorama(
            query = resolvedQuery,
            source = source,
            included = toPanoramaRecipes(allFinalItems, source, tgImageSourceMap + aiImageSourceMap, tgDetails),
            isGeneric = expansion.isGeneric,
            featuredId = featuredId
        )
        return finalResponse
    }

    /**
     * Converte lista de RecipeItem em PanoramaRecipe para o log de diagnóstico.
     * A origem da imagem é inferida a partir da URL e do mapa auxiliar construído durante o fluxo.
     */
    private fun toPanoramaRecipes(
        items: List<RecipeItem>,
        defaultSource: String,
        imageSourceMap: Map<String, String> = emptyMap(),
        tgDetails: List<RecipeDetails> = emptyList()
    ): List<AppLogger.PanoramaRecipe> {
        val tgUrlByTitle = tgDetails.associate { it.title.lowercase().trim() to it.sourceUrl }
        return items.map { item ->
            val recipeSource = when {
                defaultSource.contains("Cache") -> "Cache em memória"
                defaultSource.contains("Supabase") && defaultSource.contains("TudoGostoso") -> {
                    if (tgDetails.any { it.title.lowercase().trim() == item.name.lowercase().trim() }) "TudoGostoso"
                    else "Supabase"
                }
                defaultSource.contains("Supabase") && defaultSource.contains("OpenAI") -> {
                    if (tgDetails.any { it.title.lowercase().trim() == item.name.lowercase().trim() }) "TudoGostoso"
                    else if (imageSourceMap[item.name]?.contains("OpenAI") == true) "OpenAI"
                    else "Supabase"
                }
                else -> defaultSource
            }
            val imageSource = imageSourceMap[item.name] ?: inferImageSource(item.imageUrl)
            val sourceUrl = tgUrlByTitle[item.name.lowercase().trim()]
            AppLogger.PanoramaRecipe(
                id = item.id,
                name = item.name,
                recipeSource = recipeSource,
                imageUrl = item.imageUrl,
                imageSource = imageSource,
                ingredientsCount = item.ingredients.size,
                stepsCount = item.instructions.size,
                cookingTime = item.cookingTime,
                servings = item.servings,
                sourceUrl = sourceUrl
            )
        }
    }

    private fun inferImageSource(url: String?): String? = when {
        url.isNullOrEmpty() -> "⚠️ Sem imagem"
        url.contains("supabase.co") -> "Supabase Storage"
        url.contains("itdg.com.br") || url.contains("tudogostoso") -> "TudoGostoso (scraped)"
        url.contains("unsplash.com") -> "Unsplash"
        url.contains("brave") -> "Brave Search"
        url.contains("oaidalleapiprodscus") -> "DALL-E (OpenAI)"
        else -> "Desconhecida"
    }

    private suspend fun searchSupabaseWithExpansion(
        normalizedQuery: String,
        expansion: OpenAiClient.QueryExpansion
    ): Pair<List<RecipeEntity>, String> {
        val accumulated = mutableListOf<RecipeEntity>()

        // 1. Exact match pelo search_query
        val direct = recipeRepository.getRecipesByQuery(normalizedQuery).getOrNull().orEmpty()
        accumulated.addAll(direct)
        if (direct.isNotEmpty()) {
            AppLogger.supabaseSearchFound(normalizedQuery, direct.size)
        } else {
            AppLogger.supabaseSearchEmpty(normalizedQuery)
        }

        // 2. Se a query for específica (não genérica) e tiver um termo raiz, busca pelo raiz
        val genericTerm = expansion.genericTerm?.lowercase()?.trim()
        if (!genericTerm.isNullOrBlank() && genericTerm != normalizedQuery) {
            AppLogger.d(AppLogger.SUPABASE, "│  🔎 Buscando pelo raiz genérico: \"$genericTerm\"")
            val genericResults = recipeRepository.getRecipesByQuery(genericTerm).getOrNull().orEmpty()
            if (genericResults.isNotEmpty()) {
                AppLogger.supabaseSearchFound(genericTerm, genericResults.size)
                accumulated.addAll(genericResults)
            } else {
                AppLogger.supabaseSearchEmpty(genericTerm)
            }
        }

        // 3. Busca por nome usando ilike com o termo genérico (ou o próprio termo se genérico)
        val searchTerm = if (!genericTerm.isNullOrBlank()) genericTerm else normalizedQuery
        val byName = recipeRepository.searchRecipesByName(searchTerm).getOrNull().orEmpty()
        if (byName.isNotEmpty()) {
            AppLogger.d(AppLogger.SUPABASE, "│  🔎 Por nome (\"$searchTerm\"): ${byName.size} receita(s)")
            accumulated.addAll(byName)
        }

        // 4. Tenta correção de termo se ainda poucos resultados
        val unique = accumulated.distinctBy { it.id }
        if (unique.size < 2) {
            AppLogger.correctionStart(normalizedQuery)
            val corrected = termCorrectionService.correctTerm(normalizedQuery)
            if (corrected != normalizedQuery) {
                val correctedResults = recipeRepository.getRecipesByQuery(corrected).getOrNull().orEmpty()
                if (correctedResults.isNotEmpty()) {
                    AppLogger.supabaseSearchFound(corrected, correctedResults.size)
                    accumulated.addAll(correctedResults)
                    val byNameCorrected = recipeRepository.searchRecipesByName(corrected).getOrNull().orEmpty()
                    accumulated.addAll(byNameCorrected)
                } else {
                    AppLogger.supabaseSearchEmpty(corrected)
                }
            }
        }

        val finalUnique = accumulated.distinctBy { it.id }
        if (finalUnique.isNotEmpty()) {
            AppLogger.d(AppLogger.SUPABASE, "🔄 ${finalUnique.size} receita(s) únicas encontradas no Supabase")
            return Pair(finalUnique, genericTerm?.takeIf { it.isNotBlank() } ?: normalizedQuery)
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
                cookingTime = null,
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
